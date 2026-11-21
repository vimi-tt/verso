package com.vimi.k7.innertube.stream

import com.vimi.k7.innertube.legacy.NewPipeGateway
import com.vimi.k7.innertube.legacy.youtubeService
import com.vimi.k7.innertube.transport.BROWSER_USER_AGENT
import com.vimi.k7.innertube.transport.INNER_TUBE_API_KEY
import com.vimi.k7.innertube.transport.YouTubeHttp
import com.vimi.k7.innertube.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What resolving a song's stream came to. */
sealed interface AudioResolution {
    /** A URL to play, and which path produced it, along with stream format details. */
    class Resolved(
        val url: String,
        val source: String,
        val itag: Int? = null,
        val mimeType: String? = null,
        val contentLength: Long? = null,
        val bitrate: Int? = null,
        val containerFormat: String = "m4a",
    ) : AudioResolution

    /** Every path was tried and none gave a URL. */
    data object Unresolved : AudioResolution
}

class AudioStreamResolver(
    val session: PlayerSession,
    private val newPipe: NewPipeAudioSource,
    private val newPipeBudgetMs: Long = 8_000L,
    private val onResponse: (videoId: String, root: JSONObject) -> Unit = { _, _ -> },
    private val onLoudness: (videoId: String, loudnessDb: Float) -> Unit = { _, _ -> },
) {
    companion object {
        fun create(
            http: YouTubeHttp,
            newPipeGateway: NewPipeGateway,
            budgetMs: Long = 8_000L,
            onResponse: (videoId: String, root: JSONObject) -> Unit = { _, _ -> },
            onLoudness: (videoId: String, loudnessDb: Float) -> Unit = { _, _ -> },
        ): AudioStreamResolver {
            val playerSession = PlayerSession(
                http.visitorIdentity,
                PlayerApi(http.streamResolveClient, INNER_TUBE_API_KEY),
                StreamProbe(http.streamResolveClient, BROWSER_USER_AGENT),
            )
            return AudioStreamResolver(
                session = playerSession,
                newPipe = NewPipeAudioSource(youtubeService, newPipeGateway.newPipeScope),
                newPipeBudgetMs = budgetMs,
                onResponse = onResponse,
                onLoudness = onLoudness,
            )
        }
    }
    /** The URL to play [videoId] from, chosen for [preference]. */
    suspend fun forPlayback(videoId: String, preference: AudioPreference): AudioResolution =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            fun resolved(pick: AudioPick, source: String): AudioResolution {
                BotCheckVerdict.clearOnSuccess()
                KLog.i(
                    STREAM_TAG,
                    "Resolve[$source] OK videoId=$videoId dt=${System.currentTimeMillis() - startMs}ms",
                )
                return AudioResolution.Resolved(
                    url = pick.url,
                    source = source,
                    itag = pick.itag,
                    mimeType = pick.mimeType,
                    contentLength = pick.contentLength,
                    bitrate = pick.bitrate,
                    containerFormat = pick.containerFormat,
                )
            }

            val direct = session.visionOs(videoId)
            direct.answer?.let { answer ->
                onResponse(videoId, answer.root)
                pickAudioLogged(videoId, answer.streamingData, preference)
            }?.let { pick ->
                pick.loudnessDb?.let { onLoudness(videoId, it) }
                return@withContext resolved(pick, "visionOS")
            }

            // High-speed native fallback clients (Android VR, Android Music, iOS, TV Embedded)
            // Resolve directly via InnerTube without JS deciphering or NewPipe latency
            val native = session.nativeFallback(
                videoId,
                botCheckedAlready = direct.botChecked,
                onResponse = { onResponse(videoId, it) },
            )?.let { pickAudioLogged(videoId, it.streamingData, preference) }
            if (native != null) {
                native.loudnessDb?.let { onLoudness(videoId, it) }
                return@withContext resolved(native, "InnerTube fallback")
            }

            // Ultimate fallback: NewPipe extractor with bounded budget
            val extracted = newPipe.withinBudget(videoId, preference, 3_500L)
            extracted.url?.takeIf { it.isNotEmpty() }?.let {
                BotCheckVerdict.clearOnSuccess()
                return@withContext AudioResolution.Resolved(it, "NewPipe fallback")
            }

            KLog.e(
                STREAM_TAG,
                "Resolve FAIL videoId=$videoId all clients exhausted " +
                    "dt=${System.currentTimeMillis() - startMs}ms",
            )
            AudioResolution.Unresolved
        }

    /**
     * Resolves a raw native audio stream for downloads, prioritizing itag 251 (Opus ~160kbps)
     * and itag 140 (AAC ~128kbps) without transcoding or JS deciphering.
     */
    suspend fun forRawDownload(videoId: String, preference: AudioPreference = AudioPreference.HIGHEST): AudioResolution =
        withContext(Dispatchers.IO) {
            val direct = session.visionOs(videoId)
            direct.answer?.let { pickRawAudioStream(it.streamingData, preference) }?.let { pick ->
                BotCheckVerdict.clearOnSuccess()
                return@withContext AudioResolution.Resolved(
                    url = pick.url,
                    source = "visionOS",
                    itag = pick.itag,
                    mimeType = pick.mimeType,
                    contentLength = pick.contentLength,
                    bitrate = pick.bitrate,
                    containerFormat = pick.containerFormat,
                )
            }

            val fallback = session.nativeFallback(
                videoId,
                botCheckedAlready = direct.botChecked,
                onResponse = { onResponse(videoId, it) },
            )?.let { pickRawAudioStream(it.streamingData, preference) }
            if (fallback != null) {
                return@withContext AudioResolution.Resolved(
                    url = fallback.url,
                    source = "InnerTube fallback",
                    itag = fallback.itag,
                    mimeType = fallback.mimeType,
                    contentLength = fallback.contentLength,
                    bitrate = fallback.bitrate,
                    containerFormat = fallback.containerFormat,
                )
            }

            val extracted = newPipe.withinBudget(videoId, preference, newPipeBudgetMs)
            extracted.url?.takeIf { it.isNotBlank() }?.let {
                BotCheckVerdict.clearOnSuccess()
                return@withContext AudioResolution.Resolved(it, "NewPipe fallback")
            }

            AudioResolution.Unresolved
        }

    /**
     * Backward-compatible AAC/M4A resolution for legacy callers.
     */
    suspend fun forDownload(videoId: String, smallest: Boolean): AudioResolution =
        forRawDownload(videoId, if (smallest) AudioPreference.LOWEST else AudioPreference.HIGHEST)

    private fun pickAudioLogged(
        videoId: String,
        streamingData: JSONObject,
        preference: AudioPreference,
    ): AudioPick? {
        val pick = pickAudio(streamingData, preference)
        if (pick == null) {
            val (ciphered, total) = cipheredFormatCount(streamingData)
            KLog.w(STREAM_TAG, "No usable URL videoId=$videoId ciphered=$ciphered/$total")
        } else if (pick.muxed) {
            KLog.w(STREAM_TAG, "Using a muxed video format videoId=$videoId (no audio-only)")
        }
        return pick
    }
}
