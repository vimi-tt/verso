package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.net.Uri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal enum class StatsPeriod {
    Month,
    Year,
    AllTime,
}

internal data class ListeningStatEvent(
    val song: Song,
    val timestampMs: Long,
    val listenedMs: Long,
)

internal data class ListeningStatEntry(
    val song: Song,
    val playCount: Int,
    val lastPlayedAtMs: Long,
)

internal data class SavedPlaybackSession(
    val queue: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
)

internal data class RankedSongStat(
    val song: Song,
    val playCount: Int,
    val listenedMs: Long,
)

internal data class RankedArtistStat(
    val artist: String,
    val playCount: Int,
    val listenedMs: Long,
    val artUri: Uri?,
    val songs: List<Song>,
)

internal data class RankedAlbumStat(
    val album: String,
    val artist: String,
    val playCount: Int,
    val listenedMs: Long,
    val artUri: Uri?,
    val songs: List<Song>,
)

internal data class ListeningStatsSnapshot(
    val period: StatsPeriod = StatsPeriod.AllTime,
    val topSongs: List<RankedSongStat> = emptyList(),
    val topArtists: List<RankedArtistStat> = emptyList(),
    val topAlbums: List<RankedAlbumStat> = emptyList(),
    val topGenre: String = "",
    val totalPlays: Int = 0,
    val totalListeningMs: Long = 0L,
)

/**
 * Persists play events with real listening duration and timestamps (supporting
 * Month / Year / AllTime filtering) plus the last active playback queue for
 * paused cold-start restoration.
 */
