package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import elovaire.music.droidbeauty.app.BuildConfig
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import java.io.IOException
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
    val pendingScrobbleCount: Int = 0,
    val errorMessage: String? = null,
)

internal data class StoredLastFmSession(
    val username: String,
    val sessionKey: String,
    val apiKey: String,
    val apiSecret: String,
    val scrobbleEnabled: Boolean,
)

internal interface LastFmSessionStorage {
    fun load(): StoredLastFmSession?
    fun save(session: StoredLastFmSession)
    fun clear()
}

internal class EncryptedLastFmSessionStorage(
    context: Context,
) : LastFmSessionStorage {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { error ->
            Log.e(TAG, "EncryptedSharedPreferences unavailable for Last.fm", error)
        }.getOrNull()
    }

    override fun load(): StoredLastFmSession? {
        val store = prefs ?: return null
        val username = store.getString(KEY_USERNAME, null).orEmpty()
        val sessionKey = store.getString(KEY_SESSION_KEY, null).orEmpty()
        if (username.isBlank() || sessionKey.isBlank()) return null
        return StoredLastFmSession(
            username = username,
            sessionKey = sessionKey,
            apiKey = store.getString(KEY_API_KEY, null).orEmpty(),
            apiSecret = store.getString(KEY_API_SECRET, null).orEmpty(),
            scrobbleEnabled = store.getBoolean(KEY_SCROBBLE_ENABLED, true),
        )
    }

    override fun save(session: StoredLastFmSession) {
        val store = prefs ?: return
        store.edit()
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_SESSION_KEY, session.sessionKey)
            .putString(KEY_API_KEY, session.apiKey)
            .putString(KEY_API_SECRET, session.apiSecret)
            .putBoolean(KEY_SCROBBLE_ENABLED, session.scrobbleEnabled)
            .apply()
    }

    override fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        const val TAG = "LastFmSessionStorage"
        const val PREFS_NAME = "lastfm_session_encrypted"
        const val KEY_USERNAME = "username"
        const val KEY_SESSION_KEY = "session_key"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_SECRET = "api_secret"
        const val KEY_SCROBBLE_ENABLED = "scrobble_enabled"
    }
}

/**
 * Handles Last.fm mobile session authentication (session key stored encrypted, password never persisted),
 * Now Playing updates, 30-second scrobbling with an offline retry queue, and multi-source genre resolution.
 */
