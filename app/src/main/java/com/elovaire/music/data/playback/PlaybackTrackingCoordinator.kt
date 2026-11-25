package elovaire.music.droidbeauty.app.data.playback

import elovaire.music.droidbeauty.app.core.AndroidAppClock
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-scoped coordinator that observes [playbackState] and [progressState] directly so Compasso
 * preloading, outcome tracking, YouTube playback history, listening stats, and scrobbling continue
 * running reliably in the background (even when the screen is off).
 */
internal class PlaybackTrackingCoordinator(
    private val playbackState: StateFlow<PlaybackUiState>,
    private val progressState: StateFlow<PlaybackProgressState>,
    private val scope: CoroutineScope,
    private val onEnqueueTracks: (List<Song>) -> Unit,
    private val onPositionChanged: suspend (
        currentIndex: Int,
        currentQueue: List<Song>,
        queueSnapshotProvider: () -> Pair<Int, List<Song>>,
        onAppendTracks: (List<Song>) -> Unit,
    ) -> Unit,
    private val onRecordOutcome: suspend (song: Song, playedMs: Long, wasSkipped: Boolean) -> Unit,
    private val onReportYouTubeHistory: suspend (song: Song, playedSeconds: Int) -> Unit,
    private val onRecordRecentSong: (Song) -> Unit,
    private val onRecordSongPlayed: (Song, Long) -> Unit,
    private val onAddListenedDuration: (Song, Long) -> Unit,
    private val onSavePlaybackSession: (List<Song>, Int, Long) -> Unit,
    private val onUpdateSongGenre: (Long, String) -> Unit,
    private val onUpdateNowPlaying: suspend (Song) -> Unit,
    private val onScrobble: suspend (Song) -> Unit,
    private val onResolveGenre: suspend (Song) -> String?,
    private val nowElapsedMs: () -> Long = AndroidAppClock::elapsedTimeMs,
    private val tickIntervalMs: Long = 1_000L,
) {
    private val lock = Any()
    private var started = false
    private var stateJob: Job? = null
    private var progressJob: Job? = null
    private var tickerJob: Job? = null

    private var activeSong: Song? = null
    private var activeIndex: Int = -1
    private var lastQueueSize: Int = 0
    private var lastIsPlaying: Boolean = false
    private var lastPlayTickElapsedMs: Long? = null
    private var lastObservedPositionMs: Long = 0L

    private var accumulatedPlayMs: Long = 0L
    private var reportedExtraMs: Long = 0L
    private var reportedYouTubeHistory: Boolean = false
    private var recordedCurrentTrack: Boolean = false
    private var trackInstanceId: Long = 0L

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        stateJob = scope.launch {
            playbackState.collect { state ->
                handlePlaybackState(state)
            }
        }
        progressJob = scope.launch {
            progressState.collect { progress ->
                handleProgressState(progress)
            }
        }
        tickerJob = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                handleTick()
            }
        }
    }

    private fun handlePlaybackState(state: PlaybackUiState) {
        val current = state.currentSong
        val currentIndex = state.currentIndex
        val queue = state.queue
        val progressPos = progressState.value.positionMs

        var finalizedOutcome: Triple<Song, Long, Boolean>? = null
        var finalizedYouTubeReport: Pair<Song, Int>? = null
        var finalizedTailDuration: Pair<Song, Long>? = null
        var startedNewTrack: Song? = null
        var shouldTriggerPositionChanged = false

        synchronized(lock) {
            flushElapsedLocked(nowElapsedMs())

            val prev = activeSong
            val isSameSongRepeated = prev != null &&
                current != null &&
                prev.id == current.id &&
                accumulatedPlayMs >= REPEAT_MIN_PLAYED_MS &&
                progressPos <= REPEAT_START_WINDOW_MS &&
                lastObservedPositionMs > REPEAT_MIN_PLAYED_MS

            val trackChanged = prev?.id != current?.id ||
                (current != null && currentIndex != activeIndex) ||
                isSameSongRepeated

            if (trackChanged) {
                if (prev != null) {
                    val prevPlayedMs = accumulatedPlayMs
                    if (recordedCurrentTrack && prevPlayedMs > reportedExtraMs) {
                        finalizedTailDuration = prev to (prevPlayedMs - reportedExtraMs)
                    }
                    if (prevPlayedMs >= YOUTUBE_HISTORY_MIN_MS && !reportedYouTubeHistory) {
                        reportedYouTubeHistory = true
                        finalizedYouTubeReport = prev to (prevPlayedMs / 1_000L).toInt()
                    }
                    val wasSkipped = prev.durationMs <= 0L ||
                        prevPlayedMs < (prev.durationMs * SKIP_THRESHOLD_RATIO).toLong()
                    finalizedOutcome = Triple(prev, prevPlayedMs, wasSkipped)
                }

                activeSong = current
                activeIndex = currentIndex
                accumulatedPlayMs = 0L
                reportedExtraMs = 0L
                reportedYouTubeHistory = false
                recordedCurrentTrack = false
                lastObservedPositionMs = progressPos
                trackInstanceId += 1L

                if (current != null) {
                    startedNewTrack = current
                    shouldTriggerPositionChanged = queue.isNotEmpty()
                }
            } else if (current != null && queue.isNotEmpty() && queue.size != lastQueueSize) {
                shouldTriggerPositionChanged = true
            }

            lastQueueSize = queue.size
            lastIsPlaying = state.isPlaying && current != null
            lastPlayTickElapsedMs = if (lastIsPlaying) nowElapsedMs() else null
        }

        finalizedTailDuration?.let { (song, delta) ->
            onAddListenedDuration(song, delta)
        }
        finalizedYouTubeReport?.let { (song, playedSeconds) ->
            scope.launch {
                runCatching { onReportYouTubeHistory(song, playedSeconds) }
            }
        }
        finalizedOutcome?.let { (song, playedMs, wasSkipped) ->
            scope.launch {
                runCatching { onRecordOutcome(song, playedMs, wasSkipped) }
            }
        }
        startedNewTrack?.let { song ->
            onRecordRecentSong(song)
            scope.launch {
                runCatching { onUpdateNowPlaying(song) }
            }
        }
        if (shouldTriggerPositionChanged && current != null) {
            scope.launch {
                runCatching {
                    onPositionChanged(
                        currentIndex,
                        queue,
                        { playbackState.value.currentIndex to playbackState.value.queue },
                        onEnqueueTracks,
                    )
                }
            }
        }
    }

    private fun handleProgressState(progress: PlaybackProgressState) {
        val state = playbackState.value
        val current = state.currentSong ?: return
        var finalizedOutcome: Triple<Song, Long, Boolean>? = null
        var finalizedYouTubeReport: Pair<Song, Int>? = null
        var finalizedTailDuration: Pair<Song, Long>? = null
        var restartedTrack: Song? = null

        synchronized(lock) {
            val prev = activeSong
            val isRepeatRestart = prev != null &&
                prev.id == current.id &&
                accumulatedPlayMs >= REPEAT_MIN_PLAYED_MS &&
                progress.positionMs <= REPEAT_START_WINDOW_MS &&
                lastObservedPositionMs > REPEAT_MIN_PLAYED_MS

            if (isRepeatRestart) {
                flushElapsedLocked(nowElapsedMs())
                val prevPlayedMs = accumulatedPlayMs
                if (recordedCurrentTrack && prevPlayedMs > reportedExtraMs) {
                    finalizedTailDuration = prev to (prevPlayedMs - reportedExtraMs)
                }
                if (prevPlayedMs >= YOUTUBE_HISTORY_MIN_MS && !reportedYouTubeHistory) {
                    reportedYouTubeHistory = true
                    finalizedYouTubeReport = prev to (prevPlayedMs / 1_000L).toInt()
                }
                val wasSkipped = prev.durationMs <= 0L ||
                    prevPlayedMs < (prev.durationMs * SKIP_THRESHOLD_RATIO).toLong()
                finalizedOutcome = Triple(prev, prevPlayedMs, wasSkipped)

                accumulatedPlayMs = 0L
                reportedExtraMs = 0L
                reportedYouTubeHistory = false
                recordedCurrentTrack = false
                trackInstanceId += 1L
                lastPlayTickElapsedMs = if (state.isPlaying) nowElapsedMs() else null
                restartedTrack = current
            }
            lastObservedPositionMs = progress.positionMs
        }

        finalizedTailDuration?.let { (song, delta) ->
            onAddListenedDuration(song, delta)
        }
        finalizedYouTubeReport?.let { (song, playedSeconds) ->
            scope.launch {
                runCatching { onReportYouTubeHistory(song, playedSeconds) }
            }
        }
        finalizedOutcome?.let { (song, playedMs, wasSkipped) ->
            scope.launch {
                runCatching { onRecordOutcome(song, playedMs, wasSkipped) }
            }
        }
        restartedTrack?.let { song ->
            onRecordRecentSong(song)
            scope.launch {
                runCatching { onUpdateNowPlaying(song) }
            }
            val queue = state.queue
            if (queue.isNotEmpty()) {
                scope.launch {
                    runCatching {
                        onPositionChanged(
                            state.currentIndex,
                            queue,
                            { playbackState.value.currentIndex to playbackState.value.queue },
                            onEnqueueTracks,
                        )
                    }
                }
            }
        }
    }

    private fun handleTick() {
        val state = playbackState.value
        val current = state.currentSong
        val isActuallyPlaying = state.isPlaying && current != null
        val positionMs = progressState.value.positionMs

        var ytReport: Pair<Song, Int>? = null
        var scrobbleSong: Song? = null
        var extraDuration: Pair<Song, Long>? = null
        var sessionSnapshot: Triple<List<Song>, Int, Long>? = null

        synchronized(lock) {
            if (!isActuallyPlaying || current == null || activeSong?.id != current.id) {
                lastPlayTickElapsedMs = null
                return
            }
            val now = nowElapsedMs()
            flushElapsedLocked(now)
            lastPlayTickElapsedMs = now

            if (!reportedYouTubeHistory &&
                accumulatedPlayMs >= YOUTUBE_HISTORY_MIN_MS &&
                current.durationMs > 0L &&
                positionMs >= (current.durationMs - 2_000L)
            ) {
                reportedYouTubeHistory = true
                ytReport = current to (accumulatedPlayMs / 1_000L).toInt()
            }

            if (!recordedCurrentTrack && accumulatedPlayMs >= SCROBBLE_THRESHOLD_MS) {
                recordedCurrentTrack = true
                reportedExtraMs = SCROBBLE_THRESHOLD_MS
                scrobbleSong = current
            } else if (recordedCurrentTrack && accumulatedPlayMs - reportedExtraMs >= STATS_FLUSH_INTERVAL_MS) {
                val delta = accumulatedPlayMs - reportedExtraMs
                reportedExtraMs = accumulatedPlayMs
                extraDuration = current to delta
                val queue = state.queue.ifEmpty { listOf(current) }
                val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                sessionSnapshot = Triple(queue, index, positionMs)
            }
        }

        ytReport?.let { (song, playedSeconds) ->
            scope.launch {
                runCatching { onReportYouTubeHistory(song, playedSeconds) }
            }
        }
        scrobbleSong?.let { song ->
            onRecordSongPlayed(song, SCROBBLE_THRESHOLD_MS)
            scope.launch {
                runCatching { onScrobble(song) }
                if (song.genre.isBlank() || song.genre.equals("Unknown Genre", ignoreCase = true)) {
                    val resolvedGenre = runCatching { onResolveGenre(song) }.getOrNull()
                    if (!resolvedGenre.isNullOrBlank()) {
                        onUpdateSongGenre(song.id, resolvedGenre)
                    }
                }
            }
        }
        extraDuration?.let { (song, delta) ->
            onAddListenedDuration(song, delta)
        }
        sessionSnapshot?.let { (queue, index, pos) ->
            onSavePlaybackSession(queue, index, pos)
        }
    }

    private fun flushElapsedLocked(nowMs: Long) {
        val lastTick = lastPlayTickElapsedMs ?: return
        if (!lastIsPlaying || activeSong == null) {
            lastPlayTickElapsedMs = null
            return
        }
        val delta = (nowMs - lastTick).coerceAtLeast(0L)
        if (delta > 0L) {
            accumulatedPlayMs += delta
        }
        lastPlayTickElapsedMs = nowMs
    }

    private companion object {
        const val YOUTUBE_HISTORY_MIN_MS = 15_000L
        const val SCROBBLE_THRESHOLD_MS = 30_000L
        const val STATS_FLUSH_INTERVAL_MS = 5_000L
        const val REPEAT_MIN_PLAYED_MS = 5_000L
        const val REPEAT_START_WINDOW_MS = 2_000L
        const val SKIP_THRESHOLD_RATIO = 0.80f
    }
}
