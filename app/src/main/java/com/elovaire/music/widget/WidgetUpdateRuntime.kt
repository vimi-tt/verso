package elovaire.music.droidbeauty.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.GlanceId
import elovaire.music.droidbeauty.app.core.AndroidAppClock
import elovaire.music.droidbeauty.app.core.AppClock
import elovaire.music.droidbeauty.app.core.backend.BackendDiagnosticRecorder
import elovaire.music.droidbeauty.app.core.backend.BackendDiagnostics
import elovaire.music.droidbeauty.app.data.lyrics.LyricsPayload
import elovaire.music.droidbeauty.app.data.playback.PlaybackReader
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class WidgetLyricsSnapshot(
    val songId: Long? = null,
    val currentLine: String? = null,
    val nextLine: String? = null,
)

internal object WidgetLyricsBus {
    private val _state = MutableStateFlow(WidgetLyricsSnapshot())
    val state: StateFlow<WidgetLyricsSnapshot> = _state.asStateFlow()

    fun update(songId: Long?, currentLine: String?, nextLine: String?): Boolean {
        val next = WidgetLyricsSnapshot(songId, currentLine?.takeIf { it.isNotBlank() }, nextLine?.takeIf { it.isNotBlank() })
        if (_state.value == next) return false
        _state.value = next
        return true
    }
}

internal interface WidgetProviderUpdater {
    suspend fun activeInstanceCount(): Int
    suspend fun updateAll()
    val supportsLiveLyrics: Boolean get() = false
}

internal class GlanceWidgetProviderUpdater(
    context: Context,
    private val widget: GlanceAppWidget,
    receiver: Class<out GlanceAppWidgetReceiver>,
    override val supportsLiveLyrics: Boolean = false,
) : WidgetProviderUpdater {
    private val applicationContext = context.applicationContext
    private val manager = GlanceAppWidgetManager(applicationContext)
    private val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
    private val component = ComponentName(applicationContext, receiver)

    override suspend fun activeInstanceCount(): Int = withContext(Dispatchers.IO) {
        glanceIds().size
    }

    override suspend fun updateAll() {
        glanceIds().forEach { glanceId -> widget.update(applicationContext, glanceId) }
    }

    private suspend fun glanceIds(): List<GlanceId> = withContext(Dispatchers.IO) {
        appWidgetManager.getAppWidgetIds(component).asSequence().mapNotNull { appWidgetId ->
            try {
                manager.getGlanceIdBy(appWidgetId)
            } catch (_: IllegalArgumentException) {
                null // The host removed the widget between the two queries
            }
        }.toList()
    }
}

internal object WidgetProviderRegistry {
    fun productionProviders(context: Context): List<WidgetProviderUpdater> = listOf(
        GlanceWidgetProviderUpdater(context, CompactWidget(), CompactWidgetReceiver::class.java),
        GlanceWidgetProviderUpdater(context, CoverWidget(), CoverWidgetReceiver::class.java),
        GlanceWidgetProviderUpdater(context, StudioWidget(), StudioWidgetReceiver::class.java, supportsLiveLyrics = true),
        GlanceWidgetProviderUpdater(context, ControlsWidget(), ControlsWidgetReceiver::class.java),
    )
}

