package com.vimi.k7.innertube.stream

import kotlin.math.abs
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioTrackType

/**
 * Choosing an audio format out of a `/player` response's `streamingData`.
 * Pure functions over the JSON, pinned by `AudioFormatsTest`.
 */

/** How a listener wants a stream chosen: the three values of the music quality setting. */
enum class AudioPreference {
    /** The best bitrate on offer. */
    HIGHEST,

    /** The track closest to 128 kbps. */
    BALANCED,

    /** The smallest stream. */
    LOWEST,
}

/**
 * A chosen stream: its URL, whether it is a muxed video file standing in for
 * audio, and the loudness the format states for itself, if it states one.
 */
class AudioPick(
    val url: String,
    val muxed: Boolean,
    val loudnessDb: Float? = null,
    val itag: Int? = null,
    val mimeType: String? = null,
    val contentLength: Long? = null,
    val bitrate: Int? = null,
    val containerFormat: String = "m4a",
)

/**
 * A format's own `loudnessDb`: how far the track sits above YouTube's -14
 * LKFS target, the same quantity `playerConfig.audioConfig.loudnessDb`
 * carries on the clients that send one.
 *
 * [verified October 2026] visionOS sends no `playerConfig.audioConfig` at
 * all. Each audio format carries `loudnessDb` and `trackAbsoluteLoudnessLkfs`
 * instead, the first being the second plus 14 (5.39 and -8.61 on the fixture
 * song), and differing by about 0.01 between the AAC and Opus encodes.
 * A missing key reads as unknown, never as 0.0, which is a real measurement.
 */
internal fun formatLoudnessDb(format: JSONObject): Float? =
    (if (format.has("loudnessDb")) format.optDouble("loudnessDb").toFloat() else null)
        ?.takeIf { it.isFinite() }

/** `adaptiveFormats` then `formats`, as one list. */
private fun JSONObject.allFormats(): List<JSONObject> =
    listOf("adaptiveFormats", "formats").flatMap { key ->
        optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }
            .orEmpty()
    }

private fun JSONObject.hasPlainUrl(): Boolean = optString("url").isNotEmpty()

/**
 * The audio URL to play from [streamingData] under [preference].
 *
 * Falls back to a muxed MP4 when there is no audio-only format: the player
 * reads just its audio track, and ANDROID_VR can return nothing else (itag 18
 * only, since March 2026). Only `formats` is muxed - every `video/` entry in
 * `adaptiveFormats` is video-only, and taking the lowest-bitrate one of those
 * used to yield a silent 144p stream. [verified September 2026: visionOS
 * returns an empty `formats`, so on the primary path this fallback finds
 * nothing rather than something silent.]
 */
internal fun pickAudio(streamingData: JSONObject, preference: AudioPreference): AudioPick? {
    val audioOnly = originalTrackOnly(
        streamingData.allFormats().filter {
            it.optString("mimeType").contains("audio") && it.hasPlainUrl()
        }
    )
    val chosen = when (preference) {
        AudioPreference.LOWEST -> audioOnly.minByOrNull { it.optInt("bitrate") }
        AudioPreference.BALANCED -> audioOnly.minByOrNull { abs(it.optInt("bitrate") - 128_000) }
        AudioPreference.HIGHEST -> audioOnly.maxByOrNull { it.optInt("bitrate") }
    }
    chosen?.optString("url")?.takeIf { it.isNotEmpty() }?.let { url ->
        val itag = chosen.optInt("itag")
        val mime = chosen.optString("mimeType")
        val ext = if (mime.contains("webm") || mime.contains("opus") || itag == 251) "opus" else "m4a"
        return AudioPick(
            url = url,
            muxed = false,
            loudnessDb = formatLoudnessDb(chosen),
            itag = itag,
            mimeType = mime,
            contentLength = chosen.optString("contentLength").toLongOrNull()?.takeIf { it > 0 },
            bitrate = chosen.optInt("bitrate"),
            containerFormat = ext,
        )
    }

    val muxed = streamingData.optJSONArray("formats")
        ?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }
        .orEmpty()
        .filter { it.optString("mimeType").startsWith("video/") && it.hasPlainUrl() }
    val chosenMuxed = muxed.minByOrNull { it.optInt("bitrate") }
    return chosenMuxed?.optString("url")?.takeIf { it.isNotEmpty() }
        ?.let {
            AudioPick(
                url = it,
                muxed = true,
                itag = chosenMuxed.optInt("itag"),
                mimeType = chosenMuxed.optString("mimeType"),
                contentLength = chosenMuxed.optString("contentLength").toLongOrNull()?.takeIf { len -> len > 0 },
                bitrate = chosenMuxed.optInt("bitrate"),
                containerFormat = "mp4",
            )
        }
}

