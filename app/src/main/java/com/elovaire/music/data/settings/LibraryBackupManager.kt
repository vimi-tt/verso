package elovaire.music.droidbeauty.app.data.settings

import elovaire.music.droidbeauty.app.data.compasso.TrackNormalizer
import elovaire.music.droidbeauty.app.data.youtube.ListeningStatEvent
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject

internal data class BackupTrackRef(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val videoId: String? = null,
)

internal data class BackupPlaylistRef(
    val name: String,
    val tracks: List<BackupTrackRef>,
)

internal data class LibraryImportResult(
    val sourceFormat: String,
    val playlists: List<BackupPlaylistRef>,
    val favoriteTracks: List<BackupTrackRef>,
) {
    val totalTracks: Int
        get() = favoriteTracks.size + playlists.sumOf { it.tracks.size }
}

/**
 * Exports Verso playlists, liked songs, and listening stats to portable JSON,
 * and imports playlists/favorites from Verso JSON, Spotify JSON, or Last.fm JSON exports.
 */
internal object LibraryBackupManager {
    private const val BACKUP_VERSION = 1

    fun exportToJson(
        playlists: List<Playlist>,
        songsById: Map<Long, Song>,
        favoriteSongIds: Set<Long>,
        listeningEvents: List<ListeningStatEvent> = emptyList(),
        exportedAtMs: Long = System.currentTimeMillis(),
    ): String {
        val root = JSONObject()
            .put("app", "Verso")
            .put("version", BACKUP_VERSION)
            .put("exportedAtMs", exportedAtMs)

        val favoritesArray = JSONArray()
        favoriteSongIds.mapNotNull { songsById[it] }.forEach { song ->
            favoritesArray.put(songToJson(song))
        }
        root.put("favorites", favoritesArray)

        val playlistsArray = JSONArray()
        playlists.filterNot { it.isSystem }.forEach { playlist ->
            val tracksArray = JSONArray()
            playlist.songIds.mapNotNull { songsById[it] }.forEach { song ->
                tracksArray.put(songToJson(song))
            }
            playlistsArray.put(
                JSONObject()
                    .put("name", playlist.name)
                    .put("tracks", tracksArray),
            )
        }
        root.put("playlists", playlistsArray)

        val statsArray = JSONArray()
        listeningEvents.take(500).forEach { event ->
            statsArray.put(
                JSONObject()
                    .put("title", event.song.title)
                    .put("artist", event.song.artist)
                    .put("album", event.song.album)
                    .put("timestampMs", event.timestampMs)
                    .put("listenedMs", event.listenedMs)
                    .put("videoId", YouTubeUri.videoId(event.song.uri).orEmpty()),
            )
        }
        root.put("listeningStats", statsArray)

        return root.toString(2)
    }

    fun parseImport(rawText: String): LibraryImportResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val rootObj = runCatching { JSONObject(trimmed) }.getOrNull()
        if (rootObj != null) {
            // 1. Verso native backup
            if (rootObj.optString("app") == "Verso" || rootObj.has("favorites")) {
                val favorites = parseTrackArray(rootObj.optJSONArray("favorites"))
                val playlists = parseVersoPlaylists(rootObj.optJSONArray("playlists"))
                return LibraryImportResult(
                    sourceFormat = "Verso JSON",
                    playlists = playlists,
                    favoriteTracks = favorites,
                )
            }

            // 2. Spotify Playlist1.json / Library.json
            if (rootObj.has("playlists") || rootObj.has("tracks")) {
                val playlists = parseSpotifyPlaylists(rootObj.optJSONArray("playlists"))
                val liked = parseSpotifyTracks(rootObj.optJSONArray("tracks"))
                if (playlists.isNotEmpty() || liked.isNotEmpty()) {
                    return LibraryImportResult(
                        sourceFormat = "Spotify JSON",
                        playlists = playlists,
                        favoriteTracks = liked,
                    )
                }
            }

            // 3. Last.fm recenttracks / toptracks
            val lfmTracks = rootObj.optJSONObject("recenttracks")?.optJSONArray("track")
                ?: rootObj.optJSONObject("toptracks")?.optJSONArray("track")
            if (lfmTracks != null) {
                val parsed = parseLastFmTracks(lfmTracks)
                if (parsed.isNotEmpty()) {
                    return LibraryImportResult(
                        sourceFormat = "Last.fm JSON",
                        playlists = listOf(BackupPlaylistRef(name = "Last.fm Import", tracks = parsed)),
                        favoriteTracks = emptyList(),
                    )
                }
            }
        }

