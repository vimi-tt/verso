package com.vimi.k7.innertube.catalog

import com.vimi.k7.innertube.model.ArtistItem
import com.vimi.k7.innertube.model.ArtistPage
import com.vimi.k7.innertube.model.ArtistTasteSample
import com.vimi.k7.innertube.model.MusicShelfPage
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.SongAlbumRef

import com.vimi.k7.innertube.model.MusicSearchSuggestions
import com.vimi.k7.innertube.legacy.MusicSearch
import com.vimi.k7.innertube.legacy.MusicBrowse
import com.vimi.k7.innertube.legacy.MusicPlaylists
import com.vimi.k7.innertube.legacy.FILTER_SONGS

/**
 * Adapter contract for YouTube Music search and metadata operations exposed by
 * [com.vimi.k7.data.repository.MusicRepository].
 *
 * Keeping the existing [MusicSearch], [MusicBrowse] and [MusicPlaylists]
 * implementation behind [LegacyMusicCatalogDataSource] lets a new InnerTube
 * implementation be swapped in (and automatically fall back to legacy on any
 * error or empty result) without touching any ViewModel, Service or UI screen.
 */
interface MusicCatalogDataSource {

    fun clearCaches()

    // --- Search & suggestions ---

    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song>

    suspend fun searchNext(query: String): List<Song>

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem>

    suspend fun searchArtists(query: String): List<ArtistItem>

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem>

    suspend fun getSearchSuggestions(query: String): MusicSearchSuggestions =
        MusicSearchSuggestions()

    // --- Home, shelves, artists, albums & radio ---

    suspend fun getRecommendations(): List<Song>

    suspend fun getMusicShelves(browseId: String, params: String? = null): MusicShelfPage?

    suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage?

    suspend fun getChartArtists(country: String? = null): List<ArtistItem>

    suspend fun getArtistPage(artistId: String): ArtistPage?

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>>

    suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample?

    suspend fun getSongAlbumRef(videoId: String): SongAlbumRef?

    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song>

    suspend fun getRadioPage(
        videoId: String,
        continuationToken: String? = null,
    ): com.vimi.k7.innertube.model.RadioPage = com.vimi.k7.innertube.model.RadioPage(emptyList(), null)

    suspend fun getSongFromPanel(videoId: String): Song?

    // --- Playlists, albums & library ---

    suspend fun getPlaylist(playlistId: String): List<Song>

    suspend fun getAlbumSongs(browseId: String): List<Song>

    suspend fun getLikedMusic(): List<Song>

    suspend fun getLikedMusicPage(continuationToken: String? = null): Pair<List<Song>, String?> =
        getLikedMusic() to null

    suspend fun getUserPlaylists(): List<PlaylistDisplayItem>
    suspend fun getUserLikedAlbums(): List<PlaylistDisplayItem> = emptyList()
    suspend fun getUserSubscribedArtists(): List<PlaylistDisplayItem> = emptyList()
}

/**
 * Default adapter wrapping the existing [MusicSearch], [MusicBrowse] and
 * [MusicPlaylists] classes with zero behavior change.
 */
class LegacyMusicCatalogDataSource(
    private val musicSearch: MusicSearch,
    private val musicBrowse: MusicBrowse,
    private val musicPlaylists: MusicPlaylists,
) : MusicCatalogDataSource {

    override fun clearCaches() {
        musicSearch.clearCaches()
    }

    override suspend fun search(query: String, filter: String): List<Song> =
        musicSearch.search(query, filter)

    override suspend fun searchNext(query: String): List<Song> =
        musicSearch.searchNext(query)

    override suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> =
        musicSearch.searchAlbums(query)

    override suspend fun searchArtists(query: String): List<ArtistItem> =
        musicSearch.searchArtists(query)

    override suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> =
        musicSearch.searchPlaylists(query)

    override suspend fun getRecommendations(): List<Song> =
        musicBrowse.getRecommendations()

    override suspend fun getMusicShelves(browseId: String, params: String?): MusicShelfPage? =
        musicBrowse.getMusicShelves(browseId, params)

    override suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage? =
        musicBrowse.getMusicShelvesContinuation(token)

    override suspend fun getChartArtists(country: String?): List<ArtistItem> =
        musicBrowse.getChartArtists(country)

    override suspend fun getArtistPage(artistId: String): ArtistPage? =
        musicBrowse.getArtistPage(artistId)

    override suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> =
        musicBrowse.getArtistDetails(artistId)

    override suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample? =
        musicBrowse.getArtistTasteSample(artistId)

    override suspend fun getSongAlbumRef(videoId: String): SongAlbumRef? =
        musicBrowse.getSongAlbumRef(videoId)

    override suspend fun getRelatedSongs(videoId: String, limit: Int): List<Song> =
        musicBrowse.getRelatedSongs(videoId, limit)

    override suspend fun getRadioPage(
        videoId: String,
        continuationToken: String?,
    ): com.vimi.k7.innertube.model.RadioPage = musicBrowse.getRadioPage(videoId, continuationToken)

    override suspend fun getSongFromPanel(videoId: String): Song? =
        musicBrowse.getSongFromPanel(videoId)

    override suspend fun getPlaylist(playlistId: String): List<Song> =
        musicPlaylists.getPlaylist(playlistId)

    override suspend fun getAlbumSongs(browseId: String): List<Song> =
        musicPlaylists.getAlbumSongs(browseId)

    override suspend fun getLikedMusic(): List<Song> =
        musicPlaylists.getLikedMusic()

    override suspend fun getLikedMusicPage(continuationToken: String?): Pair<List<Song>, String?> =
        musicPlaylists.getLikedMusicPage(continuationToken)

    override suspend fun getUserPlaylists(): List<PlaylistDisplayItem> =
        musicPlaylists.getUserPlaylists()

    override suspend fun getUserLikedAlbums(): List<PlaylistDisplayItem> =
        musicPlaylists.getUserLikedAlbums()

    override suspend fun getUserSubscribedArtists(): List<PlaylistDisplayItem> =
        musicPlaylists.getUserSubscribedArtists()
}
