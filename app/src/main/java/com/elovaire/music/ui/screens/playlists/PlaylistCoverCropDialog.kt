package elovaire.music.droidbeauty.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.data.settings.PlaylistCoverImageHelper
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.cropCoverCopy
import elovaire.music.droidbeauty.app.ui.i18n.playlistEditActionCopy
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistCoverCropDialog(
    imageUri: Uri,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmCropped: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val editCopy = remember(language) { playlistEditActionCopy(language) }
    val (cropTitle, cropHint) = remember(language) { cropCoverCopy(language) }
    val scope = rememberCoroutineScope()

    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var viewportPx by remember(imageUri) { mutableFloatStateOf(0f) }
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        isLoading = true
        bitmap = PlaylistCoverImageHelper.loadBitmapForCrop(context, imageUri)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = cropTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(ElovaireRadii.artwork))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .onSizeChanged { size ->
                            viewportPx = size.width.toFloat()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val loadedBitmap = bitmap
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                        loadedBitmap != null -> {
                            val imageBitmap = remember(loadedBitmap) { loadedBitmap.asImageBitmap() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(loadedBitmap, viewportPx) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            if (viewportPx <= 0f) return@detectTransformGestures
                                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                                            val w = loadedBitmap.width.toFloat()
                                            val h = loadedBitmap.height.toFloat()
                                            val baseScale = maxOf(viewportPx / w, viewportPx / h)
                                            val dispW = w * baseScale * newScale
                                            val dispH = h * baseScale * newScale
                                            val maxOffX = ((dispW - viewportPx) / 2f).coerceAtLeast(0f)
                                            val maxOffY = ((dispH - viewportPx) / 2f).coerceAtLeast(0f)
                                            scale = newScale
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxOffY, maxOffY)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = cropTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offsetX
                                            translationY = offsetY
                                        },
                                )
                                // Subtle 3x3 rule-of-thirds crop guide
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val guideColor = Color.White.copy(alpha = 0.22f)
                                    val stroke = 1.dp.toPx()
                                    val thirdW = size.width / 3f
                                    val thirdH = size.height / 3f
                                    drawLine(guideColor, Offset(thirdW, 0f), Offset(thirdW, size.height), stroke)
                                    drawLine(guideColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), stroke)
                                    drawLine(guideColor, Offset(0f, thirdH), Offset(size.width, thirdH), stroke)
                                    drawLine(guideColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), stroke)
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(ElovaireRadii.artwork),
                            ),
                    )
                    if (isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White,
                            )
                        }
                    }
                }
                Text(
                    text = cropHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = readableSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isSaving && !isLoading && bitmap != null && viewportPx > 0f,
                    onClick = {
                        val currentBitmap = bitmap ?: return@TextButton
                        scope.launch {
                            val bytes = PlaylistCoverImageHelper.cropSquareToJpegBytes(
                                bitmap = currentBitmap,
                                viewportSizePx = viewportPx,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                            )
                            if (bytes != null) {
                                onConfirmCropped(bytes)
                            }
                        }
                    },
                ) {
                    Text(editCopy.save)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss,
            ) {
                Text(editCopy.cancel)
            }
        },
    )
}
