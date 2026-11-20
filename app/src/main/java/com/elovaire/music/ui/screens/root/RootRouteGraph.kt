package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import elovaire.music.droidbeauty.app.data.artist.ArtistImageReader
import elovaire.music.droidbeauty.app.data.playback.AudiobookChapterReader
import elovaire.music.droidbeauty.app.data.library.AudiobookDescriptionReader
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadManager
import elovaire.music.droidbeauty.app.ui.motion.MotionTransitions
import kotlinx.coroutines.launch

@Composable
@Suppress("LongMethod")
internal fun RootRouteGraph(
    navState: RootNavigationState,
    motionTransitions: MotionTransitions,
    routeState: RootRouteState,
    routeActions: RootRouteActions,
    overlayState: RootOverlayStateHolder,
    padding: RootRoutePadding,
    searchViewModel: SearchViewModel,
    youtubeHomeViewModel: YouTubeHomeViewModel,
    youTubeCatalog: YouTubeCatalogRepository,
    youTubeDownloadManager: YouTubeDownloadManager,
    listeningStatsStore: elovaire.music.droidbeauty.app.data.youtube.ListeningStatsStore,
    lastFmClient: elovaire.music.droidbeauty.app.data.youtube.LastFmClient,
    viewModelFactory: ElovaireViewModelFactory,
    artistImageRepository: ArtistImageReader,
    audiobookChapterReader: AudiobookChapterReader,
    audiobookDescriptionReader: AudiobookDescriptionReader,
    modifier: Modifier = Modifier,
) {
    RootNavigationHost(
        navState = navState,
        motionTransitions = motionTransitions,
        modifier = modifier,
    ) {
        composable(RootRouteRegistry.HOME) {
            val onYouTubeArtistSelected = androidx.compose.runtime.remember(navState) {
                { artistId: String -> navState.navigateTo(Routes.youtubeArtist(artistId)) }
            }
            val onYouTubeCollectionSelected = androidx.compose.runtime.remember(navState) {
                { collection: elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem.Collection ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = collection.summary.id,
                            isAlbum = collection.isAlbum,
                            title = collection.summary.title,
                            subtitle = collection.summary.subtitle,
                            artUri = collection.summary.thumbnailUri?.toString(),
                        ),
                    )
                }
            }
            val onOpenEditorialSeeAll = androidx.compose.runtime.remember(navState) {
                { sectionId: EditorialSectionId ->
                    navState.navigateTo(Routes.editorialSection(sectionId))
                }
            }
            val onOpenExploreMood = androidx.compose.runtime.remember(navState) {
                { mood: elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem.Mood ->
                    navState.navigateTo(
                        Routes.exploreMood(
                            browseId = mood.browseId,
                            params = mood.params,
                            title = mood.title,
                        ),
                    )
                }
            }
            AlbumTransitionContent(this) {
                HomeRouteHost(
                    navState = navState,
                    state = routeState,
                    routeActions = routeActions,
                    padding = padding,
                    youtubeHomeViewModel = youtubeHomeViewModel,
                    onYouTubeArtistSelected = onYouTubeArtistSelected,
                    onYouTubeCollectionSelected = onYouTubeCollectionSelected,
                    onOpenEditorialSeeAll = onOpenEditorialSeeAll,
                    onOpenExploreMood = onOpenExploreMood,
                )
            }
        }
        composable(RootRouteRegistry.ALBUMS) {
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val inAppFavoritesCount = routeState.appState.favoriteSongIds.size
            val totalFavoritesCount = inAppFavoritesCount + accountState.likedSongs.count { it.id !in routeState.appState.favoriteSongIds }
            AlbumTransitionContent(this) {
                LibraryHubRouteHost(
                    navState = navState,
                    routeState = routeState,
                    routeActions = routeActions,
                    padding = padding,
                    downloadedSongsCount = downloadsState.downloadedSongs.size,
                    activeDownloadsCount = downloadsState.activeDownloads.size,
                    onOpenDownloads = { navState.navigateTo(Routes.youtubeDownloads()) },
                    favoritesCount = totalFavoritesCount,
                    onOpenFavorites = { navState.navigateTo(Routes.youtubeFavorites()) },
                    youTubeSongsCount = accountState.likedSongs.size,
                    youTubeAlbumsCount = accountState.userAlbums.size,
                    youTubeArtistsCount = accountState.userArtists.size,
                    onOpenStats = { navState.navigateTo(Routes.stats()) },
                )
            }
        }
        composable(RootRouteRegistry.STATS) {
            val events by listeningStatsStore.events.collectAsStateWithLifecycle()
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            var selectedPeriod by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(elovaire.music.droidbeauty.app.data.youtube.StatsPeriod.AllTime)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                listeningStatsStore.backfillMissingGenres(lastFmClient::resolveGenre)
            }
            val stats = remember(
                events,
                selectedPeriod,
                routeState.appState.library.songs,
                routeState.appState.songPlayCounts,
                accountState.likedSongs,
                downloadsState.downloadedSongs,
            ) {
                elovaire.music.droidbeauty.app.data.youtube.buildListeningStatsSnapshot(
                    events = events,
                    period = selectedPeriod,
                    librarySongs = routeState.appState.library.songs,
                    songPlayCounts = routeState.appState.songPlayCounts,
                    extraSongs = accountState.likedSongs + downloadsState.downloadedSongs,
                    nowMs = elovaire.music.droidbeauty.app.core.AndroidAppClock.wallTimeMs(),
                )
            }
            StatsScreen(
                stats = stats,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it },
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onOpenRecap = { period -> navState.navigateTo(Routes.recap(period.name)) },
            )
        }
        composable(
            route = RootRouteRegistry.RECAP,
            arguments = listOf(
                navArgument("period") {
                    type = NavType.StringType
                    defaultValue = "AllTime"
                },
            ),
        ) { backStackEntry ->
            val periodName = android.net.Uri.decode(backStackEntry.arguments?.getString("period").orEmpty())
            val period = runCatching {
                elovaire.music.droidbeauty.app.data.youtube.StatsPeriod.valueOf(periodName)
            }.getOrDefault(elovaire.music.droidbeauty.app.data.youtube.StatsPeriod.AllTime)
            val events by listeningStatsStore.events.collectAsStateWithLifecycle()
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                listeningStatsStore.backfillMissingGenres(lastFmClient::resolveGenre)
            }
            val stats = remember(
                events,
                period,
                routeState.appState.library.songs,
                routeState.appState.songPlayCounts,
                accountState.likedSongs,
                downloadsState.downloadedSongs,
            ) {
                elovaire.music.droidbeauty.app.data.youtube.buildListeningStatsSnapshot(
                    events = events,
                    period = period,
                    librarySongs = routeState.appState.library.songs,
                    songPlayCounts = routeState.appState.songPlayCounts,
                    extraSongs = accountState.likedSongs + downloadsState.downloadedSongs,
                    nowMs = elovaire.music.droidbeauty.app.core.AndroidAppClock.wallTimeMs(),
                )
            }
            RecapScreen(
                stats = stats,
                onClose = { navState.navController.popBackStack() },
                onStartBackgroundSong = { song ->
                    if (routeState.appState.playback.currentSong?.id != song.id || !routeState.isPlaybackActuallyPlaying) {
                        routeActions.playback.playSongQueue(
                            song = song,
                            queue = listOf(song),
                            sourceLabel = "Verso Recap",
                        )
                    }
                },
                onSetMuted = { muted ->
                    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioManager?.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        if (muted) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE,
                        0,
                    )
                },
            )
        }
        composable(RootRouteRegistry.YOUTUBE_FAVORITES) {
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val favoriteSongIds = routeState.appState.favoriteSongIds
            val inAppFavoriteSongs = androidx.compose.runtime.remember(routeState.appState.library.songs, favoriteSongIds) {
                routeState.appState.library.songs.filter { it.id in favoriteSongIds }
            }
            YouTubeFavoritesScreen(
                inAppFavoriteSongs = inAppFavoriteSongs,
                accountState = accountState,
                topPadding = padding.topContent,
                bottomPadding = padding.bottomContent,
                onRefreshAccount = {
                    scope.launch { youTubeCatalog.syncAccountLibrary() }
                },
                onSongSelected = { song, queue ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = song.title,
                    )
                },
                onSongMoreActions = { song -> routeActions.playlists.toggleFavorite(song.id) },
                onOpenSettings = { navState.navigateTo(SETTINGS_ROUTE) },
            )
        }
        composable(RootRouteRegistry.YOUTUBE_DOWNLOADS) {
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            YouTubeDownloadsScreen(
                downloadsState = downloadsState,
                favoriteSongIds = routeState.appState.favoriteSongIds,
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onPlaySong = { song, queue, label ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = label,
                    )
                },
                onOpenCollection = { collection ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = collection.id,
                            isAlbum = collection.isAlbum,
                            title = collection.title,
                            subtitle = collection.subtitle,
                            artUri = collection.thumbnailUri?.toString(),
                        ),
                    )
                },
                onCancelDownload = youTubeDownloadManager::cancelDownload,
                onToggleFavorite = routeActions.playlists::toggleFavorite,
            )
        }
        composable(RootRouteRegistry.RECENTLY_ADDED) {
            AlbumTransitionContent(this) {
                RecentlyAddedRouteHost(
                    routeState = routeState,
                    routeActions = routeActions,
                    padding = padding,
                )
            }
        }
        composable(RootRouteRegistry.AUDIOBOOKS) {
            AlbumTransitionContent(this) {
                AudiobooksRouteHost(routeState, routeActions, padding)
            }
        }
        composable(
            route = RootRouteRegistry.AUDIOBOOK,
            arguments = listOf(navArgument("bookKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            AlbumTransitionContent(this) {
                AudiobookDetailRouteHost(
                    stableKey = backStackEntry.audiobookRouteKey(),
                    routeState = routeState,
                    routeActions = routeActions,
                    padding = padding,
                    chapterReader = audiobookChapterReader,
                    descriptionReader = audiobookDescriptionReader,
                )
            }
        }
        composable(
            route = RootRouteRegistry.AUDIOBOOK_TAG_EDITOR,
            arguments = listOf(navArgument("bookKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            AudiobookTagEditorRouteHost(
                bookKey = backStackEntry.audiobookRouteKey(),
                backStackEntry = backStackEntry,
                viewModelFactory = viewModelFactory,
                appLanguage = routeState.appState.appLanguage,
                onBack = routeActions::navigateUp,
                onSaveSucceeded = routeActions::completeAudiobookTagEdit,
            )
        }
        composable(RootRouteRegistry.PLAYLISTS) {
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val inAppFavoritesCount = routeState.appState.favoriteSongIds.size
            val totalFavoritesCount = inAppFavoritesCount + accountState.likedSongs.count { it.id !in routeState.appState.favoriteSongIds }
            PlaylistsRouteHost(
                navState = navState,
                state = routeState,
                routeActions = routeActions,
                padding = padding,
                youTubePlaylists = accountState.userPlaylists,
                onOpenYouTubePlaylist = { ytPlaylist ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = ytPlaylist.id,
                            isAlbum = false,
                            title = ytPlaylist.title,
                            subtitle = ytPlaylist.subtitle,
                            artUri = ytPlaylist.thumbnailUri?.toString(),
                        ),
                    )
                },
                isYouTubeLoggedIn = accountState.isLoggedIn,
                isYouTubeSyncing = accountState.isSyncing,
                favoritesCount = totalFavoritesCount,
                downloadsCount = downloadsState.downloadedSongs.size,
                onOpenFavorites = { navState.navigateTo(Routes.youtubeFavorites()) },
                onOpenDownloads = { navState.navigateTo(Routes.youtubeDownloads()) },
                onSyncYouTube = { scope.launch { youTubeCatalog.syncAccountLibrary(force = true) } },
                onOpenSettings = { navState.navigateTo(SETTINGS_ROUTE) },
            )
        }
        composable(RootRouteRegistry.SEARCH) {
            AlbumTransitionContent(this) {
                SearchRouteHost(
                    navState = navState,
                    routeState = routeState,
                    routeActions = routeActions,
                    padding = padding,
                    searchViewModel = searchViewModel,
                    youtubeHomeViewModel = youtubeHomeViewModel,
                    onYouTubeArtistSelected = { artistId ->
                        navState.navigateTo(Routes.youtubeArtist(artistId))
                    },
                    onYouTubeCollectionSelected = { summary, isAlbum ->
                        navState.navigateTo(
                            Routes.youtubeCollection(
                                collectionId = summary.id,
                                isAlbum = isAlbum,
                                title = summary.title,
                                subtitle = summary.subtitle,
                                artUri = summary.thumbnailUri?.toString(),
                            ),
                        )
                    },
                )
            }
        }
        composable(
            route = RootRouteRegistry.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
        ) { backStackEntry ->
            PlaylistDetailRouteHost(backStackEntry.playlistRouteId(), routeState, routeActions, padding)
        }
        composable(
            route = RootRouteRegistry.SMART_PLAYLIST,
            arguments = listOf(navArgument("smartPlaylistId") { type = NavType.LongType }),
        ) { backStackEntry ->
            SmartPlaylistDetailRouteHost(backStackEntry.smartPlaylistRouteId(), routeState, routeActions, padding)
        }
        composable(RootRouteRegistry.SMART_PLAYLIST_EDITOR) {
            SmartPlaylistEditorRouteHost(null, routeState, routeActions, padding)
        }
        composable(
            route = RootRouteRegistry.SMART_PLAYLIST_EDITOR_EXISTING,
            arguments = listOf(navArgument("smartPlaylistId") { type = NavType.LongType }),
        ) { backStackEntry ->
            SmartPlaylistEditorRouteHost(backStackEntry.smartPlaylistRouteId(), routeState, routeActions, padding)
        }
        composable(
            route = RootRouteRegistry.ALBUM,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
        ) { backStackEntry ->
            AlbumTransitionContent(this) {
                AlbumRouteHost(backStackEntry.albumRouteId(), navState, routeState, routeActions, padding)
            }
        }
        composable(
            route = RootRouteRegistry.ALBUM_TAG_EDITOR,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
        ) { backStackEntry ->
            AlbumTagEditorRouteHost(
                albumId = backStackEntry.albumRouteId(),
                backStackEntry = backStackEntry,
                viewModelFactory = viewModelFactory,
                appLanguage = routeState.appState.appLanguage,
                onBack = routeActions::navigateUp,
            )
        }
        composable(
            route = RootRouteRegistry.LIBRARY_COLLECTION,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("localOnly") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val localOnly = backStackEntry.arguments?.getBoolean("localOnly") ?: false
            AlbumTransitionContent(this) {
                LibraryCollectionRouteHost(
                    kind = backStackEntry.libraryCollectionKindArg(),
                    routeState = routeState,
                    routeActions = routeActions,
                    padding = padding,
                    artistImageRepository = artistImageRepository,
                    localOnly = localOnly,
                    youTubeSongs = accountState.likedSongs,
                    youTubeAlbums = accountState.userAlbums,
                    youTubeArtists = accountState.userArtists,
                    onOpenYouTubeAlbum = { ytAlbum ->
                        navState.navigateTo(
                            Routes.youtubeCollection(
                                collectionId = ytAlbum.id,
                                isAlbum = true,
                                title = ytAlbum.title,
                                subtitle = ytAlbum.subtitle,
                                artUri = ytAlbum.thumbnailUri?.toString(),
                            ),
                        )
                    },
                    onOpenYouTubeArtist = { ytArtist ->
                        navState.navigateTo(Routes.youtubeArtist(ytArtist.id))
                    },
                )
            }
        }
        composable(
            route = RootRouteRegistry.GENRE,
            arguments = listOf(navArgument("genre") { type = NavType.StringType }),
        ) { backStackEntry ->
            AlbumTransitionContent(this) {
                GenreRouteHost(backStackEntry.genreRouteArg(), routeState, routeActions, padding)
            }
        }
        composable(
            route = RootRouteRegistry.YOUTUBE_ARTIST,
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val artistId = android.net.Uri.decode(
                backStackEntry.arguments?.getString("artistId").orEmpty(),
            )
            YouTubeArtistScreen(
                artistId = artistId,
                youTubeCatalog = youTubeCatalog,
                favoriteSongIds = routeState.appState.favoriteSongIds,
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onPlaySongs = { song, queue, label ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = label,
                    )
                },
                onShuffleSongs = { queue, label ->
                    if (queue.isNotEmpty()) {
                        val shuffled = queue.shuffled()
                        routeActions.playback.playSongQueue(
                            song = shuffled.first(),
                            queue = shuffled,
                            sourceLabel = label,
                        )
                    }
                },
                onCollectionSelected = { summary, isAlbum ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = summary.id,
                            isAlbum = isAlbum,
                            title = summary.title,
                            subtitle = summary.subtitle,
                            artUri = summary.thumbnailUri?.toString(),
                        ),
                    )
                },
                onArtistSelected = { nextId ->
                    navState.navigateTo(Routes.youtubeArtist(nextId))
                },
                onToggleFavorite = routeActions.playlists::toggleFavorite,
            )
        }
        composable(
            route = RootRouteRegistry.YOUTUBE_COLLECTION,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.StringType },
                navArgument("isAlbum") { type = NavType.BoolType; defaultValue = true },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("subtitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("artUri") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val collectionId = android.net.Uri.decode(args?.getString("collectionId").orEmpty())
            val isAlbum = args?.getBoolean("isAlbum") ?: true
            val title = android.net.Uri.decode(args?.getString("title").orEmpty())
            val subtitle = android.net.Uri.decode(args?.getString("subtitle").orEmpty())
            val artUri = android.net.Uri.decode(args?.getString("artUri").orEmpty()).takeIf(String::isNotBlank)
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val offlineCollection = remember(collectionId, downloadsState.downloadedCollections) {
                downloadsState.downloadedCollections.firstOrNull { it.id == collectionId }
            }
            YouTubeCollectionScreen(
                collectionId = collectionId,
                isAlbum = isAlbum,
                initialTitle = title,
                initialSubtitle = subtitle,
                initialArtUri = artUri,
                youTubeCatalog = youTubeCatalog,
                offlineCollection = offlineCollection,
                favoriteSongIds = routeState.appState.favoriteSongIds,
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onPlaySongs = { song, queue, label ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = label,
                    )
                },
                onShuffleSongs = { queue, label ->
                    if (queue.isNotEmpty()) {
                        val shuffled = queue.shuffled()
                        routeActions.playback.playSongQueue(
                            song = shuffled.first(),
                            queue = shuffled,
                            sourceLabel = label,
                        )
                    }
                },
                onDownloadCollection = youTubeDownloadManager::enqueueCollection,
                onToggleFavorite = routeActions.playlists::toggleFavorite,
            )
        }
        composable(
            route = RootRouteRegistry.EDITORIAL_SECTION,
            arguments = listOf(
                navArgument("sectionId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sectionId = EditorialSectionId.fromRouteKey(
                backStackEntry.arguments?.getString("sectionId"),
            )
            val editorialState by youtubeHomeViewModel.editorialState.collectAsStateWithLifecycle()
            val onOpenCollection = remember(navState) {
                { collection: elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem.Collection ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = collection.summary.id,
                            isAlbum = collection.isAlbum,
                            title = collection.summary.title,
                            subtitle = collection.summary.subtitle,
                            artUri = collection.summary.thumbnailUri?.toString(),
                        ),
                    )
                }
            }
            val onOpenExploreMood = remember(navState) {
                { mood: elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem.Mood ->
                    navState.navigateTo(
                        Routes.exploreMood(
                            browseId = mood.browseId,
                            params = mood.params,
                            title = mood.title,
                        ),
                    )
                }
            }
            val onSelectTrendingTrack = remember(routeActions) {
                { song: elovaire.music.droidbeauty.app.domain.model.Song,
                  queue: List<elovaire.music.droidbeauty.app.domain.model.Song> ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = song.artist.ifBlank { "Verso" },
                    )
                }
            }
            EditorialSectionDetailScreen(
                sectionId = sectionId,
                editorialState = editorialState,
                recentSongs = routeState.recentSongs,
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onOpenCollection = onOpenCollection,
                onSelectRecentSong = routeActions.playback::playSongFromAlbumOrSingle,
                onSelectTrendingTrack = onSelectTrendingTrack,
                onOpenExploreMood = onOpenExploreMood,
                onRetrySection = youtubeHomeViewModel::retryEditorialSection,
            )
        }
        composable(
            route = RootRouteRegistry.EXPLORE_MOOD,
            arguments = listOf(
                navArgument("browseId") { type = NavType.StringType; defaultValue = "" },
                navArgument("params") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val browseId = android.net.Uri.decode(args?.getString("browseId").orEmpty())
            val params = android.net.Uri.decode(args?.getString("params").orEmpty()).takeIf(String::isNotBlank)
            val title = android.net.Uri.decode(args?.getString("title").orEmpty())
            val editorialState by youtubeHomeViewModel.editorialState.collectAsStateWithLifecycle()
            val availableMoods = remember(editorialState.moods) {
                (editorialState.moods as? EditorialSectionState.Content)?.items.orEmpty()
            }
            val onOpenCollection = remember(navState) {
                { collection: elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem.Collection ->
                    navState.navigateTo(
                        Routes.youtubeCollection(
                            collectionId = collection.summary.id,
                            isAlbum = collection.isAlbum,
                            title = collection.summary.title,
                            subtitle = collection.summary.subtitle,
                            artUri = collection.summary.thumbnailUri?.toString(),
                        ),
                    )
                }
            }
            val onSelectTrack = remember(routeActions) {
                { song: elovaire.music.droidbeauty.app.domain.model.Song,
                  queue: List<elovaire.music.droidbeauty.app.domain.model.Song> ->
                    routeActions.playback.playSongQueue(
                        song = song,
                        queue = queue,
                        sourceLabel = song.artist.ifBlank { "Verso" },
                    )
                }
            }
            ExploreMoodScreen(
                initialBrowseId = browseId,
                initialParams = params,
                initialTitle = title,
                availableMoods = availableMoods,
                youTubeCatalog = youTubeCatalog,
                bottomPadding = padding.bottomContent,
                onBack = { navState.navController.popBackStack() },
                onOpenCollection = onOpenCollection,
                onSelectTrack = onSelectTrack,
            )
        }
        composable(
            route = RootRouteRegistry.ARTIST,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
        ) { backStackEntry ->
            AlbumTransitionContent(this) {
                ArtistRouteHost(
                    backStackEntry.artistRouteArg(),
                    routeState,
                    routeActions,
                    padding,
                    artistImageRepository,
                    overlayState,
                )
            }
        }
        composable(RootRouteRegistry.EQUALIZER) {
            EqualizerRouteHost(viewModelFactory, routeActions)
        }
        composable(RootRouteRegistry.CROSSFADE) {
            CrossfadeRouteHost(routeActions, padding)
        }
        composable(RootRouteRegistry.UPSCALER) {
            UpscalerRouteHost(routeActions, padding)
        }
        composable(RootRouteRegistry.AUDIOBOOK_SETTINGS) {
            AudiobookSettingsRouteHost(routeActions, padding)
        }
        composable(RootRouteRegistry.SETTINGS_ACCOUNTS) {
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val qualityState by youTubeCatalog.qualityState.collectAsStateWithLifecycle()
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val lastFmState by lastFmClient.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var musicCacheSizeBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var downloadsSizeBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var storageRefreshNonce by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(storageRefreshNonce, downloadsState.downloadedSongs.size) {
                val (cacheBytes, dlBytes) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    directorySizeBytes(context.cacheDir) to youTubeDownloadManager.totalDownloadsSizeBytes()
                }
                musicCacheSizeBytes = cacheBytes
                downloadsSizeBytes = dlBytes
            }
            SettingsRouteHost(
                routeState = routeState,
                routeActions = routeActions,
                padding = padding,
                youTubeAccountState = accountState,
                onOpenYouTubeLogin = { navState.navigateTo(Routes.youtubeLogin()) },
                onSyncYouTubeAccount = {
                    scope.launch { youTubeCatalog.syncAccountLibrary(force = true) }
                },
                onSignOutYouTube = {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    youTubeCatalog.signOut()
                },
                youTubeQualityState = qualityState,
                onWifiStreamingQualitySelected = youTubeCatalog::setWifiStreamingQuality,
                onCellularStreamingQualitySelected = youTubeCatalog::setCellularStreamingQuality,
                onDownloadQualitySelected = youTubeCatalog::setDownloadQuality,
                onWifiOnlyDownloadsChanged = youTubeCatalog::setWifiOnlyDownloads,
                onPreferredLyricsProviderSelected = youTubeCatalog::setPreferredLyricsProvider,
                musicCacheSizeBytes = musicCacheSizeBytes,
                downloadsSizeBytes = downloadsSizeBytes,
                onClearMusicCache = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        youTubeCatalog.clearCaches()
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        storageRefreshNonce += 1
                    }
                },
                onClearDownloads = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        youTubeDownloadManager.clearAllDownloads()
                        storageRefreshNonce += 1
                    }
                },
                lastFmState = lastFmState,
                onSignInLastFm = lastFmClient::signIn,
                onToggleLastFmScrobble = lastFmClient::setScrobbleEnabled,
                onSignOutLastFm = lastFmClient::signOut,
                onClearLastFmError = lastFmClient::clearError,
                onSyncYouTubeHistoryChanged = youTubeCatalog::setSyncPlaybackHistoryToYouTube,
                scrollToAccounts = true,
            )
        }
        composable(RootRouteRegistry.SETTINGS) {
            val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
            val qualityState by youTubeCatalog.qualityState.collectAsStateWithLifecycle()
            val downloadsState by youTubeDownloadManager.state.collectAsStateWithLifecycle()
            val lastFmState by lastFmClient.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var musicCacheSizeBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var downloadsSizeBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var storageRefreshNonce by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(storageRefreshNonce, downloadsState.downloadedSongs.size) {
                val (cacheBytes, dlBytes) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    directorySizeBytes(context.cacheDir) to youTubeDownloadManager.totalDownloadsSizeBytes()
                }
                musicCacheSizeBytes = cacheBytes
                downloadsSizeBytes = dlBytes
            }
            SettingsRouteHost(
                routeState = routeState,
                routeActions = routeActions,
                padding = padding,
                youTubeAccountState = accountState,
                onOpenYouTubeLogin = { navState.navigateTo(Routes.youtubeLogin()) },
                onSyncYouTubeAccount = {
                    scope.launch { youTubeCatalog.syncAccountLibrary(force = true) }
                },
                onSignOutYouTube = {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    youTubeCatalog.signOut()
                },
                youTubeQualityState = qualityState,
                onWifiStreamingQualitySelected = youTubeCatalog::setWifiStreamingQuality,
                onCellularStreamingQualitySelected = youTubeCatalog::setCellularStreamingQuality,
                onDownloadQualitySelected = youTubeCatalog::setDownloadQuality,
                onWifiOnlyDownloadsChanged = youTubeCatalog::setWifiOnlyDownloads,
                onPreferredLyricsProviderSelected = youTubeCatalog::setPreferredLyricsProvider,
                musicCacheSizeBytes = musicCacheSizeBytes,
                downloadsSizeBytes = downloadsSizeBytes,
                onClearMusicCache = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        youTubeCatalog.clearCaches()
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        storageRefreshNonce += 1
                    }
                },
                onClearDownloads = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        youTubeDownloadManager.clearAllDownloads()
                        storageRefreshNonce += 1
                    }
                },
                lastFmState = lastFmState,
                onSignInLastFm = lastFmClient::signIn,
                onToggleLastFmScrobble = lastFmClient::setScrobbleEnabled,
                onSignOutLastFm = lastFmClient::signOut,
                onClearLastFmError = lastFmClient::clearError,
                onSyncYouTubeHistoryChanged = youTubeCatalog::setSyncPlaybackHistoryToYouTube,
            )
        }
        composable(RootRouteRegistry.YOUTUBE_LOGIN) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            YouTubeLoginScreen(
                onBack = routeActions::navigateUp,
                onLoginCookiesExtracted = { cookies ->
                    scope.launch {
                        youTubeCatalog.completeLogin(cookies)
                        navState.navController.popBackStack()
                    }
                },
            )
        }
        composable(RootRouteRegistry.MANAGE_PLAYLISTS) {
            ManagePlaylistsRouteHost(routeState, routeActions, padding)
        }
        composable(RootRouteRegistry.LIBRARY_FOLDERS) {
            LibraryFoldersRouteHost(routeState, routeActions, padding)
        }
        composable(RootRouteRegistry.NOW_PLAYING_BAR_STYLE) {
            NowPlayingBarStyleRouteHost(routeState, routeActions, padding)
        }
        composable(RootRouteRegistry.HOME_LAYOUT) {
            HomeLayoutScreen(bottomPadding = padding.detailBottom, onBack = routeActions::navigateUp)
        }
        composable(RootRouteRegistry.SMART_PLAYLIST_SETTINGS) {
            SmartPlaylistSettingsRouteHost(routeActions, padding)
        }
        composable(RootRouteRegistry.CHANGELOG) {
            ChangelogRouteHost(routeActions)
        }
        composable(RootRouteRegistry.ABOUT) {
            AboutRouteHost(routeActions, padding)
        }
        composable(RootRouteRegistry.PRIVACY_POLICY) {
            PrivacyPolicyRouteHost(routeState, routeActions, padding)
        }
    }
}

