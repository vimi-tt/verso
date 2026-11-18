package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.net.Uri
import com.vimi.k7.innertube.stream.PlayerClients
import com.vimi.k7.innertube.stream.userAgentForStreamUrl
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal enum class YouTubeDownloadStatus {
    Queued,
    Downloading,
    Failed,
}

internal data class YouTubeDownloadTask(
    val videoId: String,
    val song: Song,
    val progress: Float = 0f,
    val status: YouTubeDownloadStatus = YouTubeDownloadStatus.Queued,
)

internal data class YouTubeDownloadedCollection(
    val id: String,
    val isAlbum: Boolean,
    val title: String,
    val subtitle: String,
    val thumbnailUri: Uri? = null,
    val songs: List<Song> = emptyList(),
)

internal data class YouTubeDownloadsState(
    val activeDownloads: List<YouTubeDownloadTask> = emptyList(),
    val downloadedSongs: List<Song> = emptyList(),
    val downloadedVideoIds: Set<String> = emptySet(),
    val activeVideoIds: Set<String> = emptySet(),
    val downloadedCollections: List<YouTubeDownloadedCollection> = emptyList(),
)

/**
 * Manages offline downloads for YouTube streaming tracks inside app-private
 * storage and exposes reactive download progress + completed tracks.
 */
