package elovaire.music.droidbeauty.app.data.lyrics

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * On-demand lyric line translator with in-memory caching per songKey + target language.
 */
internal object LyricsTranslator {
    private val cache = ConcurrentHashMap<String, List<String>>()
    private val httpClient = OkHttpClient()

    suspend fun translateLines(
        cacheKey: String,
        lines: List<String>,
        targetLanguageCode: String,
    ): List<String>? {
        if (lines.isEmpty() || targetLanguageCode.isBlank()) return null
        val key = "$cacheKey|$targetLanguageCode"
        cache[key]?.let { return it }

        val joined = lines.joinToString("\n")
        val translatedJoined = withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(joined, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$targetLanguageCode&dt=t&q=$encoded"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val outer = JSONArray(body)
                    val segments = outer.optJSONArray(0) ?: return@use null
                    buildString {
                        for (i in 0 until segments.length()) {
                            append(segments.optJSONArray(i)?.optString(0).orEmpty())
                        }
                    }
                }
            }.getOrNull()
        } ?: return null

        val split = translatedJoined.split("\n")
        if (split.isEmpty()) return null
        val aligned = List(lines.size) { index ->
            split.getOrNull(index)?.trim().orEmpty()
        }
        cache[key] = aligned
        return aligned
    }
}
