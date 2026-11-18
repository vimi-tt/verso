package com.vimi.k7.innertube.stream

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DownloadAudioFormat(
    val quality: String,
    val bitrate: Int,
    val contentLength: Long?
)

/**
 * A song's audio as the app asks for it: the stream to play at the quality
 * the settings choose for this network, and the AAC/M4A stream to download.
 * The resolving itself is [AudioStreamResolver]'s.
 */
class SongStreams(
    private val context: Context,
    private val audioStreams: AudioStreamResolver,
    private val playerSession: PlayerSession,
) {
    /**
     * Get the best audio stream URL for a video.
     * Note: These URLs expire, so call this right before playback.
     * @param videoId The YouTube video ID
     * @return Result containing stream URL or error
     */
    suspend fun getStreamUrl(videoId: String): Result<String> =
        when (val resolution = audioStreams.forPlayback(videoId, currentAudioPreference())) {
            is AudioResolution.Resolved -> Result.success(resolution.url)
            AudioResolution.Unresolved ->
                Result.failure(Exception("No audio stream found for $videoId"))
        }

    /** The per-network music quality setting, read fresh for each resolution. */
    private fun currentAudioPreference(): AudioPreference = AudioPreference.HIGHEST

    /**
     * Resolve an AAC/M4A audio-only stream for a file download.
     */
    suspend fun getDownloadAudioStreamUrl(
        videoId: String,
        smallest: Boolean = false,
    ): Result<String> {
        return when (val resolution = audioStreams.forDownload(videoId, smallest)) {
            is AudioResolution.Resolved -> Result.success(resolution.url)
            AudioResolution.Unresolved ->
                Result.failure(Exception("No AAC/M4A audio stream found for $videoId"))
        }
    }

    /**
     * The qualities a song can be downloaded at, best first, each with the
     * size YouTube states for it.
     *
     * [verified October 2026, visionOS `/player`, signed out] A music id
     * answers with two AAC/M4A audio streams: itag 140 (AAC-LC, about 128
     * kbps, 44.1 kHz) and itag 139 (HE-AAC, about 48 kbps), both with a
     * plain `url` and a `contentLength`. The Opus streams beside them
     * (249/250/251) are WebM and are left out for the reason
     * [getDownloadAudioStreamUrl] gives.
     *
     * One request and no size probe: the download sheet used to resolve a
     * stream and then ask googlevideo how long it was. Only the visionOS
     * answer is read, so an empty list means "unknown", not "unavailable" -
     * the download itself still has the fallback chain behind it.
     */
    suspend fun getDownloadAudioFormats(videoId: String): List<DownloadAudioFormat> =
        withContext(Dispatchers.IO) {
            val streamingData = playerSession.visionOs(videoId).answer?.streamingData
                ?: return@withContext emptyList()
            val originals = m4aAudioFormats(streamingData)
            val best = originals.maxByOrNull { it.optInt("bitrate") }
                ?: return@withContext emptyList()
            val smallest = originals.minByOrNull { it.optInt("bitrate") }
            fun JSONObject.toOption(quality: String) = DownloadAudioFormat(
                quality = quality,
                bitrate = optInt("averageBitrate").takeIf { it > 0 } ?: optInt("bitrate"),
                contentLength = optString("contentLength").toLongOrNull()?.takeIf { it > 0L },
            )
            buildList {
                add(best.toOption("HIGH"))
                if (smallest != null && smallest.optInt("itag") != best.optInt("itag")) {
                    add(smallest.toOption("SAVER"))
                }
            }
        }
}
