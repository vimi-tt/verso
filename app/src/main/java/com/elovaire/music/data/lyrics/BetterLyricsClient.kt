package elovaire.music.droidbeauty.app.data.lyrics

import elovaire.music.droidbeauty.app.core.backend.BackendResourceRegistry
import elovaire.music.droidbeauty.app.core.backend.BackendResourceTracker
import elovaire.music.droidbeauty.app.data.network.BoundedHttpTransport
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Fetches word/syllable-synced TTML lyrics from BetterLyrics (`lyrics-api.boidu.dev`).
 */
internal class BetterLyricsClient(
    resourceTracker: BackendResourceTracker = BackendResourceRegistry,
    private val transport: BoundedHttpTransport = BoundedHttpTransport(resourceTracker = resourceTracker),
) {
    suspend fun fetch(song: Song): LyricsResult {
        if (song.title.isBlank() || song.artist.isBlank()) return LyricsResult.NotFound
        return try {
            val params = buildList {
                add("s" to song.title)
                add("a" to song.artist)
                if (song.durationMs > 0L) {
                    add("d" to (song.durationMs / 1_000L).toString())
                }
                if (song.album.isNotBlank() && !song.album.equals("Unknown Album", ignoreCase = true)) {
                    add("al" to song.album)
                }
            }
            val query = params.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            val response = transport.get(
                rawUrl = "https://lyrics-api.boidu.dev/getLyrics?$query",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json, application/ttml+xml, text/xml",
                ),
                maxBytes = MAX_RESPONSE_BYTES,
            )
            when (response.statusCode) {
                200 -> parseResponse(response.body)
                404 -> LyricsResult.NotFound
                else -> LyricsResult.Unavailable
            }
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

    private fun parseResponse(bytes: ByteArray): LyricsResult {
        if (bytes.isEmpty() || bytes.size > MAX_RESPONSE_BYTES) return LyricsResult.MalformedResponse
        val raw = bytes.toString(Charsets.UTF_8).trim()
        if (raw.isEmpty()) return LyricsResult.NotFound

        val ttml = if (raw.startsWith("{")) {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return LyricsResult.MalformedResponse
            json.optString("ttml")
                .takeIf { it.isNotBlank() }
                ?: json.optString("lyrics").takeIf { it.isNotBlank() }
                ?: return LyricsResult.NotFound
        } else {
            raw
        }

        if (!ttml.contains("<p", ignoreCase = true)) {
            return LyricsResult.NotFound
        }
        val lines = parseTtmlLines(ttml)
        if (lines.isEmpty()) return LyricsResult.NotFound
        val plain = lines.joinToString("\n") { it.text }
        return LyricsResult.Found(
            LyricsPayload(
                lines = lines,
                isSynced = lines.any { it.startTimeMs != null },
                sourceTextForEmbedding = plain,
            ),
        )
    }

    private fun parseTtmlLines(ttml: String): List<LyricsLine> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(ttml))

        val lines = mutableListOf<LyricsLine>()
        var inParagraph = false
        var pStartMs: Long? = null
        var pEndMs: Long? = null
        val pText = StringBuilder()
        val pWords = mutableListOf<LyricsWordSpan>()

        var inSpan = false
        var spanStartMs: Long? = null
        var spanEndMs: Long? = null
        val spanText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.substringAfterLast(':')?.lowercase()
                    when (tag) {
                        "p" -> {
                            inParagraph = true
                            pStartMs = parseTtmlTimestampMs(parser.getAttributeValue(null, "begin"))
                            pEndMs = parseTtmlTimestampMs(parser.getAttributeValue(null, "end"))
                            pText.clear()
                            pWords.clear()
                        }
                        "span" -> if (inParagraph) {
                            inSpan = true
                            spanStartMs = parseTtmlTimestampMs(parser.getAttributeValue(null, "begin"))
                            spanEndMs = parseTtmlTimestampMs(parser.getAttributeValue(null, "end"))
                            spanText.clear()
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty()
                    if (inParagraph) {
                        if (inSpan) {
                            spanText.append(text)
                        } else if (text.isNotEmpty()) {
                            pText.append(text)
                            val lastIdx = pWords.lastIndex
                            if (lastIdx >= 0 && text.all { it.isWhitespace() }) {
                                val prev = pWords[lastIdx]
                                pWords[lastIdx] = prev.copy(text = prev.text + text)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.substringAfterLast(':')?.lowercase()
                    when (tag) {
                        "span" -> if (inParagraph && inSpan) {
                            val token = spanText.toString()
                            if (token.isNotEmpty()) {
                                if (pText.isNotEmpty() && !pText.last().isWhitespace() && !token.first().isWhitespace()) {
                                    // Preserve word separation when TTML omits explicit whitespace nodes between spans.
                                    if (pWords.isNotEmpty() && !pWords.last().text.endsWith(" ")) {
                                        val lastIdx = pWords.lastIndex
                                        pWords[lastIdx] = pWords[lastIdx].copy(text = pWords[lastIdx].text + " ")
                                        pText.append(' ')
                                    }
                                }
                                pText.append(token)
                                val start = spanStartMs ?: pStartMs
                                val end = spanEndMs ?: pEndMs ?: start
                                if (start != null && end != null) {
                                    pWords.add(
                                        LyricsWordSpan(
                                            text = token,
                                            startTimeMs = start,
                                            endTimeMs = end.coerceAtLeast(start),
                                        ),
                                    )
                                }
                            }
                            inSpan = false
                            spanStartMs = null
                            spanEndMs = null
                            spanText.clear()
                        }

                        "p" -> if (inParagraph) {
                            val finalText = pText.toString().trim()
                            if (finalText.isNotEmpty()) {
                                val lineStart = pStartMs ?: pWords.firstOrNull()?.startTimeMs
                                val lineEnd = pEndMs ?: pWords.lastOrNull()?.endTimeMs
                                lines.add(
                                    LyricsLine(
                                        text = finalText,
                                        startTimeMs = lineStart,
                                        endTimeMs = lineEnd,
                                        index = lines.size,
                                        words = pWords.toList(),
                                    ),
                                )
                            }
                            inParagraph = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return lines
    }

    private fun parseTtmlTimestampMs(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.endsWith("ms")) {
            return value.removeSuffix("ms").toDoubleOrNull()?.toLong()?.coerceAtLeast(0L)
        }
        if (value.endsWith("s")) {
            return (value.removeSuffix("s").toDoubleOrNull()?.times(1000.0))?.toLong()?.coerceAtLeast(0L)
        }
        val parts = value.split(":")
        return when (parts.size) {
            1 -> (parts[0].toDoubleOrNull()?.times(1000.0))?.toLong()?.coerceAtLeast(0L)
            2 -> {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toDoubleOrNull() ?: return null
                ((min * 60.0 + sec) * 1000.0).toLong().coerceAtLeast(0L)
            }
            3 -> {
                val hrs = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toDoubleOrNull() ?: return null
                ((hrs * 3600.0 + min * 60.0 + sec) * 1000.0).toLong().coerceAtLeast(0L)
            }
            else -> null
        }
    }

    private companion object {
        const val USER_AGENT = "Verso/1.0 (https://github.com/elovaire/verso)"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
