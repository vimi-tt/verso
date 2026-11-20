package elovaire.music.droidbeauty.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import elovaire.music.droidbeauty.app.BuildConfig
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.ListeningStatsSnapshot
import elovaire.music.droidbeauty.app.data.youtube.StatsPeriod
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECAP_SLIDE_COUNT = 5

internal data class RecapUiCopy(
    val introEyebrow: String,
    val introTitle: String,
    val introSubtitle: String,
    val topTracksTitle: String,
    val topArtistsTitle: String,
    val genreTimeTitle: String,
    val topGenreLabel: String,
    val minutesListenedLabel: String,
    val topArtistsHeader: String,
    val topSongsHeader: String,
    val shareAction: String,
    val savedToast: String,
    val tapToContinue: String,
    val playsLabel: String,
)

private fun recapUiCopy(language: AppLanguage): RecapUiCopy = when (language) {
    AppLanguage.Portuguese -> RecapUiCopy(
        introEyebrow = "Seu ano em música",
        introTitle = "Verso Recap",
        introSubtitle = "Explore suas músicas, artistas e momentos mais marcantes",
        topTracksTitle = "Suas 5 músicas mais ouvidas",
        topArtistsTitle = "Seus 5 artistas mais ouvidos",
        genreTimeTitle = "Seu som em números",
        topGenreLabel = "Gênero principal",
        minutesListenedLabel = "Minutos ouvidos",
        topArtistsHeader = "Top Artistas",
        topSongsHeader = "Top Músicas",
        shareAction = "Compartilhar",
        savedToast = "Imagem salva na galeria",
        tapToContinue = "Toque para avançar",
        playsLabel = "reproduções",
    )
    AppLanguage.Spanish -> RecapUiCopy(
        introEyebrow = "Tu año en música",
        introTitle = "Verso Recap",
        introSubtitle = "Explora tus canciones, artistas y momentos más escuchados",
        topTracksTitle = "Tus 5 canciones más escuchadas",
        topArtistsTitle = "Tus 5 artistas más escuchados",
        genreTimeTitle = "Tu sonido en números",
        topGenreLabel = "Género principal",
        minutesListenedLabel = "Minutos escuchados",
        topArtistsHeader = "Top Artistas",
        topSongsHeader = "Top Canciones",
        shareAction = "Compartir",
        savedToast = "Imagen guardada en la galería",
        tapToContinue = "Toca para continuar",
        playsLabel = "reproducciones",
    )
    else -> RecapUiCopy(
        introEyebrow = "Your year in music",
        introTitle = "Verso Recap",
        introSubtitle = "Explore your top tracks, artists, and listening highlights",
        topTracksTitle = "These were your top five tracks",
        topArtistsTitle = "Your top five artists",
        genreTimeTitle = "Your sound in numbers",
        topGenreLabel = "Top Genre",
        minutesListenedLabel = "Minutes Listened",
        topArtistsHeader = "Top Artists",
        topSongsHeader = "Top Songs",
        shareAction = "Share",
        savedToast = "Saved image to gallery",
        tapToContinue = "Tap to continue",
        playsLabel = "plays",
    )
}

/**
 * Verso app icon: dark warm charcoal squircle with serif "ver / so" wordmark
 * split by an angled blue slash, rendered from the shared vector drawable.
 */
