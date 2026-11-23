package elovaire.music.droidbeauty.app.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import elovaire.music.droidbeauty.app.data.settings.BackupTrackRef
import elovaire.music.droidbeauty.app.data.settings.PlaylistLinkImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val owner: String,
    val trackCount: Int,
    val coverUrl: String?,
)

internal data class SpotifyPlaylistsUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val playlists: List<SpotifyPlaylistSummary> = emptyList(),
    val error: String? = null,
)

/**
 * Handles Spotify web session authentication (session cookie and access token stored encrypted
 * via Android Keystore EncryptedSharedPreferences; passwords are never seen or persisted)
 * strictly for reading the user's playlists.
 */
internal class SpotifyPlaylistClient(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient()

    private val encryptedPrefs: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // Remove any legacy unencrypted preferences if present.
        runCatching { appContext.deleteSharedPreferences(LEGACY_PLAINTEXT_PREFS_NAME) }
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { error ->
            Log.e(TAG, "EncryptedSharedPreferences unavailable for Spotify", error)
        }.getOrNull()
    }

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<SpotifyPlaylistsUiState> = _state.asStateFlow()

    private fun loadInitialState(): SpotifyPlaylistsUiState {
        val prefs = encryptedPrefs ?: return SpotifyPlaylistsUiState()
        val loggedIn = !prefs.getString(KEY_SP_DC, null).isNullOrBlank()
        if (!loggedIn) return SpotifyPlaylistsUiState(isLoggedIn = false)
        val cached = deserializePlaylists(prefs.getString(KEY_CACHED_PLAYLISTS, null).orEmpty())
        return SpotifyPlaylistsUiState(
            isLoggedIn = true,
            playlists = cached,
        )
    }

    fun isLoggedIn(): Boolean = !encryptedPrefs?.getString(KEY_SP_DC, null).isNullOrBlank()

    fun saveSession(
        spDc: String,
        accessToken: String? = null,
        expiresAtMs: Long = 0L,
        clientToken: String? = null,
        playlists: List<SpotifyPlaylistSummary>? = null,
    ) {
        val prefs = encryptedPrefs ?: return
        val cleanCookie = spDc.trim()
        if (cleanCookie.isBlank()) return
        prefs.edit()
            .putString(KEY_SP_DC, cleanCookie)
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    putString(KEY_ACCESS_TOKEN, accessToken.trim())
                    putLong(KEY_EXPIRES_AT_MS, expiresAtMs)
                }
                if (!clientToken.isNullOrBlank()) {
                    putString(KEY_CLIENT_TOKEN, clientToken.trim())
                }
                if (playlists != null) {
                    putString(KEY_CACHED_PLAYLISTS, serializePlaylists(playlists))
                }
            }
            .apply()
        _state.update { current ->
            current.copy(
                isLoggedIn = true,
                isLoading = false,
                playlists = playlists ?: current.playlists,
                error = null,
            )
        }
    }

    suspend fun enrichMissingTrackCounts() {
        val current = _state.value.playlists
        val missing = current.filter { it.trackCount <= 0 }
        if (missing.isEmpty()) return
        val updates = withContext(Dispatchers.IO) {
            val map = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, String?>>()
            coroutineScope {
                for (chunk in missing.chunked(4)) {
                    chunk.map { item ->
                        async {
                            val embed = PlaylistLinkImporter.fetchSpotifyEmbedPlaylist(item.id)
                            if (embed != null && embed.tracks.isNotEmpty()) {
                                map[item.id] = embed.tracks.size to embed.coverArtUrl
                            }
                        }
                    }.forEach { it.await() }
                }
            }
            map
        }
        if (updates.isEmpty()) return
        val enriched = _state.value.playlists.map { item ->
            val info = updates[item.id] ?: return@map item
            item.copy(
                trackCount = info.first,
                coverUrl = item.coverUrl ?: info.second,
            )
        }
        encryptedPrefs?.edit()
            ?.putString(KEY_CACHED_PLAYLISTS, serializePlaylists(enriched))
            ?.apply()
        _state.update { it.copy(playlists = enriched) }
    }

    private fun serializePlaylists(list: List<SpotifyPlaylistSummary>): String {
        val arr = org.json.JSONArray()
        for (p in list) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("owner", p.owner)
                    .put("trackCount", p.trackCount)
                    .put("coverUrl", p.coverUrl.orEmpty()),
            )
        }
        return arr.toString()
    }

    private fun deserializePlaylists(raw: String): List<SpotifyPlaylistSummary> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        SpotifyPlaylistSummary(
                            id = id,
                            name = name,
                            owner = obj.optString("owner").trim(),
                            trackCount = obj.optInt("trackCount", 0),
                            coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun signOut() {
        encryptedPrefs?.edit()?.clear()?.apply()
        runCatching {
            CookieManager.getInstance().apply {
                setCookie("https://open.spotify.com", "sp_dc=; Max-Age=0; Path=/; Secure")
                setCookie("https://accounts.spotify.com", "sp_dc=; Max-Age=0; Path=/; Secure")
                flush()
            }
        }
        _state.value = SpotifyPlaylistsUiState(isLoggedIn = false)
    }

    suspend fun syncPlaylists(force: Boolean = false) {
        if (!isLoggedIn()) {
            _state.value = SpotifyPlaylistsUiState(isLoggedIn = false)
            return
        }
        val current = _state.value
        if (current.isLoading) return
        if (!force && current.playlists.isNotEmpty()) return

        _state.update { it.copy(isLoading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val token = resolveValidAccessToken()
                    ?: error("Session expired")
                fetchPlaylistsWithToken(token)
            }
        }
        result.fold(
            onSuccess = { list ->
                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        playlists = list,
                        error = null,
                    )
                }
            },
            onFailure = { err ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = err.message ?: "Unable to load Spotify playlists",
                    )
                }
            },
        )
    }

    suspend fun fetchPlaylistTracks(playlistId: String): List<BackupTrackRef> =
        withContext(Dispatchers.IO) {
            // 1. Prefer public embed HTML first (never rate-limited with HTTP 429).
            val fromEmbed = PlaylistLinkImporter.fetchSpotifyEmbedPlaylist(playlistId)?.tracks.orEmpty()
            if (fromEmbed.isNotEmpty()) return@withContext fromEmbed
            // 2. Fallback to authenticated Web API for private playlists.
            runCatching {
                val token = resolveValidAccessToken() ?: return@runCatching emptyList()
                fetchTracksWithToken(playlistId, token)
            }.getOrDefault(emptyList())
        }

    private fun resolveValidAccessToken(): String? {
        val prefs = encryptedPrefs ?: return null
        val now = System.currentTimeMillis()
        val cached = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (!cached.isNullOrBlank() && expiresAt > now + 60_000L) {
            return cached
        }
        val spDc = prefs.getString(KEY_SP_DC, null)?.takeIf { it.isNotBlank() } ?: return null
        // 1. Try reading session script directly from open.spotify.com HTML.
        val htmlRequest = Request.Builder()
            .url("https://open.spotify.com/")
            .header("Cookie", "sp_dc=$spDc")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()
        val htmlToken = runCatching {
            httpClient.newCall(htmlRequest).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string().orEmpty()
                val match = Regex("""<script[^>]*id="session"[^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL))
                    .find(html)
                val sessionJson = match?.groupValues?.getOrNull(1)?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?: return@use null
                if (sessionJson.optBoolean("isAnonymous", false)) return@use null
                val token = sessionJson.optString("accessToken").takeIf { it.isNotBlank() } ?: return@use null
                val exp = sessionJson.optLong("accessTokenExpirationTimestampMs", now + 3_300_000L)
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, token)
                    .putLong(KEY_EXPIRES_AT_MS, exp)
                    .apply()
                token
            }
        }.getOrNull()
        if (!htmlToken.isNullOrBlank()) return htmlToken

        // 2. Fallback to get_access_token endpoint.
        val request = Request.Builder()
            .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
            .header("Cookie", "sp_dc=$spDc")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@use null
            if (json.optBoolean("isAnonymous", false)) return@use null
            val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: return@use null
            val exp = json.optLong("accessTokenExpirationTimestampMs", now + 3_300_000L)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_EXPIRES_AT_MS, exp)
                .apply()
            token
        }
    }

    private fun Request.Builder.applySpotifyWebHeaders(token: String): Request.Builder {
        val clientToken = encryptedPrefs?.getString(KEY_CLIENT_TOKEN, null)?.takeIf { it.isNotBlank() }
        header("Authorization", "Bearer $token")
        header("Accept", "application/json")
        header("User-Agent", WEB_USER_AGENT)
        header("Origin", "https://open.spotify.com")
        header("Referer", "https://open.spotify.com/")
        header("app-platform", "WebPlayer")
        if (clientToken != null) {
            header("client-token", clientToken)
        }
        return this
    }

    private fun fetchPlaylistsWithToken(token: String): List<SpotifyPlaylistSummary> {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/playlists?limit=50")
            .applySpotifyWebHeaders(token)
            .get()
            .build()
        return httpClient.newCall(request).execute().use { resp ->
            if (resp.code == 429) {
                val retrySec = resp.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 3L) ?: 1L
                Thread.sleep(retrySec * 1000L)
                return httpClient.newCall(request).execute().use { retryResp ->
                    if (!retryResp.isSuccessful) error("HTTP ${retryResp.code}")
                    parsePlaylistsJson(retryResp.body?.string().orEmpty())
                }
            }
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            parsePlaylistsJson(resp.body?.string().orEmpty())
        }
    }

    private fun parsePlaylistsJson(body: String): List<SpotifyPlaylistSummary> {
        val json = JSONObject(body)
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val owner = obj.optJSONObject("owner")?.optString("display_name").orEmpty().trim()
                val total = obj.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                val coverUrl = obj.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                add(
                    SpotifyPlaylistSummary(
                        id = id,
                        name = name,
                        owner = owner,
                        trackCount = total,
                        coverUrl = coverUrl,
                    ),
                )
            }
        }
    }

    private fun fetchTracksWithToken(
        playlistId: String,
        token: String,
    ): List<BackupTrackRef> {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100")
            .applySpotifyWebHeaders(token)
            .get()
            .build()
        return httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("items") ?: return@use emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val title = track.optString("name").trim()
                    if (title.isBlank()) continue
                    val artistsArr = track.optJSONArray("artists")
                    val artist = buildString {
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                val aName = artistsArr.optJSONObject(a)?.optString("name").orEmpty().trim()
                                if (aName.isNotBlank()) {
                                    if (isNotEmpty()) append(", ")
                                    append(aName)
                                }
                            }
                        }
                    }
                    val album = track.optJSONObject("album")?.optString("name").orEmpty().trim()
                    add(
                        BackupTrackRef(
                            title = title,
                            artist = artist,
                            album = album,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SpotifyPlaylistClient"
        private const val LEGACY_PLAINTEXT_PREFS_NAME = "verso_spotify_session"
        private const val ENCRYPTED_PREFS_NAME = "spotify_session_encrypted"
        private const val KEY_SP_DC = "sp_dc"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_CLIENT_TOKEN = "client_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_CACHED_PLAYLISTS = "cached_playlists"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
