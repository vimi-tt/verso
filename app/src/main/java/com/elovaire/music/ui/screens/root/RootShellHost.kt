package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import elovaire.music.droidbeauty.app.data.changelog.ChangelogRelease
import elovaire.music.droidbeauty.app.data.changelog.ChangelogRepository
import elovaire.music.droidbeauty.app.data.playback.AudiobookChapterReader
import elovaire.music.droidbeauty.app.data.playback.NowPlayingPlayback
import elovaire.music.droidbeauty.app.data.playback.PlaybackUiState
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadManager
import elovaire.music.droidbeauty.app.data.youtube.YouTubeTrackLinks
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import kotlinx.coroutines.launch
import elovaire.music.droidbeauty.app.domain.model.NowPlayingBarStyle
import elovaire.music.droidbeauty.app.domain.model.AudiobookSettings
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.motion.MotionTransitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BoxScope.RootChromeSlot(
    layout: RootChromeLayout,
    sharedTopBarSpec: SharedTopBarSpec?,
    chromeVisibility: RootChromeVisibility,
    playbackState: PlaybackUiState,
    nowPlayingBarStyle: NowPlayingBarStyle,
    nowPlayingViewModel: NowPlayingViewModel,
    activeBottomRoute: String,
    currentRoute: String?,
    navigationState: RootNavigationState,
    topLevelDestinations: List<TopLevelDestination>,
    motionTransitions: MotionTransitions,
    onOpenPlayer: (NowPlayingTransitionSnapshot?) -> Unit,
    isYouTubeLoggedIn: Boolean = false,
    youTubeAvatarUrl: String? = null,
    onYouTubeAccountClick: () -> Unit = {},
) {
    RootChromeHost(
        sharedTopBarSpec = sharedTopBarSpec,
        showSharedTopBarBackdrop = chromeVisibility.showSharedTopBarBackdrop,
        sharedTopBarHeight = layout.sharedTopBarHeight,
        canHostCompactNowPlaying = chromeVisibility.canHostCompactNowPlaying,
        playbackState = playbackState,
        nowPlayingBarStyle = nowPlayingBarStyle,
        nowPlayingViewModel = nowPlayingViewModel,
        showGlobalNowPlaying = chromeVisibility.showGlobalNowPlaying,
        reenteringFromPlayer = chromeVisibility.reenteringFromPlayer,
        showBottomNavigation = layout.showBottomNavigation,
        bottomNavHeight = layout.bottomNavHeight,
        navigationRailWidth = layout.navigationRailWidth,
        contentPaneStart = layout.contentPaneStart,
        contentPaneWidth = layout.contentPaneWidth,
        showNavigationRail = layout.showNavigationRail,
        activeBottomRoute = activeBottomRoute,
        currentRoute = currentRoute,
        navigationState = navigationState,
        topLevelDestinations = topLevelDestinations,
        motionTransitions = motionTransitions,
        onOpenPlayer = onOpenPlayer,
        isYouTubeLoggedIn = isYouTubeLoggedIn,
        youTubeAvatarUrl = youTubeAvatarUrl,
        onYouTubeAccountClick = onYouTubeAccountClick,
    )
}

