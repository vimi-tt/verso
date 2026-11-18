package com.vimi.k7.innertube

import com.vimi.k7.innertube.auth.YouTubeRateLimit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeRateLimitTest {

    @Before
    fun reset() = YouTubeRateLimit.clear()

    @After
    fun tearDown() = YouTubeRateLimit.clear()

    @Test
    fun `a 429 arms the hold and reports it as a rate limit`() {
        assertTrue(YouTubeRateLimit.note(429, "browse FEsubscriptions"))
        assertTrue(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `a 403 does not arm the hold`() {
        assertFalse(YouTubeRateLimit.note(403, "googlevideo"))
        assertFalse(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `a 404 does not arm the hold`() {
        assertFalse(YouTubeRateLimit.note(404, "channel feed"))
        assertFalse(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `success does not arm the hold`() {
        assertFalse(YouTubeRateLimit.note(200, "browse"))
        assertFalse(YouTubeRateLimit.isHeld())
    }
}
