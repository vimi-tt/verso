package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem

@Composable
internal fun HomeRouteHost(
    navState: RootNavigationState,
    state: RootRouteState,
    routeActions: RootRouteActions,
    padding: RootRoutePadding,
    youtubeHomeViewModel: YouTubeHomeViewModel,
    onYouTubeArtistSelected: (String) -> Unit = {},
    onYouTubeCollectionSelected: (YouTubeHomeItem.Collection) -> Unit = {},
) {
    val youtubeHomeState by youtubeHomeViewModel.uiState.collectAsStateWithLifecycle()
    val onYouTubeTrackSelected = remember(routeActions) {
        { song: elovaire.music.droidbeauty.app.domain.model.Song,
          queue: List<elovaire.music.droidbeauty.app.domain.model.Song> ->
            routeActions.playback.playSongQueue(
                song = song,
                queue = queue,
                sourceLabel = song.artist.ifBlank { "YouTube" },
            )
        }
    }

    HomeScreen(
        lastPlayedAlbum = state.libraryDerivedState.lastPlayedAlbum,
        lastPlayedPlaylist = state.libraryDerivedState.lastPlayedPlaylist,
        albumOfDay = rememberAlbumOfDay(state.appState.library.albums, state.appState.library.isAuthoritative),
        homeLayout = rememberHomeLayout(),
        songsById = state.libraryDerivedState.songsById,
        recentlyAddedAlbums = state.libraryDerivedState.recentlyAddedAlbums,
        recentSongs = state.recentSongs,
        favoriteAlbums = state.libraryDerivedState.favoriteAlbums,
        playbackState = state.appState.playback,
        isLibraryLoading = state.appState.library.isLoading,
        isLibraryAuthoritative = state.appState.library.isAuthoritative,
        libraryScanProgress = state.appState.library.scanProgress,
        favoriteSongIds = state.appState.favoriteSongIds,
        topPadding = padding.topContent,
        bottomPadding = padding.bottomContent,
        scrollToTopRequestVersion = navState.homeScrollRequestVersion,
        resetScrollOnColdStart = state.resetHomeScrollOnColdStart,
        playInitialReveal = state.playFirstLaunchHomeReveal,
        onInitialRevealFinished = routeActions.onInitialRevealFinished,
        onAlbumSelected = { album, origin ->
            routeActions.openAlbum(album, origin, AlbumOpenSource.HomeSection)
        },
        onPlaylistSelected = { playlist -> routeActions.openPlaylist(playlist.id) },
        onPlayAlbum = { album -> routeActions.playback.playAlbum(album) },
        onShuffleAlbum = { album -> routeActions.playback.playAlbum(album, shuffle = true) },
        onPlayPlaylist = routeActions.playback::playPlaylist,
        onShufflePlaylist = { playlist, songs ->
            routeActions.playback.playPlaylist(playlist, songs, shuffle = true)
        },
        onOpenRecentlyAdded = routeActions::openRecentlyAdded,
        onSongSelected = routeActions.playback::playSongFromAlbumOrSingle,
        onToggleFavorite = routeActions.playlists::toggleFavorite,
        youtubeHomeState = youtubeHomeState,
        onSelectYouTubeMood = youtubeHomeViewModel::selectMood,
        onYouTubeTrackSelected = onYouTubeTrackSelected,
        onYouTubeCollectionSelected = onYouTubeCollectionSelected,
        onRetryYouTubeHome = youtubeHomeViewModel::retry,
        onLoadMoreYouTubeHome = youtubeHomeViewModel::loadMore,
        onYouTubeArtistSelected = onYouTubeArtistSelected,
    )
}