internal class ListeningStatsStore(
    context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long,
) {
    private val appContext = context.applicationContext
    private val legacyStatsFile = File(appContext.filesDir, "listening_stats.json")
    private val eventsFile = File(appContext.filesDir, "listening_events.json")
    private val recentSongsFile = File(appContext.filesDir, "recent_played_songs.json")
    private val lastPlaybackFile = File(appContext.filesDir, "last_playback_session.json")
    private val lock = Any()

    private val _events = MutableStateFlow(loadInitialEvents())
    val events: StateFlow<List<ListeningStatEvent>> = _events.asStateFlow()

    private val _recentSongs = MutableStateFlow(loadInitialRecentSongs())
    val recentSongs: StateFlow<List<Song>> = _recentSongs.asStateFlow()

    fun recordRecentSong(song: Song) {
        if (song.mediaKind != AudioMediaKind.Music) return
        _recentSongs.update { current ->
            if (current.firstOrNull()?.id == song.id) return@update current
            (listOf(song) + current.filterNot { it.id == song.id }).take(30)
        }
        val snapshot = _recentSongs.value
        scope.launch(ioDispatcher) {
            synchronized(lock) {
                runCatching {
                    val array = JSONArray()
                    snapshot.forEach { array.put(songToJson(it)) }
                    recentSongsFile.writeText(array.toString())
                }
            }
        }
    }

    private fun loadInitialRecentSongs(): List<Song> {
        synchronized(lock) {
            if (recentSongsFile.isFile) {
                val loaded = runCatching {
                    val array = JSONArray(recentSongsFile.readText())
                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            jsonToSong(obj)?.let(::add)
                        }
                    }
                }.getOrDefault(emptyList())
                if (loaded.isNotEmpty()) return loaded
            }
            return _events.value
                .asReversed()
                .map { it.song }
                .distinctBy { it.id }
                .take(30)
        }
    }

    fun recordSongPlayed(song: Song, initialListenedMs: Long = 30_000L) {
        if (song.mediaKind != AudioMediaKind.Music) return
        val timestamp = nowMs()
        val existingGenre = _events.value
            .lastOrNull { it.song.id == song.id && it.song.genre.isNotBlank() }
            ?.song?.genre
        val resolvedGenre = song.genre
            .takeIf { it.isNotBlank() && !it.equals("Unknown Genre", ignoreCase = true) }
            ?: existingGenre.orEmpty()
        val event = ListeningStatEvent(
            song = song.copy(genre = resolvedGenre),
            timestampMs = timestamp,
            listenedMs = initialListenedMs.coerceAtLeast(0L),
        )
        _events.update { current -> current + event }
        persistAsync()
    }

    fun addListenedDuration(song: Song, deltaMs: Long) {
        if (song.mediaKind != AudioMediaKind.Music || deltaMs <= 0L) return
        _events.update { current ->
            val index = current.indexOfLast { it.song.id == song.id }
            if (index < 0) return@update current
            val mutable = current.toMutableList()
            val target = mutable[index]
            mutable[index] = target.copy(listenedMs = target.listenedMs + deltaMs)
            mutable
        }
        persistAsync()
    }

    fun updateSongGenre(songId: Long, genre: String) {
        val cleanGenre = genre.trim()
        if (cleanGenre.isBlank()) return
        var changed = false
        _events.update { current ->
            current.map { event ->
                if (event.song.id == songId && event.song.genre != cleanGenre) {
                    changed = true
                    event.copy(song = event.song.copy(genre = cleanGenre))
                } else {
                    event
                }
            }
        }
        if (changed) persistAsync()
    }

    suspend fun backfillMissingGenres(resolveGenre: suspend (Song) -> String?) {
        val missingSongs = _events.value
            .asReversed()
            .distinctBy { it.song.id }
            .map { it.song }
            .filter {
                it.genre.isBlank() || it.genre.equals("Unknown Genre", ignoreCase = true)
            }
            .take(15)
        missingSongs.forEach { song ->
            val resolved = runCatching { resolveGenre(song) }.getOrNull()
            if (!resolved.isNullOrBlank()) {
                updateSongGenre(song.id, resolved)
            }
        }
    }

    fun saveLastPlaybackSession(
        queue: List<Song>,
        currentIndex: Int,
        positionMs: Long,
    ) {
        if (queue.isEmpty()) return
        val boundedIndex = currentIndex.coerceIn(queue.indices)
        val trimmedQueue = queue.take(100)
        val safeIndex = boundedIndex.coerceIn(trimmedQueue.indices)
        scope.launch(ioDispatcher) {
            synchronized(lock) {
                runCatching {
                    val array = JSONArray()
                    trimmedQueue.forEach { song -> array.put(songToJson(song)) }
                    val root = JSONObject()
                        .put("currentIndex", safeIndex)
                        .put("positionMs", positionMs.coerceAtLeast(0L))
                        .put("queue", array)
                    lastPlaybackFile.writeText(root.toString())
                }
            }
        }
    }

    fun loadLastPlaybackSession(): SavedPlaybackSession? {
        synchronized(lock) {
            if (!lastPlaybackFile.isFile) return null
            return runCatching {
                val root = JSONObject(lastPlaybackFile.readText())
                val array = root.optJSONArray("queue") ?: return null
                val queue = buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        jsonToSong(obj)?.let(::add)
                    }
                }
                if (queue.isEmpty()) return null
                val index = root.optInt("currentIndex", 0).coerceIn(queue.indices)
                val positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L)
                SavedPlaybackSession(queue = queue, currentIndex = index, positionMs = positionMs)
            }.getOrNull()
        }
    }

    private fun persistAsync() {
        val snapshot = _events.value
        scope.launch(ioDispatcher) {
            synchronized(lock) {
                persistEventsLocked(snapshot)
            }
        }
    }

    private fun loadInitialEvents(): List<ListeningStatEvent> {
        synchronized(lock) {
            if (eventsFile.isFile) {
                return runCatching {
                    val array = JSONArray(eventsFile.readText())
                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val song = jsonToSong(obj) ?: continue
                            add(
                                ListeningStatEvent(
                                    song = song,
                                    timestampMs = obj.optLong("timestampMs", 0L),
                                    listenedMs = obj.optLong("listenedMs", 30_000L).coerceAtLeast(0L),
                                ),
                            )
                        }
                    }
                }.getOrDefault(emptyList())
            }
            if (!legacyStatsFile.isFile) return emptyList()
            return runCatching {
                val array = JSONArray(legacyStatsFile.readText())
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val song = jsonToSong(obj) ?: continue
                        val playCount = obj.optInt("playCount", 1).coerceAtLeast(1)
                        val ts = obj.optLong("lastPlayedAtMs", 0L)
                        repeat(playCount) {
                            add(
                                ListeningStatEvent(
                                    song = song,
                                    timestampMs = ts,
                                    listenedMs = 30_000L,
                                ),
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun persistEventsLocked(events: List<ListeningStatEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                songToJson(event.song)
                    .put("timestampMs", event.timestampMs)
                    .put("listenedMs", event.listenedMs),
            )
        }
        eventsFile.writeText(array.toString())
    }

    private fun songToJson(song: Song): JSONObject = JSONObject()
        .put("songId", song.id)
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("genre", song.genre)
        .put("albumId", song.albumId)
        .put("durationMs", song.durationMs)
        .put("uri", song.uri.toString())
        .put("artUri", song.artUri?.toString().orEmpty())

    private fun jsonToSong(obj: JSONObject): Song? {
        val songId = obj.optLong("songId", 0L)
        val title = obj.optString("title")
        if (songId == 0L || title.isBlank()) return null
        val uriString = obj.optString("uri")
        val artString = obj.optString("artUri").takeIf(String::isNotBlank)
        return Song(
            id = songId,
            title = title,
            isExplicit = false,
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            releaseYear = null,
            genre = obj.optString("genre"),
            audioFormat = "YouTube",
            audioQuality = null,
            fileName = "",
            albumId = obj.optLong("albumId", songId),
            durationMs = obj.optLong("durationMs", 0L),
            trackNumber = 0,
            discNumber = 0,
            dateAddedSeconds = 0L,
            dateModifiedSeconds = null,
            libraryPath = null,
            uri = Uri.parse(uriString),
            artUri = artString?.let(Uri::parse),
            metadataResolved = true,
            albumArtist = null,
            volumeNormalization = null,
            mediaKind = AudioMediaKind.Music,
            bookmarkMs = null,
            description = null,
        )
    }
}

