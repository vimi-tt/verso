package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeShelf
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii

@Composable
internal fun YouTubeMoodChipsRow(
    moods: List<YouTubeHomeItem.Mood>,
    selectedMoodKey: String?,
    onSelectMood: (YouTubeHomeItem.Mood?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (moods.isEmpty()) return
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeCopy(language) }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeChipButton(
            selected = selectedMoodKey == null,
            contentDescription = copy.allFilter,
            onClick = { onSelectMood(null) },
        )
        moods.forEach { mood ->
            MoodFilterPill(
                label = mood.title,
                selected = selectedMoodKey == mood.key,
                onClick = { onSelectMood(mood) },
            )
        }
    }
}

@Composable
private fun HomeChipButton(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape)
                },
            )
            .elovaireClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_house),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MoodFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        RoundedCornerShape(ElovaireRadii.pill),
                    )
                },
            )
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
internal fun YouTubeSpeedDialSection(
    tracks: List<Song>,
    onTrackClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeCopy(language) }
    val pages = remember(tracks) { tracks.chunked(9) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = copy.speedDialTitle,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { pageIndex -> pages[pageIndex].firstOrNull()?.id ?: pageIndex },
            modifier = Modifier.fillMaxWidth(),
        ) { pageIndex ->
            val pageItems = pages[pageIndex]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 3) {
                    val rowStart = row * 3
                    if (rowStart < pageItems.size) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (col in 0 until 3) {
                                val index = rowStart + col
                                if (index < pageItems.size) {
                                    val song = pageItems[index]
                                    SpeedDialTile(
                                        song = song,
                                        onClick = { onTrackClick(song, tracks) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(ElovaireRadii.artwork))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(ElovaireRadii.artwork),
                )
                .elovaireClickable(onClick = onClick),
        ) {
        ArtworkImage(
            uri = song.artUri,
            title = song.title,
            modifier = Modifier.matchParentSize(),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 256,
            showArtworkGlow = false,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun YouTubeHomeShelfSection(
    shelf: YouTubeHomeShelf,
    favoriteSongIds: Set<Long>,
    onTrackClick: (Song, List<Song>) -> Unit,
    onCollectionClick: (YouTubeHomeItem.Collection) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeCopy(language) }
    val tracks = remember(shelf.items) {
        shelf.items.filterIsInstance<YouTubeHomeItem.Track>().map { it.song }
    }
    val nonTracks = remember(shelf.items) {
        shelf.items.filterNot { it is YouTubeHomeItem.Track || it is YouTubeHomeItem.Mood }
    }
    val isTrackShelf = tracks.size >= nonTracks.size && tracks.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (shelf.title.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                if (isTrackShelf) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(ElovaireRadii.pill),
                            )
                            .elovaireClickable(onClick = { onTrackClick(tracks.first(), tracks) })
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = copy.playAll,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (isTrackShelf) {
            val columns = remember(tracks) { tracks.chunked(4) }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    items = columns,
                    key = { columnTracks -> columnTracks.firstOrNull()?.id ?: 0L },
                    contentType = { "yt_track_column" },
                ) { columnTracks ->
                    Column(modifier = Modifier.width(296.dp)) {
                        columnTracks.forEachIndexed { index, song ->
                            HomeRecentSongRow(
                                song = song,
                                isFavorite = song.id in favoriteSongIds,
                                onClick = { onTrackClick(song, tracks) },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                showDivider = index != columnTracks.lastIndex,
                            )
                        }
                    }
                }
            }
        } else if (nonTracks.isNotEmpty()) {
            val useTwoRowEditorialGrid = nonTracks.size >= 6
            if (useTwoRowEditorialGrid) {
                val columns = remember(nonTracks) { nonTracks.chunked(2) }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = columns,
                        key = { col -> col.firstOrNull()?.key ?: "empty" },
                        contentType = { "yt_shelf_card_column" },
                    ) { columnItems ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            columnItems.forEach { item ->
                                YouTubeShelfNonTrackItem(
                                    item = item,
                                    onCollectionClick = onCollectionClick,
                                    onArtistClick = onArtistClick,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = nonTracks,
                        key = { item -> item.key },
                        contentType = { "yt_shelf_card" },
                    ) { item ->
                        YouTubeShelfNonTrackItem(
                            item = item,
                            onCollectionClick = onCollectionClick,
                            onArtistClick = onArtistClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeShelfNonTrackItem(
    item: YouTubeHomeItem,
    onCollectionClick: (YouTubeHomeItem.Collection) -> Unit,
    onArtistClick: (String) -> Unit,
) {
    when (item) {
        is YouTubeHomeItem.Collection -> {
            YouTubeShelfCard(
                artUri = item.summary.thumbnailUri,
                title = item.summary.title,
                subtitle = item.summary.subtitle,
                onClick = { onCollectionClick(item) },
            )
        }
        is YouTubeHomeItem.Artist -> {
            YouTubeShelfCard(
                artUri = item.summary.thumbnailUri,
                title = item.summary.name,
                subtitle = item.summary.subscriberCount.orEmpty(),
                onClick = { onArtistClick(item.summary.id) },
            )
        }
        is YouTubeHomeItem.Track,
        is YouTubeHomeItem.Mood,
        -> Unit
    }
}

@Composable
private fun YouTubeShelfCard(
    artUri: android.net.Uri?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .elovaireClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(ElovaireRadii.artwork))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(ElovaireRadii.artwork),
                ),
        ) {
            ArtworkImage(
                uri = artUri,
                title = title,
                modifier = Modifier.matchParentSize(),
                cornerRadius = ElovaireRadii.artwork,
                requestedSizePx = 256,
                showArtworkGlow = false,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun YouTubeHomeStatusBanner(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeCopy(language) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = copy.loadingHome,
                style = MaterialTheme.typography.bodyMedium,
                color = readableSecondaryTextColor(),
            )
        } else if (hasError) {
            Text(
                text = copy.homeLoadError,
                style = MaterialTheme.typography.bodyMedium,
                color = readableSecondaryTextColor(),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .elovaireClickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = copy.retryLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun YouTubeHomeLoadMorePill(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeCopy(language) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .elovaireClickable(onClick = onLoadMore)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = copy.loadMore,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