@Composable
internal fun BoxScope.RootOverlaySlot(
    overlayState: RootOverlayStateHolder,
    topBarMenuActions: RootTopBarMenuActions,
    playlistActions: RootPlaylistActions,
    permissionController: RootPermissionController,
    updateController: elovaire.music.droidbeauty.app.data.update.UpdateController,
    artistInfoReader: elovaire.music.droidbeauty.app.data.artist.ArtistDiscographyReader,
    motionTransitions: MotionTransitions,
) {
    val updateState by updateController.uiState.collectAsStateWithLifecycle()
    val changelogReleases = if (overlayState.showChangelogSheet) {
        rememberChangelogReleases()
    } else {
        emptyList()
    }
    RootOverlayHost(
        showTopBarMenu = overlayState.showTopBarMenu,
        onDismissTopBarMenu = overlayState::dismissTopBarMenu,
        onOpenSettings = topBarMenuActions.openSettings,
        onOpenEqualizer = topBarMenuActions.openEqualizer,
        onOpenChangelog = topBarMenuActions.openChangelogSheet,
        onOpenAbout = topBarMenuActions.openAbout,
        showChangelogSheet = overlayState.showChangelogSheet,
        changelogReleases = changelogReleases,
        onDismissChangelogSheet = overlayState::dismissChangelogSheet,
        showPlaylistCreateDialog = overlayState.showPlaylistCreateDialog,
        onDismissPlaylistCreateDialog = overlayState::dismissPlaylistCreateDialog,
        onCreatePlaylist = playlistActions::createPlaylist,
        permissionState = permissionController.state,
        onRequestAudioPermission = permissionController::requestAudioPermission,
        updateController = updateController,
        updateState = updateState,
        artistInfoArtist = overlayState.artistInfoArtist,
        artistInfoReader = artistInfoReader,
        onDismissArtistInfo = overlayState::dismissArtistInfo,
        motionTransitions = motionTransitions,
    )
}

@Composable
internal fun rememberChangelogReleases(): List<ChangelogRelease> {
    val context = LocalContext.current.applicationContext
    val releases by produceState<List<ChangelogRelease>>(
        initialValue = emptyList(),
        key1 = context,
    ) {
        value = withContext(Dispatchers.IO) {
            ChangelogRepository(context).loadReleases()
        }
    }
    return releases
}

