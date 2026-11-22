package elovaire.music.droidbeauty.app.data.compasso

import android.net.Uri
import androidx.compose.runtime.Immutable
import elovaire.music.droidbeauty.app.data.library.db.BlockedArtistEntity
import elovaire.music.droidbeauty.app.data.library.db.BlockedTrackEntity
import elovaire.music.droidbeauty.app.data.library.db.PersistedQueueEntity
import elovaire.music.droidbeauty.app.data.library.db.PlaybackHistoryEntity
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
internal data class CompassoUiState(
    val isActive: Boolean = true,
    val discoveryRatio: Float = CompassoEngine.DEFAULT_DISCOVERY_RATIO,
    val isLoadingMore: Boolean = false,
    val seedTitle: String? = null,
    val queuedAheadCount: Int = 0,
)

internal enum class DayPeriod {
    Dawn,      // 00:00 - 05:59
    Morning,   // 06:00 - 11:59
    Afternoon, // 12:00 - 17:59
    Night,     // 18:00 - 23:59
    ;

    companion object {
        fun fromTimestampMs(timestampMs: Long): DayPeriod {
            val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
            return fromHour(cal.get(Calendar.HOUR_OF_DAY))
        }

        fun fromHour(hourOfDay: Int): DayPeriod = when (hourOfDay) {
            in 0..5 -> Dawn
            in 6..11 -> Morning
            in 12..17 -> Afternoon
            else -> Night
        }
    }
}

/**
 * Compasso continuous recommendation engine.
 *
 * Uses YouTube Music `/next` (`RDAMVM{videoId}`) as the raw candidate provider, then applies:
 * 1. Audio purity filtering (`MUSIC_VIDEO_TYPE_ATV` preferred, `UGC` dropped, `OMV` last-resort only).
 * 2. Multi-layer deduplication against the active queue, last 50 history items, session keys, and permanent blocks.
 * 3. Artist diversity window (at most 1 track by the same artist every 5 consecutive songs) with a waiting buffer.
 * 4. Discovery vs. familiarity interleaving (`discoveryRatio`, default 0.7f) weighted by time-of-day and real-time
 *    session signals (early skip < 30s penalty, completed play > 80% or like bonus).
 * 5. Resilient continuation loop (up to 4 pages) + fallback seed rotation when continuation ends or fails.
 */
