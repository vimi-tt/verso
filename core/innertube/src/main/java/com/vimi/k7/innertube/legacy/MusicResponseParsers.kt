package com.vimi.k7.innertube.legacy

import com.vimi.k7.innertube.model.MusicMetadata
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.transport.continuationItemsOrNull
import com.vimi.k7.innertube.transport.findContinuationTokens
import com.vimi.k7.innertube.transport.findObjectsByKey
import com.vimi.k7.innertube.transport.getRunText
import com.vimi.k7.innertube.transport.YOUTUBE_TAG
import com.vimi.k7.innertube.util.KLog
import org.json.JSONArray
import org.json.JSONObject

// WEB_REMIX responses read into songs and playlists: the account's library
// pages, playlist shelves and their continuation tokens. Pure; the row-level
// reading of a song's artists and album is in MusicMetadata.

internal fun parseSongsFromInternalJson(
    json: String,
    preserveDuplicates: Boolean = false
): List<Song> {
    val songs = mutableListOf<Song>()
    try {
        val root = JSONObject(json)

        // OPTIMIZED: Direct traversal instead of recursive search
        // Find the SectionListRenderer which contains the shelves
        val contentsArray = findRootContents(root) ?: return emptyList()

        // Iterate over shelves (musicCarouselShelfRenderer, musicShelfRenderer, etc.)
        for (i in 0 until contentsArray.length()) {
            val shelfWrapper = contentsArray.optJSONObject(i) ?: continue

            // Get the items array from the shelf
            val items = parseItemsFromShelf(shelfWrapper)

            // Process items
            items.forEach { item ->
                try {
                    // Strategy 1: musicResponsiveListItemRenderer (Flex Columns) - Standard Song/Video list
                    val responsiveItem = item.optJSONObject("musicResponsiveListItemRenderer")
                    if (responsiveItem != null) {
                        parseResponsiveListItem(responsiveItem)?.let { songs.add(it) }
                    }

                    // Strategy 2: musicTwoRowItemRenderer (Title/Subtitle) - Cards/Shelves
                    val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                    if (twoRowItem != null) {
                         parseTwoRowItem(twoRowItem)?.let { songs.add(it) }
                    }
                } catch (e: Exception) {
                    // Skip malformed item
                }
            }
        }
    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "Could not parse music song shelf", e)
    }
    return if (preserveDuplicates) songs else songs.distinctBy { it.id }
}

internal fun parsePlaylistsFromInternalJson(json: String): List<PlaylistDisplayItem> {
    val playlists = mutableListOf<PlaylistDisplayItem>()
    try {
        val root = JSONObject(json)

        // OPTIMIZED: Use direct traversal
        val contentsArray = findRootContents(root) ?: return emptyList()

        // Iterate over shelves and items
        for (i in 0 until contentsArray.length()) {
            val shelfWrapper = contentsArray.optJSONObject(i) ?: continue
            val items = parseItemsFromShelf(shelfWrapper)

            items.forEach { item ->
                try {
                     // Playlists are usually musicTwoRowItemRenderer
                     val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                     if (twoRowItem != null) {
                         // Extract ID
                         val navigationEndpoint = twoRowItem.optJSONObject("navigationEndpoint")
                         val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")

                         // Ensure it's a playlist
                         if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL"))) {
                             val cleanId = browseId.removePrefix("VL")

                             // Extract Title
                             val title = getRunText(twoRowItem.optJSONObject("title")).orEmpty()

                             // Extract Subtitle (Uploader / Count)
                             val subtitleObj = twoRowItem.optJSONObject("subtitle")
                             val subtitle = getRunText(subtitleObj).orEmpty()

                             val itemCount = extractItemCountFromSubtitle(subtitleObj)

                             // Extract Thumbnail
                             val thumbnails = twoRowItem.optJSONObject("thumbnailRenderer")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")

                             val thumbnailUrl = thumbnails?.let {
                                 it.optJSONObject(it.length() - 1)?.optString("url")
                             }

                             playlists.add(PlaylistDisplayItem(
                                 name = title,
                                 url = "https://music.youtube.com/playlist?list=$cleanId",
                                 uploaderName = subtitle,
                                 itemCount = itemCount,
                                 thumbnailUrl = thumbnailUrl
                             ))
                         }
                     }
                } catch (e: Exception) {
                     // Skip
                }
            }
        }
    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "Could not parse music playlist shelf", e)
    }
    return playlists 
}