internal class LastFmClient(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long,
    private val sessionStorage: LastFmSessionStorage = EncryptedLastFmSessionStorage(context),
    pendingQueueFile: File = File(context.applicationContext.filesDir, "lastfm_pending_scrobbles.json"),
    registerNetworkCallback: Boolean = true,
) {
    private val legacySessionFile = File(context.applicationContext.filesDir, "lastfm_session.json")
    private val lock = Any()
    private val pendingQueue = PendingScrobbleQueue(pendingQueueFile)
    private val genreCache = ConcurrentHashMap<String, String>()
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<LastFmAccountState> = _state.asStateFlow()

    init {
        if (registerNetworkCallback) {
            registerConnectivityFlush(context.applicationContext)
        }
        val initial = _state.value
        if (initial.isLoggedIn && initial.scrobbleEnabled && initial.pendingScrobbleCount > 0) {
            scope.launch(ioDispatcher) { flushPendingScrobbles() }
        }
    }

    fun signIn(
        username: String,
        password: String,
    ) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter your Last.fm username and password") }
            return
        }
        val apiKey = BuildConfig.LASTFM_API_KEY
        val apiSecret = BuildConfig.LASTFM_API_SECRET
        _state.update { it.copy(isSigningIn = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = runCatching {
                val params = mapOf(
                    "method" to "auth.getMobileSession",
                    "username" to cleanUser,
                    "password" to password,
                    "api_key" to apiKey,
                )
                val sig = signParams(params, apiSecret)
                val form = FormBody.Builder().apply {
                    params.forEach { (k, v) -> add(k, v) }
                    add("api_sig", sig)
                    add("format", "json")
                }.build()
                val request = Request.Builder()
                    .url(LASTFM_API_URL)
                    .post(form)
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (json != null && json.has("error")) {
                        val msg = json.optString("message").ifBlank { "Last.fm authentication failed" }
                        error(msg)
                    }
                    if (!resp.isSuccessful) {
                        error("Last.fm authentication failed (HTTP ${resp.code})")
                    }
                    val session = json?.optJSONObject("session")
                        ?: error("Invalid Last.fm session response")
                    val resolvedName = session.optString("name").ifBlank { cleanUser }
                    val sk = session.optString("key")
                    if (sk.isBlank()) error("Missing Last.fm session key")
                    resolvedName to sk
                }
            }
            result.onSuccess { (resolvedName, sk) ->
                val updated = LastFmAccountState(
                    isLoggedIn = true,
                    username = resolvedName,
                    sessionKey = sk,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    scrobbleEnabled = _state.value.scrobbleEnabled,
                    isSigningIn = false,
                    pendingScrobbleCount = _state.value.pendingScrobbleCount,
                    errorMessage = null,
                )
                synchronized(lock) { persistStateLocked(updated) }
                _state.value = updated
                flushPendingScrobbles()
            }.onFailure { error ->
                runCatching { Log.w(TAG, "Last.fm signIn failed: ${error.message}") }
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
        if (enabled && _state.value.isLoggedIn) {
            scope.launch(ioDispatcher) { flushPendingScrobbles() }
        }
    }

    fun signOut() {
        val cleared = LastFmAccountState(
            apiKey = BuildConfig.LASTFM_API_KEY,
            apiSecret = BuildConfig.LASTFM_API_SECRET,
        )
        synchronized(lock) {
            sessionStorage.clear()
            pendingQueue.clear()
            if (legacySessionFile.exists()) legacySessionFile.delete()
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
            val timestampSeconds = (nowMs() / 1000L).coerceAtLeast(1L)
            val entry = PendingScrobble(
                artist = artist,
                track = song.title,
                album = song.album,
                durationMs = song.durationMs,
                timestampSeconds = timestampSeconds,
            )
            val params = buildScrobbleParams(entry, current)
            when (postSignedLastFm(params, current.apiSecret)) {
                ScrobblePostResult.Success -> flushPendingScrobblesLocked(current)
                ScrobblePostResult.RetryableFailure -> {
                    val count = synchronized(lock) { pendingQueue.enqueue(entry) }
                    _state.update { it.copy(pendingScrobbleCount = count) }
                }
                ScrobblePostResult.PermanentFailure -> Unit
            }
        }
    }

    suspend fun flushPendingScrobbles() {
        val current = _state.value
        if (!current.isLoggedIn || !current.scrobbleEnabled) return
        withContext(ioDispatcher) {
            flushPendingScrobblesLocked(current)
        }
    }

    private fun flushPendingScrobblesLocked(current: LastFmAccountState) {
        val snapshot = synchronized(lock) { pendingQueue.snapshot() }
        if (snapshot.isEmpty()) return

        var stopOnNetwork = false
        for (entry in snapshot) {
            if (stopOnNetwork) break
            val params = buildScrobbleParams(entry, current)
            when (postSignedLastFm(params, current.apiSecret)) {
                ScrobblePostResult.Success,
                ScrobblePostResult.PermanentFailure -> {
                    val remaining = synchronized(lock) { pendingQueue.remove(entry) }
                    _state.update { it.copy(pendingScrobbleCount = remaining) }
                }
                ScrobblePostResult.RetryableFailure -> {
                    stopOnNetwork = true
                }
            }
        }
    }

    private fun buildScrobbleParams(
        entry: PendingScrobble,
        current: LastFmAccountState,
    ): Map<String, String> = buildMap {
        put("method", "track.scrobble")
        put("artist", entry.artist)
        put("track", entry.track)
        put("timestamp", entry.timestampSeconds.toString())
        if (entry.album.isNotBlank()) put("album", entry.album)
        if (entry.durationMs > 0L) put("duration", (entry.durationMs / 1000L).toString())
        put("api_key", current.apiKey)
        put("sk", current.sessionKey)
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
        val cleanTitle = elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
            .normalizeTitle(song.title)
            .ifBlank { song.title.trim() }
        val cacheKey = "${artist.lowercase()}|${cleanTitle.lowercase()}"
        genreCache[cacheKey]?.let { return it }

        return withContext(ioDispatcher) {
            val apiKey = _state.value.apiKey
            val fromLastFm = if (apiKey.isNotBlank() && artist.isNotBlank()) {
                fetchLastFmGenreTag(artist = artist, track = cleanTitle, apiKey = apiKey)
            } else {
                null
            }
            val resolved = fromLastFm
                ?: fetchItunesGenre(artist = artist, title = cleanTitle)
                ?: genreCache["artist:${artist.lowercase()}"]
            if (!resolved.isNullOrBlank()) {
                genreCache[cacheKey] = resolved
                if (artist.isNotBlank()) {
                    genreCache.putIfAbsent("artist:${artist.lowercase()}", resolved)
                }
            }
            resolved
        }
    }

    private fun postSignedLastFm(params: Map<String, String>, apiSecret: String): ScrobblePostResult {
        return try {
            val sig = signParams(params, apiSecret)
            val form = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
                add("api_sig", sig)
                add("format", "json")
            }.build()
            val request = Request.Builder().url(LASTFM_API_URL).post(form).build()
            okHttpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val method = params["method"].orEmpty()
                if (!resp.isSuccessful) {
                    runCatching { Log.w(TAG, "Last.fm $method HTTP ${resp.code}: $body") }
                    if (resp.code == 429 || resp.code >= 500) {
                        ScrobblePostResult.RetryableFailure
                    } else {
                        ScrobblePostResult.PermanentFailure
                    }
                } else {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (json != null && json.has("error")) {
                        val code = json.optInt("error")
                        runCatching { Log.w(TAG, "Last.fm $method error $code: ${json.optString("message")}") }
                        if (code in RETRYABLE_LASTFM_ERRORS) {
                            ScrobblePostResult.RetryableFailure
                        } else {
                            ScrobblePostResult.PermanentFailure
                        }
                    } else {
                        runCatching { Log.i(TAG, "Last.fm $method OK") }
                        ScrobblePostResult.Success
                    }
                }
            }
        } catch (io: IOException) {
            runCatching { Log.w(TAG, "Last.fm network error (queued): ${io.message}") }
            ScrobblePostResult.RetryableFailure
        } catch (t: RuntimeException) {
            runCatching { Log.w(TAG, "Last.fm unexpected error: ${t.message}") }
            ScrobblePostResult.PermanentFailure
        }
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
        artist.split(Regex("""\s*(?:\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|;|•|·)\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim().orEmpty()

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
            val pendingCount = pendingQueue.size()
            val encrypted = sessionStorage.load()
            if (encrypted != null) {
                if (legacySessionFile.exists()) {
                    runCatching { legacySessionFile.delete() }
                }
                val apiKey = encrypted.apiKey.ifBlank { BuildConfig.LASTFM_API_KEY }
                val apiSecret = encrypted.apiSecret.ifBlank { BuildConfig.LASTFM_API_SECRET }
                return LastFmAccountState(
                    isLoggedIn = encrypted.username.isNotBlank() &&
                        encrypted.sessionKey.isNotBlank() &&
                        apiKey.isNotBlank() &&
                        apiSecret.isNotBlank(),
                    username = encrypted.username,
                    sessionKey = encrypted.sessionKey,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    scrobbleEnabled = encrypted.scrobbleEnabled,
                    pendingScrobbleCount = pendingCount,
                )
            }

            if (!legacySessionFile.isFile) {
                return LastFmAccountState(
                    apiKey = BuildConfig.LASTFM_API_KEY,
                    apiSecret = BuildConfig.LASTFM_API_SECRET,
                    pendingScrobbleCount = pendingCount,
                )
            }

            val migrated = runCatching {
                val obj = JSONObject(legacySessionFile.readText())
                val username = obj.optString("username")
                val rawSessionKey = obj.optString("sessionKey")
                val isLegacyCookieSession = rawSessionKey.contains("=") || rawSessionKey.contains(";")
                val sessionKey = if (isLegacyCookieSession) "" else rawSessionKey
                val apiKey = obj.optString("apiKey").ifBlank { BuildConfig.LASTFM_API_KEY }
                val apiSecret = obj.optString("apiSecret").ifBlank { BuildConfig.LASTFM_API_SECRET }
                val scrobbleEnabled = obj.optBoolean("scrobbleEnabled", true)
                val cleanUser = if (isLegacyCookieSession) "" else username
                if (cleanUser.isNotBlank() && sessionKey.isNotBlank()) {
                    sessionStorage.save(
                        StoredLastFmSession(
                            username = cleanUser,
                            sessionKey = sessionKey,
                            apiKey = apiKey,
                            apiSecret = apiSecret,
                            scrobbleEnabled = scrobbleEnabled,
                        ),
                    )
                }
                LastFmAccountState(
                    isLoggedIn = cleanUser.isNotBlank() && sessionKey.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank(),
                    username = cleanUser,
                    sessionKey = sessionKey,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    scrobbleEnabled = scrobbleEnabled,
                    pendingScrobbleCount = pendingCount,
                )
            }.getOrDefault(
                LastFmAccountState(
                    apiKey = BuildConfig.LASTFM_API_KEY,
                    apiSecret = BuildConfig.LASTFM_API_SECRET,
                    pendingScrobbleCount = pendingCount,
                ),
            )
            runCatching { legacySessionFile.delete() }
            return migrated
        }
    }

    private fun persistStateLocked(state: LastFmAccountState) {
        if (state.username.isBlank() || state.sessionKey.isBlank()) {
            sessionStorage.clear()
        } else {
            sessionStorage.save(
                StoredLastFmSession(
                    username = state.username,
                    sessionKey = state.sessionKey,
                    apiKey = state.apiKey,
                    apiSecret = state.apiSecret,
                    scrobbleEnabled = state.scrobbleEnabled,
                ),
            )
        }
        if (legacySessionFile.exists()) {
            runCatching { legacySessionFile.delete() }
        }
    }

    private fun registerConnectivityFlush(appContext: Context) {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val current = _state.value
                        if (current.isLoggedIn && current.scrobbleEnabled && current.pendingScrobbleCount > 0) {
                            scope.launch(ioDispatcher) { flushPendingScrobbles() }
                        }
                    }
                },
            )
        }
    }

    private companion object {
        const val TAG = "LastFmClient"
        const val LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/"
        val RETRYABLE_LASTFM_ERRORS = setOf(11, 16, 29)
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
