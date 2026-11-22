package elovaire.music.droidbeauty.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLinkImporterTest {

    @Test
    fun `parses YouTube Music, YouTube, and raw playlist IDs`() {
        val ytMusic = PlaylistLinkImporter.parseLink("https://music.youtube.com/playlist?list=PL1234567890")
        assertTrue(ytMusic is ParsedPlaylistLink.YouTube)
        assertEquals("PL1234567890", (ytMusic as ParsedPlaylistLink.YouTube).playlistId)

        val ytWatch = PlaylistLinkImporter.parseLink("https://www.youtube.com/watch?v=abc&list=VLPL999")
        assertEquals(ParsedPlaylistLink.YouTube("PL999"), ytWatch)

        val raw = PlaylistLinkImporter.parseLink("OLAK5uy_abc123")
        assertEquals(ParsedPlaylistLink.YouTube("OLAK5uy_abc123"), raw)
    }

    @Test
    fun `parses Spotify playlist URLs and rejects invalid URLs`() {
        val spotify = PlaylistLinkImporter.parseLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=xyz")
        assertEquals(ParsedPlaylistLink.Spotify("37i9dQZF1DXcBWIGoYBM5M"), spotify)

        assertNull(PlaylistLinkImporter.parseLink("https://example.com/not-a-playlist"))
        assertNull(PlaylistLinkImporter.parseLink(""))
    }

    @Test
    fun `parses Spotify embed HTML NEXT_DATA payload`() {
        val html = """
            <html><head><meta property="og:image" content="https://i.scdn.co/image/fallback.jpg" /></head><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"name":"Chill Mix","coverArt":{"sources":[{"url":"https://i.scdn.co/image/cover640.jpg","maxWidth":640}]},"trackList":[{"title":"Midnight City","subtitle":"M83"},{"title":"Instant Crush","subtitle":"Daft Punk"}]}}}}}}
            </script>
            </body></html>
        """.trimIndent()
        val parsed = PlaylistLinkImporter.parseSpotifyEmbedHtml(html)
        assertEquals("Chill Mix", parsed?.name)
        assertEquals("https://i.scdn.co/image/cover640.jpg", parsed?.coverArtUrl)
        assertEquals(2, parsed?.tracks?.size)
        assertEquals("Midnight City", parsed?.tracks?.get(0)?.title)
        assertEquals("M83", parsed?.tracks?.get(0)?.artist)
    }
}
