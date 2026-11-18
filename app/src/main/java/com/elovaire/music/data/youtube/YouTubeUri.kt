package elovaire.music.droidbeauty.app.data.youtube

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Logical URI scheme and deterministic ID helpers for YouTube streaming tracks. */
internal object YouTubeUri {
    const val SCHEME = "elovaire-yt"
    private const val HOST_TRACK = "track"

    fun isYouTubeUri(uri: Uri?): Boolean =
        uri?.scheme.equals(SCHEME, ignoreCase = true)

    fun trackUri(videoId: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_TRACK)
            .appendPath(videoId.trim())
            .build()

    fun videoId(uri: Uri?): String? {
        if (!isYouTubeUri(uri)) return null
        if (!uri?.host.equals(HOST_TRACK, ignoreCase = true)) return null
        return uri?.pathSegments?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
    }

    /**
     * Derives a deterministic negative 64-bit ID for a YouTube [videoId] so it
     * never collides with positive MediaStore or network-library IDs.
     */
    fun trackIdFor(videoId: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("youtube:$videoId".toByteArray(StandardCharsets.UTF_8))
        var hash = 0L
        for (i in 0 until 8) {
            hash = (hash shl 8) or (digest[i].toLong() and 0xFFL)
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) -1L else -positive
    }
}