internal fun buildListeningStatsSnapshot(
    events: List<ListeningStatEvent>,
    period: StatsPeriod,
    librarySongs: List<Song>,
    songPlayCounts: Map<Long, Int>,
    extraSongs: List<Song> = emptyList(),
    nowMs: Long,
): ListeningStatsSnapshot {
    val cutoffMs = when (period) {
        StatsPeriod.Month -> Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        StatsPeriod.Year -> Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        StatsPeriod.AllTime -> 0L
    }

    val filteredEvents = if (cutoffMs > 0L) {
        events.filter { it.timestampMs >= cutoffMs }
    } else {
        events
    }

    val knownSongsById = buildMap {
        librarySongs.filter { it.mediaKind == AudioMediaKind.Music }.forEach { put(it.id, it) }
        extraSongs.forEach { putIfAbsent(it.id, it) }
    }

    val mergedById = LinkedHashMap<Long, RankedSongStat>()
    filteredEvents.groupBy { it.song.id }.forEach { (songId, songEvents) ->
        val latest = songEvents.last().song
        val resolvedSong = knownSongsById[songId]?.let { known ->
            if (latest.genre.isNotBlank()) known.copy(genre = latest.genre) else known
        } ?: latest
        mergedById[songId] = RankedSongStat(
            song = resolvedSong,
            playCount = songEvents.size,
            listenedMs = songEvents.sumOf { it.listenedMs },
        )
    }

    if (period == StatsPeriod.AllTime) {
        songPlayCounts.forEach { (songId, count) ->
            if (count <= 0) return@forEach
            val song = knownSongsById[songId] ?: return@forEach
            val existing = mergedById[songId]
            if (existing == null) {
                mergedById[songId] = RankedSongStat(
                    song = song,
                    playCount = count,
                    listenedMs = count * 30_000L,
                )
            } else if (count > existing.playCount) {
                mergedById[songId] = existing.copy(playCount = count)
            }
        }
    }

    val topSongs = mergedById.values
        .sortedWith(compareByDescending<RankedSongStat> { it.playCount }.thenByDescending { it.listenedMs })

    val topArtists = topSongs
        .filter { it.song.artist.isNotBlank() }
        .groupBy { it.song.artist.split(",", "&", "feat.", "ft.").first().trim().ifBlank { it.song.artist } }
        .map { (artistName, items) ->
            RankedArtistStat(
                artist = artistName,
                playCount = items.sumOf { it.playCount },
                listenedMs = items.sumOf { it.listenedMs },
                artUri = items.firstNotNullOfOrNull { it.song.artUri },
                songs = items.map { it.song },
            )
        }
        .sortedWith(compareByDescending<RankedArtistStat> { it.playCount }.thenByDescending { it.listenedMs })

    val topAlbums = topSongs
        .filter { it.song.album.isNotBlank() }
        .groupBy { it.song.album.trim() }
        .map { (albumName, items) ->
            val primary = items.maxByOrNull { it.playCount } ?: items.first()
            RankedAlbumStat(
                album = albumName,
                artist = primary.song.artist,
                playCount = items.sumOf { it.playCount },
                listenedMs = items.sumOf { it.listenedMs },
                artUri = items.firstNotNullOfOrNull { it.song.artUri },
                songs = items.map { it.song },
            )
        }
        .sortedWith(compareByDescending<RankedAlbumStat> { it.playCount }.thenByDescending { it.listenedMs })

    val topGenre = topSongs
        .mapNotNull { stat ->
            stat.song.genre
                .takeIf { it.isNotBlank() && !it.equals("Unknown Genre", ignoreCase = true) }
                ?.let { it to stat.playCount }
        }
        .groupBy({ it.first }, { it.second })
        .maxByOrNull { (_, counts) -> counts.sum() }
        ?.key
        .orEmpty()

    val totalPlays = topSongs.sumOf { it.playCount }
    val totalListeningMs = topSongs.sumOf { it.listenedMs }

    return ListeningStatsSnapshot(
        period = period,
        topSongs = topSongs,
        topArtists = topArtists,
        topAlbums = topAlbums,
        topGenre = topGenre,
        totalPlays = totalPlays,
        totalListeningMs = totalListeningMs,
    )
}
