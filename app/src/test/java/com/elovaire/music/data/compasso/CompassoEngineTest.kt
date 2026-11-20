package elovaire.music.droidbeauty.app.data.compasso

import android.net.TestUri
import elovaire.music.droidbeauty.app.data.library.db.BlockedTrackEntity
import elovaire.music.droidbeauty.app.data.library.db.PersistedQueueEntity
import elovaire.music.droidbeauty.app.data.library.db.PlaybackHistoryEntity
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassoEngineTest {

    // -------------------------------------------------------------------------
    // 1. TrackNormalizer tests
    // -------------------------------------------------------------------------

    @Test
    fun `TrackNormalizer strips YouTube noise tags and matches live or remaster versions`() {
        val original = TrackNormalizer.normalizeKey("Bohemian Rhapsody", "Queen")
        val remaster = TrackNormalizer.normalizeKey("Bohemian Rhapsody (Remastered 2011)", "Queen")
        val officialVideo = TrackNormalizer.normalizeKey("Bohemian Rhapsody [Official Video] HD", "Queen")
        val live = TrackNormalizer.normalizeKey("Bohemian Rhapsody (Live) 4K", "Queen")
        val aoVivo = TrackNormalizer.normalizeKey("Bohemian Rhapsody - Ao Vivo", "Queen")
        val lyrics = TrackNormalizer.normalizeKey("Bohemian Rhapsody (Lyric Video)", "Queen")

        assertEquals("bohemian rhapsody|queen", original)
        assertEquals(original, remaster)
        assertEquals(original, officialVideo)
        assertEquals(original, live)
        assertEquals(original, aoVivo)
        assertEquals(original, lyrics)
    }

    @Test
    fun `TrackNormalizer strips featured artists and accents`() {
        val k1 = TrackNormalizer.normalizeKey("Águas de Março (Official Audio)", "Elis Regina, Tom Jobim")
        val k2 = TrackNormalizer.normalizeKey("Aguas de Marco feat. Tom Jobim", "Elis Regina & Tom Jobim")
        assertEquals("aguas de marco|elis regina", k1)
        assertEquals(k1, k2)
    }

    // -------------------------------------------------------------------------
    // 2. Artist diversity window (max 1 per artist in every 5 consecutive tracks)
    // -------------------------------------------------------------------------

    @Test
    fun `selectAndInterleave enforces max 1 track per artist every 5 songs and uses waiting buffer`() {
        val engine = createEngine()
        // Provide 2 tracks from Artist A followed by 5 distinct artists
        val candidates = listOf(
            candidate("v1", "Song 1", "Artist A"),
            candidate("v2", "Song 2", "Artist A"), // violates 1-in-5 window immediately -> goes to waiting buffer
            candidate("v3", "Song 3", "Artist B"),
            candidate("v4", "Song 4", "Artist C"),
            candidate("v5", "Song 5", "Artist D"),
            candidate("v6", "Song 6", "Artist E"),
            candidate("v7", "Song 7", "Artist F"),
        )

        val result = engine.selectAndInterleave(
            discoveryCandidates = candidates,
            familiarCandidates = emptyList(),
            discoveryRatio = 1.0f,
            targetCount = 7,
            initialRecentArtists = emptyList(),
            excludedIds = mutableSetOf(),
            excludedKeys = mutableSetOf(),
        )

        assertEquals(7, result.size)
        // Verify no artist appears twice within any sliding window of 5 tracks
        val artists = result.map { TrackNormalizer.normalizePrimaryArtist(it.song.artist) }
        for (i in 0..artists.size - 5) {
            val window = artists.subList(i, i + 5)
            assertEquals("Duplicate artist found in 5-track window: $window", 5, window.distinct().size)
        }
        // Artist A's second track (v2) should be recovered from waitingBuffer at index 5 (after 4 other artists)
        assertEquals("v1", result[0].videoId)
        assertEquals("v2", result[5].videoId)
    }

    // -------------------------------------------------------------------------
    // 3. Discovery vs. Familiarity ratio (70/30 default & custom ratios)
    // -------------------------------------------------------------------------

    @Test
    fun `selectAndInterleave respects 70-30 discovery ratio`() {
        val engine = createEngine()
        val discovery = (1..12).map { i -> candidate("disc_$i", "New $i", "NewArtist $i", isFamiliar = false) }
        val familiar = (1..12).map { i -> candidate("fam_$i", "Old $i", "OldArtist $i", isFamiliar = true) }

        val selected = engine.selectAndInterleave(
            discoveryCandidates = discovery,
            familiarCandidates = familiar,
            discoveryRatio = 0.7f,
            targetCount = 10,
            initialRecentArtists = emptyList(),
            excludedIds = mutableSetOf(),
            excludedKeys = mutableSetOf(),
        )

        assertEquals(10, selected.size)
        val discoveryCount = selected.count { !it.isFamiliar }
        val familiarCount = selected.count { it.isFamiliar }
        assertEquals(7, discoveryCount)
        assertEquals(3, familiarCount)
    }

    @Test
    fun `selectAndInterleave respects 30-70 familiar-heavy ratio`() {
        val engine = createEngine()
        val discovery = (1..12).map { i -> candidate("disc_$i", "New $i", "NewArtist $i", isFamiliar = false) }
        val familiar = (1..12).map { i -> candidate("fam_$i", "Old $i", "OldArtist $i", isFamiliar = true) }

        val selected = engine.selectAndInterleave(
            discoveryCandidates = discovery,
            familiarCandidates = familiar,
            discoveryRatio = 0.3f,
            targetCount = 10,
            initialRecentArtists = emptyList(),
            excludedIds = mutableSetOf(),
            excludedKeys = mutableSetOf(),
        )

        assertEquals(10, selected.size)
        assertEquals(3, selected.count { !it.isFamiliar })
        assertEquals(7, selected.count { it.isFamiliar })
    }

    // -------------------------------------------------------------------------
    // 4. Real-time session signals: early skip (< 30s) penalty & completion/like boost
    // -------------------------------------------------------------------------

    @Test
    fun `early skip under 30s penalizes artist and lowers priority in reordering`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tracker = SessionAffinityTracker()
        val store = FakeCompassoStore()
        val engine = createEngine(store = store, tracker = tracker, dispatcher = dispatcher)

        val skippedSong = testSong(id = 10L, title = "Skip Me", artist = "Penalized Artist", durationMs = 200_000L)
        engine.recordPlaybackOutcome(song = skippedSong, playedMs = 12_000L, wasSkipped = true)

        assertTrue(tracker.artistWeight("Penalized Artist") < 0f)
        assertTrue(store.history.any { it.videoId == "vid_10" && it.wasSkippedEarly })

        val lovedSong = testSong(id = 11L, title = "Love Me", artist = "Boosted Artist", durationMs = 200_000L)
        engine.recordPlaybackOutcome(song = lovedSong, playedMs = 180_000L, wasSkipped = false)
        assertTrue(tracker.artistWeight("Boosted Artist") > 0f)

        val candidates = listOf(
            candidate("c1", "Track 1", "Penalized Artist"),
            candidate("c2", "Track 2", "Neutral Artist"),
            candidate("c3", "Track 3", "Boosted Artist"),
        )
        val reordered = engine.selectAndInterleave(
            discoveryCandidates = candidates,
            familiarCandidates = emptyList(),
            discoveryRatio = 1.0f,
            targetCount = 3,
            initialRecentArtists = emptyList(),
            excludedIds = mutableSetOf(),
            excludedKeys = mutableSetOf(),
        )
        assertEquals("Boosted Artist", reordered[0].song.artist)
        assertEquals("Neutral Artist", reordered[1].song.artist)
        assertEquals("Penalized Artist", reordered[2].song.artist)
    }

    // -------------------------------------------------------------------------
    // 5. Audio Purity Rule (ATV preferred, UGC dropped, OMV last-resort)
    // -------------------------------------------------------------------------

    @Test
    fun `audio purity drops UGC and prioritizes ATV over OMV`() {
        val engine = createEngine()
        val raw = listOf(
            candidate("u1", "Cover", "Random", videoType = CompassoEngine.MUSIC_VIDEO_TYPE_UGC),
            candidate("o1", "Hit MV", "Pop Star", videoType = CompassoEngine.MUSIC_VIDEO_TYPE_OMV),
            candidate("a1", "Studio Track", "Studio Artist", videoType = CompassoEngine.MUSIC_VIDEO_TYPE_ATV),
        )
        val (pure, omv) = engine.partitionByAudioPurity(raw)
        assertEquals(listOf("a1"), pure.map { it.videoId })
        assertEquals(listOf("o1"), omv.map { it.videoId })
    }

    // -------------------------------------------------------------------------
    // 6. Pagination loop, blocked track filtering, and fallback seed
    // -------------------------------------------------------------------------

    @Test
    fun `fillAhead paginates continuation tokens and filters blocked tracks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeCompassoStore()
        store.insertBlockedTrack(
            BlockedTrackEntity(
                videoId = "blocked_id",
                normalizedKey = TrackNormalizer.normalizeKey("Never Play", "Blocked Artist"),
                artistName = "Blocked Artist",
                blockedAt = 1_000L,
            ),
        )

        val page1 = CompassoRadioPage(
            candidates = listOf(
                candidate("blocked_id", "Never Play", "Blocked Artist"),
                candidate("alt_blocked", "Never Play (Live)", "Blocked Artist"), // same normalizedKey!
                candidate("p1_1", "Alpha", "Artist 1"),
                candidate("p1_2", "Beta", "Artist 2"),
            ),
            continuationToken = "tok_2",
        )
        val page2 = CompassoRadioPage(
            candidates = listOf(
                candidate("p2_1", "Gamma", "Artist 3"),
                candidate("p2_2", "Delta", "Artist 4"),
                candidate("p2_3", "Epsilon", "Artist 5"),
            ),
            continuationToken = null,
        )

        val radio = FakeRadioSource(
            pages = mapOf(
                ("vid_1" to null) to page1,
                ("vid_1" to "tok_2") to page2,
            ),
        )
        val engine = createEngine(radioSource = radio, store = store, dispatcher = dispatcher)
        val seed = testSong(id = 1L, title = "Seed Song", artist = "Seed Artist")
        val appended = mutableListOf<Song>()

        engine.startFromSeed(
            seedSong = seed,
            currentQueue = listOf(seed),
            onAppendTracks = { appended.addAll(it) },
        )

        val titles = appended.map { it.title }
        assertFalse(titles.contains("Never Play"))
        assertFalse(titles.contains("Never Play (Live)"))
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon"), titles)
    }

    @Test
    fun `fetchTracksForEndOfQueue extends queue on last track and returns null when explicitly disabled`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val radio = FakeRadioSource(
            pages = mapOf(
                ("vid_99" to null) to CompassoRadioPage(
                    candidates = listOf(
                        candidate("next_1", "Next One", "Artist A"),
                        candidate("next_2", "Next Two", "Artist B"),
                    ),
                    continuationToken = null,
                ),
            ),
        )
        val engine = createEngine(radioSource = radio, dispatcher = dispatcher)
        val lastTrack = testSong(id = 99L, title = "Final Track", artist = "Seed Artist")

        val fetched = engine.fetchTracksForEndOfQueue(
            currentSong = lastTrack,
            currentQueue = listOf(lastTrack),
            currentIndex = 0,
        )
        assertEquals(listOf("Next One", "Next Two"), fetched?.map { it.title })
        assertTrue(engine.uiState.value.isActive)

        // When user explicitly disables Compasso, end-of-queue returns null so playback stops normally.
        engine.setActive(false)
        val whenDisabled = engine.fetchTracksForEndOfQueue(
            currentSong = lastTrack,
            currentQueue = listOf(lastTrack),
            currentIndex = 0,
        )
        assertEquals(null, whenDisabled)
    }

    @Test
    fun `switchDiscoveryMode uses matching YouTube tuner and caps favorites share`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val liked = (1..15).map { idx ->
            testSong(id = 1000L + idx, title = "Fav $idx", artist = "Fav Artist $idx")
        }
        val radio = FakeRadioSource(
            pages = mapOf(
                ("vid_50" to null) to CompassoRadioPage(
                    candidates = (1..12).map { idx ->
                        candidate("disc_$idx", "Similar Track $idx", "Similar Artist $idx")
                    },
                    continuationToken = null,
                ),
            ),
            liked = liked,
        )
        val engine = createEngine(radioSource = radio, dispatcher = dispatcher)
        val seed = testSong(id = 50L, title = "Seed", artist = "Seed Artist")
        val oldUpcoming = testSong(id = 51L, title = "Old Upcoming", artist = "Old Artist")
        var replacedWith: List<Song> = emptyList()

        engine.switchDiscoveryMode(
            ratio = CompassoEngine.EXPLORE_DISCOVERY_RATIO,
            currentSong = seed,
            currentQueue = listOf(seed, oldUpcoming),
            currentIndex = 0,
            onReplaceUpcoming = { replacedWith = it },
        )

        assertEquals("AUTOMIX_SETTING_DISCOVER", radio.lastTunerSetting)
        assertTrue(replacedWith.isNotEmpty())
        val favCount = replacedWith.count { it.title.startsWith("Fav ") }
        assertTrue("Explore mode must cap favorites to ~10%, got $favCount/${replacedWith.size}", favCount <= 2)

        engine.switchDiscoveryMode(
            ratio = CompassoEngine.FAMILIAR_DISCOVERY_RATIO,
            currentSong = seed,
            currentQueue = listOf(seed),
            currentIndex = 0,
            onReplaceUpcoming = { replacedWith = it },
        )
        assertEquals("AUTOMIX_SETTING_FAMILIAR", radio.lastTunerSetting)
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private fun createEngine(
        radioSource: CompassoRadioSource = FakeRadioSource(),
        store: CompassoStore = FakeCompassoStore(),
        tracker: SessionAffinityTracker = SessionAffinityTracker(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = StandardTestDispatcher(),
    ): CompassoEngine = CompassoEngine(
        radioSource = radioSource,
        store = store,
        affinityTracker = tracker,
        ioDispatcher = dispatcher,
        nowMs = { 1_700_000_000_000L },
        videoIdExtractor = { song -> "vid_${song.id}" },
    )

    private fun candidate(
        videoId: String,
        title: String,
        artist: String,
        videoType: String? = CompassoEngine.MUSIC_VIDEO_TYPE_ATV,
        isFamiliar: Boolean = false,
    ): CompassoCandidate = CompassoCandidate(
        videoId = videoId,
        song = testSong(id = videoId.hashCode().toLong(), title = title, artist = artist),
        musicVideoType = videoType,
        isFamiliar = isFamiliar,
    )

    private fun testSong(
        id: Long,
        title: String,
        artist: String,
        durationMs: Long = 180_000L,
    ): Song = Song(
        id = id,
        albumId = 0L,
        title = title,
        artist = artist,
        album = "",
        albumArtist = artist,
        releaseYear = null,
        genre = "",
        audioFormat = "YouTube",
        audioQuality = null,
        durationMs = durationMs,
        trackNumber = 0,
        discNumber = 1,
        dateAddedSeconds = 0L,
        uri = TestUri("elovaire-yt://track/vid_$id"),
        artUri = null,
        isExplicit = false,
        fileName = "",
        mediaKind = AudioMediaKind.Music,
    )

    private class FakeRadioSource(
        private val pages: Map<Pair<String, String?>, CompassoRadioPage> = emptyMap(),
        private val liked: List<Song> = emptyList(),
    ) : CompassoRadioSource {
        var lastTunerSetting: String? = null

        override suspend fun getRadioPage(
            videoId: String,
            continuationToken: String?,
            tunerSetting: String,
        ): CompassoRadioPage {
            lastTunerSetting = tunerSetting
            return pages[videoId to continuationToken] ?: CompassoRadioPage(emptyList(), null)
        }

        override suspend fun resolveAtvSeedVideoId(song: Song): String? = null
        override fun likedSongsSnapshot(): List<Song> = liked
    }

    private class FakeCompassoStore : CompassoStore {
        val history = mutableListOf<PlaybackHistoryEntity>()
        val blocked = mutableListOf<BlockedTrackEntity>()
        private val blockedFlow = MutableStateFlow<List<BlockedTrackEntity>>(emptyList())
        private var persistedQueue: PersistedQueueEntity? = null

        override suspend fun upsertHistory(entry: PlaybackHistoryEntity) {
            history.removeAll { it.videoId == entry.videoId }
            history.add(0, entry)
        }

        override suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity> =
            history.sortedByDescending { it.playedAt }.take(limit)

        override suspend fun getPositiveHistory(limit: Int): List<PlaybackHistoryEntity> =
            history.filter { !it.wasSkippedEarly }.sortedByDescending { it.playedAt }.take(limit)

        override suspend fun insertBlockedTrack(entry: BlockedTrackEntity) {
            blocked.removeAll { it.videoId == entry.videoId }
            blocked.add(entry)
            blockedFlow.value = blocked.toList()
        }

        override suspend fun getAllBlockedTracks(): List<BlockedTrackEntity> = blocked.toList()

        override fun observeBlockedVideoIds(): Flow<List<String>> =
            blockedFlow.map { list -> list.map { it.videoId } }

        override suspend fun removeBlockedTrack(videoId: String) {
            blocked.removeAll { it.videoId == videoId }
            blockedFlow.value = blocked.toList()
        }

        override suspend fun savePersistedQueue(state: PersistedQueueEntity) {
            persistedQueue = state
        }

        override suspend fun getPersistedQueue(): PersistedQueueEntity? = persistedQueue
        override suspend fun clearPersistedQueue() {
            persistedQueue = null
        }
    }
}