        // 4. Bare JSON array (Last.fm or track list)
        val rootArray = runCatching { JSONArray(trimmed) }.getOrNull() ?: return null
        val tracks = parseLastFmTracks(rootArray).ifEmpty { parseTrackArray(rootArray) }
        if (tracks.isEmpty()) return null
        return LibraryImportResult(
            sourceFormat = "JSON",
            playlists = listOf(BackupPlaylistRef(name = "Imported Tracks", tracks = tracks)),
            favoriteTracks = emptyList(),
        )
    }

    fun matchTracksToSongs(
        tracks: List<BackupTrackRef>,
        allSongs: Collection<Song>,
    ): List<Song> {
        if (tracks.isEmpty() || allSongs.isEmpty()) return emptyList()
        val byVideoId = HashMap<String, Song>()
        val byNormalizedKey = HashMap<String, Song>()
        for (song in allSongs) {
            YouTubeUri.videoId(song.uri)?.let { byVideoId.putIfAbsent(it, song) }
            val key = TrackNormalizer.normalizeKey(song.title, song.artist)
            byNormalizedKey.putIfAbsent(key, song)
        }
        val matched = ArrayList<Song>()
        val seenIds = HashSet<Long>()
        for (ref in tracks) {
            val song = ref.videoId?.takeIf { it.isNotBlank() }?.let { byVideoId[it] }
                ?: byNormalizedKey[TrackNormalizer.normalizeKey(ref.title, ref.artist)]
                ?: continue
            if (seenIds.add(song.id)) {
                matched.add(song)
            }
        }
        return matched
    }

    private fun songToJson(song: Song): JSONObject = JSONObject()
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("durationMs", song.durationMs)
        .put("videoId", YouTubeUri.videoId(song.uri).orEmpty())

    private fun parseTrackArray(array: JSONArray?): List<BackupTrackRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").ifBlank { obj.optString("track") }.trim()
                val artist = obj.optString("artist").trim()
                if (title.isBlank()) continue
                add(
                    BackupTrackRef(
                        title = title,
                        artist = artist,
                        album = obj.optString("album"),
                        durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                        videoId = obj.optString("videoId").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun parseVersoPlaylists(array: JSONArray?): List<BackupPlaylistRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    BackupPlaylistRef(
                        name = name,
                        tracks = parseTrackArray(obj.optJSONArray("tracks")),
                    ),
                )
            }
        }
    }

    private fun parseSpotifyPlaylists(array: JSONArray?): List<BackupPlaylistRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue
                val items = obj.optJSONArray("items") ?: continue
                val tracks = buildList {
                    for (j in 0 until items.length()) {
                        val trackObj = items.optJSONObject(j)?.optJSONObject("track") ?: continue
                        val title = trackObj.optString("trackName").ifBlank { trackObj.optString("name") }.trim()
                        val artist = trackObj.optString("artistName").ifBlank { trackObj.optString("artist") }.trim()
                        if (title.isBlank()) continue
                        add(
                            BackupTrackRef(
                                title = title,
                                artist = artist,
                                album = trackObj.optString("albumName").ifBlank { trackObj.optString("album") },
                            ),
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    add(BackupPlaylistRef(name = name, tracks = tracks))
                }
            }
        }
    }

    private fun parseSpotifyTracks(array: JSONArray?): List<BackupTrackRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("track").ifBlank { obj.optString("trackName") }.trim()
                val artist = obj.optString("artist").ifBlank { obj.optString("artistName") }.trim()
                if (title.isBlank()) continue
                add(
                    BackupTrackRef(
                        title = title,
                        artist = artist,
                        album = obj.optString("album").ifBlank { obj.optString("albumName") },
                    ),
                )
            }
        }
    }

    private fun parseLastFmTracks(array: JSONArray?): List<BackupTrackRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("name").trim()
                val artistObj = obj.optJSONObject("artist")
                val artist = artistObj?.optString("#text")?.ifBlank { artistObj.optString("name") }
                    ?: obj.optString("artist")
                if (title.isBlank() || artist.isBlank()) continue
                val album = obj.optJSONObject("album")?.optString("#text").orEmpty()
                add(BackupTrackRef(title = title, artist = artist.trim(), album = album))
            }
        }
    }
}
