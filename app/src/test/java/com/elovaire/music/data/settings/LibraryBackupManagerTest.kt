package elovaire.music.droidbeauty.app.data.settings

import android.net.TestUri
import elovaire.music.droidbeauty.app.data.youtube.ListeningStatEvent
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibraryBackupManagerTest {

    private fun sampleSong(
        id: Long,
        title: String,
        artist: String,
        videoId: String,
    ): Song = Song(
        id = id,
        albumId = 1L,
        title = title,
        artist = artist,
        album = "Test Album",
        albumArtist = artist,
        releaseYear = null,
        genre = "Rock",
        audioFormat = "YouTube",
        audioQuality = null,
        durationMs = 190_000L,
        trackNumber = 1,
        discNumber = 1,
        dateAddedSeconds = 0L,
        uri = TestUri("elovaire-yt://track/$videoId"),
        artUri = null,
        isExplicit = false,
        fileName = "",
        mediaKind = AudioMediaKind.Music,
    )

    @Test
    fun `exportToJson and parseImport round-trips playlists favorites and stats`() {
        val s1 = sampleSong(1L, "Bohemian Rhapsody", "Queen", "vid_1")
        val s2 = sampleSong(2L, "Time", "Pink Floyd", "vid_2")
        val playlist = Playlist(id = 10L, name = "Classics", songIds = listOf(1L, 2L), isSystem = false)
        val systemPlaylist = Playlist(id = 11L, name = "System", songIds = listOf(1L), isSystem = true)

        val json = LibraryBackupManager.exportToJson(
            playlists = listOf(playlist, systemPlaylist),
            songsById = mapOf(1L to s1, 2L to s2),
            favoriteSongIds = setOf(1L),
            listeningEvents = listOf(ListeningStatEvent(song = s1, timestampMs = 1000L, listenedMs = 60_000L)),
            exportedAtMs = 5000L,
        )

        val imported = LibraryBackupManager.parseImport(json)
        assertNotNull(imported)
        assertEquals("Verso JSON", imported!!.sourceFormat)
        assertEquals(1, imported.favoriteTracks.size)
        assertEquals("Bohemian Rhapsody", imported.favoriteTracks[0].title)
        assertEquals(1, imported.playlists.size)
        assertEquals("Classics", imported.playlists[0].name)
        assertEquals(2, imported.playlists[0].tracks.size)
    }

    @Test
    fun `parseImport supports Spotify and LastFm JSON exports`() {
        val spotifyJson = """
            {
              "playlists": [
                {
                  "name": "Roadtrip",
                  "items": [
                    { "track": { "trackName": "Everlong", "artistName": "Foo Fighters", "albumName": "The Colour and the Shape" } }
                  ]
                }
              ],
              "tracks": [
                { "track": "Dreams", "artist": "Fleetwood Mac", "album": "Rumours" }
              ]
            }
        """.trimIndent()
        val spotify = LibraryBackupManager.parseImport(spotifyJson)
        assertNotNull(spotify)
        assertEquals("Spotify JSON", spotify!!.sourceFormat)
        assertEquals("Roadtrip", spotify.playlists[0].name)
        assertEquals("Everlong", spotify.playlists[0].tracks[0].title)
        assertEquals("Dreams", spotify.favoriteTracks[0].title)

        val lastFmJson = """
            {
              "recenttracks": {
                "track": [
                  { "name": "Starman", "artist": { "#text": "David Bowie" }, "album": { "#text": "Ziggy Stardust" } }
                ]
              }
            }
        """.trimIndent()
        val lastFm = LibraryBackupManager.parseImport(lastFmJson)
        assertNotNull(lastFm)
        assertEquals("Last.fm JSON", lastFm!!.sourceFormat)
        assertEquals("Starman", lastFm.playlists[0].tracks[0].title)
        assertEquals("David Bowie", lastFm.playlists[0].tracks[0].artist)
    }
}
