package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import com.vimi.k7.innertube.MetrolistInnerTubeClient
import com.vimi.k7.innertube.auth.SessionManager
import com.vimi.k7.innertube.auth.YouTubeAuthUtils
import com.vimi.k7.innertube.catalog.LegacyMusicCatalogDataSource
import com.vimi.k7.innertube.catalog.MetrolistMusicCatalogDataSource
import com.vimi.k7.innertube.catalog.MusicCatalogDataSource
import com.vimi.k7.innertube.legacy.MusicBrowse
import com.vimi.k7.innertube.legacy.MusicPlaylists
import com.vimi.k7.innertube.legacy.MusicSearch
import com.vimi.k7.innertube.legacy.NewPipeGateway
import com.vimi.k7.innertube.stream.AudioPreference
import com.vimi.k7.innertube.stream.AudioResolution
import com.vimi.k7.innertube.stream.AudioStreamResolver
import com.vimi.k7.innertube.transport.MusicApi
import com.vimi.k7.innertube.transport.YouTubeHttp
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class YouTubeResolvedStreamFormat(
    val format: String,
    val quality: String?,
)

/**
 * Coordinates InnerTube catalog browsing and stream URL resolution/caching for
 * YouTube streaming inside Elovaire's playback and data layers.
 */
internal class YouTubeStreamClient(
    private val context: Context,
    nowMs: () -> Long,
) {
    val qualityPreferences: YouTubeQualityPreferences = YouTubeQualityPreferences(context)
    private val resolvedFormats = ConcurrentHashMap<String, YouTubeResolvedStreamFormat>()
    private val _resolvedFormatUpdates = MutableStateFlow(0L)
    val resolvedFormatUpdates: StateFlow<Long> = _resolvedFormatUpdates.asStateFlow()
    private val sessionManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SessionManager(context)
    }

    private val youTubeHttp by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        YouTubeHttp(context, sessionManager)
    }

    private val newPipeGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NewPipeGateway(youTubeHttp, sessionManager)
    }

    private val musicApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MusicApi(youTubeHttp, sessionManager)
    }

    private val streamResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AudioStreamResolver.create(
            http = youTubeHttp,
            newPipeGateway = newPipeGateway,
        )
    }

    val streamCache: YouTubeStreamCache = YouTubeStreamCache(nowMs = nowMs)

    val okHttpClient: OkHttpClient
        get() = youTubeHttp.okHttpClient

    val catalog: MusicCatalogDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val search = MusicSearch(musicApi, newPipeGateway)
        val playlists = MusicPlaylists(musicApi, sessionManager)
        val browse = MusicBrowse(youTubeHttp, musicApi, search, playlists, sessionManager)
        val legacy = LegacyMusicCatalogDataSource(search, browse, playlists)
        val metrolist = MetrolistInnerTubeClient(musicApi)
        MetrolistMusicCatalogDataSource(metrolistClient = metrolist, legacy = legacy)
    }

    suspend fun resolvePlaybackUrl(
        videoId: String,
        preference: AudioPreference = qualityPreferences.currentStreamingPreference(),
    ): String {
        streamCache.get(videoId)?.let { return it }
        val generation = streamCache.generation()
        return when (val resolution = streamResolver.forPlayback(videoId, preference)) {
            is AudioResolution.Resolved -> {
                val formatLabel = when {
                    resolution.mimeType?.contains("opus", ignoreCase = true) == true ||
                        resolution.containerFormat.equals("opus", ignoreCase = true) -> "OPUS"
                    resolution.mimeType?.contains("mp4", ignoreCase = true) == true ||
                        resolution.containerFormat.equals("m4a", ignoreCase = true) -> "AAC"
                    else -> resolution.containerFormat.uppercase()
                }
                val qualityLabel = resolution.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                resolvedFormats[videoId] = YouTubeResolvedStreamFormat(
                    format = formatLabel,
                    quality = qualityLabel,
                )
                _resolvedFormatUpdates.value += 1L
                streamCache.put(videoId, resolution.url, generation)
                resolution.url
            }
            AudioResolution.Unresolved -> {
                throw IOException("Unable to resolve YouTube audio stream for $videoId")
            }
        }
    }

    fun resolvedStreamFormat(videoId: String): YouTubeResolvedStreamFormat? = resolvedFormats[videoId]

    fun resolvePlaybackUrlBlocking(
        videoId: String,
        preference: AudioPreference = qualityPreferences.currentStreamingPreference(),
    ): String = runBlocking {
        resolvePlaybackUrl(videoId, preference)
    }

    fun invalidateStream(videoId: String) {
        streamCache.invalidate(videoId)
    }

    fun clearStreamCache() {
        streamCache.invalidateAll()
        resolvedFormats.clear()
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    fun activeAccountName(): String? = sessionManager.getUserName()

    fun activeAccountHandle(): String? = sessionManager.getUserHandle()

    fun activeAccountAvatar(): String? = sessionManager.getUserAvatar()

    fun startSession(cookies: String) {
        sessionManager.startSession(cookies)
        streamCache.invalidateAll()
    }

    fun signOut() {
        sessionManager.clearSession()
        streamCache.invalidateAll()
    }

    fun fetchAndStoreAccountProfile(): Triple<String?, String?, String?> {
        val profile = musicApi.fetchAccountProfile() ?: return Triple(
            activeAccountName(),
            activeAccountHandle(),
            activeAccountAvatar(),
        )
        sessionManager.updateAccountProfile(
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            handle = profile.handle,
        )
        return Triple(
            profile.name.takeIf { it.isNotBlank() },
            profile.handle,
            profile.avatarUrl,
        )
    }

    fun reportPlaybackHistory(
        videoId: String,
        playedSeconds: Int,
        durationSeconds: Int,
    ) {
        if (!qualityPreferences.state.value.syncPlaybackHistoryToYouTube) return
        val cookies = sessionManager.getCookies()?.takeIf { it.isNotBlank() } ?: return
        val authHeader = YouTubeAuthUtils.getAuthorizationHeader(cookies, "https://music.youtube.com")
        val clientVersion = "1.20260401.01.00"
        val locale = java.util.Locale.getDefault()
        val hl = locale.language.ifBlank { "en" }
        val gl = locale.country.ifBlank { "US" }
        runCatching {
            val playerPayload = JSONObject()
                .put("videoId", videoId)
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB_REMIX")
                            .put("clientVersion", clientVersion)
                            .put("hl", hl)
                            .put("gl", gl),
                    ),
                )
            val webUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            val playerRequest = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", webUserAgent)
                .header("Cookie", cookies)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-Youtube-Client-Name", "67")
                .header("X-Youtube-Client-Version", clientVersion)
                .apply {
                    if (authHeader != null) {
                        header("Authorization", authHeader)
                        header("X-Goog-AuthUser", "0")
                    }
                }
                .post(playerPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val tracking = okHttpClient.newCall(playerRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("YouTubeHistory", "Player tracking fetch failed HTTP ${response.code} for $videoId")
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val pt = json.optJSONObject("playbackTracking") ?: return@use null
                val playbackUrl = pt.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl").orEmpty()
                val watchtimeUrl = pt.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl").orEmpty()
                playbackUrl to watchtimeUrl
            } ?: return

            val cpn = buildString(16) {
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                repeat(16) { append(chars.random()) }
            }
            val (rawPlaybackUrl, rawWatchtimeUrl) = tracking
            // Route stats pings through music.youtube.com so Origin + SAPISIDHASH + cookies match YouTube Music's watch history endpoint.
            val playbackBaseUrl = rawPlaybackUrl.replace("https://s.youtube.com/", "https://music.youtube.com/")
            val watchtimeBaseUrl = rawWatchtimeUrl.replace("https://s.youtube.com/", "https://music.youtube.com/")
            val len = durationSeconds.coerceAtLeast(playedSeconds).coerceAtLeast(1)

            if (playbackBaseUrl.isNotBlank()) {
                val sep = if ('?' in playbackBaseUrl) '&' else '?'
                val ping = Request.Builder()
                    .url("${playbackBaseUrl}${sep}ver=2&c=WEB_REMIX&cver=$clientVersion&cpn=$cpn&el=detailpage&cmt=0&len=$len&state=playing&volume=100&muted=0")
                    .header("User-Agent", webUserAgent)
                    .header("Cookie", cookies)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .apply {
                        if (authHeader != null) {
                            header("Authorization", authHeader)
                            header("X-Goog-AuthUser", "0")
                        }
                    }
                    .get()
                    .build()
                okHttpClient.newCall(ping).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        android.util.Log.w("YouTubeHistory", "Playback ping HTTP ${resp.code} for $videoId")
                    }
                }
            }
            if (watchtimeBaseUrl.isNotBlank()) {
                val sep = if ('?' in watchtimeBaseUrl) '&' else '?'
                val watch = Request.Builder()
                    .url(
                        "${watchtimeBaseUrl}${sep}ver=2&c=WEB_REMIX&cver=$clientVersion&cpn=$cpn&el=detailpage&cmt=$playedSeconds&st=0&et=$playedSeconds&rt=$playedSeconds&len=$len&state=playing&volume=100&muted=0&final=1",
                    )
                    .header("User-Agent", webUserAgent)
                    .header("Cookie", cookies)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .apply {
                        if (authHeader != null) {
                            header("Authorization", authHeader)
                            header("X-Goog-AuthUser", "0")
                        }
                    }
                    .get()
                    .build()
                okHttpClient.newCall(watch).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        android.util.Log.w("YouTubeHistory", "Watchtime ping HTTP ${resp.code} for $videoId")
                    }
                }
            }
        }.onFailure { err ->
            android.util.Log.w("YouTubeHistory", "Failed to report playback history for $videoId", err)
        }
    }
}
