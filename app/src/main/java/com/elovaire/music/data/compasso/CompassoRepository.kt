package elovaire.music.droidbeauty.app.data.compasso

import android.net.Uri
import elovaire.music.droidbeauty.app.data.library.db.BlockedTrackEntity
import elovaire.music.droidbeauty.app.data.library.db.CompassoDao
import elovaire.music.droidbeauty.app.data.library.db.PersistedQueueEntity
import elovaire.music.droidbeauty.app.data.library.db.PlaybackHistoryEntity
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

internal data class CompassoCandidate(
    val videoId: String,
    val song: Song,
    val musicVideoType: String?,
    val isFamiliar: Boolean = false,
    val familiarTimeMatch: Boolean = false,
)

internal data class CompassoRadioPage(
    val candidates: List<CompassoCandidate>,
    val continuationToken: String?,
)

internal interface CompassoRadioSource {
    suspend fun getRadioPage(videoId: String, continuationToken: String? = null): CompassoRadioPage
    suspend fun resolveAtvSeedVideoId(song: Song): String?
    fun likedSongsSnapshot(): List<Song>
}

internal interface CompassoStore {
    suspend fun upsertHistory(entry: PlaybackHistoryEntity)
    suspend fun getRecentHistory(limit: Int = 50): List<PlaybackHistoryEntity>
    suspend fun getPositiveHistory(limit: Int = 200): List<PlaybackHistoryEntity>
    suspend fun insertBlockedTrack(entry: BlockedTrackEntity)
    suspend fun getAllBlockedTracks(): List<BlockedTrackEntity>
    fun observeBlockedVideoIds(): Flow<List<String>>
    suspend fun removeBlockedTrack(videoId: String)
    suspend fun savePersistedQueue(state: PersistedQueueEntity)
    suspend fun getPersistedQueue(): PersistedQueueEntity?
    suspend fun clearPersistedQueue()
}

internal class RoomCompassoStore(
    private val dao: CompassoDao,
) : CompassoStore {
    override suspend fun upsertHistory(entry: PlaybackHistoryEntity) = dao.upsertHistory(entry)
    override suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity> = dao.getRecentHistory(limit)
    override suspend fun getPositiveHistory(limit: Int): List<PlaybackHistoryEntity> = dao.getPositiveHistory(limit)
    override suspend fun insertBlockedTrack(entry: BlockedTrackEntity) = dao.insertBlockedTrack(entry)
    override suspend fun getAllBlockedTracks(): List<BlockedTrackEntity> = dao.getAllBlockedTracks()
    override fun observeBlockedVideoIds(): Flow<List<String>> = dao.observeBlockedVideoIds()
    override suspend fun removeBlockedTrack(videoId: String) = dao.removeBlockedTrack(videoId)
    override suspend fun savePersistedQueue(state: PersistedQueueEntity) = dao.savePersistedQueue(state)
    override suspend fun getPersistedQueue(): PersistedQueueEntity? = dao.getPersistedQueue()
    override suspend fun clearPersistedQueue() = dao.clearPersistedQueue()
}

internal class YouTubeCompassoRadioSource(
    private val youTubeCatalog: YouTubeCatalogRepository,
) : CompassoRadioSource {
    override suspend fun getRadioPage(videoId: String, continuationToken: String?): CompassoRadioPage =
        youTubeCatalog.getCompassoRadioPage(videoId, continuationToken)

    override suspend fun resolveAtvSeedVideoId(song: Song): String? =
        youTubeCatalog.resolveAtvSeedVideoId(song)

    override fun likedSongsSnapshot(): List<Song> =
        youTubeCatalog.accountState.value.likedSongs
}

internal object CompassoQueueSerializer {
    fun encodeSongs(songs: List<Song>): String {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("albumId", song.albumId)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("durationMs", song.durationMs)
                    .put("uri", song.uri.toString())
                    .put("artUri", song.artUri?.toString().orEmpty()),
            )
        }
        return array.toString()
    }

    fun decodeSongs(rawJson: String): List<Song> = runCatching {
        val array = JSONArray(rawJson)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uriString = obj.optString("uri").takeIf { it.isNotBlank() } ?: continue
                add(
                    Song(
                        id = obj.optLong("id", 0L),
                        albumId = obj.optLong("albumId", 0L),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        albumArtist = obj.optString("artist"),
                        releaseYear = null,
                        genre = "",
                        audioFormat = "YouTube",
                        audioQuality = null,
                        durationMs = obj.optLong("durationMs", 0L),
                        trackNumber = 0,
                        discNumber = 1,
                        dateAddedSeconds = 0L,
                        uri = Uri.parse(uriString),
                        artUri = obj.optString("artUri").takeIf { it.isNotBlank() }?.let(Uri::parse),
                        isExplicit = false,
                        fileName = "",
                        mediaKind = AudioMediaKind.Music,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun videoIdOf(song: Song): String? = YouTubeUri.videoId(song.uri)
}
