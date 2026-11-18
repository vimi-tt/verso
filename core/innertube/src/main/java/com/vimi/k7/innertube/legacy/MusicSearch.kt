package com.vimi.k7.innertube.legacy

import com.vimi.k7.innertube.model.ArtistItem
import com.vimi.k7.innertube.model.MusicMetadata
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.UNKNOWN_ARTIST
import com.vimi.k7.innertube.model.UNKNOWN_TITLE
import com.vimi.k7.innertube.transport.MusicApi
import com.vimi.k7.innertube.transport.YOUTUBE_TAG
import com.vimi.k7.innertube.transport.extractVideoId
import com.vimi.k7.innertube.util.KLog
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Search on YouTube Music: songs, albums, artists and playlists.
 *
 * Songs and albums ask WEB_REMIX first, because its rows carry the album and
 * artist links that NewPipe's `StreamInfoItem` throws away; NewPipe is the
 * fallback and the only source for artists and playlists. Each query keeps
 * its own cursor so "load more" walks forward instead of repeating page two,
 * and a cursor never crosses from one source to the other.
 */
class MusicSearch(
    private val musicApi: MusicApi,
    private val newPipeGateway: NewPipeGateway,
) {
    // Cache extractors for pagination (bounded LRU)
    private val searchExtractorCache: MutableMap<String, SearchExtractor> = Collections.synchronizedMap(
        object : LinkedHashMap<String, SearchExtractor>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchExtractor>): Boolean = size > 50
        }
    )

    // The next continuation Page per query. Advanced by every searchNext call
    // so repeated "Load More" presses walk pages 2, 3, 4... instead of
    // refetching page 2 forever. A null value means the query is exhausted.
    private val searchNextPageCache: MutableMap<String, Page?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Page?>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Page?>): Boolean = size > 50
        }
    )

    // A present null is an exhausted InnerTube search, not a NewPipe search.
    private val musicSearchContinuations = Collections.synchronizedMap(
        object : LinkedHashMap<String, String?>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 32
        }
    )

    /** Forget every cursor: they belong to the profile that searched. */
    fun clearCaches() {
        searchExtractorCache.clear()
        searchNextPageCache.clear()
        musicSearchContinuations.clear()
    }

    /**
     * Search for songs on YouTube Music.
     * @param query The search query
     * @param filter The content filter (FILTER_SONGS, FILTER_ALBUMS, etc.)
     * @return List of songs matching the query
     */
    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song> = withContext(Dispatchers.IO) {
        // Read album links directly: StreamInfoItem loses that relationship.
        if (filter == FILTER_SONGS) {
            musicSearchContinuations.remove(query)
            val response = musicApi.postMusicMetadata("search", JSONObject()
                .put("query", query).put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"))
            if (response != null) {
                val songs = parseSongsFromInternalJson(response.toString())
                if (songs.isNotEmpty()) {
                    musicSearchContinuations[query] = MusicMetadata.continuation(response)
                    searchExtractorCache.remove(query)
                    searchNextPageCache.remove(query)
                    return@withContext songs
                }
            }
        }
        try {
            // YouTube Music search often uses the search extractor with specific filters
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(filter), "")
            searchExtractor.fetchPage()
            
            // Cache for pagination
            searchExtractorCache[query] = searchExtractor
            searchNextPageCache[query] =
                if (searchExtractor.initialPage.hasNextPage()) searchExtractor.initialPage.nextPage else null

            searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item: StreamInfoItem ->
                try {
                    Song.fromYouTube(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: UNKNOWN_TITLE,
                        artist = item.uploaderName ?: UNKNOWN_ARTIST,
                        album = "",
                        duration = item.duration * 1000L,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch next page of results for a previous query.
     */
    suspend fun searchNext(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (musicSearchContinuations.containsKey(query)) {
            val token = musicSearchContinuations[query] ?: return@withContext emptyList()
            val root = musicApi.postMusicMetadata("search", JSONObject().put("continuation", token))
                ?: return@withContext emptyList()
            val songs = parseSongsFromInternalJson(root.toString())
            musicSearchContinuations[query] = MusicMetadata.continuation(root)?.takeUnless { it == token }
            return@withContext songs
        }
        try {
            val extractor = searchExtractorCache[query] ?: return@withContext emptyList()

            // Continuation cursor from the previous page; null = exhausted
            val pageInfo = searchNextPageCache[query] ?: return@withContext emptyList()

            val nextPage = extractor.getPage(pageInfo)
            searchNextPageCache[query] = if (nextPage.hasNextPage()) nextPage.nextPage else null

            nextPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item: StreamInfoItem ->
                try {
                    Song.fromYouTube(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: UNKNOWN_TITLE,
                        artist = item.uploaderName ?: UNKNOWN_ARTIST,
                        album = "",
                        duration = item.duration * 1000L,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for albums on YouTube Music.
     * Note: Albums are often returned as PlaylistInfoItem in NewPipe for YouTube Music.
     */
    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        musicApi.postMusicMetadata("search", JSONObject().put("query", query)
            .put("params", "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"))?.let { root ->
            val releases = MusicMetadata.releaseRows(root)
            if (releases.isNotEmpty()) return@withContext releases
        }
        try {
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_ALBUMS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                PlaylistDisplayItem(
                    name = item.name.orEmpty(),
                    url = item.url, // Album URL usually works like a playlist
                    uploaderName = item.uploaderName.orEmpty(),
                    itemCount = item.streamCount.toInt(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for artists on YouTube Music.
     */
    suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_ARTISTS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<ChannelInfoItem>().mapNotNull { item ->
                ArtistItem(
                    id = item.url.substringAfterLast("/"), // Extract Browse ID from URL
                    name = item.name.orEmpty(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url,
                    subscriberCount = item.subscriberCount?.let { formatCount(it) },
                    description = item.description,
                    isVerified = item.isVerified
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for playlists on YouTube Music.
     */
    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_PLAYLISTS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                PlaylistDisplayItem(
                    name = item.name.orEmpty(),
                    url = item.url,
                    uploaderName = item.uploaderName.orEmpty(),
                    itemCount = item.streamCount.toInt(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

// NewPipe's content filters for a YouTube Music search.
const val FILTER_SONGS = "music_songs"
internal const val FILTER_ALBUMS = "music_albums"
internal const val FILTER_PLAYLISTS = "music_playlists"
internal const val FILTER_ARTISTS = "music_artists"
