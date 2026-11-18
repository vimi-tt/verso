package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class LastFmAccountState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val sessionKey: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val scrobbleEnabled: Boolean = true,
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Handles Last.fm mobile session authentication, Now Playing updates,
 * 30-second scrobbling, and multi-source genre resolution (Last.fm tags + iTunes Search).
 */
internal class LastFmClient(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long,
) {
    private val sessionFile = File(context.applicationContext.filesDir, "lastfm_session.json")
    private val lock = Any()
    private val genreCache = ConcurrentHashMap<String, String>()
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<LastFmAccountState> = _state.asStateFlow()

    fun signIn(
        username: String,
        password: String,
    ) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter your Last.fm username and password") }
            return
        }
        _state.update { it.copy(isSigningIn = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = runCatching {
                val cookiesMap = linkedMapOf<String, String>()
                val getLogin = Request.Builder()
                    .url("https://www.last.fm/login")
                    .header("User-Agent", WEB_USER_AGENT)
                    .get()
                    .build()
                val csrfToken = okHttpClient.newCall(getLogin).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { raw ->
                        val pair = raw.substringBefore(';').split('=', limit = 2)
                        if (pair.size == 2) cookiesMap[pair[0].trim()] = pair[1].trim()
                    }
                    val html = resp.body?.string().orEmpty()
                    Regex("""name=['"]csrfmiddlewaretoken['"]\s+value=['"]([^'"]+)['"]""")
                        .find(html)?.groupValues?.getOrNull(1)
                        ?: cookiesMap["csrftoken"].orEmpty()
                }
                if (csrfToken.isBlank()) error("Could not reach Last.fm login")

                val cookieHeader = cookiesMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                val form = FormBody.Builder()
                    .add("csrfmiddlewaretoken", csrfToken)
                    .add("username_or_email", cleanUser)
                    .add("password", password)
                    .add("next", "/home")
                    .build()
                val postLogin = Request.Builder()
                    .url("https://www.last.fm/login")
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Referer", "https://www.last.fm/login")
                    .header("Origin", "https://www.last.fm")
                    .header("Cookie", cookieHeader)
                    .post(form)
                    .build()
                okHttpClient.newCall(postLogin).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { raw ->
                        val pair = raw.substringBefore(';').split('=', limit = 2)
                        if (pair.size == 2) cookiesMap[pair[0].trim()] = pair[1].trim()
                    }
                    val body = resp.body?.string().orEmpty()
                    val hasSession = cookiesMap.containsKey("sessionid") ||
                        resp.request.url.encodedPath.contains("/home") ||
                        resp.request.url.encodedPath.contains("/user/")
                    if (!hasSession && body.contains("Please enter a correct", ignoreCase = true)) {
                        error("Invalid Last.fm username or password")
                    }
                    if (!cookiesMap.containsKey("sessionid") && !resp.isRedirect && body.contains("id_password")) {
                        error("Invalid Last.fm username or password")
                    }
                }
                val finalCookies = cookiesMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                cleanUser to finalCookies
            }
            result.onSuccess { (resolvedName, sessionCookies) ->
                val updated = LastFmAccountState(
                    isLoggedIn = true,
                    username = resolvedName,
                    sessionKey = sessionCookies,
                    apiKey = _state.value.apiKey,
                    apiSecret = _state.value.apiSecret,
                    scrobbleEnabled = _state.value.scrobbleEnabled,
                    isSigningIn = false,
                    errorMessage = null,
                )
                synchronized(lock) { persistStateLocked(updated) }
                _state.value = updated
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSigningIn = false,
                        errorMessage = error.message ?: "Last.fm login failed",
                    )
                }
            }
        }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        _state.update { current ->
            val updated = current.copy(scrobbleEnabled = enabled)
            synchronized(lock) { persistStateLocked(updated) }
            updated
        }
    }

    fun signOut() {
        val cleared = LastFmAccountState()
        synchronized(lock) {
            if (sessionFile.exists()) sessionFile.delete()
        }
        _state.value = cleared
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    suspend fun updateNowPlaying(song: Song) {
        val current = _state.value
        if (!current.isLoggedIn || !current.scrobbleEnabled) return
        val artist = primaryArtist(song.artist)
        if (artist.isBlank() || song.title.isBlank()) return

        withContext(ioDispatcher) {
            runCatching {
                val params = buildMap {
                    put("method", "track.updateNowPlaying")
                    put("artist", artist)
                    put("track", song.title)
                    if (song.album.isNotBlank()) put("album", song.album)
                    if (song.durationMs > 0L) put("duration", (song.durationMs / 1000L).toString())
                    put("api_key", current.apiKey)
                    put("sk", current.sessionKey)
                }
                postSignedLastFm(params, current.apiSecret)
            }
        }
    }

    suspend fun scrobble(song: Song) {
        val current = _state.value
        if (!current.isLoggedIn || !current.scrobbleEnabled) return
        val artist = primaryArtist(song.artist)
        if (artist.isBlank() || song.title.isBlank()) return

        withContext(ioDispatcher) {
            runCatching {
                val timestampSeconds = (nowMs() / 1000L).toString()
                val params = buildMap {
                    put("method", "track.scrobble")
                    put("artist", artist)
                    put("track", song.title)
                    put("timestamp", timestampSeconds)
                    if (song.album.isNotBlank()) put("album", song.album)
                    if (song.durationMs > 0L) put("duration", (song.durationMs / 1000L).toString())
                    put("api_key", current.apiKey)
                    put("sk", current.sessionKey)
                }
                postSignedLastFm(params, current.apiSecret)
            }
        }
    }

    /**
     * Resolves a clean genre label for [song], preferring Last.fm tags when an API key
     * is configured and falling back to the zero-auth iTunes Search API.
     */
    suspend fun resolveGenre(song: Song): String? {
        val existing = song.genre.trim()
        if (existing.isNotBlank() && !existing.equals("Unknown Genre", ignoreCase = true)) {
            return existing
        }
        val artist = primaryArtist(song.artist)
        val cacheKey = "${artist.lowercase()}|${song.title.trim().lowercase()}"
        genreCache[cacheKey]?.let { return it }

        return withContext(ioDispatcher) {
            val apiKey = _state.value.apiKey
            val fromLastFm = if (apiKey.isNotBlank() && artist.isNotBlank()) {
                fetchLastFmGenreTag(artist = artist, track = song.title, apiKey = apiKey)
            } else {
                null
            }
            val resolved = fromLastFm ?: fetchItunesGenre(artist = artist, title = song.title)
            if (!resolved.isNullOrBlank()) {
                genreCache[cacheKey] = resolved
            }
            resolved
        }
    }

    private fun postSignedLastFm(params: Map<String, String>, apiSecret: String) {
        val sig = signParams(params, apiSecret)
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", sig)
            add("format", "json")
        }.build()
        val request = Request.Builder().url(LASTFM_API_URL).post(form).build()
        okHttpClient.newCall(request).execute().close()
    }

    private fun fetchLastFmGenreTag(artist: String, track: String, apiKey: String): String? {
        val trackUrl = "$LASTFM_API_URL?method=track.getTopTags" +
            "&artist=${encode(artist)}&track=${encode(track)}&autocorrect=1&api_key=${encode(apiKey)}&format=json"
        parseTopTagFromUrl(trackUrl)?.let { return it }
        val artistUrl = "$LASTFM_API_URL?method=artist.getTopTags" +
            "&artist=${encode(artist)}&autocorrect=1&api_key=${encode(apiKey)}&format=json"
        return parseTopTagFromUrl(artistUrl)
    }

    private fun parseTopTagFromUrl(url: String): String? = runCatching {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            val tags = JSONObject(body).optJSONObject("toptags")?.optJSONArray("tag") ?: return@use null
            for (i in 0 until tags.length()) {
                val name = tags.optJSONObject(i)?.optString("name")?.trim().orEmpty()
                if (isValidGenreTag(name)) {
                    return@use normalizeGenreLabel(name)
                }
            }
            null
        }
    }.getOrNull()

    private fun fetchItunesGenre(artist: String, title: String): String? = runCatching {
        val term = listOf(artist, title).filter(String::isNotBlank).joinToString(" ")
        if (term.isBlank()) return@runCatching null
        val url = "https://itunes.apple.com/search?term=${encode(term)}&entity=song&limit=1&media=music"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            val results = JSONObject(body).optJSONArray("results") ?: return@use null
            val first = results.optJSONObject(0) ?: return@use null
            first.optString("primaryGenreName").trim().takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun isValidGenreTag(tag: String): Boolean {
        if (tag.isBlank() || tag.length < 2 || tag.length > 32) return false
        val lower = tag.lowercase()
        if (lower.all { it.isDigit() }) return false
        return lower !in IGNORED_GENRE_TAGS
    }

    private fun normalizeGenreLabel(tag: String): String =
        tag.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }

    private fun primaryArtist(artist: String): String =
        artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim().orEmpty()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun signParams(params: Map<String, String>, secret: String): String {
        val concatenated = params.entries
            .sortedBy { it.key }
            .joinToString("") { (k, v) -> "$k$v" } + secret
        val digest = MessageDigest.getInstance("MD5").digest(concatenated.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadInitialState(): LastFmAccountState {
        synchronized(lock) {
            if (!sessionFile.isFile) return LastFmAccountState()
            return runCatching {
                val obj = JSONObject(sessionFile.readText())
                val username = obj.optString("username")
                val sessionKey = obj.optString("sessionKey")
                LastFmAccountState(
                    isLoggedIn = username.isNotBlank() && sessionKey.isNotBlank(),
                    username = username,
                    sessionKey = sessionKey,
                    apiKey = obj.optString("apiKey"),
                    apiSecret = obj.optString("apiSecret"),
                    scrobbleEnabled = obj.optBoolean("scrobbleEnabled", true),
                )
            }.getOrDefault(LastFmAccountState())
        }
    }

    private fun persistStateLocked(state: LastFmAccountState) {
        val obj = JSONObject()
            .put("username", state.username)
            .put("sessionKey", state.sessionKey)
            .put("apiKey", state.apiKey)
            .put("apiSecret", state.apiSecret)
            .put("scrobbleEnabled", state.scrobbleEnabled)
        sessionFile.writeText(obj.toString())
    }

    private companion object {
        const val LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val IGNORED_GENRE_TAGS = setOf(
            "seen live",
            "favorites",
            "favorite",
            "favourite",
            "favourites",
            "love",
            "awesome",
            "albums i own",
            "under 2000 listeners",
            "spotify",
            "my favorite",
            "beautiful",
            "chill",
            "good",
            "best",
        )
    }
}