internal fun parseAlbumsFromInternalJson(json: String): List<PlaylistDisplayItem> {
    val albums = mutableListOf<PlaylistDisplayItem>()
    try {
        val root = JSONObject(json)
        val contentsArray = findRootContents(root) ?: return emptyList()

        for (i in 0 until contentsArray.length()) {
            val shelfWrapper = contentsArray.optJSONObject(i) ?: continue
            val items = parseItemsFromShelf(shelfWrapper)

            items.forEach { item ->
                try {
                    val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                    if (twoRowItem != null) {
                        val navigationEndpoint = twoRowItem.optJSONObject("navigationEndpoint")
                        val browseEndpoint = navigationEndpoint?.optJSONObject("browseEndpoint")
                        val browseId = browseEndpoint?.optString("browseId")
                        val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                            ?.optJSONObject("browseEndpointContextMusicConfig")
                            ?.optString("pageType")

                        val isAlbum = (browseId != null && (browseId.startsWith("MPRE") || browseId.contains("release_detail"))) ||
                            pageType == "MUSIC_PAGE_TYPE_ALBUM"

                        if (browseId != null && isAlbum) {
                            val title = getRunText(twoRowItem.optJSONObject("title")).orEmpty()
                            val subtitleObj = twoRowItem.optJSONObject("subtitle")
                            val subtitle = getRunText(subtitleObj).orEmpty()
                            val year = extractYearFromSubtitle(subtitleObj)
                            val itemCount = extractItemCountFromSubtitle(subtitleObj)

                            val thumbnails = twoRowItem.optJSONObject("thumbnailRenderer")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")

                            val thumbnailUrl = thumbnails?.let {
                                it.optJSONObject(it.length() - 1)?.optString("url")
                            }

                            val cleanArtist = parseArtistFromAlbumSubtitle(subtitle)

                            albums.add(PlaylistDisplayItem(
                                name = title,
                                url = "https://music.youtube.com/browse/$browseId",
                                uploaderName = cleanArtist,
                                itemCount = itemCount,
                                thumbnailUrl = thumbnailUrl,
                                releaseYear = year
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }
        }
    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "Could not parse music album shelf", e)
    }
    return albums
}

internal fun parseArtistsFromInternalJson(json: String): List<PlaylistDisplayItem> {
    val artists = mutableListOf<PlaylistDisplayItem>()
    try {
        val root = JSONObject(json)
        val contentsArray = findRootContents(root) ?: return emptyList()

        for (i in 0 until contentsArray.length()) {
            val shelfWrapper = contentsArray.optJSONObject(i) ?: continue
            val items = parseItemsFromShelf(shelfWrapper)

            items.forEach { item ->
                try {
                    val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                    if (twoRowItem != null) {
                        val navigationEndpoint = twoRowItem.optJSONObject("navigationEndpoint")
                        val browseEndpoint = navigationEndpoint?.optJSONObject("browseEndpoint")
                        val browseId = browseEndpoint?.optString("browseId")
                        val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                            ?.optJSONObject("browseEndpointContextMusicConfig")
                            ?.optString("pageType")

                        val isArtist = (browseId != null && (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_corpus_artists"))) ||
                            pageType == "MUSIC_PAGE_TYPE_ARTIST"

                        if (browseId != null && isArtist) {
                            val name = getRunText(twoRowItem.optJSONObject("title")).orEmpty()
                            val subtitleObj = twoRowItem.optJSONObject("subtitle")
                            val subtitle = getRunText(subtitleObj).orEmpty()

                            val thumbnails = twoRowItem.optJSONObject("thumbnailRenderer")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")

                            val thumbnailUrl = thumbnails?.let {
                                it.optJSONObject(it.length() - 1)?.optString("url")
                            }

                            if (name.isNotBlank()) {
                                artists.add(PlaylistDisplayItem(
                                    name = name,
                                    url = "https://music.youtube.com/channel/$browseId",
                                    uploaderName = subtitle,
                                    thumbnailUrl = thumbnailUrl
                                ))
                            }
                        }
                    } else {
                        val responsiveItem = item.optJSONObject("musicResponsiveListItemRenderer")
                        if (responsiveItem != null) {
                            val navigationEndpoint = responsiveItem.optJSONObject("navigationEndpoint")
                            val browseEndpoint = navigationEndpoint?.optJSONObject("browseEndpoint")
                            val browseId = browseEndpoint?.optString("browseId")

                            if (browseId != null && browseId.startsWith("UC")) {
                                val flexColumns = responsiveItem.optJSONArray("flexColumns")
                                val titleObj = flexColumns?.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                val name = getRunText(titleObj).orEmpty()

                                val subtitleObj = flexColumns?.optJSONObject(1)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                val subtitle = getRunText(subtitleObj).orEmpty()

                                val thumbnails = responsiveItem.optJSONObject("thumbnail")
                                    ?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")

                                val thumbnailUrl = thumbnails?.let {
                                    it.optJSONObject(it.length() - 1)?.optString("url")
                                }

                                if (name.isNotBlank()) {
                                    artists.add(PlaylistDisplayItem(
                                        name = name,
                                        url = "https://music.youtube.com/channel/$browseId",
                                        uploaderName = subtitle,
                                        thumbnailUrl = thumbnailUrl
                                    ))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }
        }
    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "Could not parse music artists shelf", e)
    }
    return artists
}

private fun parseArtistFromAlbumSubtitle(subtitle: String): String {
    val parts = subtitle.split("•").map { it.trim() }
    return when {
        parts.size >= 3 -> parts[1]
        parts.size == 2 -> if (parts[1].toIntOrNull() != null) parts[0] else parts[1]
        else -> subtitle.replace(Regex("^(Álbum|Album|Single|EP)\\s*•?\\s*", RegexOption.IGNORE_CASE), "").trim()
    }
}

private fun extractYearFromSubtitle(subtitleObj: JSONObject?): Int? {
    if (subtitleObj == null) return null
    val fullText = getRunText(subtitleObj) ?: return null
    val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(fullText)
    return match?.value?.toIntOrNull()
}

/**
 * Locates the 'contents' array within sectionListRenderer by traversing standard paths.
 * Handles: Home (Browse), Search Results, and Playlist Details.
 */
private fun findRootContents(root: JSONObject): JSONArray? {
    // Path 1: Standard Browse/Home/Playlist (contents -> singleColumn... -> tabs -> tab -> content -> sectionList)
    root.optJSONObject("contents")
        ?.optJSONObject("singleColumnBrowseResultsRenderer")
        ?.optJSONArray("tabs")?.optJSONObject(0)
        ?.optJSONObject("tabRenderer")
        ?.optJSONObject("content")
        ?.optJSONObject("sectionListRenderer")
        ?.optJSONArray("contents")
        ?.let { return it }

    // Path 1b: Two-column Browse (newer playlist/album layout:
    // contents -> twoColumnBrowseResultsRenderer -> secondaryContents -> sectionList)
    root.optJSONObject("contents")
        ?.optJSONObject("twoColumnBrowseResultsRenderer")
        ?.optJSONObject("secondaryContents")
        ?.optJSONObject("sectionListRenderer")
        ?.optJSONArray("contents")
        ?.let { return it }

    // Path 2: Search Results (contents -> tabbedSearchResultsRenderer -> tabs -> tab -> content -> sectionList)
    root.optJSONObject("contents")
        ?.optJSONObject("tabbedSearchResultsRenderer")
        ?.optJSONArray("tabs")?.optJSONObject(0)
        ?.optJSONObject("tabRenderer")
        ?.optJSONObject("content")
        ?.optJSONObject("sectionListRenderer")
        ?.optJSONArray("contents")
        ?.let { return it }

    // Path 3: Direct SectionList (sometimes used in continuation responses)
    root.optJSONObject("continuationContents")
        ?.optJSONObject("musicPlaylistShelfContinuation")
        ?.optJSONArray("contents")
        ?.let { 
            // Wrap items in a synthetic shelf structure to match loop expectation or return directly
            // For continuation, it's usually a list of items directly.
            // To keep logic consistent, we'll return this directly and handle it if the caller expects shelves.
            // Actually, continuations usually return items directly, not shelves.
            // Let's handle generic continuation structure:
            return it
        }



    root.optJSONObject("continuationContents")
        ?.optJSONObject("musicShelfContinuation")
        ?.optJSONArray("contents")
        ?.let { return it }

    root.optJSONObject("continuationContents")
        ?.optJSONObject("sectionListContinuation")
        ?.optJSONArray("contents")
        ?.let { return it }

    // Grid continuation (library playlist/album pages)
    root.optJSONObject("continuationContents")
        ?.optJSONObject("gridContinuation")
        ?.optJSONArray("items")
        ?.let { return it }

    // Modern continuation shape - every branch above is the legacy form.
    // See continuationItemsOrNull for what changed and what it cost.
    continuationItemsOrNull(root)?.let { return it }

    return null
}

/**
 * Extracts the list of items from a Shelf wrapper (Carousel, Shelf, or direct list).
 */
internal fun parseItemsFromShelf(shelfWrapper: JSONObject): List<JSONObject> {
    val items = mutableListOf<JSONObject>()

    // 1. musicCarouselShelfRenderer (Horizontal Scroll)
    val carousel = shelfWrapper.optJSONObject("musicCarouselShelfRenderer")
    if (carousel != null) {
        val contents = carousel.optJSONArray("contents")
        if (contents != null) {
            for (j in 0 until contents.length()) {
                contents.optJSONObject(j)?.let { items.add(it) }
            }
        }
        return items
    }

    // 2. musicShelfRenderer (Vertical List)
    val shelf = shelfWrapper.optJSONObject("musicShelfRenderer")
    if (shelf != null) {
        val contents = shelf.optJSONArray("contents")
        if (contents != null) {
            for (j in 0 until contents.length()) {
                contents.optJSONObject(j)?.let { items.add(it) }
            }
        }
        return items
    }

    // 3. musicPlaylistShelfRenderer (Playlist Detail List)
    val playlistShelf = shelfWrapper.optJSONObject("musicPlaylistShelfRenderer")
    if (playlistShelf != null) {
        val contents = playlistShelf.optJSONArray("contents")
        if (contents != null) {
            for (j in 0 until contents.length()) {
                contents.optJSONObject(j)?.let { items.add(it) }
            }
        }
        return items
    }

    // 4. gridRenderer (Library pages: grid of playlists/albums)
    val grid = shelfWrapper.optJSONObject("gridRenderer")
    if (grid != null) {
        val gridItems = grid.optJSONArray("items")
        if (gridItems != null) {
            for (j in 0 until gridItems.length()) {
                gridItems.optJSONObject(j)?.let { items.add(it) }
            }
        }
        return items
    }

    // 5. itemSectionRenderer wraps another shelf (e.g. the library grid)
    val itemSection = shelfWrapper.optJSONObject("itemSectionRenderer")
    if (itemSection != null) {
        val contents = itemSection.optJSONArray("contents")
        if (contents != null) {
            for (j in 0 until contents.length()) {
                contents.optJSONObject(j)?.let { items.addAll(parseItemsFromShelf(it)) }
            }
        }
        return items
    }

    // 6. Direct Item (if the "shelf" is actually just an item in a continuation list)
    if (shelfWrapper.has("musicResponsiveListItemRenderer") || shelfWrapper.has("musicTwoRowItemRenderer")) {
        items.add(shelfWrapper)
    }

    return items
}

internal fun parseResponsiveListItem(item: JSONObject): Song? = MusicMetadata.song(item)

private fun parseTwoRowItem(item: JSONObject): Song? = MusicMetadata.song(item)

/**
 * Extract item count from playlist subtitle.
 * The subtitle typically contains patterns like "100 songs", "50 videos", etc.
 */
private fun extractItemCountFromSubtitle(subtitleObj: JSONObject?): Int {
    if (subtitleObj == null) return -1

    try {
        // Try to find count in runs array
        val runs = subtitleObj.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val runText = runs.optJSONObject(i)?.optString("text") ?: continue
                // Look for patterns like "100 songs", "50 videos", "25 tracks"
                val countMatch = Regex("""(\d+)\s*(songs?|videos?|tracks?)""", RegexOption.IGNORE_CASE).find(runText)
                if (countMatch != null) {
                    return countMatch.groupValues[1].toIntOrNull() ?: -1
                }
                // Also check for just numbers that might represent count
                val numberMatch = Regex("""^(\d+)$""").find(runText.trim())
                if (numberMatch != null) {
                    return numberMatch.groupValues[1].toIntOrNull() ?: -1
                }
            }
        }

        // Try from simpleText
        val simpleText = subtitleObj.optString("simpleText", "")
        val countMatch = Regex("""(\d+)\s*(songs?|videos?|tracks?)""", RegexOption.IGNORE_CASE).find(simpleText)
        if (countMatch != null) {
            return countMatch.groupValues[1].toIntOrNull() ?: -1
        }
    } catch (e: Exception) {
        // Ignore
    }

    return -1
}

/**
 * Read only the continuation belonging to the playlist shelf. A generic
 * recursive "first continuation" can pick an unrelated carousel token.
 *
 * That scoping is load-bearing rather than tidiness [verified August 2026]:
 * a playlist page also carries
 * `twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.continuations[0]`,
 * and following that token returns a `musicCarouselShelfRenderer` of
 * related playlists, not more tracks. Do not widen this to a bare
 * findContinuationTokens over the whole response.
 *
 * Page one puts the token in a `continuationItemRenderer` at the end of the
 * shelf's own contents, which the shelf scopes already cover. Later pages
 * arrive under `appendContinuationItemsAction`, where there is no shelf at
 * all - without that scope the chain stopped after page two even once the
 * items themselves parsed.
 */
internal fun extractPlaylistContinuationToken(json: String): String? = try {
    val root = JSONObject(json)
    val scopes = mutableListOf<JSONObject>()
    findObjectsByKey(root, "musicPlaylistShelfRenderer", scopes)
    findObjectsByKey(root, "musicPlaylistShelfContinuation", scopes)
    findObjectsByKey(root, "appendContinuationItemsAction", scopes)
    findObjectsByKey(root, "reloadContinuationItemsCommand", scopes)
    scopes.asSequence().mapNotNull { scope ->
        val tokens = mutableListOf<String>()
        findContinuationTokens(scope, tokens)
        tokens.firstOrNull()
    }.firstOrNull()
} catch (e: Exception) {
    null
}