internal class YouTubeDownloadManager(
    context: Context,
    private val streamClient: YouTubeStreamClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long,
) {
    private val downloadsDir = File(context.applicationContext.filesDir, "youtube_downloads").apply {
        mkdirs()
    }
    private val indexFile = File(downloadsDir, "index.json")
    private val collectionsFile = File(downloadsDir, "collections.json")
    private val concurrencySemaphore = Semaphore(4)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val indexLock = Any()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<YouTubeDownloadsState> = _state.asStateFlow()

    fun localFileFor(videoId: String): File? {
        val file = audioFileFor(videoId)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun enqueueDownload(song: Song) {
        val videoId = YouTubeUri.videoId(song.uri) ?: return
        if (localFileFor(videoId) != null && videoId in _state.value.downloadedVideoIds) return
        if (activeJobs.containsKey(videoId)) return

        upsertTask(
            YouTubeDownloadTask(
                videoId = videoId,
                song = song,
                progress = 0f,
                status = YouTubeDownloadStatus.Queued,
            ),
        )

        val job = scope.launch(ioDispatcher) {
            try {
                concurrencySemaphore.withPermit {
                    performDownload(videoId, song)
                }
            } catch (cancelled: CancellationException) {
                removeTask(videoId)
                throw cancelled
            } catch (_: Throwable) {
                upsertTask(
                    YouTubeDownloadTask(
                        videoId = videoId,
                        song = song,
                        progress = 0f,
                        status = YouTubeDownloadStatus.Failed,
                    ),
                )
            } finally {
                activeJobs.remove(videoId)
            }
        }
        activeJobs[videoId] = job
    }

    fun enqueueCollection(
        songs: List<Song>,
        collection: YouTubeDownloadedCollection? = null,
    ) {
        if (collection != null && collection.id.isNotBlank() && songs.isNotEmpty()) {
            val savedCollection = collection.copy(songs = songs)
            synchronized(indexLock) {
                val updatedCollections = listOf(savedCollection) + _state.value.downloadedCollections.filterNot {
                    it.id == savedCollection.id
                }
                persistCollectionsLocked(updatedCollections)
                _state.update { current ->
                    current.copy(downloadedCollections = updatedCollections)
                }
            }
        }
        songs.forEach(::enqueueDownload)
    }

    fun getDownloadedCollection(collectionId: String): YouTubeDownloadedCollection? =
        _state.value.downloadedCollections.firstOrNull { it.id == collectionId }

    fun cancelDownload(videoId: String) {
        activeJobs.remove(videoId)?.cancel()
        partFileFor(videoId).delete()
        removeTask(videoId)
    }

    fun removeDownload(videoId: String) {
        cancelDownload(videoId)
        audioFileFor(videoId).delete()
        synchronized(indexLock) {
            val remaining = _state.value.downloadedSongs.filterNot {
                YouTubeUri.videoId(it.uri) == videoId
            }
            persistIndexLocked(remaining)
            _state.update { current ->
                current.copy(
                    downloadedSongs = remaining,
                    downloadedVideoIds = remaining.mapNotNull { YouTubeUri.videoId(it.uri) }.toSet(),
                )
            }
        }
    }

    fun totalDownloadsSizeBytes(): Long =
        downloadsDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    fun clearAllDownloads() {
        activeJobs.values.toList().forEach { it.cancel() }
        activeJobs.clear()
        synchronized(indexLock) {
            downloadsDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            _state.value = YouTubeDownloadsState()
        }
    }

    private suspend fun performDownload(videoId: String, song: Song) {
        upsertTask(
            YouTubeDownloadTask(
                videoId = videoId,
                song = song,
                progress = 0f,
                status = YouTubeDownloadStatus.Downloading,
            ),
        )
        val qualitySettings = streamClient.qualityPreferences.state.value
        if (qualitySettings.wifiOnlyDownloads && !streamClient.qualityPreferences.isOnWifi()) {
            throw IOException("Wi-Fi required for downloads")
        }
        val resolvedUrl = streamClient.resolvePlaybackUrl(
            videoId = videoId,
            preference = qualitySettings.downloadQuality,
        )
        val userAgent = userAgentForStreamUrl(resolvedUrl, PlayerClients.VISION_OS_USER_AGENT)

        val partFile = partFileFor(videoId)
        val targetFile = audioFileFor(videoId)
        partFile.delete()

        downloadChunkedStream(
            videoId = videoId,
            song = song,
            resolvedUrl = resolvedUrl,
            userAgent = userAgent,
            partFile = partFile,
        )

        if (!partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            throw IOException("Downloaded file is empty for $videoId")
        }
        targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }

        val completedSong = song.copy(
            dateAddedSeconds = nowMs() / 1000L,
            metadataResolved = true,
        )
        synchronized(indexLock) {
            val updated = listOf(completedSong) + _state.value.downloadedSongs.filterNot {
                YouTubeUri.videoId(it.uri) == videoId
            }
            persistIndexLocked(updated)
            removeTask(videoId)
            _state.update { current ->
                current.copy(
                    downloadedSongs = updated,
                    downloadedVideoIds = updated.mapNotNull { YouTubeUri.videoId(it.uri) }.toSet(),
                )
            }
        }
    }

    private fun upsertTask(task: YouTubeDownloadTask) {
        _state.update { current ->
            val updated = current.activeDownloads.filterNot { it.videoId == task.videoId } + task
            current.copy(
                activeDownloads = updated,
                activeVideoIds = updated.map { it.videoId }.toSet(),
            )
        }
    }

    private fun removeTask(videoId: String) {
        _state.update { current ->
            val updated = current.activeDownloads.filterNot { it.videoId == videoId }
            current.copy(
                activeDownloads = updated,
                activeVideoIds = updated.map { it.videoId }.toSet(),
            )
        }
    }

    private fun downloadChunkedStream(
        videoId: String,
        song: Song,
        resolvedUrl: String,
        userAgent: String,
        partFile: File,
    ) {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var offset = 0L
        var totalBytes = -1L
        var lastReportedPercent = -1
        var lastProgressUpdateMs = 0L

        BufferedOutputStream(FileOutputStream(partFile), DOWNLOAD_BUFFER_SIZE).use { output ->
            while (true) {
                val rangeEnd = offset + DOWNLOAD_CHUNK_SIZE - 1L
                val request = Request.Builder()
                    .url(resolvedUrl)
                    .header("User-Agent", userAgent)
                    .header("Range", "bytes=$offset-$rangeEnd")
                    .build()

                var shouldContinue = false
                streamClient.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 416 && offset > 0L) {
                            return@use
                        }
                        if (response.code == 403 || response.code == 410) {
                            streamClient.invalidateStream(videoId)
                        }
                        throw IOException("HTTP ${response.code} downloading $videoId")
                    }

                    val body = response.body ?: throw IOException("Empty body downloading $videoId")
                    val isPartial = response.code == 206
                    if (isPartial) {
                        val parsedTotal = parseContentRangeTotal(response.header("Content-Range"))
                        if (parsedTotal > 0L) {
                            totalBytes = parsedTotal
                        }
                    } else if (totalBytes <= 0L) {
                        totalBytes = body.contentLength()
                    }

                    var chunkBytesRead = 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            offset += read
                            chunkBytesRead += read
                            if (totalBytes > 0L) {
                                val fraction = (offset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                val percent = (fraction * 100f).toInt()
                                val currentMs = nowMs()
                                if (
                                    percent >= lastReportedPercent + 5 ||
                                    percent == 100 ||
                                    currentMs - lastProgressUpdateMs >= 150L
                                ) {
                                    lastReportedPercent = percent
                                    lastProgressUpdateMs = currentMs
                                    upsertTask(
                                        YouTubeDownloadTask(
                                            videoId = videoId,
                                            song = song,
                                            progress = fraction,
                                            status = YouTubeDownloadStatus.Downloading,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    shouldContinue = isPartial &&
                        chunkBytesRead > 0L &&
                        (totalBytes <= 0L || offset < totalBytes)
                }

                if (!shouldContinue) break
            }
            output.flush()
        }
    }

    private fun parseContentRangeTotal(header: String?): Long {
        if (header.isNullOrBlank()) return -1L
        val slashIndex = header.lastIndexOf('/')
        if (slashIndex < 0 || slashIndex == header.lastIndex) return -1L
        return header.substring(slashIndex + 1).trim().toLongOrNull() ?: -1L
    }

    private fun audioFileFor(videoId: String): File = File(downloadsDir, "$videoId.m4a")
    private fun partFileFor(videoId: String): File = File(downloadsDir, "$videoId.m4a.part")

    private fun loadInitialState(): YouTubeDownloadsState {
        synchronized(indexLock) {
            val songs = if (!indexFile.isFile) {
                emptyList()
            } else {
                runCatching {
                    val array = JSONArray(indexFile.readText())
                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val song = jsonToSong(obj) ?: continue
                            val videoId = YouTubeUri.videoId(song.uri) ?: continue
                            if (localFileFor(videoId) == null) continue
                            add(song)
                        }
                    }
                }.getOrDefault(emptyList())
            }
            val collections = if (!collectionsFile.isFile) {
                emptyList()
            } else {
                runCatching {
                    val array = JSONArray(collectionsFile.readText())
                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val id = obj.optString("id").takeIf(String::isNotBlank) ?: continue
                            val songsArray = obj.optJSONArray("songs") ?: JSONArray()
                            val collectionSongs = buildList {
                                for (j in 0 until songsArray.length()) {
                                    val songObj = songsArray.optJSONObject(j) ?: continue
                                    jsonToSong(songObj)?.let(::add)
                                }
                            }
                            if (collectionSongs.isEmpty()) continue
                            add(
                                YouTubeDownloadedCollection(
                                    id = id,
                                    isAlbum = obj.optBoolean("isAlbum", true),
                                    title = obj.optString("title"),
                                    subtitle = obj.optString("subtitle"),
                                    thumbnailUri = obj.optString("thumbnailUri")
                                        .takeIf(String::isNotBlank)
                                        ?.let(Uri::parse),
                                    songs = collectionSongs,
                                ),
                            )
                        }
                    }
                }.getOrDefault(emptyList())
            }
            return YouTubeDownloadsState(
                downloadedSongs = songs,
                downloadedVideoIds = songs.mapNotNull { YouTubeUri.videoId(it.uri) }.toSet(),
                downloadedCollections = collections,
            )
        }
    }

    private fun persistIndexLocked(songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            songToJson(song)?.let(array::put)
        }
        indexFile.writeText(array.toString())
    }

    private fun persistCollectionsLocked(collections: List<YouTubeDownloadedCollection>) {
        val array = JSONArray()
        collections.forEach { collection ->
            val songsArray = JSONArray()
            collection.songs.forEach { song ->
                songToJson(song)?.let(songsArray::put)
            }
            array.put(
                JSONObject()
                    .put("id", collection.id)
                    .put("isAlbum", collection.isAlbum)
                    .put("title", collection.title)
                    .put("subtitle", collection.subtitle)
                    .put("thumbnailUri", collection.thumbnailUri?.toString().orEmpty())
                    .put("songs", songsArray),
            )
        }
        collectionsFile.writeText(array.toString())
    }

    private fun songToJson(song: Song): JSONObject? {
        val videoId = YouTubeUri.videoId(song.uri) ?: return null
        return JSONObject()
            .put("videoId", videoId)
            .put("title", song.title)
            .put("artist", song.artist)
            .put("album", song.album)
            .put("albumId", song.albumId)
            .put("durationMs", song.durationMs)
            .put("dateAddedSeconds", song.dateAddedSeconds)
            .put("artUri", song.artUri?.toString().orEmpty())
    }

    private fun jsonToSong(obj: JSONObject): Song? {
        val videoId = obj.optString("videoId").takeIf(String::isNotBlank) ?: return null
        return Song(
            id = YouTubeUri.trackIdFor(videoId),
            title = obj.optString("title"),
            isExplicit = false,
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            releaseYear = null,
            genre = "",
            audioFormat = "YouTube",
            audioQuality = null,
            fileName = "$videoId.m4a",
            albumId = obj.optLong("albumId", YouTubeUri.trackIdFor(videoId)),
            durationMs = obj.optLong("durationMs", 0L),
            trackNumber = 0,
            discNumber = 0,
            dateAddedSeconds = obj.optLong("dateAddedSeconds", 0L),
            dateModifiedSeconds = null,
            libraryPath = null,
            uri = YouTubeUri.trackUri(videoId),
            artUri = obj.optString("artUri").takeIf(String::isNotBlank)?.let(Uri::parse),
            metadataResolved = true,
            albumArtist = null,
            volumeNormalization = null,
            mediaKind = AudioMediaKind.Music,
            bookmarkMs = null,
            description = null,
        )
    }

    private companion object {
        const val DOWNLOAD_CHUNK_SIZE = 1024L * 1024L
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
    }
}
