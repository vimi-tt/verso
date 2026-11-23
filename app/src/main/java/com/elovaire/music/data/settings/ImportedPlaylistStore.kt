package elovaire.music.droidbeauty.app.data.settings

import android.content.Context
import android.net.Uri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class ImportedPlaylistMetadata(
    val playlistId: Long,
    val coverUri: String? = null,
    val songs: List<Song> = emptyList(),
)

internal data class ImportedPlaylistsSnapshot(
    val songsById: Map<Long, Song> = emptyMap(),
    val coverUrisByPlaylistId: Map<Long, String> = emptyMap(),
)

/**
 * Persists resolved streaming/imported tracks and playlist cover URIs for locally created
 * playlists (e.g. imported from Spotify or YouTube links) so they remain visible and playable
 * even when not part of local MediaStore storage.
 */
internal class ImportedPlaylistStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "imported_playlists_catalog.json")
    private val lock = Any()
    private val entries = LinkedHashMap<Long, ImportedPlaylistMetadata>()
    private val _snapshot = MutableStateFlow(loadInitialSnapshot())
    val snapshot: StateFlow<ImportedPlaylistsSnapshot> = _snapshot.asStateFlow()

    fun savePlaylistData(
        playlistId: Long,
        coverUri: String?,
        songs: List<Song>,
    ) {
        synchronized(lock) {
            entries[playlistId] = ImportedPlaylistMetadata(
                playlistId = playlistId,
                coverUri = coverUri?.takeIf { it.isNotBlank() },
                songs = songs,
            )
            persistLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun registerSongForPlaylist(
        playlistId: Long,
        song: Song,
    ) {
        synchronized(lock) {
            val existing = entries[playlistId]
            val updatedSongs = if (existing == null) {
                listOf(song)
            } else if (existing.songs.any { it.id == song.id }) {
                existing.songs
            } else {
                existing.songs + song
            }
            entries[playlistId] = ImportedPlaylistMetadata(
                playlistId = playlistId,
                coverUri = existing?.coverUri ?: song.artUri?.toString(),
                songs = updatedSongs,
            )
            persistLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun registerFavoriteSong(song: Song) {
        synchronized(lock) {
            val existing = entries[FAVORITES_CATALOG_KEY]
            if (existing != null && existing.songs.any { it.id == song.id }) return
            val updatedSongs = (existing?.songs ?: emptyList()) + song
            entries[FAVORITES_CATALOG_KEY] = ImportedPlaylistMetadata(
                playlistId = FAVORITES_CATALOG_KEY,
                coverUri = null,
                songs = updatedSongs,
            )
            persistLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    companion object {
        private const val FAVORITES_CATALOG_KEY = -100L
    }

    private fun loadInitialSnapshot(): ImportedPlaylistsSnapshot {
        synchronized(lock) {
            if (!file.isFile) return ImportedPlaylistsSnapshot()
            runCatching {
                val root = JSONObject(file.readText())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val playlistId = key.toLongOrNull() ?: continue
                    val obj = root.optJSONObject(key) ?: continue
                    val coverUri = obj.optString("coverUri").ifBlank { null }
                    val songsArr = obj.optJSONArray("songs") ?: JSONArray()
                    val songs = buildList {
                        for (i in 0 until songsArr.length()) {
                            jsonToSong(songsArr.optJSONObject(i) ?: continue)?.let(::add)
                        }
                    }
                    entries[playlistId] = ImportedPlaylistMetadata(
                        playlistId = playlistId,
                        coverUri = coverUri,
                        songs = songs,
                    )
                }
            }
            return buildSnapshotLocked()
        }
    }

    private fun buildSnapshotLocked(): ImportedPlaylistsSnapshot {
        val songsMap = LinkedHashMap<Long, Song>()
        val coversMap = LinkedHashMap<Long, String>()
        for ((playlistId, meta) in entries) {
            meta.coverUri?.let { coversMap[playlistId] = it }
            for (song in meta.songs) {
                songsMap[song.id] = song
            }
        }
        return ImportedPlaylistsSnapshot(
            songsById = songsMap,
            coverUrisByPlaylistId = coversMap,
        )
    }

    private fun persistLocked() {
        runCatching {
            val root = JSONObject()
            for ((playlistId, meta) in entries) {
                val songsArr = JSONArray()
                meta.songs.forEach { song ->
                    songsArr.put(songToJson(song))
                }
                root.put(
                    playlistId.toString(),
                    JSONObject()
                        .put("coverUri", meta.coverUri.orEmpty())
                        .put("songs", songsArr),
                )
            }
            file.writeText(root.toString())
        }
    }

    private fun songToJson(song: Song): JSONObject = JSONObject()
        .put("id", song.id)
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("albumId", song.albumId)
        .put("durationMs", song.durationMs)
        .put("uri", song.uri.toString())
        .put("artUri", song.artUri?.toString().orEmpty())

    private fun jsonToSong(obj: JSONObject): Song? {
        val id = obj.optLong("id", 0L)
        val title = obj.optString("title")
        val uriText = obj.optString("uri")
        if (id == 0L || title.isBlank() || uriText.isBlank()) return null
        val artist = obj.optString("artist").ifBlank { "Unknown Artist" }
        val artText = obj.optString("artUri").ifBlank { null }
        return Song(
            id = id,
            albumId = obj.optLong("albumId", 0L),
            title = title,
            artist = artist,
            album = obj.optString("album"),
            albumArtist = artist,
            releaseYear = null,
            genre = "",
            audioFormat = "YouTube",
            audioQuality = null,
            durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
            trackNumber = 0,
            discNumber = 1,
            dateAddedSeconds = 0L,
            uri = Uri.parse(uriText),
            artUri = artText?.let(Uri::parse),
            isExplicit = false,
            fileName = "",
            mediaKind = AudioMediaKind.Music,
        )
    }
}
