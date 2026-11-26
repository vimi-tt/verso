package elovaire.music.droidbeauty.app.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlaylistCoverImageHelper {
    private const val MAX_DIMENSION_PX = 1024

    suspend fun readCompressedJpegBytes(
        context: Context,
        uri: Uri,
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxSide / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return@withContext null
            val scaled = if (decoded.width > MAX_DIMENSION_PX || decoded.height > MAX_DIMENSION_PX) {
                val ratio = minOf(
                    MAX_DIMENSION_PX.toFloat() / decoded.width,
                    MAX_DIMENSION_PX.toFloat() / decoded.height,
                )
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                scaled.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }

    suspend fun saveLocalPlaylistCover(
        context: Context,
        playlistId: Long,
        uri: Uri,
    ): String? = withContext(Dispatchers.IO) {
        val jpegBytes = readCompressedJpegBytes(context, uri) ?: return@withContext null
        runCatching {
            val dir = File(context.applicationContext.filesDir, "playlist_covers").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("playlist_${playlistId}_") }?.forEach { it.delete() }
            val outFile = File(dir, "playlist_${playlistId}_${System.currentTimeMillis()}.jpg")
            outFile.writeBytes(jpegBytes)
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }
}
