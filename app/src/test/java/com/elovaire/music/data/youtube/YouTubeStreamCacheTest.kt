package elovaire.music.droidbeauty.app.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeStreamCacheTest {
    @Test
    fun `returns cached url before ttl expires`() {
        var now = 1_000L
        val cache = YouTubeStreamCache(maxSize = 4, ttlMs = 5_000L, nowMs = { now })

        cache.put("vid1", "https://example.com/stream1")
        assertEquals("https://example.com/stream1", cache.get("vid1"))

        now += 4_999L
        assertEquals("https://example.com/stream1", cache.get("vid1"))

        now += 1L
        assertNull(cache.get("vid1"))
    }

    @Test
    fun `invalidateAll bumps generation and rejects stale writes`() {
        val cache = YouTubeStreamCache(maxSize = 4, ttlMs = 5_000L, nowMs = { 1_000L })
        val staleGen = cache.generation()

        cache.invalidateAll()
        cache.put("vid1", "https://example.com/stale", generation = staleGen)

        assertNull(cache.get("vid1"))
    }
}
