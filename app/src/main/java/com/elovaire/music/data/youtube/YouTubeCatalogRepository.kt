package elovaire.music.droidbeauty.app.data.youtube

import android.net.Uri
import com.vimi.k7.innertube.model.ArtistItem
import com.vimi.k7.innertube.model.ArtistPage
import com.vimi.k7.innertube.model.MusicShelfItem
import com.vimi.k7.innertube.model.MusicShelfPage
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.SimilarArtist
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal data class YouTubeArtistSummary(
    val id: String,
    val name: String,
    val thumbnailUri: Uri?,
    val subscriberCount: String?,
)

internal data class YouTubeCollectionSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val itemCount: Int,
    val thumbnailUri: Uri?,
    val releaseYear: Int?,
)

internal data class YouTubeSearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<YouTubeCollectionSummary> = emptyList(),
    val artists: List<YouTubeArtistSummary> = emptyList(),
    val playlists: List<YouTubeCollectionSummary> = emptyList(),
)

internal sealed interface YouTubeHomeItem {
    val key: String

    data class Track(val song: Song) : YouTubeHomeItem {
        override val key: String get() = "yt_t_${song.id}"
    }

    data class Collection(
        val summary: YouTubeCollectionSummary,
        val isAlbum: Boolean,
    ) : YouTubeHomeItem {
        override val key: String get() = "yt_c_${summary.id}"
    }

    data class Artist(val summary: YouTubeArtistSummary) : YouTubeHomeItem {
        override val key: String get() = "yt_a_${summary.id}"
    }

    data class Mood(
        val title: String,
        val browseId: String,
        val params: String?,
    ) : YouTubeHomeItem {
        override val key: String get() = "yt_m_${browseId}_${params.orEmpty()}"
    }
}

internal data class YouTubeHomeShelf(
    val title: String,
    val items: List<YouTubeHomeItem>,
)

internal data class YouTubeHomeFeed(
    val shelves: List<YouTubeHomeShelf> = emptyList(),
    val continuation: String? = null,
)

internal data class YouTubeArtistPage(
    val id: String,
    val name: String,
    val bio: String?,
    val monthlyAudience: String?,
    val bannerUri: Uri?,
    val topSongs: List<Song>,
    val allSongs: List<Song>,
    val albums: List<YouTubeCollectionSummary>,
    val singles: List<YouTubeCollectionSummary>,
    val similarArtists: List<YouTubeArtistSummary>,
)

internal data class YouTubeTrackLinks(
    val albumId: String? = null,
    val albumTitle: String? = null,
    val artistId: String? = null,
)

internal data class YouTubeAccountUiState(
    val isLoggedIn: Boolean = false,
    val accountName: String? = null,
    val accountHandle: String? = null,
    val accountAvatarUrl: String? = null,
    val likedSongs: List<Song> = emptyList(),
    val userPlaylists: List<YouTubeCollectionSummary> = emptyList(),
    val userAlbums: List<YouTubeCollectionSummary> = emptyList(),
    val userArtists: List<YouTubeArtistSummary> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
)

/**
 * Backend repository exposing YouTube Music search, browse, related tracks,
 * and stream prefetching using Elovaire's domain models.
 */
