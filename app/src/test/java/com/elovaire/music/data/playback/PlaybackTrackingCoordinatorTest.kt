package elovaire.music.droidbeauty.app.data.playback

import android.net.TestUri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTrackingCoordinatorTest {

    @Test
    fun `counts play time only while playing and does not duplicate scrobble or YouTube history`() = runTest {
        var fakeElapsedMs = 0L
        val playbackState = MutableStateFlow(PlaybackUiState())
        val progressState = MutableStateFlow(PlaybackProgressState())

        val scrobbled = mutableListOf<Long>()
        val ytReported = mutableListOf<Pair<Long, Int>>()
        val outcomes = mutableListOf<Triple<Long, Long, Boolean>>()

        val coordinator = PlaybackTrackingCoordinator(
            playbackState = playbackState,
            progressState = progressState,
            scope = backgroundScope,
            onEnqueueTracks = {},
            onPositionChanged = { _, _, _, _ -> },
            onRecordOutcome = { song, playedMs, skipped -> outcomes += Triple(song.id, playedMs, skipped) },
            onReportYouTubeHistory = { song, sec -> ytReported += song.id to sec },
            onRecordRecentSong = {},
            onRecordSongPlayed = { _, _ -> },
            onAddListenedDuration = { _, _ -> },
            onSavePlaybackSession = { _, _, _ -> },
            onUpdateSongGenre = { _, _ -> },
            onUpdateNowPlaying = {},
            onScrobble = { song -> scrobbled += song.id },
            onResolveGenre = { null },
            nowElapsedMs = { fakeElapsedMs },
            tickIntervalMs = 1_000L,
        )
        coordinator.start()

        val song = testSong(id = 10L, durationMs = 40_000L)
        playbackState.value = PlaybackUiState(queue = listOf(song), currentIndex = 0, isPlaying = true)
        runCurrent()

        // Advance 10s while playing
        repeat(10) {
            fakeElapsedMs += 1_000L
            progressState.value = progressState.value.copy(positionMs = fakeElapsedMs)
            advanceTimeBy(1_000L)
            runCurrent()
        }

        // Pause for 20s — time must NOT advance
        playbackState.value = playbackState.value.copy(isPlaying = false)
        runCurrent()
        repeat(20) {
            fakeElapsedMs += 1_000L
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(0, scrobbled.size)

        // Resume for 29s (total 39s >= 30s scrobble and >= 38s YouTube near-end)
        playbackState.value = playbackState.value.copy(isPlaying = true)
        runCurrent()
        repeat(29) {
            fakeElapsedMs += 1_000L
            val pos = progressState.value.positionMs + 1_000L
            progressState.value = progressState.value.copy(positionMs = pos)
            advanceTimeBy(1_000L)
            runCurrent()
        }

        assertEquals(listOf(10L), scrobbled)
        assertEquals(1, ytReported.size)

        // End of queue (last track finishes) -> outcome recorded, YouTube history NOT duplicated
        playbackState.value = PlaybackUiState(queue = listOf(song), currentIndex = -1, isPlaying = false)
        runCurrent()

        assertEquals(1, outcomes.size)
        assertEquals(10L, outcomes.first().first)
        assertFalse(outcomes.first().third) // 39s / 40s > 80% -> not skipped
        assertEquals(1, ytReported.size)
        assertEquals(1, scrobbled.size)
    }

    @Test
    fun `records outcome and new scrobble when same track repeats`() = runTest {
        var fakeElapsedMs = 0L
        val playbackState = MutableStateFlow(PlaybackUiState())
        val progressState = MutableStateFlow(PlaybackProgressState())

        val scrobbled = mutableListOf<Long>()
        val outcomes = mutableListOf<Triple<Long, Long, Boolean>>()

        val coordinator = PlaybackTrackingCoordinator(
            playbackState = playbackState,
            progressState = progressState,
            scope = backgroundScope,
            onEnqueueTracks = {},
            onPositionChanged = { _, _, _, _ -> },
            onRecordOutcome = { song, playedMs, skipped -> outcomes += Triple(song.id, playedMs, skipped) },
            onReportYouTubeHistory = { _, _ -> },
            onRecordRecentSong = {},
            onRecordSongPlayed = { _, _ -> },
            onAddListenedDuration = { _, _ -> },
            onSavePlaybackSession = { _, _, _ -> },
            onUpdateSongGenre = { _, _ -> },
            onUpdateNowPlaying = {},
            onScrobble = { song -> scrobbled += song.id },
            onResolveGenre = { null },
            nowElapsedMs = { fakeElapsedMs },
            tickIntervalMs = 1_000L,
        )
        coordinator.start()

        val song = testSong(id = 20L, durationMs = 35_000L)
        playbackState.value = PlaybackUiState(queue = listOf(song), currentIndex = 0, isPlaying = true)
        runCurrent()

        repeat(32) {
            fakeElapsedMs += 1_000L
            progressState.value = progressState.value.copy(positionMs = (it + 1) * 1_000L)
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(1, scrobbled.size)

        // Repeat same track: position jumps back to 500ms
        fakeElapsedMs += 1_000L
        progressState.value = progressState.value.copy(positionMs = 500L)
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(1, outcomes.size)
        assertTrue(outcomes.first().second >= 32_000L)

        // Play repeated track for 31s -> should scrobble a second time
        repeat(31) {
            fakeElapsedMs += 1_000L
            progressState.value = progressState.value.copy(positionMs = 500L + (it + 1) * 1_000L)
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(2, scrobbled.size)
    }

    private fun testSong(id: Long, durationMs: Long): Song = Song(
        id = id,
        title = "Track $id",
        isExplicit = false,
        artist = "Artist $id",
        album = "Album",
        releaseYear = null,
        genre = "Pop",
        audioFormat = "YouTube",
        audioQuality = null,
        fileName = "",
        albumId = 0L,
        durationMs = durationMs,
        trackNumber = 1,
        discNumber = 1,
        dateAddedSeconds = 0L,
        uri = TestUri("youtube://video/vid_$id"),
        artUri = null,
        mediaKind = AudioMediaKind.Music,
    )
}
