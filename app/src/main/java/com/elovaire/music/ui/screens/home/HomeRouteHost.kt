package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Song

@Composable
internal fun HomeRouteHost(
    navState: RootNavigationState,
    state: RootRouteState,
    routeActions: RootRouteActions,
    padding: RootRoutePadding,
    youtubeHomeViewModel: YouTubeHomeViewModel,
    @Suppress("UNUSED_PARAMETER")
    onYouTubeArtistSelected: (String) -> Unit = {},
    onYouTubeCollectionSelected: (YouTubeHomeItem.Collection) -> Unit = {},
    onOpenEditorialSeeAll: (EditorialSectionId) -> Unit = {},
    onOpenExploreMood: (YouTubeHomeItem.Mood) -> Unit = {},
) {
    val editorialState by youtubeHomeViewModel.editorialState.collectAsStateWithLifecycle()

    val onSelectTrendingTrack = remember(routeActions) {
        { song: Song, queue: List<Song> ->
            routeActions.playback.playSongQueue(
                song = song,
                queue = queue,
                sourceLabel = song.artist.ifBlank { "Verso" },
            )
        }
    }

    val onSelectRecentSong = remember(routeActions, youtubeHomeViewModel) {
        { song: Song ->
            if (YouTubeUri.isYouTubeUri(song.uri)) {
                routeActions.playback.playSongQueue(
                    song = song,
                    queue = listOf(song),
                    sourceLabel = song.artist.ifBlank { "Verso" },
                )
                youtubeHomeViewModel.loadSmartRelatedForQueue(song) { related ->
                    routeActions.playback.enqueueSongs(related)
                }
            } else {
                routeActions.playback.playSongFromAlbumOrSingle(song)
            }
        }
    }

    val onPlayHero = remember(youtubeHomeViewModel, onSelectTrendingTrack, onYouTubeCollectionSelected) {
        { item: EditorialCollectionItem ->
            youtubeHomeViewModel.playHeroCollection(
                item = item,
                onQueueReady = onSelectTrendingTrack,
                onFallbackOpen = onYouTubeCollectionSelected,
            )
        }
    }

    EditorialHomeScreen(
        editorialState = editorialState,
        recentSongs = state.recentSongs,
        topPadding = padding.topContent,
        bottomPadding = padding.bottomContent,
        scrollToTopRequestVersion = navState.homeScrollRequestVersion,
        resetScrollOnColdStart = state.resetHomeScrollOnColdStart,
        onRefresh = youtubeHomeViewModel::refreshEditorialHome,
        onPlayHero = onPlayHero,
        onOpenCollection = onYouTubeCollectionSelected,
        onSelectRecentSong = onSelectRecentSong,
        onSelectTrendingTrack = onSelectTrendingTrack,
        onOpenSeeAll = onOpenEditorialSeeAll,
        onOpenExploreMood = onOpenExploreMood,
        onRetrySection = youtubeHomeViewModel::retryEditorialSection,
    )
}
