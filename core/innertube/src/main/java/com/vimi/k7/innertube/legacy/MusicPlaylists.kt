package com.vimi.k7.innertube.legacy

import com.vimi.k7.innertube.model.MusicMetadata
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.auth.SessionManager
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.UNKNOWN_ALBUM
import com.vimi.k7.innertube.model.UNKNOWN_ARTIST
import com.vimi.k7.innertube.model.UNKNOWN_TITLE
import com.vimi.k7.innertube.transport.MusicApi
import com.vimi.k7.innertube.transport.YOUTUBE_TAG
import com.vimi.k7.innertube.transport.extractContinuationToken
import com.vimi.k7.innertube.transport.extractVideoId
import com.vimi.k7.innertube.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The songs of a YouTube Music playlist or album, and the account's own
 * library of playlists and likes.
 *
 * One walker, [getBrowsePlaylistSongs], reads a playlist through WEB_REMIX
 * browse and its continuation chain for both session states. A page that
 * fails marks the load incomplete rather than ending it, so the NewPipe
 * fallback can still run; an HTTP error is never a playlist's end.
 */
class MusicPlaylists(
    private val musicApi: MusicApi,
    private val sessionManager: SessionManager,
) {
    suspend fun getPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        // For "Your Likes" playlist, use getLikedMusic which handles pagination
        if (playlistId == "LM" || playlistId == "VLLM") {
            return@withContext getLikedMusic()
        }

        // Album browse ids aren't playlists; they only resolve via /browse
        if (playlistId.startsWith("MPRE")) {
            return@withContext getAlbumSongs(playlistId)
        }

        // For other playlists, use internal method
        getPlaylistInternal(playlistId)
    }

    /**
     * Fetch an album's tracks by its browse id (MPREb…).
     * Album pages aren't playlists, so they must go through /browse.
     */
    suspend fun getAlbumSongs(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val body = musicApi.browseMusic(browseId) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val songs = MusicMetadata.albumSongs(root, browseId)
            // Signed out, the page lists each track's video where it has one.
            // The album's audio playlist has the songs; one more request, and
            // only for an album that needs it.
            if (songs.isEmpty() || !MusicMetadata.albumHasVideoVersions(root)) return@withContext songs
            val audioPlaylist = MusicMetadata.albumAudioPlaylistId(root) ?: return@withContext songs
            val audio = getBrowsePlaylistSongs(audioPlaylist)
            if (audio.songs.isEmpty()) songs else MusicMetadata.withSongVersions(songs, audio.songs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching album $browseId", e)
            emptyList()
        }
    }

    /**
     * Get a single paginated chunk of liked music.
     * When continuationToken is null, fetches the first page from the official "Músicas que gostei"
     * playlist (browseId: "VLLM"), with fallback to "FEmusic_liked_videos".
     * Returns the parsed songs and the next continuation token if available.
     */
    suspend fun getLikedMusicPage(continuationToken: String? = null): Pair<List<Song>, String?> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            val fallback = if (continuationToken == null) getPlaylistInternal("LM") else emptyList()
            return@withContext fallback to null
        }

        try {
            val (json, fromVllm) = if (continuationToken == null) {
                val vllm = musicApi.fetchInternalApi("VLLM")
                if (vllm.isNotBlank() && vllm.contains("musicResponsiveListItemRenderer")) {
                    vllm to true
                } else {
                    musicApi.fetchInternalApi("FEmusic_liked_videos") to false
                }
            } else {
                musicApi.fetchContinuation(continuationToken) to false
            }

            if (json.isEmpty()) return@withContext emptyList<Song>() to null

            val songs = parseSongsFromInternalJson(json)
            val nextToken = extractContinuationToken(json)
            songs to nextToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching liked music page", e)
            emptyList<Song>() to null
        }
    }

    /**
     * Get liked music with bounded pagination support.
     * YouTube Music API returns paginated results, capped at 10 pages for legacy callers.
     */
    suspend fun getLikedMusic(): List<Song> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            return@withContext getPlaylistInternal("LM")
        }

        try {
            val allSongs = mutableListOf<Song>()
            var continuationToken: String? = null
            var pageCount = 0
            val maxPages = 10

            do {
                val (songs, nextToken) = getLikedMusicPage(continuationToken)
                if (songs.isEmpty()) break
                allSongs.addAll(songs)
                continuationToken = nextToken
                pageCount++
                KLog.d(YOUTUBE_TAG, "Liked songs page $pageCount: ${songs.size} songs, total: ${allSongs.size}")
            } while (continuationToken != null && pageCount < maxPages)

            if (allSongs.isNotEmpty()) {
                return@withContext allSongs.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching liked music", e)
        }

        // Fallback to NewPipe method
        getPlaylistInternal("LM")
    }

    /**
     * Get the user's playlists.
     * Uses Internal YTM API with Cookies.
     */
    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        
        try {
            val playlists = mutableListOf<PlaylistDisplayItem>()

            // Likes has a real account browse id; mixes must come from the API.
            playlists.add(PlaylistDisplayItem(
                name = "Your Likes",
                url = "https://music.youtube.com/playlist?list=LM",
                uploaderName = "You"
            ))

            // Fetch Library (Liked Playlists), following grid continuations so
            // large libraries come back in full rather than just the first page.
            // Note: FEmusic_liked_playlists gets playlists you've saved/liked
            var json = musicApi.fetchInternalApi("FEmusic_liked_playlists")
            var pageCount = 0
            val maxPages = 20
            while (json.isNotEmpty() && pageCount < maxPages) {
                val parsed = parsePlaylistsFromInternalJson(json)
                playlists.addAll(parsed)
                pageCount++
                KLog.d(YOUTUBE_TAG, "Library playlists page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = musicApi.fetchContinuation(token)
            }

            // The library grid can include "Your Likes" (VLLM) which we already synthesized
            playlists.distinctBy { it.id }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching user playlists", e)
            emptyList()
        }
    }

    /**
     * Fetches user's explicitly liked / saved albums from "FEmusic_liked_albums" with pagination.
     */
    suspend fun getUserLikedAlbums(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()

        try {
            val albums = mutableListOf<PlaylistDisplayItem>()
            var json = musicApi.fetchInternalApi("FEmusic_liked_albums")
            var pageCount = 0
            val maxPages = 20
            while (json.isNotEmpty() && pageCount < maxPages) {
                val parsed = parseAlbumsFromInternalJson(json)
                albums.addAll(parsed)
                pageCount++
                KLog.d(YOUTUBE_TAG, "Library albums page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = musicApi.fetchContinuation(token)
            }
            albums.distinctBy { it.id }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching user albums", e)
            emptyList()
        }
    }

    /**
     * Fetches user's subscribed artists from "FEmusic_library_corpus_artists" with pagination.
     */
    suspend fun getUserSubscribedArtists(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()

        try {
            val artists = mutableListOf<PlaylistDisplayItem>()
            var json = musicApi.fetchInternalApi("FEmusic_library_corpus_artists")
            var pageCount = 0
            val maxPages = 20
            while (json.isNotEmpty() && pageCount < maxPages) {
                val parsed = parseArtistsFromInternalJson(json)
                artists.addAll(parsed)
                pageCount++
                KLog.d(YOUTUBE_TAG, "Library artists page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = musicApi.fetchContinuation(token)
            }
            artists.distinctBy { it.id }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching user subscribed artists", e)
            emptyList()
        }
    }

    /**
     * Internal playlist fetching without the LM redirect to avoid infinite recursion.
     */
    suspend fun getPlaylistInternal(playlistId: String): List<Song> = withContext(Dispatchers.IO) {

        // WEB_REMIX first for both session states: NewPipe drops album links
        // and cannot distinguish a playlist's title from a track's album.
        val accountResult = getBrowsePlaylistSongs(playlistId)
        if (accountResult.complete && accountResult.songs.isNotEmpty()) {
            return@withContext accountResult.songs
        }

        var newPipeComplete = true
        val newPipeSongs = try {
            val urlId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
            val playlistUrl = "https://www.youtube.com/playlist?list=$urlId"

            val playlistExtractor = youtubeService.getPlaylistExtractor(playlistUrl)
            playlistExtractor.fetchPage()

            val allItems = mutableListOf<StreamInfoItem>()
            allItems.addAll(playlistExtractor.initialPage.items.filterIsInstance<StreamInfoItem>())

            var currentPage = playlistExtractor.initialPage
            var newPipePages = 1
            while (currentPage.hasNextPage() && newPipePages < MAX_PLAYLIST_PAGES) {
                try {
                    currentPage = playlistExtractor.getPage(currentPage.nextPage)
                    newPipePages++
                    allItems.addAll(currentPage.items.filterIsInstance<StreamInfoItem>())
                } catch (e: Exception) {
                    newPipeComplete = false
                    KLog.w(
                        YOUTUBE_TAG,
                        "NewPipe playlist continuation failed for $playlistId after ${allItems.size} items",
                        e
                    )
                    break
                }
            }

            allItems.mapNotNull { item ->
                Song.fromYouTube(
                    videoId = extractVideoId(item.url),
                    title = item.name ?: UNKNOWN_TITLE,
                    artist = item.uploaderName ?: UNKNOWN_ARTIST,
                    album = UNKNOWN_ALBUM,
                    duration = item.duration * 1000L,
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            newPipeComplete = false
            emptyList()
        }

        if (newPipeComplete && newPipeSongs.isNotEmpty()) return@withContext newPipeSongs

        // Do not throw away useful rows if every complete path failed, but log
        // loudly that this is degraded rather than pretending the exact page
        // boundary is the playlist's real end.
        val partial = listOf(accountResult.songs, newPipeSongs)
            .maxByOrNull { it.size }
            .orEmpty()
        if (partial.isNotEmpty()) {
            KLog.w(
                YOUTUBE_TAG,
                "Returning incomplete playlist $playlistId (${partial.size} songs) after all full-load paths failed"
            )
        }
        partial
    }

    private data class PlaylistLoadResult(
        val songs: List<Song>,
        val complete: Boolean
    )

    /**
     * WEB_REMIX playlist browse, including every continuation.
     *
     * Account cookies are attached by [MusicApi.browseMusic] and [MusicApi.fetchContinuation]
     * when there is a session and omitted when there is not, so this one walker
     * serves both: a public playlist pages to its end anonymously, and an owned
     * or private one needs the session that those two helpers already apply.
     */
    private fun getBrowsePlaylistSongs(playlistId: String): PlaylistLoadResult {
        val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("FE")) {
            playlistId
        } else {
            "VL$playlistId"
        }
        val allSongs = mutableListOf<Song>()
        val seenTokens = mutableSetOf<String>()
        var json = musicApi.browseMusic(browseId)
            ?: return PlaylistLoadResult(emptyList(), complete = false)

        var pages = 0
        while (pages < MAX_PLAYLIST_PAGES) {
            pages++
            if (json.isBlank()) return PlaylistLoadResult(allSongs, complete = false)
            allSongs += parseSongsFromInternalJson(json, preserveDuplicates = true)
            val token = extractPlaylistContinuationToken(json)
                ?: return PlaylistLoadResult(allSongs, complete = true)
            if (!seenTokens.add(token)) {
                KLog.w(YOUTUBE_TAG, "Repeated playlist continuation for $playlistId")
                return PlaylistLoadResult(allSongs, complete = false)
            }
            json = musicApi.fetchContinuation(token)
            if (json.isEmpty()) {
                KLog.w(
                    YOUTUBE_TAG,
                    "Authenticated playlist continuation failed for $playlistId after ${allSongs.size} songs"
                )
                return PlaylistLoadResult(allSongs, complete = false)
            }
        }
        return PlaylistLoadResult(allSongs, complete = false)
    }

    private companion object {
        const val MAX_PLAYLIST_PAGES = 20
    }
}
