package elovaire.music.droidbeauty.app.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlaylistCoverImageHelper {
    private const val OUTPUT_SQUARE_PX = 1024
    private const val CROP_SOURCE_MAX_PX = 2048

    suspend fun loadBitmapForCrop(
        context: Context,
        uri: Uri,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > CROP_SOURCE_MAX_PX) {
                        val ratio = CROP_SOURCE_MAX_PX.toFloat() / maxSide
                        decoder.setTargetSize(
                            (info.size.width * ratio).toInt().coerceAtLeast(1),
                            (info.size.height * ratio).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } else {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                var sampleSize = 1
                while (maxSide / sampleSize > CROP_SOURCE_MAX_PX) {
                    sampleSize *= 2
                }
                BitmapFactory.decodeByteArray(
                    rawBytes,
                    0,
                    rawBytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            }
        }.getOrNull()
    }

    suspend fun cropSquareToJpegBytes(
        bitmap: Bitmap,
        viewportSizePx: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (viewportSizePx <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val baseScale = maxOf(viewportSizePx / w.toFloat(), viewportSizePx / h.toFloat())
            val totalScale = (baseScale * scale.coerceAtLeast(1f)).coerceAtLeast(0.0001f)
            val dispW = w * totalScale
            val dispH = h * totalScale
            val imgLeft = dispW / 2f - viewportSizePx / 2f - offsetX
            val imgTop = dispH / 2f - viewportSizePx / 2f - offsetY

            val srcLeft = (imgLeft / totalScale).toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
            val srcTop = (imgTop / totalScale).toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
            val maxSquare = minOf(w - srcLeft, h - srcTop).coerceAtLeast(1)
            val srcSize = (viewportSizePx / totalScale).toInt().coerceIn(1, maxSquare)

            val cropped = Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcSize, srcSize)
            val finalBitmap = if (cropped.width > OUTPUT_SQUARE_PX) {
                Bitmap.createScaledBitmap(cropped, OUTPUT_SQUARE_PX, OUTPUT_SQUARE_PX, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            } else {
                cropped
            }
            ByteArrayOutputStream().use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                if (finalBitmap !== bitmap) finalBitmap.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }

    suspend fun saveLocalPlaylistCoverBytes(
        context: Context,
        playlistId: Long,
        jpegBytes: ByteArray,
    ): String? = withContext(Dispatchers.IO) {
        if (jpegBytes.isEmpty()) return@withContext null
        runCatching {
            val dir = File(context.applicationContext.filesDir, "playlist_covers").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("playlist_${playlistId}_") }?.forEach { it.delete() }
            val outFile = File(dir, "playlist_${playlistId}_${System.currentTimeMillis()}.jpg")
            outFile.writeBytes(jpegBytes)
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }
}
