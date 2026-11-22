package elovaire.music.droidbeauty.app.data.settings

import elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
import elovaire.music.droidbeauty.app.domain.model.Song
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal sealed interface ParsedPlaylistLink {
    data class YouTube(val playlistId: String) : ParsedPlaylistLink
    data class Spotify(val playlistId: String) : ParsedPlaylistLink
}

internal data class SpotifyEmbedPlaylist(
    val name: String,
    val coverArtUrl: String? = null,
    val tracks: List<BackupTrackRef>,
)

/**
 * Parses and imports playlists from YouTube / YouTube Music or Spotify public playlist links.
 */
internal object PlaylistLinkImporter {
    private val httpClient = OkHttpClient()

    fun parseLink(rawInput: String): ParsedPlaylistLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        // Raw YouTube playlist ID
        if (!trimmed.contains("://") && !trimmed.contains("/")) {
            val cleanId = trimmed.removePrefix("VL")
            if (isLikelyYouTubePlaylistId(cleanId)) {
                return ParsedPlaylistLink.YouTube(cleanId)
            }
            return null
        }

        val normalizedUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()

        // YouTube / YouTube Music
        if (host.contains("youtube.com") || host.contains("youtu.be")) {
            val listParam = extractQueryParam(uri.rawQuery, "list")?.removePrefix("VL")
            if (!listParam.isNullOrBlank()) {
                return ParsedPlaylistLink.YouTube(listParam)
            }
            val browseMatch = Regex("""/browse/(VL[A-Za-z0-9_-]+)""").find(uri.path.orEmpty())
            val browseId = browseMatch?.groupValues?.getOrNull(1)?.removePrefix("VL")
            if (!browseId.isNullOrBlank()) {
                return ParsedPlaylistLink.YouTube(browseId)
            }
        }

        // Spotify
        if (host.contains("spotify.com")) {
            val match = Regex("""/playlist/([A-Za-z0-9]+)""").find(uri.path.orEmpty())
            val playlistId = match?.groupValues?.getOrNull(1)
            if (!playlistId.isNullOrBlank()) {
                return ParsedPlaylistLink.Spotify(playlistId)
            }
        }

        return null
    }

    suspend fun fetchSpotifyEmbedPlaylist(playlistId: String): SpotifyEmbedPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$playlistId")
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val html = resp.body?.string().orEmpty()
                    parseSpotifyEmbedHtml(html)
                }
            }.getOrNull()
        }

    internal fun parseSpotifyEmbedHtml(html: String): SpotifyEmbedPlaylist? {
        val nextDataMatch = Regex(
            """<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(html) ?: return null
        val jsonText = nextDataMatch.groupValues[1]
        return runCatching {
            val root = JSONObject(jsonText)
            val entity = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")
                ?: return@runCatching null
            val name = entity.optString("name").ifBlank { entity.optString("title") }.trim()
                .ifBlank { "Spotify Playlist" }
            val coverArtUrl = extractSpotifyCoverUrl(entity, html)
            val trackList = entity.optJSONArray("trackList") ?: return@runCatching null
            val tracks = buildList {
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val artist = item.optString("subtitle").trim()
                    if (title.isNotBlank()) {
                        add(BackupTrackRef(title = title, artist = artist))
                    }
                }
            }
            if (tracks.isEmpty()) null else SpotifyEmbedPlaylist(name = name, coverArtUrl = coverArtUrl, tracks = tracks)
        }.getOrNull()
    }

    private fun extractSpotifyCoverUrl(entity: JSONObject, html: String): String? {
        val sources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            ?: entity.optJSONObject("visualIdentity")?.optJSONArray("image")
        if (sources != null) {
            var bestUrl: String? = null
            var bestWidth = -1
            for (i in 0 until sources.length()) {
                val obj = sources.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                val width = obj.optInt("maxWidth", obj.optInt("width", 0))
                if (url.isNotBlank() && width >= bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
            if (!bestUrl.isNullOrBlank()) return bestUrl
        }
        val ogImage = Regex("""<meta[^>]*property="og:image"[^>]*content="([^"]+)"""").find(html)
            ?: Regex("""<meta[^>]*content="([^"]+)"[^>]*property="og:image"""").find(html)
        return ogImage?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun matchLocalOrFallback(
        ref: BackupTrackRef,
        localByKey: Map<String, Song>,
    ): Song? {
        val key = TrackNormalizer.normalizeKey(ref.title, ref.artist)
        return localByKey[key]
    }

    private fun isLikelyYouTubePlaylistId(id: String): Boolean =
        id.startsWith("PL") || id.startsWith("OLAK5uy_") || id.startsWith("RDCLAK5uy_") || id.startsWith("UU")

    private fun extractQueryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        for (pair in rawQuery.split("&")) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            if (pair.substring(0, idx) == key) {
                return pair.substring(idx + 1).takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
