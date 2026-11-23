package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Owns the ready-state shell after the root permission gate has granted media access. */
@Composable
internal fun ElovaireRootReadyHost(
    composition: RootComposition,
    resetHomeScrollOnColdStart: Boolean,
) {
    val appState by composition.rootViewModel.appState.collectAsStateWithLifecycle()
    val rawDerivedState = rememberRootLibraryDerivedState(
        library = appState.library,
        playback = appState.playback,
        playlists = appState.playlists,
        songPlayCounts = appState.songPlayCounts,
    )
    val importedPlaylistsSnapshot by composition.importedPlaylistStore.snapshot.collectAsStateWithLifecycle()
    val derivedState = androidx.compose.runtime.remember(rawDerivedState, importedPlaylistsSnapshot) {
        if (importedPlaylistsSnapshot.songsById.isEmpty()) {
            rawDerivedState
        } else {
            rawDerivedState.copy(
                songsById = importedPlaylistsSnapshot.songsById + rawDerivedState.songsById,
            )
        }
    }
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
        val savedCompasso = composition.compassoEngine.restorePersistedQueueState()
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
            } else if (savedCompasso != null && playbackState.queue.isEmpty()) {
                val restoredSongs = elovaire.music.droidbeauty.app.data.compasso.CompassoQueueSerializer
                    .decodeSongs(savedCompasso.queueJson)
                if (restoredSongs.isNotEmpty()) {
                    val idx = savedCompasso.currentIndex.coerceIn(restoredSongs.indices)
                    val activeSong = restoredSongs[idx]
                    composition.playback.restorePausedQueue(
                        songs = restoredSongs,
                        currentIndex = idx,
                        positionMs = 0L,
                        sourceLabel = activeSong.album.ifBlank { activeSong.artist },
                    )
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id, playbackState.queue.size, isPlaybackActuallyPlaying) {
        val current = playbackState.currentSong ?: return@LaunchedEffect
        val queue = playbackState.queue.ifEmpty { listOf(current) }
        val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        composition.listeningStatsStore.saveLastPlaybackSession(
            queue = queue,
            currentIndex = index,
            positionMs = composition.playback.progressState.value.positionMs,
        )
        if (composition.compassoEngine.uiState.value.isActive) {
            composition.compassoEngine.persistQueueState(queue, index)
        }
    }
    var previousFavoriteIds by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Set<Long>?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(appState.favoriteSongIds) {
        val prev = previousFavoriteIds
        previousFavoriteIds = appState.favoriteSongIds
        val quality = composition.youTubeCatalog.qualityState.value
        val canAutoDownload = quality.autoDownloadLikedOnWifi && composition.youTubeDownloadManager.isOnWifi()
        fun resolveFavoriteSong(songId: Long): elovaire.music.droidbeauty.app.domain.model.Song? {
            return derivedState.songsById[songId]
                ?: playbackState.currentSong?.takeIf { it.id == songId }
                ?: playbackState.queue.firstOrNull { it.id == songId }
                ?: composition.youTubeCatalog.accountState.value.likedSongs.firstOrNull { it.id == songId }
                ?: elovaire.music.droidbeauty.app.data.youtube.YouTubeSongRegistry.find(songId)
        }
        if (prev != null) {
            val newlyLiked = appState.favoriteSongIds - prev
            for (likedId in newlyLiked) {
                val song = resolveFavoriteSong(likedId)
                if (song != null) {
                    if (elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.isYouTubeUri(song.uri) ||
                        derivedState.songsById[likedId] == null
                    ) {
                        composition.importedPlaylistStore.registerFavoriteSong(song)
                    }
                    composition.compassoEngine.recordLikeSignal(song)
                    if (canAutoDownload && elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.isYouTubeUri(song.uri)) {
                        composition.youTubeDownloadManager.enqueueDownload(song)
                    }
                }
            }
        } else {
            for (favId in appState.favoriteSongIds) {
                if (derivedState.songsById[favId] == null) {
                    resolveFavoriteSong(favId)?.let { song ->
                        composition.importedPlaylistStore.registerFavoriteSong(song)
                    }
                }
            }
        }
    }
    val ytQualityState by composition.youTubeCatalog.qualityState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(ytQualityState.autoDownloadLikedOnWifi, appState.favoriteSongIds.size) {
        if (ytQualityState.autoDownloadLikedOnWifi && composition.youTubeDownloadManager.isOnWifi()) {
            val downloadedIds = composition.youTubeDownloadManager.state.value.downloadedVideoIds
            for (favId in appState.favoriteSongIds) {
                val song = derivedState.songsById[favId] ?: continue
                val videoId = elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.videoId(song.uri) ?: continue
                if (videoId !in downloadedIds) {
                    composition.youTubeDownloadManager.enqueueDownload(song)
                }
            }
        }
    }
    val compassoScope = androidx.compose.runtime.rememberCoroutineScope()
    var previousCompassoSong by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<elovaire.music.droidbeauty.app.domain.model.Song?>(null)
    }
    var previousCompassoPlayedMs by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(0L)
    }
    var trackedSongId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(-1L) }
    var accumulatedPlayMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var reportedExtraMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var reportedYouTubeHistory by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var recordedCurrentTrack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id, playbackState.currentIndex, playbackState.queue.size) {
        val current = playbackState.currentSong
        val prev = previousCompassoSong
        val prevPlayedMs = previousCompassoPlayedMs
        if (prev != null && current != null && prev.id != current.id) {
            if (prevPlayedMs >= 15_000L && !(reportedYouTubeHistory && trackedSongId == prev.id)) {
                val playedSeconds = (prevPlayedMs / 1000L).toInt()
                compassoScope.launch {
                    composition.youTubeCatalog.reportPlaybackHistory(prev, playedSeconds = playedSeconds)
                }
            }
            val wasSkipped = prev.durationMs <= 0L ||
                prevPlayedMs < (prev.durationMs * 0.80f).toLong()
            previousCompassoPlayedMs = 0L
            compassoScope.launch {
                composition.compassoEngine.recordPlaybackOutcome(
                    song = prev,
                    playedMs = prevPlayedMs,
                    wasSkipped = wasSkipped,
                )
            }
        }
        previousCompassoSong = current
        val queueSnapshot = playbackState.queue
        val indexSnapshot = playbackState.currentIndex
        if (current != null && queueSnapshot.isNotEmpty()) {
            compassoScope.launch {
                composition.compassoEngine.onPlaybackPositionChanged(
                    currentIndex = indexSnapshot,
                    currentQueue = queueSnapshot,
                    autoActivateAtEndOfCollection = true,
                    onAppendTracks = { newTracks ->
                        composition.playback.enqueueSongs(newTracks)
                    },
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id) {
        val song = playbackState.currentSong ?: return@LaunchedEffect
        composition.listeningStatsStore.recordRecentSong(song)
        composition.lastFmClient.updateNowPlaying(song)
        composition.listenBrainzClient.updateNowPlaying(song)
    }
    androidx.compose.runtime.LaunchedEffect(playbackState.currentSong?.id, isPlaybackActuallyPlaying) {
        val song = playbackState.currentSong ?: return@LaunchedEffect
        if (trackedSongId != song.id) {
            trackedSongId = song.id
            accumulatedPlayMs = 0L
            reportedExtraMs = 0L
            reportedYouTubeHistory = false
            recordedCurrentTrack = false
        }
        if (!isPlaybackActuallyPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            accumulatedPlayMs += 1_000L
            previousCompassoPlayedMs = accumulatedPlayMs
            if (
                !reportedYouTubeHistory &&
                accumulatedPlayMs >= 15_000L &&
                song.durationMs > 0L &&
                composition.playback.progressState.value.positionMs >= (song.durationMs - 2_000L)
            ) {
                reportedYouTubeHistory = true
                composition.youTubeCatalog.reportPlaybackHistory(
                    song,
                    playedSeconds = (accumulatedPlayMs / 1000L).toInt(),
                )
            }
            if (!recordedCurrentTrack && accumulatedPlayMs >= 30_000L) {
                recordedCurrentTrack = true
                reportedExtraMs = 30_000L
                composition.listeningStatsStore.recordSongPlayed(song, initialListenedMs = 30_000L)
                composition.lastFmClient.scrobble(song)
                composition.listenBrainzClient.scrobble(song)
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
                val queue = playbackState.queue.ifEmpty { listOf(song) }
                val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                composition.listeningStatsStore.saveLastPlaybackSession(
                    queue = queue,
                    currentIndex = index,
                    positionMs = composition.playback.progressState.value.positionMs,
                )
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
        compassoEngine = composition.compassoEngine,
        importedPlaylistStore = composition.importedPlaylistStore,
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCompassoEngine provides composition.compassoEngine,
    ) {
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
                listenBrainzClient = composition.listenBrainzClient,
                onExportLibraryJson = {
                    elovaire.music.droidbeauty.app.data.settings.LibraryBackupManager.exportToJson(
                        playlists = appState.playlists,
                        songsById = derivedState.songsById,
                        favoriteSongIds = appState.favoriteSongIds,
                        listeningEvents = composition.listeningStatsStore.events.value,
                    )
                },
                onImportLibraryJson = { rawJson ->
                    val parsed = elovaire.music.droidbeauty.app.data.settings.LibraryBackupManager.parseImport(rawJson)
                        ?: return@RootRouteGraph 0
                    val allSongs = derivedState.songsById.values
                    var importedCount = 0
                    val favMatched = elovaire.music.droidbeauty.app.data.settings.LibraryBackupManager
                        .matchTracksToSongs(parsed.favoriteTracks, allSongs)
                    val favIdsToAdd = favMatched.map { it.id }.filter { it !in appState.favoriteSongIds }
                    if (favIdsToAdd.isNotEmpty()) {
                        composition.favoritesStore.setFavoriteSongs(favIdsToAdd, favorite = true).await()
                        importedCount += favIdsToAdd.size
                    }
                    for (pl in parsed.playlists) {
                        val matched = elovaire.music.droidbeauty.app.data.settings.LibraryBackupManager
                            .matchTracksToSongs(pl.tracks, allSongs)
                        if (matched.isNotEmpty()) {
                            val result = composition.playlistStore.createPlaylist(pl.name).await()
                            if (result is elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.Success) {
                                result.playlistId?.let { newId ->
                                    composition.playlistStore.addSongsToPlaylist(newId, matched.map { it.id }).await()
                                    importedCount += matched.size
                                }
                            }
                        }
                    }
                    importedCount
                },
                onImportPlaylistLink = { rawUrl, customName ->
                    val parsedLink = elovaire.music.droidbeauty.app.data.settings.PlaylistLinkImporter.parseLink(rawUrl)
                        ?: return@RootRouteGraph 0
                    when (parsedLink) {
                        is elovaire.music.droidbeauty.app.data.settings.ParsedPlaylistLink.YouTube -> {
                            val songs = runCatching {
                                composition.youTubeCatalog.getPlaylistSongs(parsedLink.playlistId)
                            }.getOrDefault(emptyList())
                            if (songs.isEmpty()) return@RootRouteGraph 0
                            val targetName = customName?.trim()?.takeIf { it.isNotBlank() } ?: "YouTube Playlist"
                            val result = composition.playlistStore.createPlaylist(targetName).await()
                            if (result is elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.Success) {
                                result.playlistId?.let { newId ->
                                    composition.importedPlaylistStore.savePlaylistData(
                                        playlistId = newId,
                                        coverUri = songs.firstOrNull()?.artUri?.toString(),
                                        songs = songs,
                                    )
                                    composition.playlistStore.addSongsToPlaylist(newId, songs.map { it.id }).await()
                                    songs.size
                                } ?: 0
                            } else {
                                0
                            }
                        }
                        is elovaire.music.droidbeauty.app.data.settings.ParsedPlaylistLink.Spotify -> {
                            val embed = elovaire.music.droidbeauty.app.data.settings.PlaylistLinkImporter
                                .fetchSpotifyEmbedPlaylist(parsedLink.playlistId)
                                ?: return@RootRouteGraph 0
                            val localByKey = derivedState.songsById.values.associateBy { song ->
                                elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
                                    .normalizeKey(song.title, song.artist)
                            }
                            val resolved = ArrayList<elovaire.music.droidbeauty.app.domain.model.Song>()
                            val seenIds = HashSet<Long>()
                            kotlinx.coroutines.coroutineScope {
                                for (chunk in embed.tracks.take(50).chunked(5)) {
                                    val batch = chunk.map { ref ->
                                        async {
                                            elovaire.music.droidbeauty.app.data.settings.PlaylistLinkImporter
                                                .matchLocalOrFallback(ref, localByKey)
                                                ?: runCatching {
                                                    composition.youTubeCatalog.searchSongs("${ref.title} ${ref.artist}").firstOrNull()
                                                }.getOrNull()
                                        }
                                    }
                                    for (deferred in batch) {
                                        val match = deferred.await()
                                        if (match != null && seenIds.add(match.id)) {
                                            resolved.add(match)
                                        }
                                    }
                                }
                            }
                            if (resolved.isEmpty()) return@RootRouteGraph 0
                            val targetName = customName?.trim()?.takeIf { it.isNotBlank() } ?: embed.name
                            val result = composition.playlistStore.createPlaylist(targetName).await()
                            if (result is elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.Success) {
                                result.playlistId?.let { newId ->
                                    composition.importedPlaylistStore.savePlaylistData(
                                        playlistId = newId,
                                        coverUri = embed.coverArtUrl ?: resolved.firstOrNull()?.artUri?.toString(),
                                        songs = resolved,
                                    )
                                    composition.playlistStore.addSongsToPlaylist(newId, resolved.map { it.id }).await()
                                    resolved.size
                                } ?: 0
                            } else {
                                0
                            }
                        }
                    }
                },
                importedCoverUris = importedPlaylistsSnapshot.coverUrisByPlaylistId,
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
}
