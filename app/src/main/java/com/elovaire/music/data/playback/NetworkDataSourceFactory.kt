package elovaire.music.droidbeauty.app.data.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.BaseDataSource
import elovaire.music.droidbeauty.app.data.library.network.NetworkFileSystemRegistry
import elovaire.music.droidbeauty.app.data.library.network.NetworkReadHandle
import elovaire.music.droidbeauty.app.data.library.network.NetworkReadPurpose
import elovaire.music.droidbeauty.app.data.library.network.NetworkResourceUri
import elovaire.music.droidbeauty.app.data.library.network.RemoteIoFailureKind
import elovaire.music.droidbeauty.app.data.library.network.NetworkRemoteIoException
import elovaire.music.droidbeauty.app.data.library.network.remoteIoFailureKind
import elovaire.music.droidbeauty.app.data.youtube.YouTubeOkHttpDataSource
import elovaire.music.droidbeauty.app.data.youtube.YouTubeStreamClient
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Routes Elovaire's network and YouTube URI schemes to their protocol adapters. */
@UnstableApi
internal class NetworkDataSourceFactory(
    private val defaultFactory: DefaultDataSource.Factory,
    private val registryProvider: () -> NetworkFileSystemRegistry,
    private val youTubeClientProvider: (() -> YouTubeStreamClient)? = null,
    private val youTubeLocalFileProvider: ((String) -> File?)? = null,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return RoutingDataSource(
            defaultFactory,
            registryProvider,
            youTubeClientProvider,
            youTubeLocalFileProvider,
        )
    }
}