internal class WidgetUpdateRuntime(
    private val playbackReader: PlaybackReader,
    private val snapshotStore: WidgetSnapshotStore,
    private val scope: CoroutineScope,
    private val providers: List<WidgetProviderUpdater>,
    private val clock: AppClock = AndroidAppClock,
    private val diagnostics: BackendDiagnosticRecorder = BackendDiagnostics,
    private val language: StateFlow<AppLanguage>? = null,
    private val lyricsFetcher: (suspend (Song) -> LyricsPayload?)? = null,
    private val positionMsProvider: (() -> Long)? = null,
) {
    private val lock = Any()
    private var closed = false
    private var reconciliationJob: Job? = null
    private var reconciliationPending = false
    private var observationJob: Job? = null
    private var lyricsJob: Job? = null
    private var observedProviders: List<WidgetProviderUpdater> = emptyList()

    @Suppress("TooGenericExceptionCaught")
    fun onProviderLifecycleChanged() {
        if (providers.isEmpty()) return
        synchronized(lock) {
            if (closed) return
            if (reconciliationJob?.isActive == true) {
                reconciliationPending = true
                return
            }
            val currentJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    do {
                        synchronized(lock) { reconciliationPending = false }
                        reconcileInstances()
                    } while (synchronized(lock) { reconciliationPending && !closed })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    diagnostics.recordWorkerFailure("widget-provider-lifecycle", failure)
                } finally {
                    val completedJob = currentCoroutineContext()[Job]
                    val retry = synchronized(lock) {
                        if (reconciliationJob === completedJob) reconciliationJob = null
                        reconciliationPending && !closed
                    }
                    if (retry) onProviderLifecycleChanged()
                }
            }
            reconciliationJob = currentJob
            currentJob.start()
        }
    }

    fun release() {
        val jobs = synchronized(lock) {
            if (closed) return
            closed = true
            listOfNotNull(reconciliationJob, observationJob, lyricsJob).also {
                reconciliationJob = null
                observationJob = null
                lyricsJob = null
                observedProviders = emptyList()
            }
        }
        jobs.forEach { job -> job.cancel() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun reconcileInstances() {
        val activeProviders = providers.filter { provider ->
            try {
                provider.activeInstanceCount() > 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                diagnostics.recordWorkerFailure("widget-instance-query", failure)
                false
            }
        }
        synchronized(lock) {
            if (closed) return
            if (activeProviders != observedProviders) {
                observationJob?.cancel()
                observationJob = null
                lyricsJob?.cancel()
                lyricsJob = null
                observedProviders = activeProviders
            }
            if (activeProviders.isEmpty()) {
                observationJob?.cancel()
                observationJob = null
                lyricsJob?.cancel()
                lyricsJob = null
            } else {
                if (observationJob?.isActive != true) {
                    val job = scope.launch(start = CoroutineStart.LAZY) { observePlayback(activeProviders) }
                    observationJob = job
                    job.start()
                }
                val lyricsProviders = activeProviders.filter { it.supportsLiveLyrics }
                if (lyricsProviders.isNotEmpty() && lyricsFetcher != null && positionMsProvider != null && lyricsJob?.isActive != true) {
                    val job = scope.launch(start = CoroutineStart.LAZY) { observeLiveLyrics(lyricsProviders) }
                    lyricsJob = job
                    job.start()
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun observeLiveLyrics(lyricsProviders: List<WidgetProviderUpdater>) {
        val fetcher = lyricsFetcher ?: return
        val positionProvider = positionMsProvider ?: return
        var cachedSongId: Long? = null
        var cachedPayload: LyricsPayload? = null
        while (currentCoroutineContext().isActive) {
            val song = playbackReader.nowPlayingState.value.currentSong
            if (song == null) {
                if (WidgetLyricsBus.update(null, null, null)) {
                    WidgetRenderUpdates.notifyChanged()
                    lyricsProviders.forEach { runCatching { it.updateAll() } }
                }
                cachedSongId = null
                cachedPayload = null
                delay(LYRICS_IDLE_POLL_MS)
                continue
            }
            if (cachedSongId != song.id) {
                cachedSongId = song.id
                cachedPayload = runCatching { fetcher(song) }.getOrNull()
            }
            val payload = cachedPayload
            val lines = payload?.lines.orEmpty()
            val (currentLine, nextLine) = if (lines.isEmpty()) {
                null to null
            } else if (payload?.isSynced == true) {
                val posMs = positionProvider()
                val idx = (payload.currentLineIndexAt(posMs) ?: 0).coerceIn(0, lines.lastIndex)
                val curr = lines[idx].text.takeIf { it.isNotBlank() }
                val next = lines.drop(idx + 1).firstOrNull { it.text.isNotBlank() }?.text
                curr to next
            } else {
                val nonBlank = lines.map { it.text }.filter { it.isNotBlank() }
                nonBlank.getOrNull(0) to nonBlank.getOrNull(1)
            }
            if (WidgetLyricsBus.update(song.id, currentLine, nextLine)) {
                WidgetRenderUpdates.notifyChanged()
                lyricsProviders.forEach { runCatching { it.updateAll() } }
            }
            val playing = playbackReader.transportState.value.isPlaying
            delay(if (playing && payload?.isSynced == true) LYRICS_ACTIVE_POLL_MS else LYRICS_IDLE_POLL_MS)
        }
    }

    @OptIn(FlowPreview::class)
    @Suppress("TooGenericExceptionCaught")
    private suspend fun observePlayback(activeProviders: List<WidgetProviderUpdater>) {
        var previous = snapshotStore.readLatest()
        var previousLanguage: AppLanguage? = null
        combine(
            playbackReader.nowPlayingState,
            playbackReader.transportState,
            playbackReader.queueState,
            language ?: flowOf(AppLanguage.English),
            ::PlaybackSample,
        ).debounce(UPDATE_COALESCE_MS).collect { sample ->
            val snapshot = projectWidgetPlaybackSnapshot(
                nowPlaying = sample.nowPlaying,
                transport = sample.transport,
                queue = sample.queue,
                previous = previous,
                elapsedRealtimeMs = clock.elapsedTimeMs(),
            )
            val changed = try {
                snapshotStore.writeIfChanged(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                diagnostics.recordWorkerFailure("widget-snapshot-write", failure)
                false
            }
            val languageChanged = previousLanguage != sample.language
            previousLanguage = sample.language
            if (changed || languageChanged) {
                if (changed) {
                    previous = snapshot
                    WidgetRenderUpdates.notifyChanged()
                }
                activeProviders.forEach { provider ->
                    try {
                        provider.updateAll()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        diagnostics.recordWorkerFailure("widget-provider-update", failure)
                    }
                }
            }
        }
    }

    private data class PlaybackSample(
        val nowPlaying: elovaire.music.droidbeauty.app.data.playback.PlaybackNowPlayingState,
        val transport: elovaire.music.droidbeauty.app.data.playback.PlaybackTransportState,
        val queue: elovaire.music.droidbeauty.app.data.playback.PlaybackQueueState,
        val language: AppLanguage,
    )

    private companion object {
        const val UPDATE_COALESCE_MS = 50L
        const val LYRICS_ACTIVE_POLL_MS = 750L
        const val LYRICS_IDLE_POLL_MS = 2_000L
    }
}
