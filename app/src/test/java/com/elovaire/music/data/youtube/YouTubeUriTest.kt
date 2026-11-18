package elovaire.music.droidbeauty.app.data.youtube

import android.net.TestUri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeUriTest {
    @Test
    fun `isYouTubeUri matches elovaire-yt scheme`() {
        val uri = TestUri("elovaire-yt://track/dQw4w9WgXcQ")
        assertTrue(YouTubeUri.isYouTubeUri(uri))
    }

    @Test
    fun `non-youtube uri returns false and null`() {
        val uri = TestUri("content://media/external/audio/media/42")
        assertFalse(YouTubeUri.isYouTubeUri(uri))
        assertNull(YouTubeUri.videoId(uri))
    }

    @Test
    fun `trackIdFor is deterministic and negative`() {
        val id1 = YouTubeUri.trackIdFor("abc123")
        val id2 = YouTubeUri.trackIdFor("abc123")
        val id3 = YouTubeUri.trackIdFor("xyz789")
        assertEquals(id1, id2)
        assertTrue(id1 < 0L)
        assertNotEquals(id1, id3)
    }
}
