package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import elovaire.music.droidbeauty.app.data.playback.PlaybackQueueCommands
import elovaire.music.droidbeauty.app.data.settings.PlaylistStore
import elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadManager
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Album
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.i18n.UiPhrase
import kotlinx.coroutines.launch

internal data class SongMenuActions(
    val playlists: List<Playlist> = emptyList(),
    val songsById: Map<Long, Song> = emptyMap(),
    val onAddToPlaylist: (playlistId: Long, song: Song) -> PlaylistMutationRequest = { _, _ ->
        kotlinx.coroutines.CompletableDeferred(PlaylistMutationResult.InvalidInput)
    },
    val onCreatePlaylist: PlaylistCreateAction = {
        kotlinx.coroutines.CompletableDeferred(PlaylistMutationResult.InvalidInput)
    },
    val onAddToQueue: (Song) -> Unit = {},
    val onGoToAlbum: (Song) -> Unit = {},
    val onPlayRelated: ((Song) -> Unit)? = null,
    val downloadedVideoIds: Set<String> = emptySet(),
    val activeDownloadVideoIds: Set<String> = emptySet(),
    val onDownloadSong: ((Song) -> Unit)? = null,
    val onRemoveDownload: ((Song) -> Unit)? = null,
    val onDeleteFromLibrary: (Song) -> Unit = {},
    val deletePhrase: UiPhrase = UiPhrase.DeleteFromLibrary,
)

internal val LocalSongMenuActions = compositionLocalOf { SongMenuActions() }

@Composable
internal fun rememberRootSongMenuActions(
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    albumsById: Map<Long, Album>,
    playbackManager: PlaybackQueueCommands,
    playlistStore: PlaylistStore,
    onDeleteSongsFromDevice: (List<Song>) -> Unit,
    openAlbum: (Album, ExpandOrigin, AlbumOpenSource) -> Unit,
    navigateToAlbumId: (Long) -> Unit,
    youTubeCatalog: YouTubeCatalogRepository? = null,
    youTubeDownloadManager: YouTubeDownloadManager? = null,
    downloadedVideoIds: Set<String> = emptySet(),
    activeDownloadVideoIds: Set<String> = emptySet(),
): SongMenuActions {
    val scope = rememberCoroutineScope()
    return remember(
        playlists,
        songsById,
        albumsById,
        playbackManager,
        playlistStore,
        onDeleteSongsFromDevice,
        youTubeCatalog,
        youTubeDownloadManager,
        downloadedVideoIds,
        activeDownloadVideoIds,
    ) {
        SongMenuActions(
            playlists = playlists.filterNot { it.isSystem },
            songsById = songsById,
            onAddToPlaylist = { playlistId, song ->
                playlistStore.addSongsToPlaylist(playlistId, listOf(song.id))
            },
            onCreatePlaylist = playlistStore::createPlaylist,
            onAddToQueue = playbackManager::enqueueSong,
            onGoToAlbum = { song ->
                val album = albumsById[song.albumId]
                if (album != null) {
                    openAlbum(album, ExpandOrigin(), AlbumOpenSource.LibraryAlbums)
                } else if (song.albumId != 0L) {
                    navigateToAlbumId(song.albumId)
                }
            },
            onPlayRelated = youTubeCatalog?.let { catalog ->
                { song ->
                    val videoId = YouTubeUri.videoId(song.uri)
                    if (videoId != null) {
                        scope.launch {
                            val related = runCatching { catalog.getSmartRelatedSongs(song) }.getOrDefault(emptyList())
                            val queue = listOf(song) + related.filterNot { it.id == song.id }
                            playbackManager.playSong(
                                song = song,
                                collection = queue,
                                sourceLabel = song.title,
                            )
                        }
                    }
                }
            },
            downloadedVideoIds = downloadedVideoIds,
            activeDownloadVideoIds = activeDownloadVideoIds,
            onDownloadSong = youTubeDownloadManager?.let { manager ->
                { song -> manager.enqueueDownload(song) }
            },
            onRemoveDownload = youTubeDownloadManager?.let { manager ->
                { song ->
                    YouTubeUri.videoId(song.uri)?.let(manager::removeDownload)
                }
            },
            onDeleteFromLibrary = { song ->
                onDeleteSongsFromDevice(listOf(song))
            },
        )
    }
}
