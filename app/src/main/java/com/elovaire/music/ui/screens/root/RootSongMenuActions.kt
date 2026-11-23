package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import elovaire.music.droidbeauty.app.data.playback.NowPlayingPlayback
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
    val onBlockTrack: ((Song) -> Unit)? = null,
    val onBlockArtist: ((Song) -> Unit)? = null,
    val youTubePlaylists: List<elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary> = emptyList(),
    val onAddToYouTubePlaylist: (suspend (playlistId: String, song: Song) -> Boolean)? = null,
    val isYouTubeLoggedIn: Boolean = false,
    val isYouTubeSyncing: Boolean = false,
    val onRefreshYouTubePlaylists: (() -> Unit)? = null,
    val importedCoverUris: Map<Long, String> = emptyMap(),
    val deletePhrase: UiPhrase = UiPhrase.DeleteFromLibrary,
)

internal val LocalSongMenuActions = compositionLocalOf { SongMenuActions() }

@Composable
internal fun rememberRootSongMenuActions(
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    albumsById: Map<Long, Album>,
    playbackManager: NowPlayingPlayback,
    playlistStore: PlaylistStore,
    onDeleteSongsFromDevice: (List<Song>) -> Unit,
    openAlbum: (Album, ExpandOrigin, AlbumOpenSource) -> Unit,
    navigateToAlbumId: (Long) -> Unit,
    youTubeCatalog: YouTubeCatalogRepository? = null,
    youTubeDownloadManager: YouTubeDownloadManager? = null,
    compassoEngine: elovaire.music.droidbeauty.app.data.compasso.CompassoEngine? = null,
    importedPlaylistStore: elovaire.music.droidbeauty.app.data.settings.ImportedPlaylistStore? = null,
    downloadedVideoIds: Set<String> = emptySet(),
    activeDownloadVideoIds: Set<String> = emptySet(),
): SongMenuActions {
    val scope = rememberCoroutineScope()
    val ytAccountState = youTubeCatalog?.accountState?.collectAsStateWithLifecycle()?.value
    val importedSnapshot = importedPlaylistStore?.snapshot?.collectAsStateWithLifecycle()?.value
    val editableYouTubePlaylists = remember(ytAccountState?.userPlaylists, ytAccountState?.isLoggedIn) {
        if (ytAccountState?.isLoggedIn == true) {
            ytAccountState.userPlaylists.filterNot { it.id == "LM" || it.id == "VLLM" }
        } else {
            emptyList()
        }
    }
    return remember(
        playlists,
        songsById,
        albumsById,
        playbackManager,
        playlistStore,
        onDeleteSongsFromDevice,
        youTubeCatalog,
        youTubeDownloadManager,
        compassoEngine,
        importedPlaylistStore,
        downloadedVideoIds,
        activeDownloadVideoIds,
        editableYouTubePlaylists,
        ytAccountState?.isLoggedIn,
        ytAccountState?.isSyncing,
        importedSnapshot?.coverUrisByPlaylistId,
    ) {
        SongMenuActions(
            playlists = playlists.filterNot { it.isSystem },
            songsById = songsById,
            onAddToPlaylist = { playlistId, song ->
                importedPlaylistStore?.registerSongForPlaylist(playlistId, song)
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
            onBlockTrack = compassoEngine?.let { engine ->
                { song ->
                    scope.launch {
                        engine.blockTrack(song) {
                            val queue = playbackManager.queueState.value.queue
                            for (idx in queue.indices.reversed()) {
                                if (queue[idx].id == song.id) {
                                    playbackManager.removeQueueIndex(idx)
                                }
                            }
                        }
                    }
                }
            },
            onBlockArtist = compassoEngine?.let { engine ->
                { song ->
                    val targetArtist = elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
                        .normalizePrimaryArtist(song.artist)
                    scope.launch {
                        engine.blockArtist(song.artist) {
                            val queue = playbackManager.queueState.value.queue
                            for (idx in queue.indices.reversed()) {
                                val candidateArtist = elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
                                    .normalizePrimaryArtist(queue[idx].artist)
                                if (candidateArtist == targetArtist) {
                                    playbackManager.removeQueueIndex(idx)
                                }
                            }
                        }
                    }
                }
            },
            youTubePlaylists = editableYouTubePlaylists,
            onAddToYouTubePlaylist = youTubeCatalog?.let { catalog ->
                { ytPlaylistId, song ->
                    val videoId = YouTubeUri.videoId(song.uri)
                    if (videoId.isNullOrBlank()) {
                        false
                    } else {
                        catalog.addVideoToYouTubePlaylist(ytPlaylistId, videoId)
                    }
                }
            },
            isYouTubeLoggedIn = ytAccountState?.isLoggedIn == true,
            isYouTubeSyncing = ytAccountState?.isSyncing == true,
            onRefreshYouTubePlaylists = youTubeCatalog?.let { catalog ->
                {
                    scope.launch { catalog.syncAccountLibrary(force = true) }
                }
            },
            importedCoverUris = importedSnapshot?.coverUrisByPlaylistId.orEmpty(),
        )
    }
}
