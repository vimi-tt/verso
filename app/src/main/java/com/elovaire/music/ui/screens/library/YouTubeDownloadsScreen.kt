package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadStatus
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadTask
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadsState
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.screens.common.readableMutedIconColor
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii

@Composable
internal fun YouTubeDownloadsScreen(
    downloadsState: YouTubeDownloadsState,
    favoriteSongIds: Set<Long>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, String) -> Unit,
    onOpenCollection: (YouTubeDownloadedCollection) -> Unit = {},
    onCancelDownload: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeDownloadCopy(language) }
    val commonCopy = remember(language) { commonUiCopy(language) }
    val listState = rememberElovaireLazyListState("yt_downloads_screen")
    var showInProgress by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(listState),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = topBarOccupiedHeight() + 14.dp,
                bottom = bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "yt_downloads_in_progress_toggle") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleCard(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .elovaireClickable { showInProgress = !showInProgress },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = copy.downloadsInProgress,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (downloadsState.activeDownloads.isNotEmpty()) {
                                    Text(
                                        text = localizedCountLabel(
                                            downloadsState.activeDownloads.size,
                                            "song",
                                            language,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = readableSecondaryTextColor(),
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_chevron_left),
                                contentDescription = null,
                                tint = readableMutedIconColor(),
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        rotationZ = if (showInProgress) 270f else 180f
                                    },
                            )
                        }
                    }

                    AnimatedVisibility(visible = showInProgress) {
                        ModuleCard(
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            if (downloadsState.activeDownloads.isEmpty()) {
                                Text(
                                    text = copy.noActiveDownloads,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = readableSecondaryTextColor(),
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    downloadsState.activeDownloads.forEach { task ->
                                        ActiveDownloadRow(
                                            task = task,
                                            onCancel = { onCancelDownload(task.videoId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (downloadsState.downloadedCollections.isNotEmpty()) {
                item(key = "yt_downloaded_collections") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(
                            items = downloadsState.downloadedCollections,
                            key = { "${it.isAlbum}_${it.id}" },
                        ) { collection ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(ElovaireRadii.card))
                                    .elovaireClickable { onOpenCollection(collection) }
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ArtworkImage(
                                    uri = collection.thumbnailUri,
                                    title = collection.title,
                                    modifier = Modifier.size(128.dp),
                                    cornerRadius = ElovaireRadii.card,
                                )
                                Text(
                                    text = collection.title.ifBlank {
                                        if (collection.isAlbum) commonCopy.albums else commonCopy.playlists
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = localizedCountLabel(collection.songs.size, "song", language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readableSecondaryTextColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (downloadsState.downloadedSongs.isEmpty() && downloadsState.downloadedCollections.isEmpty()) {
                item(key = "yt_downloaded_songs_empty") {
                    ModuleCard(
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Text(
                            text = copy.noDownloadedSongs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = readableSecondaryTextColor(),
                        )
                    }
                }
            } else if (downloadsState.downloadedSongs.isNotEmpty()) {
                item(key = "yt_downloaded_songs_list") {
                    ModuleCard(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Column {
                            downloadsState.downloadedSongs.forEachIndexed { index, song ->
                                HomeRecentSongRow(
                                    song = song,
                                    isFavorite = song.id in favoriteSongIds,
                                    onClick = {
                                        onPlaySong(
                                            song,
                                            downloadsState.downloadedSongs,
                                            copy.downloadsTitle,
                                        )
                                    },
                                    onToggleFavorite = { onToggleFavorite(song.id) },
                                    showDivider = index != downloadsState.downloadedSongs.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = copy.downloadsTitle,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ActiveDownloadRow(
    task: YouTubeDownloadTask,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            uri = task.song.artUri,
            title = task.song.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = ElovaireRadii.artwork,
            showArtworkGlow = false,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = task.song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (task.status) {
                YouTubeDownloadStatus.Queued -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ElovaireRadii.pill)),
                    )
                }
                YouTubeDownloadStatus.Downloading -> {
                    if (task.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElovaireRadii.pill)),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElovaireRadii.pill)),
                        )
                    }
                }
                YouTubeDownloadStatus.Failed -> {
                    Text(
                        text = task.song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_trash_2),
            contentDescription = null,
            tint = readableMutedIconColor(),
            modifier = Modifier
                .size(20.dp)
                .elovaireClickable(onClick = onCancel),
        )
    }
}
