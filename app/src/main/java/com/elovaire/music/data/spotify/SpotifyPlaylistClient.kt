package elovaire.music.droidbeauty.app.data.spotify

import android.content.Context
import elovaire.music.droidbeauty.app.data.settings.BackupTrackRef
import elovaire.music.droidbeauty.app.data.settings.PlaylistLinkImporter
import kotlinx.coroutines.Dispatchers
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

internal class SpotifyPlaylistClient(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()

    private val _state = MutableStateFlow(
        SpotifyPlaylistsUiState(
            isLoggedIn = !prefs.getString(KEY_SP_DC, null).isNullOrBlank(),
        ),
    )
    val state: StateFlow<SpotifyPlaylistsUiState> = _state.asStateFlow()

    fun isLoggedIn(): Boolean = !prefs.getString(KEY_SP_DC, null).isNullOrBlank()

    fun saveSession(
        spDc: String,
        accessToken: String? = null,
        expiresAtMs: Long = 0L,
    ) {
        prefs.edit()
            .putString(KEY_SP_DC, spDc.trim())
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    putString(KEY_ACCESS_TOKEN, accessToken.trim())
                    putLong(KEY_EXPIRES_AT_MS, expiresAtMs)
                }
            }
            .apply()
        _state.update { it.copy(isLoggedIn = true, error = null) }
    }

    fun signOut() {
        prefs.edit().clear().apply()
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
            val fromApi = runCatching {
                val token = resolveValidAccessToken() ?: return@runCatching emptyList()
                fetchTracksWithToken(playlistId, token)
            }.getOrDefault(emptyList())
            if (fromApi.isNotEmpty()) return@withContext fromApi
            PlaylistLinkImporter.fetchSpotifyEmbedPlaylist(playlistId)?.tracks.orEmpty()
        }

    private fun resolveValidAccessToken(): String? {
        val now = System.currentTimeMillis()
        val cached = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (!cached.isNullOrBlank() && expiresAt > now + 60_000L) {
            return cached
        }
        val spDc = prefs.getString(KEY_SP_DC, null)?.takeIf { it.isNotBlank() } ?: return null
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
            val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: return@use null
            val exp = json.optLong("accessTokenExpirationTimestampMs", now + 3_000_000L)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_EXPIRES_AT_MS, exp)
                .apply()
            token
        }
    }

    private fun fetchPlaylistsWithToken(token: String): List<SpotifyPlaylistSummary> {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/playlists?limit=50")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("items") ?: return@use emptyList()
            buildList {
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
    }

    private fun fetchTracksWithToken(
        playlistId: String,
        token: String,
    ): List<BackupTrackRef> {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
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
        private const val PREFS_NAME = "verso_spotify_session"
        private const val KEY_SP_DC = "sp_dc"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
