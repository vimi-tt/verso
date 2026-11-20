package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeAccountUiState
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.youtubeAccountCopy
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

private enum class FavoritesSourceFilter {
    All,
    YouTube,
    InApp,
}

private enum class FavoritesSortMode {
    Recent,
    Alphabet,
    Artist,
    Album,
}

@Composable
internal fun YouTubeFavoritesScreen(
    inAppFavoriteSongs: List<Song>,
    accountState: YouTubeAccountUiState,
    @Suppress("UNUSED_PARAMETER")
    topPadding: Dp,
    bottomPadding: Dp,
    onBack: () -> Unit = {},
    onRefreshAccount: () -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onSongMoreActions: (Song) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeAccountCopy(language) }
    val listState = rememberElovaireLazyListState("yt_favorites_screen")

    var selectedSource by rememberSaveable { mutableStateOf(FavoritesSourceFilter.All) }
    var sortMode by rememberSaveable { mutableStateOf(FavoritesSortMode.Recent) }

    LaunchedEffect(accountState.isLoggedIn) {
        if (accountState.isLoggedIn &&
            accountState.likedSongs.isEmpty() &&
            !accountState.isSyncing
        ) {
            onRefreshAccount()
        }
    }

    val combinedFavoriteSongs = remember(
        inAppFavoriteSongs,
        accountState.likedSongs,
        selectedSource,
        sortMode,
    ) {
        val base = when (selectedSource) {
            FavoritesSourceFilter.YouTube -> accountState.likedSongs
            FavoritesSourceFilter.InApp -> inAppFavoriteSongs
            FavoritesSourceFilter.All -> {
                val merged = LinkedHashMap<Long, Song>()
                inAppFavoriteSongs.forEach { merged[it.id] = it }
                accountState.likedSongs.forEach { song ->
                    if (!merged.containsKey(song.id)) {
                        merged[song.id] = song
                    }
                }
                merged.values.toList()
            }
        }
        when (sortMode) {
            FavoritesSortMode.Recent -> base
            FavoritesSortMode.Alphabet -> base.sortedBy { it.title.trim().lowercase() }
            FavoritesSortMode.Artist -> base.sortedWith(
                compareBy<Song> { it.artist.trim().lowercase() }.thenBy { it.title.trim().lowercase() },
            )
            FavoritesSortMode.Album -> base.sortedWith(
                compareBy<Song> { it.album.trim().lowercase() }.thenBy { it.title.trim().lowercase() },
            )
        }
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
                top = topBarOccupiedHeight() + 14.dp,
                bottom = bottomPadding + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "fav_header", contentType = "fav_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.favorites_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = readableSecondaryTextColor(),
                    )
                    if (combinedFavoriteSongs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EditorialActionPill(
                                label = stringResource(R.string.editorial_home_play),
                                iconResId = R.drawable.ic_lucide_play,
                                primary = true,
                                onClick = {
                                    val first = combinedFavoriteSongs.firstOrNull() ?: return@EditorialActionPill
                                    onSongSelected(first, combinedFavoriteSongs)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            EditorialActionPill(
                                label = stringResource(R.string.downloads_shuffle),
                                iconResId = R.drawable.ic_lucide_shuffle,
                                primary = false,
                                onClick = {
                                    val shuffled = combinedFavoriteSongs.shuffled()
                                    val first = shuffled.firstOrNull() ?: return@EditorialActionPill
                                    onSongSelected(first, shuffled)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item(key = "fav_filters", contentType = "fav_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "src_all") {
                            EditorialFilterPill(
                                label = copy.filterAll,
                                selected = selectedSource == FavoritesSourceFilter.All,
                                onClick = { selectedSource = FavoritesSourceFilter.All },
                            )
                        }
                        item(key = "src_yt") {
                            EditorialFilterPill(
                                label = copy.filterYouTube,
                                selected = selectedSource == FavoritesSourceFilter.YouTube,
                                onClick = { selectedSource = FavoritesSourceFilter.YouTube },
                            )
                        }
                        item(key = "src_app") {
                            EditorialFilterPill(
                                label = copy.filterLikedInApp,
                                selected = selectedSource == FavoritesSourceFilter.InApp,
                                onClick = { selectedSource = FavoritesSourceFilter.InApp },
                            )
                        }
                    }

                    if (combinedFavoriteSongs.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "sort_recent") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_recent),
                                    selected = sortMode == FavoritesSortMode.Recent,
                                    onClick = { sortMode = FavoritesSortMode.Recent },
                                )
                            }
                            item(key = "sort_az") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_alphabet),
                                    selected = sortMode == FavoritesSortMode.Alphabet,
                                    onClick = { sortMode = FavoritesSortMode.Alphabet },
                                )
                            }
                            item(key = "sort_artist") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_artist),
                                    selected = sortMode == FavoritesSortMode.Artist,
                                    onClick = { sortMode = FavoritesSortMode.Artist },
                                )
                            }
                            item(key = "sort_album") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_album),
                                    selected = sortMode == FavoritesSortMode.Album,
                                    onClick = { sortMode = FavoritesSortMode.Album },
                                )
                            }
                        }
                    }

                    if (accountState.isSyncing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ElovaireSpacing.screenHorizontalPadding)
                                .height(2.dp),
                        )
                    }
                }
            }

            if (combinedFavoriteSongs.isEmpty()) {
                item(key = "fav_empty", contentType = "fav_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = copy.noFavoritesMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = readableSecondaryTextColor(),
                        )
                        if (!accountState.isLoggedIn && selectedSource != FavoritesSourceFilter.InApp) {
                            EditorialActionPill(
                                label = copy.signInLabel,
                                iconResId = R.drawable.ic_lucide_user_round,
                                primary = true,
                                onClick = onOpenSettings,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = combinedFavoriteSongs,
                    key = { "fav_song_${it.id}" },
                    contentType = { "fav_song_row" },
                ) { song ->
                    EditorialLibrarySongRow(
                        song = song,
                        isFavorite = true,
                        onClick = { onSongSelected(song, combinedFavoriteSongs) },
                        onToggleFavorite = { onSongMoreActions(song) },
                        modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                    )
                }
            }
        }

        PinnedBackTopBar(
            title = stringResource(R.string.favorites_screen_title),
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
