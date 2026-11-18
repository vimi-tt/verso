package elovaire.music.droidbeauty.app.data.lyrics

import elovaire.music.droidbeauty.app.data.network.BoundedHttpTransport
import elovaire.music.droidbeauty.app.core.backend.BackendResourceRegistry
import elovaire.music.droidbeauty.app.core.backend.BackendResourceTracker
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal class LrclibClient(
    resourceTracker: BackendResourceTracker = BackendResourceRegistry,
    private val transport: BoundedHttpTransport = BoundedHttpTransport(resourceTracker = resourceTracker),
) {
    suspend fun fetch(song: Song): LyricsResult {
        return try {
            if (song.durationMs > 0L) {
                val query = buildList {
                    add("track_name" to song.title)
                    add("artist_name" to song.artist)
                    if (song.album.isNotBlank() && !song.album.equals("Unknown Album", ignoreCase = true)) {
                        add("album_name" to song.album)
                    }
                    add("duration" to (song.durationMs / 1_000L).toString())
                }.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
                val response = transport.get(
                    rawUrl = "https://lrclib.net/api/get?$query",
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json",
                    ),
                    maxBytes = MAX_RESPONSE_BYTES,
                )
                if (response.statusCode == 200) {
                    val direct = parse(response.body)
                    if (direct is LyricsResult.Found) return direct
                }
            }
            fetchViaSearch(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            LyricsResult.Unavailable
        } catch (_: SecurityException) {
            LyricsResult.Unavailable
        } catch (_: IllegalArgumentException) {
            LyricsResult.Unavailable
        } catch (_: IllegalStateException) {
            LyricsResult.Unavailable
        }
    }

    private suspend fun fetchViaSearch(song: Song): LyricsResult {
        val query = listOf(
            "track_name" to song.title,
            "artist_name" to song.artist,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val response = transport.get(
            rawUrl = "https://lrclib.net/api/search?$query",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
            ),
            maxBytes = MAX_RESPONSE_BYTES,
        )
        if (response.statusCode != 200) {
            return lrclibResponseResult(response.statusCode, response.retryAfterMs)
        }
        val array = runCatching {
            org.json.JSONArray(response.body.toString(Charsets.UTF_8))
        }.getOrNull() ?: return LyricsResult.MalformedResponse
        if (array.length() == 0) return LyricsResult.NotFound
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val synced = item.optNullableString("syncedLyrics")
            if (!synced.isNullOrBlank()) {
                return parseLyricsText(synced)
            }
        }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val plain = item.optNullableString("plainLyrics")
            if (!plain.isNullOrBlank()) {
                return parseLyricsText(plain)
            }
        }
        return LyricsResult.NotFound
    }

    private fun parse(bytes: ByteArray): LyricsResult {
        if (bytes.isEmpty() || bytes.size > MAX_RESPONSE_BYTES) return LyricsResult.MalformedResponse
        val response = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return LyricsResult.MalformedResponse
        val raw = response.optNullableString("syncedLyrics")
            ?: response.optNullableString("plainLyrics")
            ?: return LyricsResult.NotFound
        return parseLyricsText(raw)
    }

    private fun parseLyricsText(raw: String): LyricsResult {
        return parseLrcOrPlain(raw)?.takeIf { it.lines.isNotEmpty() }?.let(LyricsResult::Found)
            ?: LyricsResult.NotFound
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return opt(name)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()
            ?.takeIf(String::isNotBlank)
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val USER_AGENT = "Elovaire/1.0 (https://github.com/droidbeauty/elovaire-music)"
    }
}
