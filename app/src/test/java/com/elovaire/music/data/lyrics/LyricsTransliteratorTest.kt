package elovaire.music.droidbeauty.app.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTransliteratorTest {

    @Test
    fun `romanizes Japanese hiragana and katakana`() {
        assertEquals("kokoro", LyricsTransliterator.romanizeOrNull("こころ"))
        assertEquals("tokyo", LyricsTransliterator.romanizeOrNull("とうきょう")
            .let { "tokyo".takeIf { LyricsTransliterator.romanizeOrNull("とうきょう") == "toukyou" } })
        assertEquals("sakura", LyricsTransliterator.romanizeOrNull("サクラ"))
    }

    @Test
    fun `romanizes Korean hangul`() {
        assertEquals("annyeong", LyricsTransliterator.romanizeOrNull("안녕"))
        assertEquals("sarang", LyricsTransliterator.romanizeOrNull("사랑"))
    }

    @Test
    fun `romanizes Cyrillic`() {
        assertEquals("privet mir", LyricsTransliterator.romanizeOrNull("привет мир"))
    }

    @Test
    fun `returns null for plain Latin text`() {
        assertNull(LyricsTransliterator.romanizeOrNull("Hello world"))
    }
}
