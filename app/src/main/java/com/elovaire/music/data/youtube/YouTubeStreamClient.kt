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
import com.vimi.k7.innertube.stream.PlayerClients
import com.vimi.k7.innertube.transport.INNER_TUBE_API_KEY
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

private data class PlaybackTrackingUrls(
    val playbackUrl: String,
    val watchtimeUrl: String,
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
    private val resolvedLoudnessDb = ConcurrentHashMap<String, Float>()
    private val authenticatedTrackingCache = ConcurrentHashMap<String, PlaybackTrackingUrls>()
    @Volatile
    private var cachedSignatureTimestamp: Int? = null
    private val resolvedFormatSeq = java.util.concurrent.atomic.AtomicLong(0L)
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
            onLoudness = { videoId, loudnessDb ->
                if (loudnessDb.isFinite()) {
                    resolvedLoudnessDb[videoId] = loudnessDb
                }
            },
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
                _resolvedFormatUpdates.value = resolvedFormatSeq.incrementAndGet()
                streamCache.put(videoId, resolution.url, generation)
                resolution.url
            }
            AudioResolution.Unresolved -> {
                throw IOException("Unable to resolve YouTube audio stream for $videoId")
            }
        }
    }

    fun resolvedStreamFormat(videoId: String): YouTubeResolvedStreamFormat? = resolvedFormats[videoId]

    fun resolvedLoudnessDb(videoId: String): Float? = resolvedLoudnessDb[videoId]

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

    fun renameYouTubePlaylist(playlistId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false
        val action = org.json.JSONObject()
            .put("action", "ACTION_SET_PLAYLIST_NAME")
            .put("playlistName", cleanName)
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun addVideoToYouTubePlaylist(playlistId: String, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val action = org.json.JSONObject()
            .put("action", "ACTION_ADD_VIDEO")
            .put("addedVideoId", videoId)
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun removeVideoFromYouTubePlaylist(playlistId: String, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val action = org.json.JSONObject()
            .put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
            .put("removedVideoId", videoId)
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun setYouTubePlaylistDescription(playlistId: String, description: String): Boolean {
        val action = org.json.JSONObject()
            .put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
            .put("playlistDescription", description.trim())
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun moveVideoInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ): Boolean {
        if (setVideoId.isBlank()) return false
        val action = org.json.JSONObject()
            .put("action", "ACTION_MOVE_VIDEO_BEFORE")
            .put("setVideoId", setVideoId)
            .apply {
                if (!successorSetVideoId.isNullOrBlank()) {
                    put("movedSetVideoIdSuccessor", successorSetVideoId)
                }
            }
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun setYouTubePlaylistThumbnail(playlistId: String, imageBytes: ByteArray): Boolean {
        if (imageBytes.isEmpty() || !isLoggedIn()) return false
        val blobId = musicApi.uploadMusicPlaylistThumbnail(imageBytes) ?: return false
        val crop = org.json.JSONObject()
            .put("left", 0.0)
            .put("top", 0.0)
            .put("right", 1.0)
            .put("bottom", 1.0)
        val customThumb = org.json.JSONObject()
            .put("encryptedBlobId", blobId)
            .put("imageCrop", crop)
        val action = org.json.JSONObject()
            .put("action", "ACTION_SET_CUSTOM_THUMBNAIL")
            .put("customThumbnail", customThumb)
        return editYouTubePlaylist(playlistId, org.json.JSONArray().put(action))
    }

    fun deleteYouTubePlaylist(playlistId: String): Boolean {
        val cleanId = playlistId.removePrefix("VL").trim()
        if (cleanId.isBlank() || !isLoggedIn()) return false
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250101.01.00")
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("playlistId", cleanId)
        return musicApi.postMusicApi("playlist/delete", body) != null
    }

    fun createYouTubePlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE",
        videoIds: List<String> = emptyList(),
    ): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || !isLoggedIn()) return null
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250101.01.00")
        val initialBatch = videoIds.take(50)
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("title", cleanTitle)
            .put("privacyStatus", privacyStatus)
            .apply {
                if (description.isNotBlank()) put("description", description.trim())
                if (initialBatch.isNotEmpty()) put("videoIds", org.json.JSONArray(initialBatch))
            }
        val raw = musicApi.postMusicApi("playlist/create", body) ?: return null
        val createdId = runCatching {
            org.json.JSONObject(raw).optString("playlistId").takeIf { it.isNotBlank() }
        }.getOrNull() ?: return null
        val remaining = videoIds.drop(50)
        if (remaining.isNotEmpty()) {
            val actions = org.json.JSONArray().apply {
                remaining.forEach { vid ->
                    put(
                        org.json.JSONObject()
                            .put("action", "ACTION_ADD_VIDEO")
                            .put("addedVideoId", vid),
                    )
                }
            }
            editYouTubePlaylist(createdId, actions)
        }
        return createdId
    }

    private fun editYouTubePlaylist(playlistId: String, actions: org.json.JSONArray): Boolean {
        val cleanId = playlistId.removePrefix("VL").trim()
        if (cleanId.isBlank()) return false
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250101.01.00")
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("playlistId", cleanId)
            .put("actions", actions)
        val response = musicApi.postMusicApi("browse/edit_playlist", body) ?: return false
        val status = runCatching { org.json.JSONObject(response).optString("status") }.getOrDefault("")
        return status.isBlank() || status.equals("STATUS_SUCCEEDED", ignoreCase = true)
    }

    fun setVideoLikeStatus(videoId: String, liked: Boolean): Boolean {
        if (videoId.isBlank() || !isLoggedIn()) return false
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250101.01.00")
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("target", org.json.JSONObject().put("videoId", videoId))
        val endpoint = if (liked) "like/like" else "like/removelike"
        return musicApi.postMusicApi(endpoint, body) != null
    }

    fun setArtistSubscription(channelId: String, subscribed: Boolean): Boolean {
        val cleanId = channelId.trim()
        if (cleanId.isBlank() || !isLoggedIn()) return false
        val client = org.json.JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250101.01.00")
        val body = org.json.JSONObject()
            .put("context", org.json.JSONObject().put("client", client))
            .put("channelIds", org.json.JSONArray().put(cleanId))
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        return musicApi.postMusicApi(endpoint, body) != null
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

    private fun extractPlaybackTracking(root: JSONObject): PlaybackTrackingUrls? {
        val pt = root.optJSONObject("playbackTracking") ?: return null
        val playbackUrl = pt.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl").orEmpty()
        val watchtimeUrl = pt.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl").orEmpty()
        if (playbackUrl.isBlank() && watchtimeUrl.isBlank()) return null
        return PlaybackTrackingUrls(playbackUrl = playbackUrl, watchtimeUrl = watchtimeUrl)
    }

    private fun resolveSignatureTimestamp(webUserAgent: String): Int {
        cachedSignatureTimestamp?.let { return it }
        val resolved = runCatching {
            val homeReq = Request.Builder()
                .url("https://music.youtube.com/")
                .header("User-Agent", webUserAgent)
                .get()
                .build()
            val html = okHttpClient.newCall(homeReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                resp.body?.string().orEmpty()
            }
            val playerJsPath = Regex("""(/s/player/[^"']+/base\.js)""").find(html)?.groupValues?.get(1)
                ?: return@runCatching null
            val jsReq = Request.Builder()
                .url("https://music.youtube.com$playerJsPath")
                .header("User-Agent", webUserAgent)
                .get()
                .build()
            val js = okHttpClient.newCall(jsReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                resp.body?.string().orEmpty()
            }
            Regex("""signatureTimestamp:(\d+)""").find(js)?.groupValues?.get(1)?.toIntOrNull()
        }.getOrNull()
        val finalSts = resolved ?: 20774
        if (resolved != null) cachedSignatureTimestamp = resolved
        return finalSts
    }

    private fun fetchAuthenticatedTrackingUrls(
        videoId: String,
        cookies: String,
        authHeader: String?,
        visitorData: String?,
    ): PlaybackTrackingUrls? {
        authenticatedTrackingCache[videoId]?.let { return it }
        val jsonMediaType = "application/json".toMediaType()
        val webRemixVersion = "1.20261117.06.00"
        val webUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val hl = java.util.Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "pt"
        val gl = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() && it.length == 2 } ?: "BR"
        val sts = resolveSignatureTimestamp(webUserAgent)

        // 1. Try authenticated WEB_REMIX /player so playbackTracking URLs carry the logged-in user session.
        val webPayload = JSONObject().apply {
            put("videoId", videoId)
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", webRemixVersion)
                        put("hl", hl)
                        put("gl", gl)
                        if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                    },
                ),
            )
            put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject()
                        .put("signatureTimestamp", sts)
                        .put("html5Preference", "HTML5_PREF_WANTS"),
                ),
            )
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()
        val webReq = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", webUserAgent)
            .header("Cookie", cookies)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", webRemixVersion)
            .apply {
                if (!visitorData.isNullOrBlank()) header("X-Goog-Visitor-Id", visitorData)
                if (authHeader != null) {
                    header("Authorization", authHeader)
                    header("X-Goog-AuthUser", "0")
                }
            }
            .post(webPayload.toRequestBody(jsonMediaType))
            .build()
        val webTracking = runCatching {
            okHttpClient.newCall(webReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("YouTubeHistory", "WEB_REMIX player HTTP ${resp.code} for $videoId")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val json = JSONObject(body)
                extractPlaybackTracking(json).also {
                    if (it == null) {
                        val status = json.optJSONObject("playabilityStatus")?.optString("status")
                        android.util.Log.w("YouTubeHistory", "WEB_REMIX player had no playbackTracking (status=$status) for $videoId")
                    }
                }
            }
        }.getOrNull()
        if (webTracking != null) {
            android.util.Log.i("YouTubeHistory", "Resolved playbackTracking via WEB_REMIX for $videoId")
            authenticatedTrackingCache[videoId] = webTracking
            return webTracking
        }

        // 2. Fallback to unauthenticated IOS /player (mobile clients reject web SAPISIDHASH on /player).
        val iosClient = PlayerClients.IOS
        val iosPayload = JSONObject().apply {
            put("videoId", videoId)
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().apply {
                        put("clientName", iosClient.name)
                        put("clientVersion", iosClient.version)
                        put("hl", hl)
                        put("gl", gl)
                        if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                        iosClient.fields.forEach { (k, v) -> put(k, v) }
                    },
                ),
            )
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()
        val iosReq = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", iosClient.userAgent)
            .header("X-Youtube-Client-Name", iosClient.id.toString())
            .header("X-Youtube-Client-Version", iosClient.version)
            .apply {
                if (!visitorData.isNullOrBlank()) header("X-Goog-Visitor-Id", visitorData)
            }
            .post(iosPayload.toRequestBody(jsonMediaType))
            .build()
        val iosTracking = runCatching {
            okHttpClient.newCall(iosReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("YouTubeHistory", "IOS player HTTP ${resp.code} for $videoId")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                extractPlaybackTracking(JSONObject(body))
            }
        }.getOrNull()
        if (iosTracking != null) {
            android.util.Log.i("YouTubeHistory", "Resolved playbackTracking via IOS fallback for $videoId")
            authenticatedTrackingCache[videoId] = iosTracking
        }
        return iosTracking
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
        val visitorData = youTubeHttp.visitorIdentity.cachedOrNull()
        runCatching {
            val tracking = fetchAuthenticatedTrackingUrls(
                videoId = videoId,
                cookies = cookies,
                authHeader = authHeader,
                visitorData = visitorData,
            ) ?: run {
                android.util.Log.w("YouTubeHistory", "No authenticated playbackTracking URLs for $videoId")
                return
            }

            val cpn = buildString(16) {
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                repeat(16) { append(chars.random()) }
            }
            val webUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            // Route stats pings through music.youtube.com so Origin + SAPISIDHASH + cookies match YouTube Music's watch history endpoint.
            val playbackBaseUrl = tracking.playbackUrl.replace("https://s.youtube.com/", "https://music.youtube.com/")
            val watchtimeBaseUrl = tracking.watchtimeUrl.replace("https://s.youtube.com/", "https://music.youtube.com/")
            val len = durationSeconds.coerceAtLeast(playedSeconds).coerceAtLeast(1)

            fun Request.Builder.applyYouTubeMusicAuth(): Request.Builder = this
                .header("User-Agent", webUserAgent)
                .header("Cookie", cookies)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-Youtube-Client-Name", "67")
                .header("X-Youtube-Client-Version", clientVersion)
                .apply {
                    if (!visitorData.isNullOrBlank()) {
                        header("X-Goog-Visitor-Id", visitorData)
                    }
                    if (authHeader != null) {
                        header("Authorization", authHeader)
                        header("X-Goog-AuthUser", "0")
                    }
                }

            if (playbackBaseUrl.isNotBlank()) {
                val sep = if ('?' in playbackBaseUrl) '&' else '?'
                val ping = Request.Builder()
                    .url("${playbackBaseUrl}${sep}ver=2&c=WEB_REMIX&cver=$clientVersion&cpn=$cpn&el=detailpage&cmt=0&len=$len&state=playing&volume=100&muted=0")
                    .applyYouTubeMusicAuth()
                    .get()
                    .build()
                okHttpClient.newCall(ping).execute().use { resp ->
                    if (resp.isSuccessful) {
                        android.util.Log.i("YouTubeHistory", "Playback ping HTTP ${resp.code} for $videoId")
                    } else {
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
                    .applyYouTubeMusicAuth()
                    .get()
                    .build()
                okHttpClient.newCall(watch).execute().use { resp ->
                    if (resp.isSuccessful) {
                        android.util.Log.i("YouTubeHistory", "Watchtime ping HTTP ${resp.code} for $videoId (${playedSeconds}s)")
                    } else {
                        android.util.Log.w("YouTubeHistory", "Watchtime ping HTTP ${resp.code} for $videoId")
                    }
                }
            }
        }.onFailure { err ->
            android.util.Log.w("YouTubeHistory", "Failed to report playback history for $videoId", err)
        }
    }
}
