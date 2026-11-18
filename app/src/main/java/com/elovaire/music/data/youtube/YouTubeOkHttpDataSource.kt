@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package elovaire.music.droidbeauty.app.data.youtube

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.vimi.k7.innertube.stream.PlayerClients
import com.vimi.k7.innertube.stream.userAgentForStreamUrl
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import okhttp3.Call
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Lightweight [HttpDataSource] backed by OkHttp's HTTP/2 [Call.Factory], sharing
 * sockets and connection pools with `YouTubeHttp` and injecting the dynamic
 * InnerTube `User-Agent` for each stream URL.
 */
internal class YouTubeOkHttpDataSource(
    private val callFactory: Call.Factory,
    private val defaultUserAgent: String = PlayerClients.VISION_OS_USER_AGENT,
) : BaseDataSource(/* isNetwork = */ true), HttpDataSource {

    private val requestProperties = HttpDataSource.RequestProperties()
    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseByteStream: InputStream? = null
    private var opened = false
    private var bytesToRead = 0L
    private var bytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0L
        bytesToRead = 0L
        transferInitializing(dataSpec)

        val request = buildRequest(dataSpec)
        val executedResponse = try {
            callFactory.newCall(request).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }
        response = executedResponse

        val responseBody = executedResponse.body
        val responseCode = executedResponse.code
        val responseMessage = executedResponse.message

        if (!executedResponse.isSuccessful) {
            val headers = executedResponse.headers.toMultimap()
            closeConnectionQuietly()
            if (responseCode == 416) {
                val documentSize = parseContentRangeSize(executedResponse.header("Content-Range"))
                if (dataSpec.position >= 0 && documentSize > 0 && dataSpec.position == documentSize) {
                    opened = true
                    transferStarted(dataSpec)
                    return 0L
                }
                throw HttpDataSource.HttpDataSourceException(
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            val cause = HttpDataSource.InvalidResponseCodeException(
                responseCode,
                responseMessage,
                null,
                headers,
                dataSpec,
                ByteArray(0),
            )
            throw cause
        }

        if (responseBody == null) {
            closeConnectionQuietly()
            throw HttpDataSource.HttpDataSourceException(
                "Empty response body",
                dataSpec,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }

        val bytesToSkip = if (responseCode == 200 && dataSpec.position != 0L) dataSpec.position else 0L
        val contentLength = responseBody.contentLength()
        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (contentLength != -1L) {
            contentLength - bytesToSkip
        } else {
            C.LENGTH_UNSET.toLong()
        }

        responseByteStream = responseBody.byteStream()
        opened = true
        transferStarted(dataSpec)

        if (bytesToSkip > 0L) {
            try {
                skipFully(bytesToSkip, dataSpec)
            } catch (e: HttpDataSource.HttpDataSourceException) {
                closeConnectionQuietly()
                throw e
            }
        }
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT
        }
        val toRead = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesToRead - bytesRead).toInt()
        }
        val stream = responseByteStream ?: return C.RESULT_END_OF_INPUT
        val read = try {
            stream.read(buffer, offset, toRead)
        } catch (e: IOException) {
            val spec = dataSpec ?: throw e
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                spec,
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        }
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? =
        response?.request?.url?.toString()?.let(Uri::parse) ?: dataSpec?.uri

    override fun getResponseCode(): Int = response?.code ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap().orEmpty()

    override fun setRequestProperty(name: String, value: String) {
        requestProperties.set(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        closeConnectionQuietly()
        dataSpec = null
    }

    private fun buildRequest(dataSpec: DataSpec): Request {
        val url = dataSpec.uri.toString().toHttpUrlOrNull()
            ?: throw HttpDataSource.HttpDataSourceException(
                "Malformed URL: ${dataSpec.uri}",
                dataSpec,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        val builder = Request.Builder().url(url)

        val headers = mutableMapOf<String, String>()
        headers.putAll(requestProperties.snapshot)
        headers.putAll(dataSpec.httpRequestHeaders)
        headers["User-Agent"] = userAgentForStreamUrl(dataSpec.uri.toString(), defaultUserAgent)

        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                (dataSpec.position + dataSpec.length - 1).toString()
            } else {
                ""
            }
            headers["Range"] = "bytes=${dataSpec.position}-$end"
        }

        if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
            headers["Accept-Encoding"] = "identity"
        }

        builder.headers(headers.toHeaders())
        val requestBody = when {
            dataSpec.httpBody != null -> dataSpec.httpBody!!.toRequestBody(null)
            dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST ->
                ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull())
            else -> null
        }
        builder.method(dataSpec.httpMethodString, requestBody)
        return builder.build()
    }

    private fun skipFully(bytesToSkip: Long, dataSpec: DataSpec) {
        val stream = responseByteStream ?: return
        val skipBuffer = ByteArray(4096)
        var remaining = bytesToSkip
        while (remaining > 0L) {
            if (Thread.currentThread().isInterrupted) {
                throw HttpDataSource.HttpDataSourceException(
                    InterruptedIOException(),
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            val read = try {
                stream.read(skipBuffer, 0, minOf(remaining, skipBuffer.size.toLong()).toInt())
            } catch (e: IOException) {
                throw HttpDataSource.HttpDataSourceException.createForIOException(
                    e,
                    dataSpec,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            if (read == -1) {
                throw HttpDataSource.HttpDataSourceException(
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            remaining -= read
            bytesTransferred(read)
        }
    }

    private fun closeConnectionQuietly() {
        runCatching { responseByteStream?.close() }
        runCatching { response?.close() }
        responseByteStream = null
        response = null
    }

    private fun parseContentRangeSize(contentRange: String?): Long {
        if (contentRange.isNullOrBlank()) return -1L
        return contentRange.substringAfterLast('/').toLongOrNull() ?: -1L
    }
}
