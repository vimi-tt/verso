package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Owns the ready-state shell after the root permission gate has granted media access. */
@Composable
internal fun ElovaireRootReadyHost(
    composition: RootComposition,
    resetHomeScrollOnColdStart: Boolean,
) {
    val appState by composition.rootViewModel.appState.collectAsStateWithLifecycle()
    val derivedState = rememberRootLibraryDerivedState(
        library = appState.library,
        playback = appState.playback,
        playlists = appState.playlists,
        songPlayCounts = appState.songPlayCounts,
    )
    val albumCollectionLayoutMode = appState.albumCollectionLayoutModeName.toAlbumLayoutMode()
    val libraryState = appState.library
    val playbackState = appState.playback
    val isPlaybackActuallyPlaying = playbackState.isPlaying && playbackState.currentSong != null
    val uiRuntime = rememberRootUiRuntime(
        navController = composition.navController,
        routeState = appState,
        libraryState = libraryState,
        playbackState = playbackState,
        albumsById = derivedState.albumsById,
        isPlaybackActuallyPlaying = isPlaybackActuallyPlaying,
    )
    val navigationState = uiRuntime.navigationState
    val currentRoute = uiRuntime.routeObservation.route
    val playerLayerController = uiRuntime.playerLayerController
    RootEffectsHost(
        composition = composition,
        uiRuntime = uiRuntime,
        derivedState = derivedState,
    )

    androidx.compose.runtime.LaunchedEffect(composition.youTubeCatalog) {
        if (composition.youTubeCatalog.accountState.value.isLoggedIn) {
            composition.youTubeCatalog.syncAccountLibrary()
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (playbackState.queue.isEmpty() && playbackState.currentSong == null) {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                composition.listeningStatsStore.loadLastPlaybackSession()
            }
            if (saved != null && saved.queue.isNotEmpty() && playbackState.queue.isEmpty()) {
                val activeSong = saved.queue[saved.currentIndex]
                composition.playback.restorePausedQueue(
                    songs = saved.queue,
                    currentIndex = saved.currentIndex,
                    positionMs = saved.positionMs,
                    sourceLabel = activeSong.album.ifBlank { activeSong.artist },
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id, playbackState.queue.size) {
        val current = playbackState.currentSong ?: return@LaunchedEffect
        val queue = playbackState.queue.ifEmpty { listOf(current) }
        val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        composition.listeningStatsStore.saveLastPlaybackSession(
            queue = queue,
            currentIndex = index,
            positionMs = composition.playback.progressState.value.positionMs,
        )
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id) {
        val song = playbackState.currentSong ?: return@LaunchedEffect
        composition.lastFmClient.updateNowPlaying(song)
    }
    var trackedSongId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(-1L) }
    var accumulatedPlayMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var reportedExtraMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var recordedCurrentTrack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id, isPlaybackActuallyPlaying) {
        val song = playbackState.currentSong ?: return@LaunchedEffect
        if (trackedSongId != song.id) {
            trackedSongId = song.id
            accumulatedPlayMs = 0L
            reportedExtraMs = 0L
            recordedCurrentTrack = false
        }
        if (!isPlaybackActuallyPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            accumulatedPlayMs += 1_000L
            if (!recordedCurrentTrack && accumulatedPlayMs >= 30_000L) {
                recordedCurrentTrack = true
                reportedExtraMs = 30_000L
                composition.listeningStatsStore.recordSongPlayed(song, initialListenedMs = 30_000L)
                composition.lastFmClient.scrobble(song)
                composition.youTubeCatalog.reportPlaybackHistory(song, playedSeconds = 30)
                if (song.genre.isBlank() || song.genre.equals("Unknown Genre", ignoreCase = true)) {
                    val resolvedGenre = composition.lastFmClient.resolveGenre(song)
                    if (!resolvedGenre.isNullOrBlank()) {
                        composition.listeningStatsStore.updateSongGenre(song.id, resolvedGenre)
                    }
                }
            } else if (recordedCurrentTrack && accumulatedPlayMs - reportedExtraMs >= 5_000L) {
                val delta = accumulatedPlayMs - reportedExtraMs
                reportedExtraMs = accumulatedPlayMs
                composition.listeningStatsStore.addListenedDuration(song, delta)
            }
        }
    }

    val actionRuntime = rememberRootActionRuntime(
        context = composition.context,
        playback = composition.playback,
        playlistStore = composition.playlistStore,
        favoritesStore = composition.favoritesStore,
        libraryDependencies = composition.libraryActionDependencies,
        settingsDependencies = composition.settingsActionDependencies,
        updateController = composition.updateController,
        navController = composition.navController,
        appState = appState,
        derivedState = derivedState,
        albumCollectionLayoutMode = albumCollectionLayoutMode,
        resetHomeScrollOnColdStart = resetHomeScrollOnColdStart,
        permissionController = composition.permissionController,
        deleteController = composition.deleteController,
        uiRuntime = uiRuntime,
        youTubeCatalog = composition.youTubeCatalog,
        youTubeDownloadManager = composition.youTubeDownloadManager,
    )

    ElovaireRootShell(
        overscrollFactory = uiRuntime.shellInputs.overscrollFactory,
        songMenuActions = actionRuntime.songMenuActions,
        chromeHazeState = uiRuntime.shellInputs.chromeHazeState,
        sharedBackIconPainter = uiRuntime.shellInputs.sharedBackIconPainter,
        sharedTopMenuIconPainter = uiRuntime.shellInputs.sharedTopMenuIconPainter,
        appLanguage = appState.appLanguage,
        chromeVisibility = uiRuntime.chromeVisibility,
        sharedTopBarController = uiRuntime.shellInputs.sharedTopBarController,
        navHostBlur = uiRuntime.shellInputs.navHostBlur,
        navHostScrimAlpha = uiRuntime.shellInputs.navHostScrimAlpha,
        routeHost = { routePadding, modifier ->
            RootRouteGraph(
                navState = navigationState,
                motionTransitions = composition.motionTransitions,
                routeState = actionRuntime.routeState,
                routeActions = actionRuntime.routeActions,
                overlayState = uiRuntime.overlayState,
                padding = routePadding,
                searchViewModel = composition.searchViewModel,
                youtubeHomeViewModel = composition.youtubeHomeViewModel,
                youTubeCatalog = composition.youTubeCatalog,
                youTubeDownloadManager = composition.youTubeDownloadManager,
                listeningStatsStore = composition.listeningStatsStore,
                lastFmClient = composition.lastFmClient,
                viewModelFactory = composition.viewModelFactory,
                artistImageRepository = composition.artistImageRepository,
                audiobookChapterReader = composition.audiobookChapterReader,
                audiobookDescriptionReader = composition.audiobookDescriptionReader,
                modifier = modifier,
            )
        },
        chromeHost = { layout ->
            val ytAccountState by composition.youTubeCatalog.accountState.collectAsStateWithLifecycle()
            RootChromeSlot(
                layout = layout,
                sharedTopBarSpec = uiRuntime.sharedTopBarSpec,
                chromeVisibility = uiRuntime.chromeVisibility,
                playbackState = playbackState,
                nowPlayingBarStyle = appState.nowPlayingBarStyle,
                nowPlayingViewModel = composition.nowPlayingViewModel,
                activeBottomRoute = uiRuntime.routeObservation.activeBottomRoute,
                currentRoute = currentRoute,
                navigationState = navigationState,
                topLevelDestinations = DefaultTopLevelDestinations,
                motionTransitions = composition.motionTransitions,
                onOpenPlayer = playerLayerController::requestOpen,
                isYouTubeLoggedIn = ytAccountState.isLoggedIn,
                youTubeAvatarUrl = ytAccountState.accountAvatarUrl,
                onYouTubeAccountClick = {
                    navigationState.navigateTo(Routes.settingsAccounts())
                },
            )
        },
        overlayHost = {
            RootOverlaySlot(
                overlayState = uiRuntime.overlayState,
                topBarMenuActions = uiRuntime.topBarMenuActions,
                playlistActions = actionRuntime.playlistActions,
                permissionController = composition.permissionController,
                updateController = composition.updateController,
                artistInfoReader = composition.artistImageRepository,
                motionTransitions = composition.motionTransitions,
            )
        },
        playerLayerHost = {
            RootPlayerLayerSlot(
                playback = composition.playback,
                audiobookChapterReader = composition.audiobookChapterReader,
                chromeVisibility = uiRuntime.chromeVisibility,
                playerLayerState = playerLayerController.state,
                playerLayerController = playerLayerController,
                nowPlayingViewModel = composition.nowPlayingViewModel,
                songsById = derivedState.songsById,
                playbackState = playbackState,
                audiobookSettings = appState.audiobookSettings,
                appState = appState,
                playlistActions = actionRuntime.playlistActions,
                openCurrentPlayingAlbum = uiRuntime.openCurrentPlayingAlbum,
                openCurrentPlayingArtist = actionRuntime.routeActions::openArtist,
                youTubeCatalog = composition.youTubeCatalog,
                youTubeDownloadManager = composition.youTubeDownloadManager,
                navigationState = navigationState,
            )
        },
    )
}
