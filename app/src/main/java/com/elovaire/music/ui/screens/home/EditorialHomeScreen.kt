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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

internal const val EDITORIAL_RESUME_LIMIT = 6
internal const val EDITORIAL_CAROUSEL_LIMIT = 10
internal const val EDITORIAL_TRENDING_LIMIT = 10

@Composable
internal fun EditorialHomeScreen(
    editorialState: EditorialHomeUiState,
    recentSongs: List<Song>,
    topPadding: Dp,
    bottomPadding: Dp,
    scrollToTopRequestVersion: Long,
    resetScrollOnColdStart: Boolean,
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

    LazyColumn(
        state = listState,
        overscrollEffect = null,
        modifier = modifier
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
                title = stringResource(R.string.editorial_section_featured_title),
                subtitle = stringResource(R.string.editorial_section_featured_subtitle),
                showSeeAll = editorialState.featured is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Featured) },
            ) {
                when (val section = editorialState.featured) {
                    EditorialSectionState.Loading -> EditorialHeroSkeleton(
                        modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                    )
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_featured_empty),
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Featured) },
                    )
                    is EditorialSectionState.Content -> {
                        // Preenchido na Etapa 2
                        EditorialHeroSkeleton(
                            modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        )
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
                title = stringResource(R.string.editorial_section_resume_title),
                subtitle = stringResource(R.string.editorial_section_resume_subtitle),
                showSeeAll = recentSongs.isNotEmpty(),
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Resume) },
            ) {
                if (recentSongs.isEmpty()) {
                    EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_resume_empty),
                    )
                } else {
                    // Preenchido na Etapa 2
                    EditorialCarouselSkeleton(cardSize = 144.dp, count = 4)
                }
            }
        }

        // 3. Lançamentos (máx. 10 itens)
        item(
            key = "editorial_section_new_releases",
            contentType = "editorial_new_releases",
        ) {
            EditorialSectionBlock(
                title = stringResource(R.string.editorial_section_new_releases_title),
                subtitle = stringResource(R.string.editorial_section_new_releases_subtitle),
                showSeeAll = editorialState.newReleases is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.NewReleases) },
            ) {
                when (editorialState.newReleases) {
                    EditorialSectionState.Loading -> EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_new_releases_empty),
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.NewReleases) },
                    )
                    is EditorialSectionState.Content -> {
                        // Preenchido na Etapa 3
                        EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
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
                title = stringResource(R.string.editorial_section_essentials_title),
                subtitle = stringResource(R.string.editorial_section_essentials_subtitle),
                showSeeAll = editorialState.essentials is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Essentials) },
            ) {
                when (editorialState.essentials) {
                    EditorialSectionState.Loading -> EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_essentials_empty),
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Essentials) },
                    )
                    is EditorialSectionState.Content -> {
                        // Preenchido na Etapa 3
                        EditorialCarouselSkeleton(cardSize = 164.dp, count = 4)
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
                title = stringResource(R.string.editorial_section_trending_title),
                subtitle = stringResource(R.string.editorial_section_trending_subtitle),
                showSeeAll = editorialState.trending is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Trending) },
            ) {
                when (editorialState.trending) {
                    EditorialSectionState.Loading -> EditorialTrendingSkeleton()
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_trending_empty),
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Trending) },
                    )
                    is EditorialSectionState.Content -> {
                        // Preenchido na Etapa 3
                        EditorialTrendingSkeleton()
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
                title = stringResource(R.string.editorial_section_moods_title),
                subtitle = stringResource(R.string.editorial_section_moods_subtitle),
                showSeeAll = editorialState.moods is EditorialSectionState.Content,
                onSeeAllClick = { onOpenSeeAll(EditorialSectionId.Moods) },
            ) {
                when (editorialState.moods) {
                    EditorialSectionState.Loading -> EditorialMoodsSkeleton()
                    EditorialSectionState.Empty -> EditorialSectionEmptyState(
                        message = stringResource(R.string.editorial_section_moods_empty),
                    )
                    EditorialSectionState.Error -> EditorialSectionErrorState(
                        onRetry = { onRetrySection(EditorialSectionId.Moods) },
                    )
                    is EditorialSectionState.Content -> {
                        // Preenchido na Etapa 4
                        EditorialMoodsSkeleton()
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorialSectionBlock(
    title: String,
    subtitle: String,
    showSeeAll: Boolean,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditorialSectionHeader(
            title = title,
            subtitle = subtitle,
            showSeeAll = showSeeAll,
            onSeeAllClick = onSeeAllClick,
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
) {
    val seeAllLabel = stringResource(R.string.editorial_home_see_all)
    val seeAllCd = stringResource(R.string.editorial_home_see_all_cd, title)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
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
    val retryLabel = stringResource(R.string.editorial_home_retry)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.editorial_home_section_error),
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
