package elovaire.music.droidbeauty.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import elovaire.music.droidbeauty.app.BuildConfig
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ShareCardMode {
    Song,
    Lyrics,
}

private data class ShareCardThemePreset(
    val gradient: List<Color>,
    val accent: Color,
)

private val SHARE_CARD_THEMES = listOf(
    ShareCardThemePreset(listOf(Color(0xFF19183D), Color(0xFF3E223D)), Color(0xFFFF6E4A)),
    ShareCardThemePreset(listOf(Color(0xFF3F2352), Color(0xFF6B3E78)), Color(0xFFD8B4F8)),
    ShareCardThemePreset(listOf(Color(0xFF18181D), Color(0xFF0A0A0D)), Color(0xFFFFFFFF)),
    ShareCardThemePreset(listOf(Color(0xFF542525), Color(0xFF281316)), Color(0xFFFF8C7A)),
    ShareCardThemePreset(listOf(Color(0xFF1A3A4A), Color(0xFF0E202B)), Color(0xFF7AD3F0)),
)

@Composable
internal fun ShareCardSheet(
    song: Song,
    language: AppLanguage,
    lyricsSnippet: List<String>,
    positionMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLightMode by rememberSaveable { mutableStateOf(false) }
    var cardMode by rememberSaveable { mutableStateOf(ShareCardMode.Song) }
    var selectedThemeIndex by rememberSaveable { mutableIntStateOf(0) }
    val cardGraphicsLayer = rememberGraphicsLayer()

    val isPt = language == AppLanguage.Portuguese
    val isEs = language == AppLanguage.Spanish
    val titleLabel = when {
        isPt -> "Compartilhar"
        isEs -> "Compartir"
        else -> "Share"
    }
    val lightLabel = if (isPt) "Claro" else if (isEs) "Claro" else "Light"
    val darkLabel = if (isPt) "Escuro" else if (isEs) "Oscuro" else "Dark"
    val songTabLabel = if (isPt) "Card da música" else if (isEs) "Tarjeta" else "Song Card"
    val lyricsTabLabel = if (isPt) "Card da letra" else if (isEs) "Letra" else "Lyrics Card"
    val themeLabel = if (isPt) "Tema do card" else if (isEs) "Tema" else "Card Theme"
    val savePngLabel = if (isPt) "Baixar PNG" else if (isEs) "Guardar PNG" else "Save Card"
    val sharePngLabel = if (isPt) "Compartilhar" else if (isEs) "Compartir" else "Share PNG"
    val linkLabel = if (isPt) "Link YouTube" else if (isEs) "Enlace YT" else "YT Music Link"
    val savedToast = if (isPt) "PNG salvo na galeria" else if (isEs) "PNG guardado" else "Saved PNG to gallery"

    val preset = SHARE_CARD_THEMES.getOrElse(selectedThemeIndex) { SHARE_CARD_THEMES.first() }
    val sheetSurface = MaterialTheme.colorScheme.surface
    val sheetOnSurface = MaterialTheme.colorScheme.onSurface
    val cardBg = if (isLightMode) Color(0xFFF6F4F9) else Color(0xFF14141A)
    val cardFg = if (isLightMode) Color(0xFF121216) else Color.White
    val cardSub = if (isLightMode) Color(0xFF555562) else Color.White.copy(alpha = 0.72f)
    val youtubeVideoId = remember(song.uri) { YouTubeUri.videoId(song.uri) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = sheetSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header + Light/Dark toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleLabel,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = sheetOnSurface,
                        )
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetOnSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        listOf(true to lightLabel, false to darkLabel).forEach { (light, label) ->
                            val selected = isLightMode == light
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(
                                        if (selected) sheetOnSurface else Color.Transparent,
                                    )
                                    .elovaireClickable { isLightMode = light }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) sheetSurface else sheetOnSurface,
                                )
                            }
                        }
                    }
                }

                // Song Card / Lyrics Card mode tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        ShareCardMode.Song to songTabLabel,
                        ShareCardMode.Lyrics to lyricsTabLabel,
                    ).forEach { (mode, label) ->
                        val selected = cardMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(ElovaireRadii.pill))
                                .background(if (selected) preset.accent else Color.Transparent)
                                .elovaireClickable { cardMode = mode }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) Color.White else sheetOnSurface,
                            )
                        }
                    }
                }

                // Capturable Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .drawWithContent {
                            cardGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(cardGraphicsLayer)
                        }
                        .background(Brush.verticalGradient(preset.gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (song.artUri != null) {
                        ArtworkImage(
                            uri = song.artUri,
                            title = song.title,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(44.dp)
                                .alpha(0.32f),
                            cornerRadius = 0.dp,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(cardBg.copy(alpha = 0.94f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (cardMode == ShareCardMode.Song) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                ArtworkImage(
                                    uri = song.artUri,
                                    title = song.title,
                                    modifier = Modifier.size(152.dp),
                                    cornerRadius = 14.dp,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cardFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cardSub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val progress = if (durationMs > 0L) {
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.05f, 1f)
                            } else {
                                0.35f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(cardSub.copy(alpha = 0.25f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                                        .background(preset.accent),
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtworkImage(
                                    uri = song.artUri,
                                    title = song.title,
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 10.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = cardFg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cardSub,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val lines = lyricsSnippet.ifEmpty {
                                listOf(song.title, song.artist)
                            }.take(4)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                lines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = cardFg,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VersoOption1Icon(size = 18.dp)
                                Text(
                                    text = "Verso",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cardFg,
                                )
                            }
                            if (durationMs > 0L) {
                                Text(
                                    text = formatCardDuration(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cardSub,
                                )
                            }
                        }
                    }
                }

                // Card Theme Swatches
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = themeLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = sheetOnSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SHARE_CARD_THEMES.forEachIndexed { index, themePreset ->
                            val selected = selectedThemeIndex == index
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(themePreset.gradient))
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.5.dp, sheetOnSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .elovaireClickable { selectedThemeIndex = index },
                            )
                        }
                    }
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShareSheetActionPill(
                        iconResId = R.drawable.ic_lucide_download,
                        label = savePngLabel,
                        primary = true,
                        onClick = {
                            scope.launch {
                                val bitmap = cardGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                val saved = saveShareCardBitmapToGallery(context, bitmap)
                                if (saved) {
                                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ShareSheetActionPill(
                        iconResId = R.drawable.ic_lucide_share_2,
                        label = sharePngLabel,
                        primary = false,
                        onClick = {
                            scope.launch {
                                val bitmap = cardGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                shareCardBitmap(context, bitmap, song.title)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (youtubeVideoId != null) {
                        ShareSheetActionPill(
                            iconResId = R.drawable.ic_lucide_music,
                            label = linkLabel,
                            primary = false,
                            onClick = {
                                val url = "https://music.youtube.com/watch?v=$youtubeVideoId"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}\n$url")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, song.title))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSheetActionPill(
    iconResId: Int,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (primary) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
    }
    val fg = if (primary) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .elovaireClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatCardDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private suspend fun saveShareCardBitmapToGallery(context: Context, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val filename = "verso-card.png"
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

private suspend fun shareCardBitmap(context: Context, bitmap: Bitmap, title: String) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "recap").apply { mkdirs() }
            val file = File(dir, "verso-card.png")
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
    context.startActivity(Intent.createChooser(intent, title))
}
