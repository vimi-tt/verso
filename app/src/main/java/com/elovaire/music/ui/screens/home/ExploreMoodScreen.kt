package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeShelf
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing
import kotlinx.coroutines.CancellationException

@Composable
internal fun ExploreMoodScreen(
    initialBrowseId: String,
    initialParams: String?,
    initialTitle: String,
    availableMoods: List<EditorialMoodItem>,
    youTubeCatalog: YouTubeCatalogRepository,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenCollection: (YouTubeHomeItem.Collection) -> Unit,
    onSelectTrack: (Song, List<Song>) -> Unit,
) {
    val listState = rememberLazyListState()
    var activeBrowseId by remember(initialBrowseId) { mutableStateOf(initialBrowseId) }
    var activeParams by remember(initialParams) { mutableStateOf(initialParams) }
    var activeTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var feedState by remember {
        mutableStateOf<EditorialSectionState<YouTubeHomeShelf>>(EditorialSectionState.Loading)
    }

    LaunchedEffect(activeBrowseId, activeParams, reloadNonce) {
        if (activeBrowseId.isBlank()) {
            feedState = EditorialSectionState.Empty
            return@LaunchedEffect
        }
        feedState = EditorialSectionState.Loading
        try {
            val feed = youTubeCatalog.getHomeFeed(browseId = activeBrowseId, params = activeParams)
            val shelves = feed.shelves.filterNot { shelf ->
                shelf.items.all { it is YouTubeHomeItem.Mood }
            }
            feedState = if (shelves.isEmpty()) {
                EditorialSectionState.Empty
            } else {
                EditorialSectionState.Content(shelves)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            feedState = EditorialSectionState.Error
        }
    }

    val topTitle = activeTitle.ifBlank { stringResource(R.string.editorial_explore_title) }

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
                top = topBarOccupiedHeight() + 16.dp,
                bottom = bottomPadding + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (availableMoods.isNotEmpty()) {
                item(key = "explore_mood_chips", contentType = "explore_mood_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = availableMoods,
                            key = { it.key },
                            contentType = { "explore_mood_chip" },
                        ) { moodItem ->
                            val isSelected = moodItem.mood.browseId == activeBrowseId &&
                                moodItem.mood.params == activeParams
                            EditorialMoodChip(
                                title = moodItem.title,
                                selected = isSelected,
                                onClick = {
                                    activeBrowseId = moodItem.mood.browseId
                                    activeParams = moodItem.mood.params
                                    activeTitle = moodItem.title
                                },
                            )
                        }
                    }
                }
            }

            when (val state = feedState) {
                EditorialSectionState.Loading -> {
                    item(key = "explore_loading", contentType = "loading") {
                        EditorialSectionEmptyState(
                            message = stringResource(R.string.editorial_home_loading_play),
                        )
                    }
                }
                EditorialSectionState.Empty -> {
                    item(key = "explore_empty", contentType = "empty") {
                        EditorialSectionEmptyState(
                            message = stringResource(R.string.editorial_explore_empty),
                        )
                    }
                }
                EditorialSectionState.Error -> {
                    item(key = "explore_error", contentType = "error") {
                        EditorialSectionErrorState(onRetry = { reloadNonce += 1 })
                    }
                }
                is EditorialSectionState.Content -> {
                    state.items.forEachIndexed { index, shelf ->
                        item(
                            key = "explore_shelf_${index}_${shelf.title}",
                            contentType = "explore_shelf",
                        ) {
                            ExploreShelfBlock(
                                shelf = shelf,
                                onOpenCollection = onOpenCollection,
                                onSelectTrack = onSelectTrack,
                            )
                        }
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = topTitle,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ExploreShelfBlock(
    shelf: YouTubeHomeShelf,
    onOpenCollection: (YouTubeHomeItem.Collection) -> Unit,
    onSelectTrack: (Song, List<Song>) -> Unit,
) {
    val collections = remember(shelf.items) {
        shelf.items.filterIsInstance<YouTubeHomeItem.Collection>().map { col ->
            EditorialCollectionItem(
                id = col.summary.id,
                title = col.summary.title,
                subtitle = col.summary.subtitle.ifBlank { col.summary.releaseYear?.toString().orEmpty() },
                artUri = col.summary.thumbnailUri,
                isAlbum = col.isAlbum,
                collection = col,
            )
        }
    }
    val tracks = remember(shelf.items) {
        shelf.items.filterIsInstance<YouTubeHomeItem.Track>().map { it.song }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (shelf.title.isNotBlank()) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
            )
        }
        if (collections.isNotEmpty()) {
            EditorialCollectionCarousel(
                items = collections,
                keyPrefix = "explore_${shelf.title}",
                onCollectionClick = onOpenCollection,
            )
        } else if (tracks.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tracks.take(10).forEach { song ->
                    EditorialSongListRow(
                        song = song,
                        onClick = { onSelectTrack(song, tracks) },
                    )
                }
            }
        }
    }
}