internal class YouTubeCatalogRepository(
    private val streamClient: YouTubeStreamClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _accountState = MutableStateFlow(
        YouTubeAccountUiState(
            isLoggedIn = streamClient.isLoggedIn(),
            accountName = streamClient.activeAccountName(),
            accountHandle = streamClient.activeAccountHandle(),
            accountAvatarUrl = streamClient.activeAccountAvatar(),
        ),
    )
    val accountState: StateFlow<YouTubeAccountUiState> = _accountState.asStateFlow()
    val qualityState: StateFlow<YouTubeQualitySettings> = streamClient.qualityPreferences.state
    val resolvedFormatUpdates: StateFlow<Long> = streamClient.resolvedFormatUpdates

    fun resolvedStreamFormat(videoId: String): YouTubeResolvedStreamFormat? =
        streamClient.resolvedStreamFormat(videoId)

    fun setWifiStreamingQuality(preference: com.vimi.k7.innertube.stream.AudioPreference) {
        streamClient.qualityPreferences.setWifiStreamingQuality(preference)
        streamClient.streamCache.invalidateAll()
    }

    fun setCellularStreamingQuality(preference: com.vimi.k7.innertube.stream.AudioPreference) {
        streamClient.qualityPreferences.setCellularStreamingQuality(preference)
        streamClient.streamCache.invalidateAll()
    }

    fun setDownloadQuality(preference: com.vimi.k7.innertube.stream.AudioPreference) {
        streamClient.qualityPreferences.setDownloadQuality(preference)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        streamClient.qualityPreferences.setWifiOnlyDownloads(enabled)
    }

    fun setPreferredLyricsProvider(provider: LyricsProviderPreference) {
        streamClient.qualityPreferences.setPreferredLyricsProvider(provider)
    }

    fun setSyncPlaybackHistoryToYouTube(enabled: Boolean) {
        streamClient.qualityPreferences.setSyncPlaybackHistoryToYouTube(enabled)
    }

    suspend fun reportPlaybackHistory(song: Song, playedSeconds: Int = 30) {
        val videoId = YouTubeUri.videoId(song.uri) ?: return
        val durationSeconds = (song.durationMs / 1000L).toInt().coerceAtLeast(playedSeconds)
        withContext(ioDispatcher) {
            streamClient.reportPlaybackHistory(
                videoId = videoId,
                playedSeconds = playedSeconds,
                durationSeconds = durationSeconds,
            )
        }
    }

    suspend fun completeLogin(cookies: String) {
        withContext(ioDispatcher) {
            streamClient.startSession(cookies)
            val (name, handle, avatar) = streamClient.fetchAndStoreAccountProfile()
            _accountState.update {
                it.copy(
                    isLoggedIn = true,
                    accountName = name,
                    accountHandle = handle,
                    accountAvatarUrl = avatar,
                    syncError = null,
                )
            }
        }
        syncAccountLibrary(force = true)
    }

    suspend fun syncAccountLibrary(force: Boolean = false) {
        if (!streamClient.isLoggedIn()) {
            _accountState.value = YouTubeAccountUiState()
            return
        }
        val current = _accountState.value
        if (current.isSyncing) return
        if (!force && (current.likedSongs.isNotEmpty() || current.userPlaylists.isNotEmpty() || current.userAlbums.isNotEmpty() || current.userArtists.isNotEmpty())) {
            return
        }
        _accountState.update { it.copy(isSyncing = true, syncError = null) }
        runCatching {
            withContext(ioDispatcher) {
                coroutineScope {
                    val profileDeferred = async { streamClient.fetchAndStoreAccountProfile() }
                    val likedDeferred = async {
                        streamClient.catalog.getLikedMusic().map { it.toElovaireSong() }
                    }
                    val playlistsDeferred = async {
                        streamClient.catalog.getUserPlaylists().map { it.toCollectionSummary() }
                    }
                    val albumsDeferred = async {
                        streamClient.catalog.getUserLikedAlbums().map { it.toCollectionSummary() }
                    }
                    val artistsDeferred = async {
                        streamClient.catalog.getUserSubscribedArtists().map { it.toSubscribedArtistSummary() }
                    }
                    val (name, handle, avatar) = profileDeferred.await()
                    val liked = likedDeferred.await()
                    val playlists = playlistsDeferred.await()
                    val albums = albumsDeferred.await()
                    val artists = artistsDeferred.await()
                    _accountState.update {
                        it.copy(
                            isLoggedIn = true,
                            accountName = name ?: it.accountName,
                            accountHandle = handle ?: it.accountHandle,
                            accountAvatarUrl = avatar ?: it.accountAvatarUrl,
                            likedSongs = liked,
                            userPlaylists = playlists,
                            userAlbums = albums,
                            userArtists = artists,
                            isSyncing = false,
                            syncError = null,
                        )
                    }
                }
            }
        }.onFailure { error ->
            _accountState.update {
                it.copy(
                    isSyncing = false,
                    syncError = error.message ?: "Sync failed",
                )
            }
        }
    }

    fun signOut() {
        streamClient.signOut()
        _accountState.value = YouTubeAccountUiState()
    }
    suspend fun searchSongs(query: String): List<Song> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.search(query).map { it.toElovaireSong() }
    }

    suspend fun searchNextSongs(query: String): List<Song> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.searchNext(query).map { it.toElovaireSong() }
    }

    suspend fun searchAlbums(query: String): List<YouTubeCollectionSummary> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.searchAlbums(query).map { it.toCollectionSummary() }
    }

    suspend fun searchArtists(query: String): List<YouTubeArtistSummary> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.searchArtists(query).map { it.toArtistSummary() }
    }

    suspend fun searchPlaylists(query: String): List<YouTubeCollectionSummary> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.searchPlaylists(query).map { it.toCollectionSummary() }
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        streamClient.catalog.getSearchSuggestions(query).queries
    }

    suspend fun getRecommendations(): List<Song> = withContext(ioDispatcher) {
        streamClient.catalog.getRecommendations().map { it.toElovaireSong() }
    }

    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song> = withContext(ioDispatcher) {
        streamClient.catalog.getRelatedSongs(videoId, limit).map { it.toElovaireSong() }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> = withContext(ioDispatcher) {
        streamClient.catalog.getPlaylist(playlistId).map { it.toElovaireSong() }
    }

    suspend fun getAlbumSongs(browseId: String): List<Song> = withContext(ioDispatcher) {
        streamClient.catalog.getAlbumSongs(browseId).map { it.toElovaireSong() }
    }

    suspend fun getArtistPage(artistId: String): YouTubeArtistPage? = withContext(ioDispatcher) {
        streamClient.catalog.getArtistPage(artistId)?.toYouTubeArtistPage()
    }

    suspend fun resolveTrackLinks(videoId: String): YouTubeTrackLinks? = withContext(ioDispatcher) {
        val ref = runCatching { streamClient.catalog.getSongAlbumRef(videoId) }.getOrNull() ?: return@withContext null
        YouTubeTrackLinks(
            albumId = ref.albumId.takeIf { it.isNotBlank() },
            albumTitle = ref.albumTitle?.takeIf { it.isNotBlank() && !it.equals("Unknown Album", ignoreCase = true) },
            artistId = ref.artistId?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun resolveArtistIdByName(artistName: String): String? = withContext(ioDispatcher) {
        if (artistName.isBlank()) return@withContext null
        runCatching { streamClient.catalog.searchArtists(artistName).firstOrNull()?.id }.getOrNull()
    }

    suspend fun getHomeFeed(
        browseId: String = HOME_BROWSE_ID,
        params: String? = null,
    ): YouTubeHomeFeed = withContext(ioDispatcher) {
        streamClient.catalog.getMusicShelves(browseId, params)?.toHomeFeed() ?: YouTubeHomeFeed()
    }

    suspend fun getHomeFeedContinuation(token: String): YouTubeHomeFeed = withContext(ioDispatcher) {
        streamClient.catalog.getMusicShelvesContinuation(token)?.toHomeFeed() ?: YouTubeHomeFeed()
    }

    suspend fun getNewReleasesFeed(): YouTubeHomeFeed = withContext(ioDispatcher) {
        streamClient.catalog.getMusicShelves(NEW_RELEASES_BROWSE_ID, null)?.toHomeFeed() ?: YouTubeHomeFeed()
    }

    suspend fun getExploreFeed(): YouTubeHomeFeed = withContext(ioDispatcher) {
        streamClient.catalog.getMusicShelves(EXPLORE_BROWSE_ID, null)?.toHomeFeed() ?: YouTubeHomeFeed()
    }

    suspend fun getChartsFeed(): YouTubeHomeFeed = withContext(ioDispatcher) {
        streamClient.catalog.getMusicShelves(CHARTS_BROWSE_ID, null)?.toHomeFeed() ?: YouTubeHomeFeed()
    }

    suspend fun getMoodsAndGenres(): List<YouTubeHomeItem.Mood> = withContext(ioDispatcher) {
        val page = streamClient.catalog.getMusicShelves(MOODS_BROWSE_ID, null) ?: return@withContext emptyList()
        page.shelves.flatMap { shelf ->
            shelf.items.filterIsInstance<MusicShelfItem.Mood>().map {
                YouTubeHomeItem.Mood(title = it.title, browseId = it.browseId, params = it.params)
            }
        }.distinctBy { it.title }
    }

    suspend fun getCollectionSongs(id: String, isAlbum: Boolean): List<Song> = withContext(ioDispatcher) {
        if (isAlbum) {
            getAlbumSongs(id)
        } else {
            getPlaylistSongs(id)
        }
    }

    suspend fun prefetchStream(song: Song) = withContext(ioDispatcher) {
        val videoId = YouTubeUri.videoId(song.uri) ?: return@withContext
        runCatching { streamClient.resolvePlaybackUrl(videoId) }
    }

    fun clearCaches() {
        streamClient.catalog.clearCaches()
        streamClient.clearStreamCache()
    }

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val EXPLORE_BROWSE_ID = "FEmusic_explore"
        const val NEW_RELEASES_BROWSE_ID = "FEmusic_new_releases"
        const val CHARTS_BROWSE_ID = "FEmusic_charts"
        const val MOODS_BROWSE_ID = "FEmusic_moods_and_genres"
    }
}

private fun MusicShelfPage.toHomeFeed(): YouTubeHomeFeed = YouTubeHomeFeed(
    shelves = shelves.mapNotNull { shelf ->
        val mapped = shelf.items.mapNotNull { item ->
            when (item) {
                is MusicShelfItem.Track -> YouTubeHomeItem.Track(item.song.toElovaireSong())
                is MusicShelfItem.Collection -> YouTubeHomeItem.Collection(
                    summary = item.playlist.toCollectionSummary(),
                    isAlbum = item.isAlbum,
                )
                is MusicShelfItem.Artist -> YouTubeHomeItem.Artist(item.artist.toArtistSummary())
                is MusicShelfItem.Mood -> YouTubeHomeItem.Mood(
                    title = item.title,
                    browseId = item.browseId,
                    params = item.params,
                )
            }
        }
        if (mapped.isEmpty()) null else YouTubeHomeShelf(title = shelf.title, items = mapped)
    },
    continuation = continuation,
)

private fun ArtistItem.toArtistSummary(): YouTubeArtistSummary = YouTubeArtistSummary(
    id = id,
    name = name,
    thumbnailUri = (highResThumbnailUrl ?: thumbnailUrl)?.takeIf(String::isNotBlank)?.let(Uri::parse),
    subscriberCount = subscriberCount,
)

private fun PlaylistDisplayItem.toCollectionSummary(): YouTubeCollectionSummary = YouTubeCollectionSummary(
    id = id,
    title = name,
    subtitle = uploaderName,
    itemCount = itemCount,
    thumbnailUri = (highResThumbnailUrl ?: thumbnailUrl)?.takeIf(String::isNotBlank)?.let(Uri::parse),
    releaseYear = releaseYear,
)

private fun PlaylistDisplayItem.toSubscribedArtistSummary(): YouTubeArtistSummary = YouTubeArtistSummary(
    id = id,
    name = name,
    thumbnailUri = (highResThumbnailUrl ?: thumbnailUrl)?.takeIf(String::isNotBlank)?.let(Uri::parse),
    subscriberCount = uploaderName.takeIf { it.isNotBlank() },
)

private fun SimilarArtist.toArtistSummary(): YouTubeArtistSummary = YouTubeArtistSummary(
    id = id,
    name = name,
    thumbnailUri = thumbnailUrl?.takeIf(String::isNotBlank)?.let(Uri::parse),
    subscriberCount = null,
)

private fun ArtistPage.toYouTubeArtistPage(): YouTubeArtistPage = YouTubeArtistPage(
    id = id,
    name = name,
    bio = bio,
    monthlyAudience = monthlyAudience,
    bannerUri = bannerUrl?.takeIf(String::isNotBlank)?.let(Uri::parse),
    topSongs = topSongs.map { it.toElovaireSong() },
    allSongs = songs.map { it.toElovaireSong() },
    albums = albums.map { it.toCollectionSummary() },
    singles = singles.map { it.toCollectionSummary() },
    similarArtists = similarArtists.map { it.toArtistSummary() },
)