internal class CompassoEngine(
    private val radioSource: CompassoRadioSource,
    private val store: CompassoStore,
    private val affinityTracker: SessionAffinityTracker = SessionAffinityTracker(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val videoIdExtractor: (Song) -> String? = { YouTubeUri.videoId(it.uri) },
) {
    private val _uiState = MutableStateFlow(CompassoUiState())
    val uiState: StateFlow<CompassoUiState> = _uiState.asStateFlow()

    private val fetchMutex = Mutex()
    private val sessionNormalizedKeys = LinkedHashSet<String>()
    private val waitingBuffer = ArrayList<CompassoCandidate>()

    private var activeSeedVideoId: String? = null
    private var continuationToken: String? = null
    private var userExplicitlyDisabled: Boolean = false

    fun setActive(enabled: Boolean) {
        userExplicitlyDisabled = !enabled
        _uiState.update { it.copy(isActive = enabled) }
    }

    fun setDiscoveryRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1f)
        _uiState.update { it.copy(discoveryRatio = clamped) }
    }

    /**
     * Switches discovery mode (Familiar / 70-30 / Explore) and immediately refreshes the upcoming
     * queue using YouTube Music's matching Flow tuner setting (`FAMILIAR` / `NORMAL` / `DISCOVER`).
     */
    suspend fun switchDiscoveryMode(
        ratio: Float,
        currentSong: Song?,
        currentQueue: List<Song>,
        currentIndex: Int,
        onReplaceUpcoming: (List<Song>) -> Unit,
    ): List<Song> {
        val clamped = ratio.coerceIn(0f, 1f)
        _uiState.update { it.copy(isActive = true, discoveryRatio = clamped) }
        userExplicitlyDisabled = false
        waitingBuffer.clear()
        continuationToken = null
        val seedSong = currentSong ?: currentQueue.getOrNull(currentIndex) ?: return emptyList()
        val seedId = videoIdExtractor(seedSong)
            ?: withContext(ioDispatcher) { runCatching { radioSource.resolveAtvSeedVideoId(seedSong) }.getOrNull() }
        if (seedId != null) {
            activeSeedVideoId = seedId
        }
        val safeIndex = currentIndex.coerceIn(0, (currentQueue.lastIndex).coerceAtLeast(0))
        val prefixQueue = if (currentQueue.isNotEmpty()) currentQueue.take(safeIndex + 1) else listOf(seedSong)
        return fillAheadIfNeeded(
            currentIndex = prefixQueue.lastIndex.coerceAtLeast(0),
            currentQueue = prefixQueue,
            forceFetch = true,
            onAppendTracks = onReplaceUpcoming,
        )
    }

    private fun tunerSettingForRatio(ratio: Float): String = when {
        ratio <= 0.45f -> "AUTOMIX_SETTING_FAMILIAR"
        ratio > 0.80f -> "AUTOMIX_SETTING_DISCOVER"
        else -> "AUTOMIX_SETTING_NORMAL"
    }

    /**
     * Starts a Compasso session from [seedSong], resolving to an ATV studio track if the seed
     * looks like an OMV/UGC video, and returns the initial batch of recommended tracks to append.
     */
    suspend fun startFromSeed(
        seedSong: Song,
        seedMusicVideoType: String? = null,
        currentQueue: List<Song> = listOf(seedSong),
        onAppendTracks: (List<Song>) -> Unit,
    ): List<Song> {
        val rawVideoId = videoIdExtractor(seedSong)
            ?: withContext(ioDispatcher) { runCatching { radioSource.resolveAtvSeedVideoId(seedSong) }.getOrNull() }
            ?: return emptyList()
        userExplicitlyDisabled = false
        val seedKey = TrackNormalizer.normalizeKey(seedSong.title, seedSong.artist)
        sessionNormalizedKeys.clear()
        sessionNormalizedKeys.add(seedKey)
        waitingBuffer.clear()
        continuationToken = null

        val resolvedSeedId = if (seedMusicVideoType == MUSIC_VIDEO_TYPE_OMV || seedMusicVideoType == MUSIC_VIDEO_TYPE_UGC) {
            withContext(ioDispatcher) { radioSource.resolveAtvSeedVideoId(seedSong) } ?: rawVideoId
        } else {
            rawVideoId
        }

        activeSeedVideoId = resolvedSeedId
        _uiState.update {
            it.copy(
                isActive = true,
                seedTitle = seedSong.title,
            )
        }
        return fillAheadIfNeeded(
            currentIndex = currentQueue.indexOfFirst { it.id == seedSong.id }.coerceAtLeast(0),
            currentQueue = currentQueue,
            forceFetch = true,
            onAppendTracks = onAppendTracks,
        )
    }

    /**
     * Called whenever playback advances or the queue changes. Automatically preloads when
     * `<= PRELOAD_THRESHOLD_TRACKS` (5) tracks remain ahead.
     */
    suspend fun onPlaybackPositionChanged(
        currentIndex: Int,
        currentQueue: List<Song>,
        autoActivateAtEndOfCollection: Boolean = true,
        onAppendTracks: (List<Song>) -> Unit,
    ) {
        val remainingAhead = (currentQueue.size - 1 - currentIndex).coerceAtLeast(0)
        _uiState.update { it.copy(queuedAheadCount = remainingAhead) }

        val currentSong = currentQueue.getOrNull(currentIndex)
        if (currentSong != null) {
            sessionNormalizedKeys.add(TrackNormalizer.normalizeKey(currentSong.title, currentSong.artist))
        }

        if (!_uiState.value.isActive) {
            if (!userExplicitlyDisabled && autoActivateAtEndOfCollection && remainingAhead <= 1 && currentSong != null) {
                val ytId = videoIdExtractor(currentSong)
                    ?: withContext(ioDispatcher) { runCatching { radioSource.resolveAtvSeedVideoId(currentSong) }.getOrNull() }
                if (ytId != null) {
                    activeSeedVideoId = ytId
                    continuationToken = null
                    _uiState.update { it.copy(isActive = true, seedTitle = currentSong.title) }
                } else {
                    return
                }
            } else {
                return
            }
        }

        if (remainingAhead <= PRELOAD_THRESHOLD_TRACKS) {
            fillAheadIfNeeded(
                currentIndex = currentIndex,
                currentQueue = currentQueue,
                forceFetch = false,
                onAppendTracks = onAppendTracks,
            )
        }
    }

    /**
     * Called directly by [PlaybackManager] when the user taps Next on the last track or when the
     * last track reaches [Player.STATE_ENDED].
     *
     * Returns:
     * - `null` if the user explicitly turned Compasso off (caller may stop playback normally).
     * - non-empty `List<Song>` with tracks to enqueue and play immediately.
     * - `emptyList()` if Compasso is active but offline / no candidates could be fetched (caller
     *   should keep the player open instead of closing it).
     */
    suspend fun fetchTracksForEndOfQueue(
        currentSong: Song,
        currentQueue: List<Song>,
        currentIndex: Int,
    ): List<Song>? {
        if (!_uiState.value.isActive && userExplicitlyDisabled) {
            return null
        }
        val currentVideoId = videoIdExtractor(currentSong)
            ?: withContext(ioDispatcher) { runCatching { radioSource.resolveAtvSeedVideoId(currentSong) }.getOrNull() }
        if (!_uiState.value.isActive) {
            if (currentVideoId == null) return null
            activeSeedVideoId = currentVideoId
            continuationToken = null
            _uiState.update { it.copy(isActive = true, seedTitle = currentSong.title) }
        } else if (activeSeedVideoId == null && currentVideoId != null) {
            activeSeedVideoId = currentVideoId
        }
        return fillAheadIfNeeded(
            currentIndex = currentIndex,
            currentQueue = currentQueue,
            forceFetch = true,
            onAppendTracks = {},
        )
    }

    suspend fun recordPlaybackOutcome(
        song: Song,
        playedMs: Long,
        wasSkipped: Boolean,
    ) {
        val videoId = videoIdExtractor(song) ?: return
        val normalizedKey = TrackNormalizer.normalizeKey(song.title, song.artist)
        val skippedEarly = wasSkipped && playedMs < SessionAffinityTracker.EARLY_SKIP_THRESHOLD_MS
        val completed = song.durationMs > 0L &&
            (playedMs.toFloat() / song.durationMs.toFloat()) >= SessionAffinityTracker.COMPLETED_PLAY_RATIO

        if (skippedEarly) {
            affinityTracker.recordEarlySkip(song.artist)
        } else if (completed) {
            affinityTracker.recordCompletedPlay(videoId, song.artist)
        }

        sessionNormalizedKeys.add(normalizedKey)
        withContext(ioDispatcher) {
            runCatching {
                store.upsertHistory(
                    PlaybackHistoryEntity(
                        videoId = videoId,
                        normalizedKey = normalizedKey,
                        artistId = null,
                        artistName = song.artist,
                        title = song.title,
                        album = song.album,
                        artUri = song.artUri?.toString(),
                        durationMs = song.durationMs,
                        playedAt = nowMs(),
                        playDurationMs = playedMs.coerceAtLeast(0L),
                        wasSkippedEarly = skippedEarly,
                    ),
                )
            }
        }
    }

    fun recordLikeSignal(song: Song) {
        val videoId = videoIdExtractor(song)
        affinityTracker.recordLike(videoId, song.artist)
    }

    /**
     * Permanently blocks [song] in Room, removes it from the waiting buffer, and invokes
     * [onRemovedFromQueue] so the player removes it (and skips if it was the active track).
     */
    suspend fun blockTrack(
        song: Song,
        onRemovedFromQueue: () -> Unit,
    ) {
        val videoId = videoIdExtractor(song) ?: "local_${song.id}"
        val normalizedKey = TrackNormalizer.normalizeKey(song.title, song.artist)
        waitingBuffer.removeAll {
            it.videoId == videoId || TrackNormalizer.normalizeKey(it.song.title, it.song.artist) == normalizedKey
        }
        affinityTracker.recordEarlySkip(song.artist)
        withContext(ioDispatcher) {
            runCatching {
                store.insertBlockedTrack(
                    BlockedTrackEntity(
                        videoId = videoId,
                        normalizedKey = normalizedKey,
                        artistName = song.artist,
                        blockedAt = nowMs(),
                    ),
                )
            }
        }
        onRemovedFromQueue()
    }

    /**
     * Permanently blocks [artistName] in Room, removes matching tracks from the waiting buffer,
     * and invokes [onRemovedFromQueue] so the player removes upcoming tracks by that artist.
     */
    suspend fun blockArtist(
        artistName: String,
        onRemovedFromQueue: () -> Unit,
    ) {
        val normalizedArtist = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (normalizedArtist.isBlank()) return
        waitingBuffer.removeAll {
            TrackNormalizer.normalizePrimaryArtist(it.song.artist) == normalizedArtist
        }
        affinityTracker.recordEarlySkip(artistName)
        withContext(ioDispatcher) {
            runCatching {
                store.insertBlockedArtist(
                    BlockedArtistEntity(
                        normalizedArtist = normalizedArtist,
                        artistName = artistName.trim(),
                        blockedAt = nowMs(),
                    ),
                )
            }
        }
        onRemovedFromQueue()
    }

    suspend fun unblockArtist(artistName: String) {
        val normalizedArtist = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (normalizedArtist.isBlank()) return
        withContext(ioDispatcher) {
            runCatching { store.removeBlockedArtist(normalizedArtist) }
        }
    }

    suspend fun persistQueueState(
        queue: List<Song>,
        currentIndex: Int,
    ) {
        val snapshot = _uiState.value
        withContext(ioDispatcher) {
            runCatching {
                store.savePersistedQueue(
                    PersistedQueueEntity(
                        queueJson = CompassoQueueSerializer.encodeSongs(queue),
                        currentIndex = currentIndex.coerceAtLeast(0),
                        continuationToken = continuationToken,
                        discoveryRatio = snapshot.discoveryRatio,
                        isActive = snapshot.isActive,
                        seedVideoId = activeSeedVideoId,
                        updatedAt = nowMs(),
                    ),
                )
            }
        }
    }

    suspend fun restorePersistedQueueState(): PersistedQueueEntity? = withContext(ioDispatcher) {
        val saved = runCatching { store.getPersistedQueue() }.getOrNull() ?: return@withContext null
        continuationToken = saved.continuationToken
        activeSeedVideoId = saved.seedVideoId
        _uiState.update {
            it.copy(
                isActive = saved.isActive,
                discoveryRatio = saved.discoveryRatio.coerceIn(0f, 1f),
            )
        }
        saved
    }

    suspend fun fillAheadIfNeeded(
        currentIndex: Int,
        currentQueue: List<Song>,
        forceFetch: Boolean = false,
        onAppendTracks: (List<Song>) -> Unit,
    ): List<Song> = fetchMutex.withLock {
        val remainingAhead = (currentQueue.size - 1 - currentIndex).coerceAtLeast(0)
        val needed = (TARGET_BUFFER_AHEAD - remainingAhead).coerceAtLeast(0)
        if (!forceFetch && needed <= 0) return@withLock emptyList()
        val targetCount = if (needed > 0) needed else TARGET_BUFFER_AHEAD
        val tunerSetting = tunerSettingForRatio(_uiState.value.discoveryRatio)

        _uiState.update { it.copy(isLoadingMore = true) }
        try {
            val recentHistory = withContext(ioDispatcher) {
                runCatching { store.getRecentHistory(HISTORY_DEDUP_LIMIT) }.getOrDefault(emptyList())
            }
            val positiveHistory = withContext(ioDispatcher) {
                runCatching { store.getPositiveHistory(200) }.getOrDefault(emptyList())
            }
            val blockedTracks = withContext(ioDispatcher) {
                runCatching { store.getAllBlockedTracks() }.getOrDefault(emptyList())
            }
            val blockedArtists = withContext(ioDispatcher) {
                runCatching { store.getAllBlockedArtists() }.getOrDefault(emptyList())
            }

            val blockedIds = blockedTracks.mapTo(HashSet()) { it.videoId }
            val blockedKeys = blockedTracks.mapTo(HashSet()) { it.normalizedKey }
            val blockedArtistKeys = blockedArtists.mapTo(HashSet()) { it.normalizedArtist }
            val recentIds = recentHistory.mapTo(HashSet()) { it.videoId }
            val recentKeys = recentHistory.mapTo(HashSet()) { it.normalizedKey }
            val queueIds = currentQueue.mapNotNullTo(HashSet()) { videoIdExtractor(it) }
            val queueKeys = currentQueue.mapTo(HashSet()) { TrackNormalizer.normalizeKey(it.title, it.artist) }

            val excludedIds = HashSet<String>().apply {
                addAll(blockedIds)
                addAll(recentIds)
                addAll(queueIds)
            }
            val excludedKeys = HashSet<String>().apply {
                addAll(blockedKeys)
                addAll(recentKeys)
                addAll(queueKeys)
                addAll(sessionNormalizedKeys)
            }

            // Pull fresh discovery candidates via continuation loop (up to MAX_PAGINATION_REQUESTS).
            // Count POST-FILTER valid candidates inside the loop so a page full of already-queued
            // songs triggers continuation or seed rotation instead of falling back to Favorites.
            val freshCandidates = ArrayList<CompassoCandidate>()
            val omvFallbackCandidates = ArrayList<CompassoCandidate>()
            val seenFreshIds = HashSet<String>()
            var validFreshCount = 0
            var pagesFetched = 0

            // Prefer the tail of the queue (or current song) when starting a fresh cycle so we
            // extend from the latest musical vibe instead of re-fetching an old seed's queue.
            var currentSeed = if (continuationToken.isNullOrBlank()) {
                currentQueue.lastOrNull()?.let { videoIdExtractor(it) }
                    ?: currentQueue.getOrNull(currentIndex)?.let { videoIdExtractor(it) }
                    ?: activeSeedVideoId
                    ?: affinityTracker.bestFallbackSeed()
                    ?: positiveHistory.firstOrNull()?.videoId
            } else {
                activeSeedVideoId
                    ?: currentQueue.lastOrNull()?.let { videoIdExtractor(it) }
                    ?: currentQueue.getOrNull(currentIndex)?.let { videoIdExtractor(it) }
                    ?: affinityTracker.bestFallbackSeed()
                    ?: positiveHistory.firstOrNull()?.videoId
            }
            if (currentSeed == null) {
                val anchorSong = currentQueue.getOrNull(currentIndex) ?: currentQueue.lastOrNull()
                if (anchorSong != null) {
                    currentSeed = withContext(ioDispatcher) {
                        runCatching { radioSource.resolveAtvSeedVideoId(anchorSong) }.getOrNull()
                    }
                }
            }
            val usedFallbackSeeds = HashSet<String>()
            if (currentSeed != null) {
                usedFallbackSeeds.add(currentSeed)
                activeSeedVideoId = currentSeed
            }

            while (pagesFetched < MAX_PAGINATION_REQUESTS &&
                (validFreshCount + waitingBuffer.size) < targetCount &&
                currentSeed != null
            ) {
                pagesFetched += 1
                val seedForRequest = currentSeed
                val page = withContext(ioDispatcher) {
                    runCatching { radioSource.getRadioPage(seedForRequest, continuationToken, tunerSetting) }
                        .getOrElse { CompassoRadioPage(emptyList(), null) }
                }
                continuationToken = page.continuationToken

                val (pureBatch, omvBatch) = partitionByAudioPurity(page.candidates)
                var addedValidThisPage = 0
                for (candidate in pureBatch) {
                    if (!seenFreshIds.add(candidate.videoId)) continue
                    freshCandidates.add(candidate)
                    val key = TrackNormalizer.normalizeKey(candidate.song.title, candidate.song.artist)
                    val artistKey = TrackNormalizer.normalizePrimaryArtist(candidate.song.artist)
                    if (candidate.videoId !in excludedIds &&
                        key !in excludedKeys &&
                        artistKey !in blockedArtistKeys &&
                        (candidate.song.durationMs <= 0L || candidate.song.durationMs <= MAX_TRACK_DURATION_MS)
                    ) {
                        validFreshCount += 1
                        addedValidThisPage += 1
                    }
                }
                for (candidate in omvBatch) {
                    if (seenFreshIds.add(candidate.videoId)) {
                        omvFallbackCandidates.add(candidate)
                    }
                }

                if (continuationToken.isNullOrBlank() || addedValidThisPage == 0) {
                    // Rotate to another seed: current song, another queue track, newly discovered
                    // candidate, session affinity, or positive history.
                    val fallback = currentQueue.getOrNull(currentIndex)?.let { song ->
                        videoIdExtractor(song)?.takeIf {
                            it !in usedFallbackSeeds &&
                                it !in blockedIds &&
                                TrackNormalizer.normalizePrimaryArtist(song.artist) !in blockedArtistKeys
                        }
                    }
                        ?: currentQueue.asReversed().firstNotNullOfOrNull { song ->
                            videoIdExtractor(song)?.takeIf {
                                it !in usedFallbackSeeds &&
                                    it !in blockedIds &&
                                    TrackNormalizer.normalizePrimaryArtist(song.artist) !in blockedArtistKeys
                            }
                        }
                        ?: freshCandidates.firstOrNull {
                            it.videoId !in usedFallbackSeeds &&
                                it.videoId !in blockedIds &&
                                TrackNormalizer.normalizePrimaryArtist(it.song.artist) !in blockedArtistKeys
                        }?.videoId
                        ?: affinityTracker.bestFallbackSeed(excludingVideoIds = usedFallbackSeeds)
                        ?: positiveHistory.firstOrNull {
                            it.videoId !in usedFallbackSeeds &&
                                it.videoId !in blockedIds &&
                                TrackNormalizer.normalizePrimaryArtist(it.artistName) !in blockedArtistKeys
                        }?.videoId
                        ?: radioSource.likedSongsSnapshot().firstNotNullOfOrNull { song ->
                            videoIdExtractor(song)?.takeIf {
                                it !in usedFallbackSeeds &&
                                    it !in blockedIds &&
                                    TrackNormalizer.normalizePrimaryArtist(song.artist) !in blockedArtistKeys
                            }
                        }
                    if (fallback != null) {
                        usedFallbackSeeds.add(fallback)
                        currentSeed = fallback
                        activeSeedVideoId = fallback
                        continuationToken = null
                    } else if (continuationToken.isNullOrBlank()) {
                        break
                    }
                }
            }

            // Only fall back to OMV if the entire fetched batch had no pure ATV candidates
            val discoveryPool = if (freshCandidates.isNotEmpty()) freshCandidates else omvFallbackCandidates

            // Build familiarity pool from positive history (weighted by current time of day) + liked songs
            val currentPeriod = DayPeriod.fromTimestampMs(nowMs())
            val familiarPool = buildFamiliarityPool(
                positiveHistory = positiveHistory,
                likedSongs = radioSource.likedSongsSnapshot(),
                currentPeriod = currentPeriod,
                blockedIds = blockedIds,
                blockedKeys = blockedKeys,
                blockedArtistKeys = blockedArtistKeys,
                queueIds = queueIds,
                queueKeys = queueKeys,
                recentIds = recentIds,
            )

            // Filter discovery pool against all anti-repetition & block rules
            var filteredDiscovery = filterValidCandidates(
                candidates = discoveryPool,
                excludedIds = excludedIds,
                excludedKeys = excludedKeys,
                blockedArtistKeys = blockedArtistKeys,
            )

            // Score and reorder candidates using real-time session affinity + discoveryRatio + 1-in-5 artist window
            val tailArtists = currentQueue
                .takeLast(ARTIST_WINDOW_LOOKBACK)
                .map { TrackNormalizer.normalizePrimaryArtist(it.artist) }

            var selected = selectAndInterleave(
                discoveryCandidates = filteredDiscovery,
                familiarCandidates = familiarPool,
                discoveryRatio = _uiState.value.discoveryRatio,
                targetCount = targetCount,
                initialRecentArtists = tailArtists,
                excludedIds = excludedIds,
                excludedKeys = excludedKeys,
            )

            // Emergency fallback at end-of-queue: if strict 50-track history dedup filtered out the
            // entire batch, relax history dedup while strictly preserving blocked tracks and active queue dedup.
            if (selected.isEmpty() && forceFetch && discoveryPool.isNotEmpty()) {
                val strictOnlyIds = HashSet<String>().apply {
                    addAll(blockedIds)
                    addAll(queueIds)
                }
                val strictOnlyKeys = HashSet<String>().apply {
                    addAll(blockedKeys)
                    addAll(queueKeys)
                }
                filteredDiscovery = filterValidCandidates(
                    candidates = discoveryPool,
                    excludedIds = strictOnlyIds,
                    excludedKeys = strictOnlyKeys,
                    blockedArtistKeys = blockedArtistKeys,
                )
                selected = selectAndInterleave(
                    discoveryCandidates = filteredDiscovery,
                    familiarCandidates = familiarPool,
                    discoveryRatio = _uiState.value.discoveryRatio,
                    targetCount = targetCount,
                    initialRecentArtists = tailArtists,
                    excludedIds = strictOnlyIds,
                    excludedKeys = strictOnlyKeys,
                )
            }

            val songsToAppend = selected.map { it.song }
            if (songsToAppend.isNotEmpty()) {
                songsToAppend.forEach { song ->
                    sessionNormalizedKeys.add(TrackNormalizer.normalizeKey(song.title, song.artist))
                }
                onAppendTracks(songsToAppend)
                val updatedQueue = currentQueue + songsToAppend
                _uiState.update {
                    it.copy(queuedAheadCount = (updatedQueue.size - 1 - currentIndex).coerceAtLeast(0))
                }
                persistQueueState(updatedQueue, currentIndex)
            }
            songsToAppend
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    /**
     * Audio Purity Rule:
     * - Discard `MUSIC_VIDEO_TYPE_UGC`.
     * - Prioritize `MUSIC_VIDEO_TYPE_ATV` (and untyped studio audio items).
     * - Keep `MUSIC_VIDEO_TYPE_OMV` in a separate last-resort bucket.
     */
    internal fun partitionByAudioPurity(
        candidates: List<CompassoCandidate>,
    ): Pair<List<CompassoCandidate>, List<CompassoCandidate>> {
        val pure = ArrayList<CompassoCandidate>()
        val omv = ArrayList<CompassoCandidate>()
        for (candidate in candidates) {
            when (candidate.musicVideoType) {
                MUSIC_VIDEO_TYPE_UGC -> Unit // Always discard user uploads
                MUSIC_VIDEO_TYPE_OMV -> omv.add(candidate)
                else -> pure.add(candidate) // ATV or studio track without explicit type
            }
        }
        return pure to omv
    }

    internal fun selectAndInterleave(
        discoveryCandidates: List<CompassoCandidate>,
        familiarCandidates: List<CompassoCandidate>,
        discoveryRatio: Float,
        targetCount: Int,
        initialRecentArtists: List<String>,
        excludedIds: MutableSet<String>,
        excludedKeys: MutableSet<String>,
    ): List<CompassoCandidate> {
        val clampedRatio = discoveryRatio.coerceIn(0f, 1f)
        val sortedDiscovery = discoveryCandidates
            .sortedByDescending { affinityTracker.artistWeight(it.song.artist) }
            .toMutableList()
        val sortedFamiliar = familiarCandidates
            .sortedWith(
                compareByDescending<CompassoCandidate> { it.familiarTimeMatch }
                    .thenByDescending { affinityTracker.artistWeight(it.song.artist) },
            )
            .toMutableList()

        val recentArtists = ArrayDeque<String>().apply {
            initialRecentArtists.takeLast(ARTIST_WINDOW_LOOKBACK).forEach { addLast(it) }
        }
        val selected = ArrayList<CompassoCandidate>(targetCount)
        var discoveryPicked = 0
        var familiarPicked = 0

        fun canPlace(candidate: CompassoCandidate): Boolean {
            val key = TrackNormalizer.normalizeKey(candidate.song.title, candidate.song.artist)
            if (candidate.videoId in excludedIds || key in excludedKeys) return false
            val artistKey = TrackNormalizer.normalizePrimaryArtist(candidate.song.artist)
            return artistKey.isEmpty() || artistKey !in recentArtists
        }

        fun commit(candidate: CompassoCandidate) {
            val key = TrackNormalizer.normalizeKey(candidate.song.title, candidate.song.artist)
            excludedIds.add(candidate.videoId)
            excludedKeys.add(key)
            val artistKey = TrackNormalizer.normalizePrimaryArtist(candidate.song.artist)
            if (artistKey.isNotEmpty()) {
                recentArtists.addLast(artistKey)
                while (recentArtists.size > ARTIST_WINDOW_LOOKBACK) {
                    recentArtists.removeFirst()
                }
            }
            selected.add(candidate)
            if (candidate.isFamiliar) familiarPicked += 1 else discoveryPicked += 1
        }

        fun tryPickFromWaitingBuffer(preferFamiliar: Boolean): CompassoCandidate? {
            val idx = waitingBuffer.indexOfFirst { it.isFamiliar == preferFamiliar && canPlace(it) }
                .takeIf { it >= 0 }
                ?: waitingBuffer.indexOfFirst { canPlace(it) }
            return if (idx >= 0) waitingBuffer.removeAt(idx) else null
        }

        fun tryPickFromPool(pool: MutableList<CompassoCandidate>): CompassoCandidate? {
            val iter = pool.iterator()
            while (iter.hasNext()) {
                val candidate = iter.next()
                val key = TrackNormalizer.normalizeKey(candidate.song.title, candidate.song.artist)
                if (candidate.videoId in excludedIds || key in excludedKeys) {
                    iter.remove()
                    continue
                }
                if (canPlace(candidate)) {
                    iter.remove()
                    return candidate
                } else {
                    iter.remove()
                    if (waitingBuffer.size < MAX_WAITING_BUFFER_SIZE) {
                        waitingBuffer.add(candidate)
                    }
                }
            }
            return null
        }

        val maxFamiliar = when {
            clampedRatio >= 1f -> 0
            clampedRatio <= 0f -> targetCount
            else -> kotlin.math.round(targetCount * (1f - clampedRatio)).toInt().coerceIn(0, targetCount)
        }

        while (selected.size < targetCount) {
            val totalPicked = discoveryPicked + familiarPicked
            val desiredDiscovery = (totalPicked + 1) * clampedRatio
            val canPickFamiliar = familiarPicked < maxFamiliar
            val wantDiscovery = when {
                clampedRatio >= 1f -> true
                clampedRatio <= 0f -> false
                !canPickFamiliar -> true
                else -> discoveryPicked < desiredDiscovery
            }

            val next = if (wantDiscovery) {
                tryPickFromWaitingBuffer(preferFamiliar = false)
                    ?: tryPickFromPool(sortedDiscovery)
                    ?: if (canPickFamiliar) {
                        tryPickFromWaitingBuffer(preferFamiliar = true)
                            ?: tryPickFromPool(sortedFamiliar)
                    } else {
                        null
                    }
            } else {
                (if (canPickFamiliar) {
                    tryPickFromWaitingBuffer(preferFamiliar = true)
                        ?: tryPickFromPool(sortedFamiliar)
                } else {
                    null
                })
                    ?: tryPickFromWaitingBuffer(preferFamiliar = false)
                    ?: tryPickFromPool(sortedDiscovery)
            }

            if (next == null) break
            commit(next)
        }

        return selected
    }

    private fun filterValidCandidates(
        candidates: List<CompassoCandidate>,
        excludedIds: Set<String>,
        excludedKeys: Set<String>,
        blockedArtistKeys: Set<String> = emptySet(),
    ): List<CompassoCandidate> {
        val localIds = HashSet<String>()
        val localKeys = HashSet<String>()
        return candidates.filter { candidate ->
            if (candidate.song.durationMs > MAX_TRACK_DURATION_MS) return@filter false
            val artistKey = TrackNormalizer.normalizePrimaryArtist(candidate.song.artist)
            if (artistKey in blockedArtistKeys) return@filter false
            val key = TrackNormalizer.normalizeKey(candidate.song.title, candidate.song.artist)
            if (candidate.videoId in excludedIds || key in excludedKeys) return@filter false
            if (!localIds.add(candidate.videoId) || !localKeys.add(key)) return@filter false
            true
        }
    }

    private fun buildFamiliarityPool(
        positiveHistory: List<PlaybackHistoryEntity>,
        likedSongs: List<Song>,
        currentPeriod: DayPeriod,
        blockedIds: Set<String>,
        blockedKeys: Set<String>,
        blockedArtistKeys: Set<String>,
        queueIds: Set<String>,
        queueKeys: Set<String>,
        recentIds: Set<String>,
    ): List<CompassoCandidate> {
        val out = ArrayList<CompassoCandidate>()
        val seenIds = HashSet<String>()

        // Older positive history entries (outside the last 50 recent dedup window) with time-of-day match boost
        for (entry in positiveHistory) {
            if (entry.videoId in blockedIds || entry.normalizedKey in blockedKeys) continue
            if (TrackNormalizer.normalizePrimaryArtist(entry.artistName) in blockedArtistKeys) continue
            if (entry.videoId in queueIds || entry.normalizedKey in queueKeys) continue
            if (entry.videoId in recentIds) continue
            if (!seenIds.add(entry.videoId)) continue
            val samePeriod = DayPeriod.fromTimestampMs(entry.playedAt) == currentPeriod
            out.add(
                CompassoCandidate(
                    videoId = entry.videoId,
                    song = entry.toDomainSong(),
                    musicVideoType = MUSIC_VIDEO_TYPE_ATV,
                    isFamiliar = true,
                    familiarTimeMatch = samePeriod,
                ),
            )
        }

        // Liked songs from YouTube library
        for (song in likedSongs) {
            val videoId = videoIdExtractor(song) ?: continue
            val key = TrackNormalizer.normalizeKey(song.title, song.artist)
            if (videoId in blockedIds || key in blockedKeys) continue
            if (TrackNormalizer.normalizePrimaryArtist(song.artist) in blockedArtistKeys) continue
            if (videoId in queueIds || key in queueKeys) continue
            if (videoId in recentIds) continue
            if (!seenIds.add(videoId)) continue
            out.add(
                CompassoCandidate(
                    videoId = videoId,
                    song = song,
                    musicVideoType = MUSIC_VIDEO_TYPE_ATV,
                    isFamiliar = true,
                    familiarTimeMatch = false,
                ),
            )
        }
        return out
    }

    private fun PlaybackHistoryEntity.toDomainSong(): Song = Song(
        id = videoId.hashCode().toLong() and 0x7FFF_FFFFL,
        albumId = 0L,
        title = title,
        artist = artistName,
        album = album,
        albumArtist = artistName,
        releaseYear = null,
        genre = "",
        audioFormat = "YouTube",
        audioQuality = null,
        durationMs = durationMs,
        trackNumber = 0,
        discNumber = 1,
        dateAddedSeconds = playedAt / 1000L,
        uri = YouTubeUri.trackUri(videoId),
        artUri = artUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        isExplicit = false,
        fileName = "",
        mediaKind = AudioMediaKind.Music,
    )

    companion object {
        const val DEFAULT_DISCOVERY_RATIO = 0.7f
        const val FAMILIAR_DISCOVERY_RATIO = 0.3f
        const val EXPLORE_DISCOVERY_RATIO = 0.9f
        const val TARGET_BUFFER_AHEAD = 15
        const val PRELOAD_THRESHOLD_TRACKS = 5
        const val HISTORY_DEDUP_LIMIT = 50
        const val ARTIST_WINDOW_LOOKBACK = 4 // max 1 track per artist in every 5 consecutive tracks
        const val MAX_PAGINATION_REQUESTS = 4
        const val MAX_WAITING_BUFFER_SIZE = 40
        const val MAX_TRACK_DURATION_MS = 12 * 60_000L

        const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
        const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"
        const val MUSIC_VIDEO_TYPE_UGC = "MUSIC_VIDEO_TYPE_UGC"
    }
}