@Composable
internal fun BoxScope.RootPlayerLayerSlot(
    playback: NowPlayingPlayback,
    audiobookChapterReader: AudiobookChapterReader,
    chromeVisibility: RootChromeVisibility,
    playerLayerState: PlayerLayerState,
    playerLayerController: RootPlayerLayerController,
    nowPlayingViewModel: NowPlayingViewModel,
    songsById: Map<Long, Song>,
    playbackState: PlaybackUiState,
    audiobookSettings: AudiobookSettings,
    appState: RootAppState,
    playlistActions: RootPlaylistActions,
    openCurrentPlayingAlbum: (Long) -> Unit,
    openCurrentPlayingArtist: (String) -> Unit = {},
    youTubeCatalog: YouTubeCatalogRepository? = null,
    youTubeDownloadManager: YouTubeDownloadManager? = null,
    navigationState: RootNavigationState,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val downloadsState = youTubeDownloadManager?.state?.collectAsStateWithLifecycle()?.value
    val currentSong = playbackState.currentSong
    val currentVideoId = currentSong?.uri?.let(YouTubeUri::videoId)
    val resolvedFormatUpdate = youTubeCatalog?.resolvedFormatUpdates?.collectAsStateWithLifecycle()?.value ?: 0L
    val resolvedStreamFormatInfo = androidx.compose.runtime.remember(currentVideoId, resolvedFormatUpdate, youTubeCatalog) {
        currentVideoId?.let { youTubeCatalog?.resolvedStreamFormat(it) }
    }
    val trackLinks by produceState<YouTubeTrackLinks?>(
        initialValue = null,
        key1 = currentVideoId,
        key2 = youTubeCatalog,
    ) {
        value = if (currentVideoId != null && youTubeCatalog != null) {
            youTubeCatalog.resolveTrackLinks(currentVideoId)
        } else {
            null
        }
    }
    val isCurrentSongYouTubeDownloaded = currentVideoId != null &&
        downloadsState?.downloadedVideoIds?.contains(currentVideoId) == true
    val isCurrentSongYouTubeDownloading = currentVideoId != null &&
        downloadsState?.activeVideoIds?.contains(currentVideoId) == true
    val openResolvedArtist: (String) -> Unit = { artistName ->
        val hasLocalArtist = songsById.values.any { it.artist.equals(artistName, ignoreCase = true) }
        if (currentVideoId != null || !hasLocalArtist) {
            val cachedArtistId = trackLinks?.artistId
            if (!cachedArtistId.isNullOrBlank()) {
                playerLayerController.hide(false)
                navigationState.navigateTo(Routes.youtubeArtist(cachedArtistId))
            } else if (youTubeCatalog != null) {
                scope.launch {
                    val resolvedId = youTubeCatalog.resolveArtistIdByName(artistName)
                    playerLayerController.hide(false)
                    if (!resolvedId.isNullOrBlank()) {
                        navigationState.navigateTo(Routes.youtubeArtist(resolvedId))
                    } else {
                        openCurrentPlayingArtist(artistName)
                    }
                }
            } else {
                playerLayerController.hide(false)
                openCurrentPlayingArtist(artistName)
            }
        } else {
            playerLayerController.hide(false)
            openCurrentPlayingArtist(artistName)
        }
    }
    val openCurrentYouTubeAlbum: (() -> Unit)? = if (currentSong != null && currentVideoId != null && !trackLinks?.albumId.isNullOrBlank()) {
        {
            val albumId = trackLinks?.albumId.orEmpty()
            val albumTitle = trackLinks?.albumTitle ?: currentSong.album
            playerLayerController.hide(false)
            navigationState.navigateTo(
                Routes.youtubeCollection(
                    collectionId = albumId,
                    isAlbum = true,
                    title = albumTitle,
                    subtitle = currentSong.artist,
                    artUri = currentSong.artUri?.toString(),
                ),
            )
        }
    } else {
        null
    }
    RootPlayerLayerHost(
        visible = chromeVisibility.showPlayerOverlay,
        playerLayerState = playerLayerState,
        transitionGeneration = playerLayerController.transitionGeneration,
        onExitFinished = playerLayerController::clearTransitionSnapshot,
        onReturnToCompactFinished = playerLayerController::finishReturnToCompact,
        nowPlayingViewModel = nowPlayingViewModel,
        playbackManager = playback,
        audiobookChapterReader = audiobookChapterReader,
        songsById = songsById,
        audiobookSettings = audiobookSettings,
        isCurrentSongFavorite = playbackState.currentSong?.id in appState.favoriteSongIds,
        playlists = appState.playlists.filterNot { it.isSystem },
        onBack = { playerLayerController.hide(true) },
        onOpenCurrentAlbum = openCurrentPlayingAlbum,
        onOpenCurrentArtist = openResolvedArtist,
        resolvedCurrentAlbumTitle = trackLinks?.albumTitle,
        onOpenCurrentYouTubeAlbum = openCurrentYouTubeAlbum,
        resolvedCurrentStreamFormat = resolvedStreamFormatInfo?.format ?: playbackState.resolvedAudioFormat,
        resolvedCurrentStreamQuality = resolvedStreamFormatInfo?.quality ?: playbackState.resolvedAudioQuality,
        isCurrentSongYouTubeDownloaded = isCurrentSongYouTubeDownloaded,
        isCurrentSongYouTubeDownloading = isCurrentSongYouTubeDownloading,
        onDownloadCurrentSong = youTubeDownloadManager?.let { manager ->
            { song -> manager.enqueueDownload(song) }
        },
        onRemoveCurrentSongDownload = youTubeDownloadManager?.let { manager ->
            { song -> YouTubeUri.videoId(song.uri)?.let(manager::removeDownload) }
        },
        onToggleFavorite = playlistActions::toggleFavorite,
        onAddCurrentSongToPlaylist = LocalSongMenuActions.current.onAddToPlaylist,
        onCreatePlaylist = playlistActions::createPlaylist,
        onOpenEqualizer = {
            playerLayerController.hide(false)
            navigationState.navigateTo(EQUALIZER_ROUTE)
        },
        transitionSnapshot = playerLayerController.transitionSnapshot,
        modifier = Modifier.fillMaxSize(),
    )
}