/**
 * Pick raw native audio format prioritizing itag 251 (Opus ~160kbps) or itag 140 (AAC ~128kbps).
 * Zero transcoding / zero FFmpeg.
 */
internal fun pickRawAudioStream(
    streamingData: JSONObject,
    preference: AudioPreference = AudioPreference.HIGHEST
): AudioPick? {
    val audioOnly = originalTrackOnly(
        streamingData.allFormats().filter {
            it.optString("mimeType").contains("audio") && it.hasPlainUrl()
        }
    )
    if (audioOnly.isEmpty()) return pickAudio(streamingData, preference)

    val chosen = when (preference) {
        AudioPreference.HIGHEST -> {
            // Prioritize itag 251 (Opus ~160kbps), then itag 140 (AAC ~128kbps), then highest bitrate
            audioOnly.firstOrNull { it.optInt("itag") == 251 }
                ?: audioOnly.firstOrNull { it.optInt("itag") == 140 }
                ?: audioOnly.maxByOrNull { it.optInt("bitrate") }
        }
        AudioPreference.BALANCED, AudioPreference.LOWEST -> {
            // Prioritize itag 140 (AAC ~128kbps), then itag 251, then ~128kbps
            audioOnly.firstOrNull { it.optInt("itag") == 140 }
                ?: audioOnly.firstOrNull { it.optInt("itag") == 251 }
                ?: audioOnly.minByOrNull { abs(it.optInt("bitrate") - 128_000) }
        }
    } ?: return pickAudio(streamingData, preference)

    val url = chosen.optString("url").takeIf { it.isNotBlank() } ?: return null
    val itag = chosen.optInt("itag")
    val mime = chosen.optString("mimeType")
    val bitrate = chosen.optInt("bitrate")
    val contentLength = chosen.optString("contentLength").toLongOrNull()?.takeIf { it > 0 }
    val ext = if (mime.contains("webm") || mime.contains("opus") || itag == 251) "opus" else "m4a"

    return AudioPick(
        url = url,
        muxed = false,
        loudnessDb = formatLoudnessDb(chosen),
        itag = itag,
        mimeType = mime,
        contentLength = contentLength,
        bitrate = bitrate,
        containerFormat = ext,
    )
}

/**
 * The original soundtrack's AAC/M4A audio-only formats that carry a plain
 * URL. Downloads take only these: the file is published as `.m4a` and then
 * tagged, so an Opus/WebM stream or a muxed fallback would put bytes from the
 * wrong container behind that name.
 */
internal fun m4aAudioFormats(streamingData: JSONObject): List<JSONObject> {
    val formats = streamingData.optJSONArray("adaptiveFormats") ?: return emptyList()
    return originalTrackOnly(
        (0 until formats.length()).mapNotNull(formats::optJSONObject).filter { format ->
            val mime = format.optString("mimeType")
            mime.startsWith("audio/mp4") &&
                (mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true)) &&
                format.optString("url").isNotBlank()
        }
    )
}

/** The M4A URL to download: the smallest stream when [smallest], else the best. */
internal fun pickM4aUrl(streamingData: JSONObject, smallest: Boolean): String? {
    val formats = m4aAudioFormats(streamingData)
    val chosen = if (smallest) {
        formats.minByOrNull { it.optInt("bitrate") }
    } else {
        formats.maxByOrNull { it.optInt("bitrate") }
    }
    return chosen?.optString("url")?.takeIf(String::isNotBlank)
}

/** How many formats in [streamingData] are ciphered, for the "no usable URL" log line. */
internal fun cipheredFormatCount(streamingData: JSONObject): Pair<Int, Int> {
    val formats = streamingData.allFormats()
    val ciphered = formats.count {
        it.optString("signatureCipher").isNotEmpty() || it.optString("cipher").isNotEmpty()
    }
    return ciphered to formats.size
}

/**
 * Keep YouTube's original soundtrack when a video also carries dubbed,
 * descriptive or secondary audio.
 *
 * Older and single-track responses label nothing; those untyped formats are
 * the compatibility fallback. When every format is explicitly labelled
 * non-original the result is empty, so the caller uses its muxed fallback
 * rather than knowingly selecting a dub.
 */
internal fun originalTrackOnly(formats: List<JSONObject>): List<JSONObject> {
    val typed = formats.map { it to audioTrackTypeOf(it) }
    val originals = typed.filter { it.second == AudioTrackType.ORIGINAL }.map { it.first }
    if (originals.isNotEmpty()) return originals
    return typed.filter { it.second == null }.map { it.first }
}

/** The track type a format declares in its `xtags`, or null when it declares none. */
internal fun audioTrackTypeOf(format: JSONObject): AudioTrackType? {
    val xtags = format.optString("xtags").takeIf { it.isNotBlank() } ?: return null
    return runCatching { YoutubeParsingHelper.extractAudioTrackType(xtags) }.getOrNull()
}