@Composable
private fun SearchRouteHost(
    navState: RootNavigationState,
    routeState: RootRouteState,
    routeActions: RootRouteActions,
    padding: RootRoutePadding,
    searchViewModel: SearchViewModel,
    youtubeHomeViewModel: YouTubeHomeViewModel,
    onYouTubeArtistSelected: (String) -> Unit = {},
    onYouTubeCollectionSelected: (YouTubeCollectionSummary, Boolean) -> Unit = { _, _ -> },
) {
    SearchRoute(
        viewModel = searchViewModel,
        libraryState = routeState.appState.library,
        playlists = routeState.appState.playlists,
        favoriteSongIds = routeState.appState.favoriteSongIds,
        topPadding = padding.topContent,
        bottomPadding = padding.bottomContent,
        scrollToTopRequestVersion = navState.searchScrollRequestVersion,
        onSearchActiveChanged = routeActions.onSearchActiveChanged,
        onPlaySong = { song, queue ->
            if (elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.isYouTubeUri(song.uri)) {
                routeActions.playback.playSongQueue(
                    song = song,
                    queue = listOf(song),
                    sourceLabel = song.artist.ifBlank { "Verso" },
                )
                youtubeHomeViewModel.loadSmartRelatedForQueue(song) { related ->
                    routeActions.playback.enqueueSongs(related)
                }
            } else {
                routeActions.playback.playSongQueue(
                    song = song,
                    queue = queue,
                    sourceLabel = searchViewModel.playbackSourceLabelFor(queue, song.album),
                )
            }
        },
        onAlbumSelected = { album, origin ->
            routeActions.openAlbum(album, origin, AlbumOpenSource.SearchResults)
        },
        onArtistSelected = routeActions::openArtist,
        onAudiobookSelected = { book -> routeActions.openAudiobook(book.stableKey) },
        onPlaylistSelected = { playlist -> routeActions.openPlaylist(playlist.id) },
        onToggleFavorite = routeActions.playlists::toggleFavorite,
        onYouTubeArtistSelected = onYouTubeArtistSelected,
        onYouTubeCollectionSelected = onYouTubeCollectionSelected,
    )
}

private fun directorySizeBytes(root: java.io.File?): Long {
    if (root == null || !root.exists()) return 0L
    if (root.isFile) return root.length()
    return root.listFiles()?.sumOf(::directorySizeBytes) ?: 0L
}
