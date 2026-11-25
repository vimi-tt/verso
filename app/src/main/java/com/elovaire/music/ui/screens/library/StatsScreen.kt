package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.ListeningStatsSnapshot
import elovaire.music.droidbeauty.app.data.youtube.StatsPeriod
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.components.rememberArtworkPaletteAccent
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing
import elovaire.music.droidbeauty.app.ui.theme.elovaireScaledSp

private enum class StatsTab {
    Tracks,
    Artists,
    Albums,
}

internal data class StatsUiCopy(
    val title: String,
    val recapTitle: String,
    val recapSubtitle: String,
    val openRecap: String,
    val playsUnit: String,
    val totalPlaysLabel: String,
    val listeningTimeLabel: String,
    val topGenreLabel: String,
    val emptyMessage: String,
    val periodMonth: String,
    val periodYear: String,
    val periodAllTime: String,
)

internal fun statsUiCopy(language: AppLanguage): StatsUiCopy = when (language) {
    AppLanguage.Portuguese -> StatsUiCopy(
        title = "Estatísticas",
        recapTitle = "Verso Recap",
        recapSubtitle = "Explore suas músicas, artistas e tempo ouvidos",
        openRecap = "Abrir Recap",
        playsUnit = "reprod.",
        totalPlaysLabel = "Reproduções",
        listeningTimeLabel = "Tempo total",
        topGenreLabel = "Gênero principal",
        emptyMessage = "Ouça algumas músicas para ver suas estatísticas aqui",
        periodMonth = "Mês",
        periodYear = "Anual",
        periodAllTime = "Todo o período",
    )
    AppLanguage.Spanish -> StatsUiCopy(
        title = "Estadísticas",
        recapTitle = "Verso Recap",
        recapSubtitle = "Explora tus canciones, artistas y tiempo escuchado",
        openRecap = "Abrir Recap",
        playsUnit = "reprod.",
        totalPlaysLabel = "Reproducciones",
        listeningTimeLabel = "Tiempo total",
        topGenreLabel = "Género principal",
        emptyMessage = "Escucha algunas canciones para ver tus estadísticas aquí",
        periodMonth = "Mes",
        periodYear = "Anual",
        periodAllTime = "Todo el período",
    )
    else -> StatsUiCopy(
        title = "Stats",
        recapTitle = "Verso Recap",
        recapSubtitle = "Explore your top tracks, artists, and listening time",
        openRecap = "Open Recap",
        playsUnit = "plays",
        totalPlaysLabel = "Plays",
        listeningTimeLabel = "Total time",
        topGenreLabel = "Top genre",
        emptyMessage = "Listen to a few tracks to see your stats here",
        periodMonth = "Month",
        periodYear = "Year",
        periodAllTime = "All time",
    )
}