@UnstableApi
private class RoutingDataSource(
    private val defaultFactory: DefaultDataSource.Factory,
    private val registryProvider: () -> NetworkFileSystemRegistry,
    private val youTubeClientProvider: (() -> YouTubeStreamClient)?,
    private val youTubeLocalFileProvider: ((String) -> File?)?,
) : BaseDataSource(true) {
    private var delegate: DataSource? = null
    private var networkHandle: NetworkReadHandle? = null
    private var currentUri: android.net.Uri? = null
    private var networkTransferStarted = false
    private var networkBytesRemaining: Long? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        currentUri = dataSpec.uri
        if (YouTubeUri.isYouTubeUri(dataSpec.uri)) {
            return openYouTube(dataSpec)
        }
        if (!NetworkResourceUri.isNetworkUri(dataSpec.uri)) {
            val local = defaultFactory.createDataSource()
            delegate = local
            return local.open(dataSpec)
        }
        transferInitializing(dataSpec)
        val sourceId = NetworkResourceUri.sourceId(dataSpec.uri)
            ?: throw IOException("Network media source is missing its source identity")
        val path = NetworkResourceUri.path(dataSpec.uri)
            ?: throw IOException("Network media source is missing its path")
        val handle = try {
            registryProvider().openBlocking(
                sourceId = sourceId,
                path = path,
                position = dataSpec.position,
                length = dataSpec.length,
                purpose = NetworkReadPurpose.Playback,
            )
        } catch (failure: NetworkRemoteIoException) {
            throw DataSourceException(
                failure,
                failure.media3ErrorCode(),
            )
        }
        networkHandle = handle
        networkBytesRemaining = handle.length
        transferStarted(dataSpec)
        networkTransferStarted = true
        return handle.length ?: C_LENGTH_UNKNOWN
    }

    private fun openYouTube(dataSpec: DataSpec): Long {
        val videoId = YouTubeUri.videoId(dataSpec.uri)
            ?: throw IOException("YouTube media source is missing its video ID")
        val localFile = youTubeLocalFileProvider?.invoke(videoId)
        if (localFile != null) {
            val localSource = defaultFactory.createDataSource()
            delegate = localSource
            return localSource.open(
                dataSpec.buildUpon()
                    .setUri(android.net.Uri.fromFile(localFile))
                    .build(),
            )
        }
        val client = youTubeClientProvider?.invoke()
            ?: throw IOException("YouTube streaming client is not configured")
        val resolvedUrl = client.resolvePlaybackUrlBlocking(videoId)
        val httpSource = YouTubeOkHttpDataSource(client.okHttpClient)
        delegate = httpSource
        return try {
            httpSource.open(
                dataSpec.buildUpon()
                    .setUri(resolvedUrl)
                    .build(),
            )
        } catch (failure: HttpDataSource.InvalidResponseCodeException) {
            if (failure.responseCode == 403 || failure.responseCode == 410) {
                runCatching { httpSource.close() }
                client.invalidateStream(videoId)
                val freshUrl = client.resolvePlaybackUrlBlocking(videoId)
                try {
                    httpSource.open(
                        dataSpec.buildUpon()
                            .setUri(freshUrl)
                            .build(),
                    )
                } catch (retryFailure: Exception) {
                    client.invalidateStream(videoId)
                    throw retryFailure
                }
            } else {
                client.invalidateStream(videoId)
                throw failure
            }
        } catch (failure: IOException) {
            client.invalidateStream(videoId)
            throw failure
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        networkHandle?.let { handle ->
            val read = try {
                readNetworkPlayback(handle.input, buffer, offset, length, networkBytesRemaining)
            } catch (failure: NetworkRemoteIoException) {
                throw DataSourceException(failure, failure.media3ErrorCode())
            } catch (failure: IOException) {
                val remoteFailure = NetworkRemoteIoException(
                    kind = failure.remoteIoFailureKind(),
                    message = "Network media read failed",
                    cause = failure,
                )
                throw DataSourceException(remoteFailure, remoteFailure.media3ErrorCode())
            }
            if (read > 0) networkBytesRemaining = networkBytesRemaining?.minus(read)
            if (read > 0) bytesTransferred(read)
            return read
        }
        val activeDelegate = delegate ?: throw IOException("Data source is not open")
        return try {
            activeDelegate.read(buffer, offset, length)
        } catch (failure: IOException) {
            currentUri?.let { uri ->
                if (YouTubeUri.isYouTubeUri(uri)) {
                    YouTubeUri.videoId(uri)?.let { videoId ->
                        youTubeClientProvider?.invoke()?.invalidateStream(videoId)
                    }
                }
            }
            throw failure
        }
    }

    override fun getUri(): android.net.Uri? = delegate?.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        val shouldEndTransfer = networkTransferStarted
        try {
            networkHandle?.close()
            delegate?.close()
        } finally {
            networkHandle = null
            networkBytesRemaining = null
            delegate = null
            currentUri = null
            if (shouldEndTransfer) {
                networkTransferStarted = false
                transferEnded()
            }
        }
    }

    private companion object {
        const val C_LENGTH_UNKNOWN = -1L
    }
}

internal fun readNetworkPlayback(input: InputStream, buffer: ByteArray, offset: Int, length: Int, remaining: Long?): Int {
    if (length == 0) return 0
    val count = input.read(buffer, offset, length)
    if (count < 0 && remaining != null && remaining > 0L) {
        throw NetworkRemoteIoException(RemoteIoFailureKind.ConnectionReset, "Network media ended before the expected length")
    }
    return count
}

private fun NetworkRemoteIoException.media3ErrorCode(): Int = when (kind) {
    RemoteIoFailureKind.RangeOutOfBounds -> PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    RemoteIoFailureKind.Authentication,
    RemoteIoFailureKind.Permission,
    -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
    RemoteIoFailureKind.PathMissing,
    RemoteIoFailureKind.ShareMissing,
    RemoteIoFailureKind.SourceRemoved,
    -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
    RemoteIoFailureKind.Timeout -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    RemoteIoFailureKind.HostUnreachable,
    RemoteIoFailureKind.ConnectionReset,
    RemoteIoFailureKind.TransientServer,
    -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    RemoteIoFailureKind.RangeUnsupported,
    RemoteIoFailureKind.SessionInvalid,
    RemoteIoFailureKind.Protocol,
    RemoteIoFailureKind.Interrupted,
    RemoteIoFailureKind.Unknown,
    -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
}
