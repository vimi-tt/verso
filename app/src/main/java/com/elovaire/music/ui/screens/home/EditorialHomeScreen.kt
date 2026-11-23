package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import elovaire.music.droidbeauty.app.ui.theme.InkText

internal const val EDITORIAL_RESUME_LIMIT = 6
internal const val EDITORIAL_CAROUSEL_LIMIT = 10
internal const val EDITORIAL_TRENDING_LIMIT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorialHomeScreen(
    editorialState: EditorialHomeUiState,
    recentSongs: List<Song>,
    topPadding: Dp,
    bottomPadding: Dp,
    scrollToTopRequestVersion: Long,
    resetScrollOnColdStart: Boolean,
    onRefresh: () -> Unit,
    onPlayHero: (EditorialCollectionItem) -> Unit,
    onOpenCollection: (YouTubeHomeItem.Collection) -> Unit,
    onSelectRecentSong: (Song) -> Unit,
    onSelectTrendingTrack: (Song, List<Song>) -> Unit,
    onOpenSeeAll: (EditorialSectionId) -> Unit,
    onOpenExploreMood: (YouTubeHomeItem.Mood) -> Unit,
    onRetrySection: (EditorialSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberElovaireLazyListState("home_screen")
    val pullToRefreshState = rememberPullToRefreshState()
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val resumeItems = remember(recentSongs) {
        recentSongs.distinctBy { it.id }.take(EDITORIAL_RESUME_LIMIT)
    }

    LaunchedEffect(resetScrollOnColdStart) {
        if (resetScrollOnColdStart) {
            lazyListPositionCache["home_screen"] = 0 to 0
            listState.scrollToItem(0, 0)
            withFrameNanos { }
            listState.scrollToItem(0, 0)
        }
    }
    LaunchedEffect(scrollToTopRequestVersion) {
        if (scrollToTopRequestVersion > 0L &&
            listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset > 0
        ) {
            listState.animateScrollToItem(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = editorialState.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = editorialState.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding),
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
    LazyColumn(
        state = listState,
        overscrollEffect = null,
        modifier = Modifier
            .fillMaxSize()
            .ensureSingleItemRubberBand(listState),
        contentPadding = PaddingValues(
            top = topPadding + 12.dp,
            bottom = bottomPadding + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // 1. Destaque (Hero)
        item(
            key = "editorial_section_featured",
            contentType = "editorial_featured",
        ) {
            EditorialSectionBlock(
                title = copy.featuredTitle,
                subtitle = copy.featuredSubtitle,
                showSeeAll = editorialState.featured is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Featured) },
            ) {
                when (val section = editorialState.featured) {
                    EditorialSectionState.Loading -> EditorialHeroSkeleton(
                        modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                    )
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = copy.featuredEmpty,
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Featured) },
                    )
                    is EditorialSectionState.Content -> {
                        val hero = section.items.firstOrNull()
                        if (hero != null) {
                            EditorialHeroCard(
                                item = hero,
                                isPlayingLoading = editorialState.playingHeroId == hero.id,
                                onOpen = { onOpenCollection(hero.collection) },
                                onPlay = { onPlayHero(hero) },
                                modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            )
                        } else {
                            EditorialSectionEmptyState(
                                message = copy.featuredEmpty,
                            )
                        }
                    }
                }
            }
        }

        // 2. Para retomar (Histórico local, máx. 6 itens)
        item(
            key = "editorial_section_resume",
            contentType = "editorial_resume",
        ) {
            EditorialSectionBlock(
                title = copy.resumeTitle,
                subtitle = copy.resumeSubtitle,
                showSeeAll = resumeItems.isNotEmpty(),
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Resume) },
            ) {
                if (resumeItems.isEmpty()) {
                    EditorialSectionEmptyState(
                        message = copy.resumeEmpty,
                    )
                } else {
                    EditorialResumeRow(
                        songs = resumeItems,
                        onSongClick = onSelectRecentSong,
                    )
                }
            }
        }

        // 3. Lançamentos (máx. 10 itens)
        item(
            key = "editorial_section_new_releases",
            contentType = "editorial_new_releases",
        ) {
            EditorialSectionBlock(
                title = copy.newReleasesTitle,
                subtitle = copy.newReleasesSubtitle,
                showSeeAll = editorialState.newReleases is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.NewReleases) },
                categoryAccent = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.categories.discover,
            ) {
                when (val section = editorialState.newReleases) {
                    EditorialSectionState.Loading -> EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = copy.newReleasesEmpty,
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.NewReleases) },
                    )
                    is EditorialSectionState.Content -> {
                        val limited = remember(section.items) {
                            section.items.take(EDITORIAL_CAROUSEL_LIMIT)
                        }
                        EditorialCollectionCarousel(
                            items = limited,
                            keyPrefix = "new_release",
                            onCollectionClick = onOpenCollection,
                        )
                    }
                }
            }
        }

        // 4. Essenciais (máx. 10 itens)
        item(
            key = "editorial_section_essentials",
            contentType = "editorial_essentials",
        ) {
            EditorialSectionBlock(
                title = copy.essentialsTitle,
                subtitle = copy.essentialsSubtitle,
                showSeeAll = editorialState.essentials is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Essentials) },
                categoryAccent = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.categories.favorites,
            ) {
                when (val section = editorialState.essentials) {
                    EditorialSectionState.Loading -> EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = copy.essentialsEmpty,
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Essentials) },
                    )
                    is EditorialSectionState.Content -> {
                        val limited = remember(section.items) {
                            section.items.take(EDITORIAL_CAROUSEL_LIMIT)
                        }
                        EditorialCollectionCarousel(
                            items = limited,
                            keyPrefix = "essential",
                            onCollectionClick = onOpenCollection,
                        )
                    }
                }
            }
        }

        // 5. Em alta (Parada numerada, máx. 10 itens)
        item(
            key = "editorial_section_trending",
            contentType = "editorial_trending",
        ) {
            EditorialSectionBlock(
                title = copy.trendingTitle,
                subtitle = copy.trendingSubtitle,
                showSeeAll = editorialState.trending is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Trending) },
                categoryAccent = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.categories.discover,
            ) {
                when (val section = editorialState.trending) {
                    EditorialSectionState.Loading -> EditorialTrendingSkeleton()
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = copy.trendingEmpty,
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Trending) },
                    )
                    is EditorialSectionState.Content -> {
                        val limited = remember(section.items) {
                            section.items.take(EDITORIAL_TRENDING_LIMIT)
                        }
                        EditorialTrendingCarousel(
                            tracks = limited,
                            onTrackClick = onSelectTrendingTrack,
                        )
                    }
                }
            }
        }

        // 6. Explorar por clima (Chips de gênero e mood)
        item(
            key = "editorial_section_moods",
            contentType = "editorial_moods",
        ) {
            EditorialSectionBlock(
                title = copy.moodsTitle,
                subtitle = copy.moodsSubtitle,
                showSeeAll = editorialState.moods is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Moods) },
                categoryAccent = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.categories.discover,
            ) {
                when (val section = editorialState.moods) {
                    EditorialSectionState.Loading -> EditorialMoodsSkeleton()
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = copy.moodsEmpty,
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Moods) },
                    )
                    is EditorialSectionState.Content -> {
                        val limited = remember(section.items) { section.items.take(12) }
                        EditorialMoodsFlow(
                            moods = limited,
                            onMoodClick = onOpenExploreMood,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun EditorialHeroCard(
    item: EditorialCollectionItem,
    isPlayingLoading: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val openCd = copy.featuredCd(item.title)
    val playLabel = if (isPlayingLoading) copy.loadingPlay else copy.play
    val playCd = copy.playCd(item.title)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.06f)
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .semantics {
                role = Role.Button
                contentDescription = openCd
            }
            .elovaireClickable(onClick = onOpen),
    ) {
        ArtworkImage(
            uri = item.artUri,
            title = item.title,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = ElovaireRadii.card,
            requestedSizePx = 640,
            showArtworkGlow = false,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.38f to Color.Black.copy(alpha = 0.56f),
                        1.0f to Color.Black.copy(alpha = 0.88f),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .background(Color.White)
                        .semantics {
                            role = Role.Button
                            contentDescription = playCd
                        }
                        .elovaireClickable(enabled = !isPlayingLoading, onClick = onPlay)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPlayingLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = InkText,
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_play),
                            contentDescription = null,
                            tint = InkText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = playLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = InkText,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditorialResumeRow(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = songs,
            key = { song -> "resume_${song.id}" },
            contentType = { "editorial_resume_card" },
        ) { song ->
            EditorialResumeCard(
                song = song,
                onClick = { onSongClick(song) },
            )
        }
    }
}

