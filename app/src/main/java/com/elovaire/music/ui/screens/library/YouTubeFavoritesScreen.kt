package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeAccountUiState
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.localizedArtistName
import elovaire.music.droidbeauty.app.ui.i18n.localizedExternalAudioText
import elovaire.music.droidbeauty.app.ui.i18n.youtubeAccountCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.interaction.rememberElovaireInteractionSource
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii

private enum class FavoritesSourceFilter {
    All,
    YouTube,
    InApp,
}

@Composable
internal fun YouTubeFavoritesScreen(
    inAppFavoriteSongs: List<Song>,
    accountState: YouTubeAccountUiState,
    topPadding: Dp,
    bottomPadding: Dp,
    onRefreshAccount: () -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onSongMoreActions: (Song) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeAccountCopy(language) }
    var selectedSource by remember { mutableStateOf(FavoritesSourceFilter.All) }

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
    ) {
        when (selectedSource) {
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
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = topPadding,
            end = 20.dp,
            bottom = bottomPadding + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FavoritesFilterChip(
                        label = copy.filterAll,
                        selected = selectedSource == FavoritesSourceFilter.All,
                        onClick = { selectedSource = FavoritesSourceFilter.All },
                    )
                    FavoritesFilterChip(
                        label = copy.filterYouTube,
                        selected = selectedSource == FavoritesSourceFilter.YouTube,
                        onClick = { selectedSource = FavoritesSourceFilter.YouTube },
                    )
                    FavoritesFilterChip(
                        label = copy.filterLikedInApp,
                        selected = selectedSource == FavoritesSourceFilter.InApp,
                        onClick = { selectedSource = FavoritesSourceFilter.InApp },
                    )
                }

                if (accountState.isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }
        }

        if (combinedFavoriteSongs.isEmpty()) {
            item {
                EmptyFavoritesCard(
                    message = copy.noFavoritesMessage,
                    actionLabel = if (!accountState.isLoggedIn && selectedSource != FavoritesSourceFilter.InApp) copy.signInLabel else null,
                    onAction = onOpenSettings,
                )
            }
        } else {
            items(combinedFavoriteSongs, key = { "fav_song_${it.id}" }) { song ->
                FavoriteSongRow(
                    song = song,
                    language = language,
                    onClick = { onSongSelected(song, combinedFavoriteSongs) },
                    onMoreClick = { onSongMoreActions(song) },
                )
            }
        }
    }
}

@Composable
private fun FavoritesFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ElovaireRadii.pill),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun FavoriteSongRow(
    song: Song,
    language: elovaire.music.droidbeauty.app.domain.model.AppLanguage,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val interactionSource = rememberElovaireInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .elovaireClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = song.artUri,
            title = localizedExternalAudioText(song.title, song.id, language),
            placeholderIconResId = R.drawable.ic_lucide_music,
            modifier = Modifier.size(48.dp),
            cornerRadius = ElovaireRadii.artworkSmall,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = localizedExternalAudioText(song.title, song.id, language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedArtistName(song.artist, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ElovaireRadii.pill))
                .clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_star_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyFavoritesCard(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    ModuleCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .clickable(onClick = onAction),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