@Composable
internal fun VersoOption1Icon(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = R.drawable.ic_verso_app_icon),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
internal fun RecapScreen(
    stats: ListeningStatsSnapshot,
    onClose: () -> Unit,
    onStartBackgroundSong: (Song) -> Unit,
    onSetMuted: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { recapUiCopy(language) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSlide by rememberSaveable { mutableIntStateOf(0) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    val finalCardGraphicsLayer = rememberGraphicsLayer()

    val topSong = stats.topSongs.firstOrNull()?.song
    LaunchedEffect(topSong?.id) {
        if (topSong != null) {
            onStartBackgroundSong(topSong)
        }
    }
    LaunchedEffect(isMuted) {
        onSetMuted(isMuted)
    }
    DisposableEffect(Unit) {
        onDispose {
            onSetMuted(false)
        }
    }

    val top5Songs = stats.topSongs.take(5)
    val top5Artists = stats.topArtists.take(5)
    val totalMinutes = (stats.totalListeningMs / 60_000L).coerceAtLeast(1L)
    val resolvedGenre = stats.topGenre.ifBlank { "Music" }
    val heroArtUri = top5Artists.firstOrNull()?.artUri ?: topSong?.artUri

    val bgGradient = when (currentSlide) {
        0 -> listOf(Color(0xFF28193D), Color(0xFF0E1B2A), Color(0xFF0A0A0E))
        1 -> listOf(Color(0xFF0E3C66), Color(0xFF0D2842), Color(0xFF081018))
        2 -> listOf(Color(0xFF3E194D), Color(0xFF1F1233), Color(0xFF0A0A10))
        3 -> listOf(Color(0xFF4B1D14), Color(0xFF241118), Color(0xFF0B0A0D))
        else -> listOf(Color(0xFF121218), Color(0xFF0B0B0F), Color(0xFF07070A))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradient))
            .pointerInput(currentSlide) {
                detectTapGestures { offset ->
                    if (currentSlide < RECAP_SLIDE_COUNT - 1) {
                        if (offset.x < size.width * 0.30f) {
                            currentSlide = (currentSlide - 1).coerceAtLeast(0)
                        } else {
                            currentSlide = (currentSlide + 1).coerceAtMost(RECAP_SLIDE_COUNT - 1)
                        }
                    } else if (offset.x < size.width * 0.25f) {
                        currentSlide = (currentSlide - 1).coerceAtLeast(0)
                    }
                }
            },
    ) {
        if (heroArtUri != null && currentSlide == 0) {
            ArtworkImage(
                uri = heroArtUri,
                title = copy.introTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .alpha(0.32f),
                cornerRadius = 0.dp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Story-style segmented tab bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(RECAP_SLIDE_COUNT) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(ElovaireRadii.pill))
                                .background(
                                    if (index <= currentSlide) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.28f)
                                    },
                                )
                                .elovaireClickable { currentSlide = index },
                        )
                    }
                }

                // Header: Verso Option 1 icon + "Verso" | Mute | Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VersoOption1Icon(size = 24.dp)
                        Text(
                            text = "Verso",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .elovaireClickable { isMuted = !isMuted },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isMuted) R.drawable.ic_lucide_volume_x else R.drawable.ic_lucide_volume_2,
                                ),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .elovaireClickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_x),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Slide Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "RecapSlideTransition",
                ) { slide ->
                    when (slide) {
                        0 -> RecapIntroSlide(
                            copy = copy,
                            heroArtUri = heroArtUri,
                            topSong = topSong,
                            totalPlays = stats.totalPlays,
                            totalMinutes = totalMinutes,
                        )
                        1 -> RecapTopTracksSlide(
                            copy = copy,
                            topSongs = top5Songs,
                        )
                        2 -> RecapTopArtistsSlide(
                            copy = copy,
                            topArtists = top5Artists,
                        )
                        3 -> RecapGenreAndTimeSlide(
                            copy = copy,
                            topGenre = resolvedGenre,
                            totalMinutes = totalMinutes,
                            totalPlays = stats.totalPlays,
                        )
                        else -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    finalCardGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(finalCardGraphicsLayer)
                                },
                        ) {
                            RecapFinalSummaryCard(
                                copy = copy,
                                periodLabel = when (stats.period) {
                                    StatsPeriod.Month -> statsUiCopy(language).periodMonth
                                    StatsPeriod.Year -> statsUiCopy(language).periodYear
                                    StatsPeriod.AllTime -> statsUiCopy(language).periodAllTime
                                },
                                heroArtUri = heroArtUri,
                                topArtists = top5Artists.map { it.artist },
                                topSongs = top5Songs.map { it.song.title },
                                totalMinutes = totalMinutes,
                                topGenre = resolvedGenre,
                            )
                        }
                    }
                }
            }

            // Footer
            if (currentSlide == RECAP_SLIDE_COUNT - 1) {
                val savePngLabel = when (language) {
                    AppLanguage.Portuguese -> "Baixar PNG"
                    AppLanguage.Spanish -> "Descargar PNG"
                    else -> "Save PNG"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(Color.White.copy(alpha = 0.16f))
                            .elovaireClickable {
                                scope.launch {
                                    val bitmap = finalCardGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                    val saved = saveRecapBitmapToGallery(context, bitmap)
                                    if (saved) {
                                        Toast.makeText(context, copy.savedToast, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_download),
                            contentDescription = savePngLabel,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = savePngLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(Color.White)
                            .elovaireClickable {
                                scope.launch {
                                    val bitmap = finalCardGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                    shareRecapBitmap(context, bitmap)
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_share_2),
                            contentDescription = copy.shareAction,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = copy.shareAction,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(
                    text = copy.tapToContinue,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RecapIntroSlide(
    copy: RecapUiCopy,
    heroArtUri: android.net.Uri?,
    topSong: Song?,
    totalPlays: Int,
    totalMinutes: Long,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = copy.introEyebrow,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFF6E4A),
            textAlign = TextAlign.Center,
        )
        Text(
            text = copy.introTitle,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        ArtworkImage(
            uri = heroArtUri,
            title = copy.introTitle,
            modifier = Modifier.size(210.dp),
            cornerRadius = 20.dp,
        )
        Text(
            text = copy.introSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (topSong != null) {
            Text(
                text = "${topSong.title} • $totalPlays ${copy.playsLabel} • ${totalMinutes}m",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecapTopTracksSlide(
    copy: RecapUiCopy,
    topSongs: List<elovaire.music.droidbeauty.app.data.youtube.RankedSongStat>,
) {
    val rowColors = listOf(
        Color(0xFF0A8FD8),
        Color(0xFF0A74B0),
        Color(0xFF085C8C),
        Color(0xFF07476D),
        Color(0xFF063452),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = copy.topTracksTitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        topSongs.forEachIndexed { index, stat ->
            val bg = rowColors.getOrElse(index) { rowColors.last() }
            if (index == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg),
                ) {
                    ArtworkImage(
                        uri = stat.song.artUri,
                        title = stat.song.title,
                        modifier = Modifier
                            .width(148.dp)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "#1",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = stat.song.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stat.song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${stat.playCount} ${copy.playsLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        uri = stat.song.artUri,
                        title = stat.song.title,
                        modifier = Modifier.size(58.dp),
                        cornerRadius = 0.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${stat.song.title}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stat.song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapTopArtistsSlide(
    copy: RecapUiCopy,
    topArtists: List<elovaire.music.droidbeauty.app.data.youtube.RankedArtistStat>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = copy.topArtistsTitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        topArtists.forEachIndexed { index, stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (index == 0) 0.16f else 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (index == 0) Color(0xFFFF6E4A) else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                ArtworkImage(
                    uri = stat.artUri,
                    title = stat.artist,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 26.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.artist,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stat.playCount} ${copy.playsLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapGenreAndTimeSlide(
    copy: RecapUiCopy,
    topGenre: String,
    totalMinutes: Long,
    totalPlays: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Text(
            text = copy.genreTimeTitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = copy.minutesListenedLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = "%,d".format(totalMinutes),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                color = Color(0xFFFF6E4A),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = copy.topGenreLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = topGenre,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$totalPlays ${copy.playsLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun RecapFinalSummaryCard(
    copy: RecapUiCopy,
    periodLabel: String,
    heroArtUri: android.net.Uri?,
    topArtists: List<String>,
    topSongs: List<String>,
    totalMinutes: Long,
    topGenre: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1A1A1E)),
    ) {
        // Top visual banner (inspired by Spotify Wrapped summary card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF19183D), Color(0xFFFF6E4A)),
                    ),
                ),
        ) {
            Text(
                text = "2026",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                ),
                color = Color.White.copy(alpha = 0.24f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 12.dp),
            )
            ArtworkImage(
                uri = heroArtUri,
                title = "Verso Recap",
                modifier = Modifier
                    .size(156.dp)
                    .align(Alignment.Center),
                cornerRadius = 14.dp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = copy.topArtistsHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    topArtists.take(5).forEachIndexed { index, artist ->
                        Text(
                            text = "${index + 1} $artist",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = copy.topSongsHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    topSongs.take(5).forEachIndexed { index, song ->
                        Text(
                            text = "${index + 1} $song",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.minutesListenedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        text = "%,d".format(totalMinutes),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.topGenreLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        text = topGenre,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VersoOption1Icon(size = 24.dp)
                    Text(
                        text = "Verso",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                Text(
                    text = "VERSO RECAP • ${periodLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
        }
    }
}

private suspend fun saveRecapBitmapToGallery(context: Context, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val filename = "verso-recap.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Verso")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } ?: return@runCatching false
                true
            } else {
                val dir = File(context.cacheDir, "recap").apply { mkdirs() }
                val file = File(dir, filename)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                true
            }
        }.getOrDefault(false)
    }

private suspend fun shareRecapBitmap(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "recap").apply { mkdirs() }
            val file = File(dir, "verso-recap.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.update.fileprovider",
                file,
            )
        }.getOrNull()
    } ?: return

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Verso Recap"))
}
