package elovaire.music.droidbeauty.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.components.rememberArtworkPaletteAccent
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.editorialHomeCopy
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy
import elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun YouTubeCollectionScreen(
    collectionId: String,
    isAlbum: Boolean,
    initialTitle: String,
    initialSubtitle: String,
    initialArtUri: String?,
    youTubeCatalog: YouTubeCatalogRepository,
    offlineCollection: YouTubeDownloadedCollection? = null,
    downloadedVideoIds: Set<String> = emptySet(),
    activeDownloadVideoIds: Set<String> = emptySet(),
    favoriteSongIds: Set<Long>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlaySongs: (Song, List<Song>, String) -> Unit,
    onShuffleSongs: (List<Song>, String) -> Unit,
    onDownloadCollection: ((List<Song>, YouTubeDownloadedCollection) -> Unit)? = null,
    onToggleFavorite: (Long) -> Unit,
) {
    val language = LocalAppLanguage.current
    val ytCopy = remember(language) { youtubeCopy(language) }
    val dlCopy = remember(language) { youtubeDownloadCopy(language) }
    val commonCopy = remember(language) { commonUiCopy(language) }
    val shuffleLabel = remember(language) { editorialHomeCopy(language).shuffle }
    val scope = rememberCoroutineScope()
    val listState = rememberElovaireLazyListState("yt_collection_$collectionId")

    var songs by remember(collectionId, isAlbum, offlineCollection) {
        mutableStateOf(offlineCollection?.songs.orEmpty())
    }
    var isLoading by remember(collectionId, isAlbum, offlineCollection) {
        mutableStateOf(offlineCollection?.songs.isNullOrEmpty())
    }
    var hasError by remember(collectionId, isAlbum) { mutableStateOf(false) }
    var retryNonce by remember(collectionId, isAlbum) { mutableIntStateOf(0) }

    LaunchedEffect(collectionId, isAlbum, retryNonce) {
        val hasOfflineSongs = !offlineCollection?.songs.isNullOrEmpty()
        if (!hasOfflineSongs) {
            isLoading = true
        }
        hasError = false
        try {
            val loaded = youTubeCatalog.getCollectionSongs(collectionId, isAlbum)
            if (loaded.isNotEmpty()) {
                songs = loaded
                hasError = false
            } else if (!hasOfflineSongs) {
                hasError = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (!hasOfflineSongs) {
                hasError = true
            }
        } finally {
            isLoading = false
        }
    }

    val resolvedTitle = initialTitle
        .ifBlank { offlineCollection?.title.orEmpty() }
        .ifBlank { if (isAlbum) commonCopy.albums else commonCopy.playlists }
    val resolvedSubtitle = initialSubtitle.ifBlank { offlineCollection?.subtitle.orEmpty() }
    val resolvedArtUri = remember(initialArtUri, offlineCollection, songs) {
        initialArtUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: offlineCollection?.thumbnailUri
            ?: songs.firstOrNull()?.artUri
    }
    val isCollectionDownloaded = remember(offlineCollection, songs, downloadedVideoIds) {
        if (offlineCollection != null) {
            true
        } else if (songs.isEmpty()) {
            false
        } else {
            val ids = songs.mapNotNull { YouTubeUri.videoId(it.uri) }
            ids.isNotEmpty() && ids.all { it in downloadedVideoIds }
        }
    }
    val isCollectionDownloading = remember(songs, activeDownloadVideoIds) {
        songs.any { YouTubeUri.videoId(it.uri) in activeDownloadVideoIds }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val paletteAccent by rememberArtworkPaletteAccent(resolvedArtUri)
    val headerAccent = (paletteAccent ?: MaterialTheme.colorScheme.primary)
        .copy(alpha = 0.68f)
        .compositeOver(bgColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        // Editorial dynamic color atmosphere extracted from the cover artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
        ) {
            if (resolvedArtUri != null) {
                ArtworkImage(
                    uri = resolvedArtUri,
                    title = resolvedTitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp)
                        .alpha(0.32f),
                    cornerRadius = 0.dp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to headerAccent.copy(alpha = 0.92f),
                            0.55f to headerAccent.copy(alpha = 0.45f),
                            0.85f to bgColor.copy(alpha = 0.88f),
                            1.0f to bgColor,
                        ),
                    ),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(listState),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = topBarOccupiedHeight() + 10.dp,
                bottom = bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "yt_collection_header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ArtworkImage(
                        uri = resolvedArtUri,
                        title = resolvedTitle,
                        modifier = Modifier.size(204.dp),
                        cornerRadius = 16.dp,
                        showArtworkGlow = true,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val cleanedSubtitle = remember(resolvedSubtitle, songs.size) {
                            if (songs.isNotEmpty()) {
                                resolvedSubtitle
                                    .replace(Regex("\\s*[•·]\\s*\\d+\\s+\\p{L}+\\s*$"), "")
                                    .trim()
                            } else {
                                resolvedSubtitle.trim()
                            }
                        }
                        val meta = buildList {
                            cleanedSubtitle.takeIf(String::isNotBlank)?.let(::add)
                            if (songs.isNotEmpty()) {
                                add(localizedCountLabel(songs.size, "song", language))
                            }
                        }.joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCollectionDownloaded) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(ElovaireRadii.pill),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lucide_check),
                                    contentDescription = dlCopy.downloadedAction,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = dlCopy.downloadedAction,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "yt_collection_actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Centered signature circular Play button
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = ytCopy.playAll
                                }
                                .elovaireClickable { onPlaySongs(songs.first(), songs, resolvedTitle) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_play),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(23.dp),
                            )
                        }

                        // Secondary actions side-by-side below the Play button: Shuffle, Radio, Download
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CollectionQuickAction(
                                label = shuffleLabel,
                                iconResId = R.drawable.ic_lucide_shuffle,
                                onClick = { onShuffleSongs(songs, resolvedTitle) },
                            )
                            CollectionQuickAction(
                                label = ytCopy.radioLabel,
                                iconResId = R.drawable.ic_lucide_audio_waveform,
                                onClick = {
                                    val seed = songs.first()
                                    val videoId = YouTubeUri.videoId(seed.uri) ?: return@CollectionQuickAction
                                    scope.launch {
                                        val related = runCatching {
                                            youTubeCatalog.getRelatedSongs(videoId)
                                        }.getOrDefault(emptyList())
                                        val queue = listOf(seed) + related.filterNot { it.id == seed.id }
                                        onPlaySongs(seed, queue, resolvedTitle)
                                    }
                                },
                            )
                            if (onDownloadCollection != null) {
                                CollectionQuickAction(
                                    label = when {
                                        isCollectionDownloaded -> dlCopy.downloadedAction
                                        isCollectionDownloading -> dlCopy.downloadingAction
                                        else -> dlCopy.downloadAction
                                    },
                                    iconResId = if (isCollectionDownloaded) {
                                        R.drawable.ic_lucide_check
                                    } else {
                                        R.drawable.ic_lucide_download
                                    },
                                    highlighted = isCollectionDownloaded,
                                    isLoading = isCollectionDownloading && !isCollectionDownloaded,
                                    onClick = {
                                        if (!isCollectionDownloaded) {
                                            onDownloadCollection(
                                                songs,
                                                YouTubeDownloadedCollection(
                                                    id = collectionId,
                                                    isAlbum = isAlbum,
                                                    title = resolvedTitle,
                                                    subtitle = resolvedSubtitle,
                                                    thumbnailUri = resolvedArtUri,
                                                    songs = songs,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    item(key = "yt_collection_loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = ytCopy.loadingCollection,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                            )
                        }
                    }
                }
                hasError && songs.isEmpty() -> {
                    item(key = "yt_collection_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = ytCopy.collectionLoadError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .elovaireClickable { retryNonce++ }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = ytCopy.retryLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                songs.isNotEmpty() -> {
                    itemsIndexed(
                        items = songs,
                        key = { index, song -> "${index}_${song.id}" },
                    ) { index, song ->
                        val trackDownloaded = isCollectionDownloaded ||
                            (YouTubeUri.videoId(song.uri) in downloadedVideoIds)
                        NumberedCollectionTrackRow(
                            index = index + 1,
                            song = song,
                            isFavorite = song.id in favoriteSongIds,
                            isDownloaded = trackDownloaded,
                            downloadedCd = dlCopy.downloadedAction,
                            onClick = { onPlaySongs(song, songs, resolvedTitle) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                        )
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun CollectionQuickAction(
    label: String,
    iconResId: Int,
    highlighted: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (highlighted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = label,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else readableSecondaryTextColor(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NumberedCollectionTrackRow(
    index: Int,
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    downloadedCd: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .elovaireClickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = readableSecondaryTextColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isDownloaded) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_check),
                        contentDescription = downloadedCd,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        InlineFavoriteSongButton(
            isFavorite = isFavorite,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onToggleFavorite,
        )
        SongOverflowMenuButton(
            song = song,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