@Composable
internal fun StatsScreen(
    stats: ListeningStatsSnapshot,
    selectedPeriod: StatsPeriod,
    onPeriodSelected: (StatsPeriod) -> Unit,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenRecap: (StatsPeriod) -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { statsUiCopy(language) }
    val common = remember(language) { commonUiCopy(language) }
    val listState = rememberElovaireLazyListState("stats_screen")
    var selectedTab by rememberSaveable { mutableStateOf(StatsTab.Tracks) }

    val totalMinutes = (stats.totalListeningMs / 60_000L).coerceAtLeast(0L)
    val timeText = if (totalMinutes >= 60L) {
        "${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    } else {
        "${totalMinutes}m"
    }
    val fallbackGenre = when (language) {
        AppLanguage.Portuguese, AppLanguage.Spanish -> "Música"
        else -> "Music"
    }
    val resolvedGenre = stats.topGenre.ifBlank { fallbackGenre }
    val topSongArt = stats.topSongs.firstOrNull()?.song?.artUri
    val coverAccent by rememberArtworkPaletteAccent(topSongArt)
    val heroAccent = (coverAccent ?: MaterialTheme.colorScheme.primary)
        .copy(alpha = 0.36f)
        .compositeOver(MaterialTheme.colorScheme.surfaceVariant)

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
                start = ElovaireSpacing.screenHorizontalPadding,
                end = ElovaireSpacing.screenHorizontalPadding,
                top = topBarOccupiedHeight() + ElovaireSpacing.md,
                bottom = bottomPadding + ElovaireSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.lg),
        ) {
            item(key = "stats_period_chips") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
                ) {
                    val periods = listOf(
                        StatsPeriod.Month to copy.periodMonth,
                        StatsPeriod.Year to copy.periodYear,
                        StatsPeriod.AllTime to copy.periodAllTime,
                    )
                    periods.forEach { (period, label) ->
                        val selected = selectedPeriod == period
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .elovaireClickable { onPeriodSelected(period) },
                            shape = RoundedCornerShape(ElovaireRadii.pill),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = if (selected) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "stats_recap_hero") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .elovaireClickable(enabled = stats.topSongs.isNotEmpty(), onClick = { onOpenRecap(selectedPeriod) }),
                    shape = RoundedCornerShape(ElovaireRadii.module),
                    color = heroAccent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (topSongArt != null) {
                            ArtworkImage(
                                uri = topSongArt,
                                title = copy.recapTitle,
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(40.dp)
                                    .alpha(0.32f),
                                cornerRadius = 0.dp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            heroAccent.copy(alpha = 0.88f),
                                            heroAccent.copy(alpha = 0.62f),
                                        ),
                                    ),
                                ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    VersoOption1Icon(size = 24.dp)
                                    Text(
                                        text = copy.recapTitle,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = elovaireScaledSp(21f),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    text = copy.recapSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = readableSecondaryTextColor(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (stats.topSongs.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.padding(top = 4.dp),
                                        shape = RoundedCornerShape(ElovaireRadii.pill),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = copy.openRecap,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                            if (topSongArt != null) {
                                ArtworkImage(
                                    uri = topSongArt,
                                    title = copy.recapTitle,
                                    modifier = Modifier.size(84.dp),
                                    cornerRadius = ElovaireRadii.artwork,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "stats_summary_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatSummaryPill(
                        label = copy.totalPlaysLabel,
                        value = stats.totalPlays.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatSummaryPill(
                        label = copy.listeningTimeLabel,
                        value = timeText,
                        modifier = Modifier.weight(1f),
                    )
                    StatSummaryPill(
                        label = copy.topGenreLabel,
                        value = resolvedGenre,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "stats_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val tabs = listOf(
                        StatsTab.Tracks to common.songs,
                        StatsTab.Artists to common.artists,
                        StatsTab.Albums to common.albums,
                    )
                    tabs.forEach { (tab, label) ->
                        val selected = selectedTab == tab
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .elovaireClickable { selectedTab = tab },
                            shape = RoundedCornerShape(ElovaireRadii.pill),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = if (selected) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                StatsTab.Tracks -> {
                    if (stats.topSongs.isEmpty()) {
                        item(key = "stats_empty_tracks") {
                            StatsEmptyCard(message = copy.emptyMessage)
                        }
                    } else {
                        val maxCount = stats.topSongs.first().playCount.coerceAtLeast(1)
                        itemsIndexed(
                            items = stats.topSongs.take(50),
                            key = { index, item -> "track_${index}_${item.song.id}" },
                        ) { index, item ->
                            LastFmRankedRow(
                                rank = index + 1,
                                title = item.song.title,
                                subtitle = item.song.artist,
                                artUri = item.song.artUri,
                                playCount = item.playCount,
                                maxPlayCount = maxCount,
                                playsUnit = copy.playsUnit,
                            )
                        }
                    }
                }
                StatsTab.Artists -> {
                    if (stats.topArtists.isEmpty()) {
                        item(key = "stats_empty_artists") {
                            StatsEmptyCard(message = copy.emptyMessage)
                        }
                    } else {
                        val maxCount = stats.topArtists.first().playCount.coerceAtLeast(1)
                        itemsIndexed(
                            items = stats.topArtists.take(50),
                            key = { index, item -> "artist_${index}_${item.artist}" },
                        ) { index, item ->
                            LastFmRankedRow(
                                rank = index + 1,
                                title = item.artist,
                                subtitle = "${item.songs.size} ${common.songs.lowercase()}",
                                artUri = item.artUri,
                                playCount = item.playCount,
                                maxPlayCount = maxCount,
                                playsUnit = copy.playsUnit,
                            )
                        }
                    }
                }
                StatsTab.Albums -> {
                    if (stats.topAlbums.isEmpty()) {
                        item(key = "stats_empty_albums") {
                            StatsEmptyCard(message = copy.emptyMessage)
                        }
                    } else {
                        val maxCount = stats.topAlbums.first().playCount.coerceAtLeast(1)
                        itemsIndexed(
                            items = stats.topAlbums.take(50),
                            key = { index, item -> "album_${index}_${item.album}" },
                        ) { index, item ->
                            LastFmRankedRow(
                                rank = index + 1,
                                title = item.album,
                                subtitle = item.artist,
                                artUri = item.artUri,
                                playCount = item.playCount,
                                maxPlayCount = maxCount,
                                playsUnit = copy.playsUnit,
                            )
                        }
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = copy.title,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun StatSummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ModuleCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = readableSecondaryTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LastFmRankedRow(
    rank: Int,
    title: String,
    subtitle: String,
    artUri: android.net.Uri?,
    playCount: Int,
    maxPlayCount: Int,
    playsUnit: String,
) {
    val ratio = (playCount.toFloat() / maxPlayCount.toFloat()).coerceIn(0.06f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = readableSecondaryTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(24.dp),
            )
            ArtworkImage(
                uri = artUri,
                title = title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 10.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
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
            Text(
                text = "$playCount $playsUnit",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(ElovaireRadii.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun StatsEmptyCard(message: String) {
    ModuleCard(contentPadding = PaddingValues(18.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = readableSecondaryTextColor(),
        )
    }
}
