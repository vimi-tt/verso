package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import elovaire.music.droidbeauty.app.data.playback.AudiobookChapterReader
import elovaire.music.droidbeauty.app.data.playback.NowPlayingPlayback
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.AudiobookSettings
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.interaction.consumePointersWithoutSemantics
import elovaire.music.droidbeauty.app.ui.motion.PlayerOverlayMotionHost

@Composable
internal fun RootPlayerLayerHost(
    visible: Boolean,
    playerLayerState: PlayerLayerState,
    transitionGeneration: Long,
    onExitFinished: (Long) -> Unit,
    onReturnToCompactFinished: (Long) -> Unit,
    nowPlayingViewModel: NowPlayingViewModel,
    playbackManager: NowPlayingPlayback,
    audiobookChapterReader: AudiobookChapterReader,
    songsById: Map<Long, Song>,
    audiobookSettings: AudiobookSettings,
    isCurrentSongFavorite: Boolean,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onOpenCurrentAlbum: (Long) -> Unit,
    onOpenCurrentArtist: (String) -> Unit = {},
    resolvedCurrentAlbumTitle: String? = null,
    onOpenCurrentYouTubeAlbum: (() -> Unit)? = null,
    resolvedCurrentStreamFormat: String? = null,
    resolvedCurrentStreamQuality: String? = null,
    isCurrentSongYouTubeDownloaded: Boolean = false,
    isCurrentSongYouTubeDownloading: Boolean = false,
    onDownloadCurrentSong: ((Song) -> Unit)? = null,
    onRemoveCurrentSongDownload: ((Song) -> Unit)? = null,
    onToggleFavorite: (Long) -> Unit,
    onAddCurrentSongToPlaylist: (Long, Song) -> PlaylistMutationRequest,
    onCreatePlaylist: PlaylistCreateAction,
    onOpenEqualizer: () -> Unit,
    transitionSnapshot: NowPlayingTransitionSnapshot?,
    modifier: Modifier = Modifier,
) {
    PlayerOverlayMotionHost(
        visible = visible,
        surfaceId = "root.full_player",
        onExitFinished = {
            onExitFinished(transitionGeneration)
            if (playerLayerState == PlayerLayerState.ReturningToCompact) {
                onReturnToCompactFinished(transitionGeneration)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .zIndex(RootLayerZ.PlayerOverlay),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .consumePointersWithoutSemantics(),
            )
            NowPlayingRouteHost(
                viewModel = nowPlayingViewModel,
                playbackManager = playbackManager,
                audiobookChapterReader = audiobookChapterReader,
                isPlayerVisible = visible,
                enrichedSongsById = songsById,
                audiobookSettings = audiobookSettings,
                isFavorite = isCurrentSongFavorite,
                playlists = playlists,
                onBack = onBack,
                onOpenCurrentAlbum = onOpenCurrentAlbum,
                onOpenCurrentArtist = onOpenCurrentArtist,
                resolvedCurrentAlbumTitle = resolvedCurrentAlbumTitle,
                onOpenCurrentYouTubeAlbum = onOpenCurrentYouTubeAlbum,
                resolvedCurrentStreamFormat = resolvedCurrentStreamFormat,
                resolvedCurrentStreamQuality = resolvedCurrentStreamQuality,
                isCurrentSongYouTubeDownloaded = isCurrentSongYouTubeDownloaded,
                isCurrentSongYouTubeDownloading = isCurrentSongYouTubeDownloading,
                onDownloadCurrentSong = onDownloadCurrentSong,
                onRemoveCurrentSongDownload = onRemoveCurrentSongDownload,
                onToggleFavorite = onToggleFavorite,
                onAddCurrentSongToPlaylist = onAddCurrentSongToPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onOpenEqualizer = onOpenEqualizer,
                transitionSnapshot = transitionSnapshot,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