@Composable
private fun EditorialResumeCard(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(ElovaireRadii.artwork))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
    ) {
        ArtworkImage(
            uri = song.artUri,
            title = song.title,
            modifier = Modifier.size(160.dp),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 360,
            showArtworkGlow = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist.ifBlank { " " },
                style = MaterialTheme.typography.labelLarge,
                color = readableSecondaryTextColor(),
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EditorialCollectionCarousel(
    items: List<EditorialCollectionItem>,
    keyPrefix: String,
    onCollectionClick: (YouTubeHomeItem.Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
    ) {
        items(
            items = items,
            key = { item -> "${keyPrefix}_${item.id}" },
            contentType = { "editorial_collection_card" },
        ) { item ->
            EditorialCollectionCard(
                item = item,
                cardWidth = 176.dp,
                onClick = { onCollectionClick(item.collection) },
            )
        }
    }
}

@Composable
internal fun EditorialCollectionCard(
    item: EditorialCollectionItem,
    cardWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(ElovaireRadii.artwork))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkImage(
            uri = item.artUri,
            title = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 360,
            showArtworkGlow = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle.ifBlank { " " },
                style = MaterialTheme.typography.labelLarge,
                color = readableSecondaryTextColor(),
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EditorialTrendingCarousel(
    tracks: List<EditorialChartTrack>,
    onTrackClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = remember(tracks) { tracks.map { it.song } }
    val columns = remember(tracks) { tracks.chunked(2) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(
            items = columns,
            key = { col -> "trending_col_${col.firstOrNull()?.rank ?: 0}" },
            contentType = { "editorial_trending_column" },
        ) { columnTracks ->
            Column(
                modifier = Modifier.width(292.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                columnTracks.forEach { entry ->
                    EditorialTrendingRow(
                        entry = entry,
                        onClick = { onTrackClick(entry.song, queue) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditorialTrendingRow(
    entry: EditorialChartTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val rankCd = copy.trendingRankCd(entry.rank, entry.song.title, entry.song.artist)
    val rankLabel = remember(entry.rank) { entry.rank.toString().padStart(2, '0') }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(ElovaireRadii.artwork))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = rankCd
            }
            .elovaireClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rankLabel,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = readableSecondaryTextColor(),
            modifier = Modifier.width(30.dp),
        )
        ArtworkImage(
            uri = entry.song.artUri,
            title = entry.song.title,
            modifier = Modifier.size(56.dp),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 160,
            showArtworkGlow = false,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.song.artist.isNotBlank()) {
                Text(
                    text = entry.song.artist,
                    style = MaterialTheme.typography.labelLarge,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorialMoodsFlow(
    moods: List<EditorialMoodItem>,
    onMoodClick: (YouTubeHomeItem.Mood) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        moods.forEach { item ->
            EditorialMoodChip(
                title = item.title,
                selected = false,
                onClick = { onMoodClick(item.mood) },
            )
        }
    }
}

@Composable
internal fun EditorialMoodChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val chipCd = copy.moodsChipCd(title)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(ElovaireRadii.pill),
                    )
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = chipCd
            }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EditorialSectionBlock(
    title: String,
    subtitle: String,
    showSeeAll: Boolean,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
    ) {
        EditorialSectionHeader(
            title = title,
            subtitle = subtitle,
            showSeeAll = showSeeAll,
            onSeeAllClick = onSeeAllClick,
            categoryAccent = categoryAccent,
            modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
        )
        content()
    }
}

@Composable
internal fun EditorialSectionHeader(
    title: String,
    subtitle: String,
    showSeeAll: Boolean,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryAccent: Color? = null,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val seeAllLabel = copy.seeAll
    val seeAllCd = copy.seeAllCd(title)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = ElovaireSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (categoryAccent != null) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(categoryAccent),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showSeeAll) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .semantics {
                            role = Role.Button
                            contentDescription = seeAllCd
                        }
                        .elovaireClickable(onClick = onSeeAllClick)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = seeAllLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = readableSecondaryTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EditorialSectionEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = readableSecondaryTextColor(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding, vertical = 8.dp),
    )
}

@Composable
internal fun EditorialSectionErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { editorialHomeCopy(language) }
    val retryLabel = copy.retry
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = copy.sectionError,
            style = MaterialTheme.typography.bodyLarge,
            color = readableSecondaryTextColor(),
        )
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(ElovaireRadii.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                )
                .semantics {
                    role = Role.Button
                    contentDescription = retryLabel
                }
                .elovaireClickable(onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = retryLabel,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "EditorialSkeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EditorialSkeletonAlpha",
    )
    return alpha
}

@Composable
private fun EditorialHeroSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.06f)
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .background(tint),
    )
}

@Composable
private fun EditorialCarouselSkeleton(
    cardSize: Dp,
    count: Int,
) {
    val alpha = rememberSkeletonAlpha()
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    LazyRow(
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            count = count,
            key = { idx -> "skeleton_card_$idx" },
            contentType = { "editorial_skeleton_card" },
        ) {
            Column(
                modifier = Modifier.width(cardSize),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(cardSize)
                        .clip(RoundedCornerShape(ElovaireRadii.artwork))
                        .background(tint),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint.copy(alpha = alpha * 0.7f)),
                )
            }
        }
    }
}

@Composable
private fun EditorialTrendingSkeleton() {
    val alpha = rememberSkeletonAlpha()
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint),
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.artwork))
                        .background(tint),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint.copy(alpha = alpha * 0.7f)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorialMoodsSkeleton() {
    val alpha = rememberSkeletonAlpha()
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(92.dp, 118.dp, 84.dp, 108.dp, 96.dp, 124.dp).forEach { w ->
            Box(
                modifier = Modifier
                    .width(w)
                    .height(48.dp)
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(tint),
            )
        }
    }
}
