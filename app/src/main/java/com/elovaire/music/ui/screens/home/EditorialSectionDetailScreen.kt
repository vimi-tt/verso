package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.editorialHomeCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

@Composable
internal fun EditorialSectionDetailScreen(
    sectionId: EditorialSectionId,
    editorialState: EditorialHomeUiState,
    recentSongs: List<Song>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenCollection: (YouTubeHomeItem.Collection) -> Unit,
    onSelectRecentSong: (Song) -> Unit,
    onSelectTrendingTrack: (Song, List<Song>) -> Unit,
    onOpenExploreMood: (YouTubeHomeItem.Mood) -> Unit,
    onRetrySection: (EditorialSectionId) -> Unit,
) {
    val listState = rememberLazyListState()
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val title = when (sectionId) {
        EditorialSectionId.Featured -> copy.featuredTitle
        EditorialSectionId.Resume -> copy.resumeTitle
        EditorialSectionId.NewReleases -> copy.newReleasesTitle
        EditorialSectionId.Essentials -> copy.essentialsTitle
        EditorialSectionId.Trending -> copy.trendingTitle
        EditorialSectionId.Moods -> copy.moodsTitle
    }
    val subtitle = when (sectionId) {
        EditorialSectionId.Featured -> copy.featuredSubtitle
        EditorialSectionId.Resume -> copy.resumeSubtitle
        EditorialSectionId.NewReleases -> copy.newReleasesSubtitle
        EditorialSectionId.Essentials -> copy.essentialsSubtitle
        EditorialSectionId.Trending -> copy.trendingSubtitle
        EditorialSectionId.Moods -> copy.moodsSubtitle
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(listState),
            contentPadding = PaddingValues(
                top = topBarOccupiedHeight() + 18.dp,
                bottom = bottomPadding + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "detail_subtitle", contentType = "detail_subtitle") {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = readableSecondaryTextColor(),
                    modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                )
            }

            when (sectionId) {
                EditorialSectionId.Resume -> {
                    if (recentSongs.isEmpty()) {
                        item(key = "resume_empty", contentType = "empty") {
                            EditorialSectionEmptyState(message = copy.resumeEmpty)
                        }
                    } else {
                        items(
                            items = recentSongs,
                            key = { song -> "detail_resume_${song.id}" },
                            contentType = { "detail_song_row" },
                        ) { song ->
                            EditorialSongListRow(
                                song = song,
                                onClick = { onSelectRecentSong(song) },
                                modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            )
                        }
                    }
                }

                EditorialSectionId.Featured,
                EditorialSectionId.NewReleases,
                EditorialSectionId.Essentials,
                -> {
                    val state = when (sectionId) {
                        EditorialSectionId.Featured -> editorialState.featured
                        EditorialSectionId.NewReleases -> editorialState.newReleases
                        else -> editorialState.essentials
                    }
                    val emptyMsg = when (sectionId) {
                        EditorialSectionId.Featured -> copy.featuredEmpty
                        EditorialSectionId.NewReleases -> copy.newReleasesEmpty
                        else -> copy.essentialsEmpty
                    }
                    when (state) {
                        EditorialSectionState.Loading -> {
                            item(key = "col_loading", contentType = "loading") {
                                EditorialSectionEmptyState(message = copy.loadingPlay)
                            }
                        }
                        EditorialSectionState.Empty -> {
                            item(key = "col_empty", contentType = "empty") {
                                EditorialSectionEmptyState(message = emptyMsg)
                            }
                        }
                        EditorialSectionState.Error -> {
                            item(key = "col_error", contentType = "error") {
                                EditorialSectionErrorState(onRetry = { onRetrySection(sectionId) })
                            }
                        }
                        is EditorialSectionState.Content -> {
                            val rows = state.items.chunked(2)
                            items(
                                items = rows,
                                key = { row -> "detail_grid_${row.firstOrNull()?.id.orEmpty()}" },
                                contentType = { "detail_collection_grid_row" },
                            ) { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    pair.forEach { item ->
                                        EditorialCollectionCard(
                                            item = item,
                                            cardWidth = 164.dp,
                                            onClick = { onOpenCollection(item.collection) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                EditorialSectionId.Trending -> {
                    when (val state = editorialState.trending) {
                        EditorialSectionState.Loading -> {
                            item(key = "trend_loading", contentType = "loading") {
                                EditorialSectionEmptyState(message = copy.loadingPlay)
                            }
                        }
                        EditorialSectionState.Empty -> {
                            item(key = "trend_empty", contentType = "empty") {
                                EditorialSectionEmptyState(message = copy.trendingEmpty)
                            }
                        }
                        EditorialSectionState.Error -> {
                            item(key = "trend_error", contentType = "error") {
                                EditorialSectionErrorState(onRetry = { onRetrySection(sectionId) })
                            }
                        }
                        is EditorialSectionState.Content -> {
                            val queue = state.items.map { it.song }
                            items(
                                items = state.items,
                                key = { entry -> "detail_trend_${entry.rank}_${entry.song.id}" },
                                contentType = { "detail_trending_row" },
                            ) { entry ->
                                EditorialTrendingRow(
                                    entry = entry,
                                    onClick = { onSelectTrendingTrack(entry.song, queue) },
                                    modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                )
                            }
                        }
                    }
                }

                EditorialSectionId.Moods -> {
                    when (val state = editorialState.moods) {
                        EditorialSectionState.Loading -> {
                            item(key = "moods_loading", contentType = "loading") {
                                EditorialSectionEmptyState(message = copy.loadingPlay)
                            }
                        }
                        EditorialSectionState.Empty -> {
                            item(key = "moods_empty", contentType = "empty") {
                                EditorialSectionEmptyState(message = copy.moodsEmpty)
                            }
                        }
                        EditorialSectionState.Error -> {
                            item(key = "moods_error", contentType = "error") {
                                EditorialSectionErrorState(onRetry = { onRetrySection(sectionId) })
                            }
                        }
                        is EditorialSectionState.Content -> {
                            item(key = "detail_moods_flow", contentType = "detail_moods_flow") {
                                EditorialMoodsFlow(
                                    moods = state.items,
                                    onMoodClick = onOpenExploreMood,
                                )
                            }
                        }
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = title,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun EditorialSongListRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(ElovaireRadii.artwork))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = song.artUri,
            title = song.title,
            modifier = Modifier.size(52.dp),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 160,
            showArtworkGlow = false,
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
            if (song.artist.isNotBlank()) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.labelLarge,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
