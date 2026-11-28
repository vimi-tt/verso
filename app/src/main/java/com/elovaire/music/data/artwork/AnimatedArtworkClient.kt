package elovaire.music.droidbeauty.app.data.artwork

import android.util.LruCache
import elovaire.music.droidbeauty.app.core.backend.BackendResourceRegistry
import elovaire.music.droidbeauty.app.core.backend.BackendResourceTracker
import elovaire.music.droidbeauty.app.data.network.BoundedHttpTransport
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal data class AnimatedArtworkMedia(
    val videoUrl: String,
    val isHls: Boolean,
    val isVertical: Boolean,
)

internal sealed interface AnimatedArtworkLookupResult {
    data class Found(val media: AnimatedArtworkMedia) : AnimatedArtworkLookupResult
    data object NotFound : AnimatedArtworkLookupResult
    data object Unavailable : AnimatedArtworkLookupResult
}

/**
 * Resolves looping animated album artwork / canvas streams via BetterLyrics' artwork service
 * (`artwork.boidu.dev`), preferring direct MP4 streams over HLS playlists for lower decode overhead.
 */
internal class AnimatedArtworkClient(
    resourceTracker: BackendResourceTracker = BackendResourceRegistry,
    private val transport: BoundedHttpTransport = BoundedHttpTransport(
        connectTimeoutMs = CONNECT_TIMEOUT_MS,
        readTimeoutMs = READ_TIMEOUT_MS,
        resourceTracker = resourceTracker,
    ),
) {
    suspend fun fetch(song: Song): AnimatedArtworkLookupResult {
        if (song.mediaKind == AudioMediaKind.Audiobook) return AnimatedArtworkLookupResult.NotFound
        val title = song.title.trim()
        val artist = song.artist.trim()
        if (title.isEmpty() || artist.isEmpty()) return AnimatedArtworkLookupResult.NotFound

        val query = buildQuery(song, title, artist)
        val primary = fetchEndpoint("https://artwork.boidu.dev/?$query")
        if (primary is AnimatedArtworkLookupResult.Found) {
            return primary
        }
        val canvas = fetchEndpoint("https://artwork.boidu.dev/canvas?$query")
        return when {
            canvas is AnimatedArtworkLookupResult.Found -> canvas
            primary is AnimatedArtworkLookupResult.NotFound && canvas is AnimatedArtworkLookupResult.NotFound ->
                AnimatedArtworkLookupResult.NotFound
            else -> AnimatedArtworkLookupResult.Unavailable
        }
    }

    private fun buildQuery(song: Song, title: String, artist: String): String {
        val params = buildList {
            add("s" to title)
            add("a" to artist)
            if (song.durationMs > 0L) {
                add("d" to (song.durationMs / 1_000L).toString())
            }
            if (song.album.isNotBlank() && !song.album.equals("Unknown Album", ignoreCase = true)) {
                add("al" to song.album.trim())
            }
        }
        return params.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private suspend fun fetchEndpoint(url: String): AnimatedArtworkLookupResult {
        return try {
            val response = transport.get(
                rawUrl = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json",
                ),
                maxBytes = MAX_RESPONSE_BYTES,
            )
            when (response.statusCode) {
                200 -> parseMedia(response.body)
                404 -> AnimatedArtworkLookupResult.NotFound
                else -> AnimatedArtworkLookupResult.Unavailable
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            AnimatedArtworkLookupResult.Unavailable
        } catch (_: SecurityException) {
            AnimatedArtworkLookupResult.Unavailable
        } catch (_: IllegalArgumentException) {
            AnimatedArtworkLookupResult.Unavailable
        } catch (_: IllegalStateException) {
            AnimatedArtworkLookupResult.Unavailable
        }
    }

    private fun parseMedia(bytes: ByteArray): AnimatedArtworkLookupResult {
        if (bytes.isEmpty() || bytes.size > MAX_RESPONSE_BYTES) {
            return AnimatedArtworkLookupResult.Unavailable
        }
        val raw = bytes.toString(Charsets.UTF_8).trim()
        if (!raw.startsWith("{")) return AnimatedArtworkLookupResult.NotFound
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return AnimatedArtworkLookupResult.Unavailable

        // Prefer square direct MP4 (lowest decode/network overhead), then HLS, then vertical canvas.
        val candidates = listOf(
            Triple(json.optHttpsUrl("videoUrl"), false, false),
            Triple(json.optHttpsUrl("animated"), true, false),
            Triple(json.optHttpsUrl("videoUrlVertical"), false, true),
            Triple(json.optHttpsUrl("animatedVertical"), true, true),
        )
        val match = candidates.firstOrNull { (url, _, _) -> url != null }
            ?: return AnimatedArtworkLookupResult.NotFound

        val resolvedUrl = match.first ?: return AnimatedArtworkLookupResult.NotFound
        val isHls = match.second || resolvedUrl.contains(".m3u8", ignoreCase = true)
        return AnimatedArtworkLookupResult.Found(
            AnimatedArtworkMedia(
                videoUrl = resolvedUrl,
                isHls = isHls,
                isVertical = match.third,
            ),
        )
    }

    private fun JSONObject.optHttpsUrl(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key).trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
        return value.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    private companion object {
        const val USER_AGENT = "Verso/1.0 (https://github.com/elovaire/verso)"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 6_000
        const val MAX_RESPONSE_BYTES = 128 * 1024
    }
}

internal object AnimatedArtworkCache {
    private const val MAX_ENTRIES = 64
    private val cache = LruCache<String, AnimatedArtworkLookupResult>(MAX_ENTRIES)
    private val client = AnimatedArtworkClient()

    suspend fun resolve(song: Song): AnimatedArtworkMedia? {
        val key = cacheKey(song) ?: return null
        synchronized(cache) {
            when (val cached = cache.get(key)) {
                is AnimatedArtworkLookupResult.Found -> return cached.media
                AnimatedArtworkLookupResult.NotFound -> return null
                AnimatedArtworkLookupResult.Unavailable, null -> Unit
            }
        }
        val result = client.fetch(song)
        if (result !is AnimatedArtworkLookupResult.Unavailable) {
            synchronized(cache) {
                cache.put(key, result)
            }
        }
        return (result as? AnimatedArtworkLookupResult.Found)?.media
    }

    private fun cacheKey(song: Song): String? {
        if (song.mediaKind == AudioMediaKind.Audiobook) return null
        val title = song.title.trim().lowercase(Locale.ROOT)
        val artist = song.artist.trim().lowercase(Locale.ROOT)
        if (title.isEmpty() || artist.isEmpty()) return null
        val album = song.album.trim().lowercase(Locale.ROOT)
        return "$title|$artist|$album"
    }
}
