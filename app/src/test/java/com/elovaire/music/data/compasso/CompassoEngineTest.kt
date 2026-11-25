package elovaire.music.droidbeauty.app.data.compasso

import android.net.TestUri
import elovaire.music.droidbeauty.app.data.library.db.BlockedArtistEntity
import elovaire.music.droidbeauty.app.data.library.db.BlockedTrackEntity
import elovaire.music.droidbeauty.app.data.library.db.PersistedQueueEntity
import elovaire.music.droidbeauty.app.data.library.db.PlaybackHistoryEntity
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        val k1 = TrackNormalizer.normalizeKey(
            title = "Águas de Março (Official Audio)",
            artist = "Elis Regina, Tom Jobim",
            structuredArtists = listOf("Elis Regina", "Tom Jobim"),
        )
        val k2 = TrackNormalizer.normalizeKey("Aguas de Marco feat. Tom Jobim", "Elis Regina feat. Tom Jobim")
        assertEquals("aguas de marco|elis regina", k1)
        assertEquals(k1, k2)
    }

    @Test
    fun `TrackNormalizer preserves non-Latin scripts while still stripping Latin accents`() {
        val jp = TrackNormalizer.normalizeKey("夜に駆ける", "YOASOBI")
        val kr = TrackNormalizer.normalizeKey("밤편지", "아이유")
        val cyrillic = TrackNormalizer.normalizeKey("Кино - Группа крови", "Кино")
        val accented = TrackNormalizer.normalizeKey("Café Tacvba", "Café")

        assertEquals("夜に駆ける|yoasobi", jp)
        assertEquals("밤편지|아이유", kr)
        assertTrue(jp != kr)
        assertTrue(cyrillic.startsWith("кино группа крови|кино"))
        assertEquals("cafe tacvba|cafe", accented)
    }

    @Test
    fun `TrackNormalizer does not truncate band names or real title words like Long Live`() {
        assertEquals("ac dc", TrackNormalizer.normalizePrimaryArtist("AC/DC"))
        assertEquals("simon garfunkel", TrackNormalizer.normalizePrimaryArtist("Simon & Garfunkel"))
        assertEquals("earth wind fire", TrackNormalizer.normalizePrimaryArtist("Earth, Wind & Fire"))
        assertEquals("tyler the creator", TrackNormalizer.normalizePrimaryArtist("Tyler, The Creator"))
        assertEquals("long live", TrackNormalizer.normalizeTitle("Long Live"))
        assertEquals("video games", TrackNormalizer.normalizeTitle("Video Games"))
        assertEquals(
            "elis regina",
            TrackNormalizer.normalizePrimaryArtist(
                rawArtist = "Elis Regina, Tom Jobim",
                structuredArtists = listOf("Elis Regina", "Tom Jobim"),
            ),
        )
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

    @Test
    fun `restorePersistedQueueState respects saved isActive flag`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeCompassoStore()
        store.savePersistedQueue(
            PersistedQueueEntity(
                queueJson = CompassoQueueSerializer.encodeSongs(listOf(testSong(1L, "Saved", "Artist"))),
                currentIndex = 0,
                continuationToken = "cont_1",
                discoveryRatio = 0.3f,
                isActive = false,
                seedVideoId = "vid_1",
                updatedAt = 1_000L,
            ),
        )
        val engine = createEngine(store = store, dispatcher = dispatcher)
        val restored = engine.restorePersistedQueueState()
        assertEquals(false, restored?.isActive)
        assertFalse(engine.uiState.value.isActive)
        assertEquals(0.3f, engine.uiState.value.discoveryRatio, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 7. Artist blocking & Time-of-Day (DayPeriod) weighting
    // -------------------------------------------------------------------------

    @Test
    fun `blockArtist filters all tracks by that artist from discovery and familiarity pools`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeCompassoStore()
        val radio = FakeRadioSource(
            pages = mapOf(
                ("vid_1" to null) to CompassoRadioPage(
                    candidates = listOf(
                        candidate("b1", "Bad Track 1", "Blocked Band"),
                        candidate("b2", "Bad Track 2", "Blocked Band feat. Guest"),
                        candidate("g1", "Good Track 1", "Good Band 1"),
                        candidate("g2", "Good Track 2", "Good Band 2"),
                        candidate("g3", "Good Track 3", "Good Band 3"),
                    ),
                    continuationToken = null,
                ),
            ),
            liked = listOf(
                testSong(id = 901L, title = "Liked Blocked", artist = "Blocked Band"),
                testSong(id = 902L, title = "Liked Good", artist = "Good Band 4"),
            ),
        )
        val engine = createEngine(radioSource = radio, store = store, dispatcher = dispatcher)
        var callbackInvoked = false
        engine.blockArtist("Blocked Band") { callbackInvoked = true }
        assertTrue(callbackInvoked)

        val appended = mutableListOf<Song>()
        val seed = testSong(id = 1L, title = "Seed", artist = "Seed Artist")
        engine.startFromSeed(
            seedSong = seed,
            currentQueue = listOf(seed),
            onAppendTracks = { appended.addAll(it) },
        )

        val artists = appended.map { TrackNormalizer.normalizePrimaryArtist(it.artist) }
        assertFalse(artists.contains("blocked band"))
        assertTrue(appended.any { it.title == "Good Track 1" })
    }

    @Test
    fun `legacy blocked artists and pipe keys remain accurate after normalizer update`() = runTest {
        val store = FakeCompassoStore()
        // Legacy blocked artist saved with old truncated key "simon"
        store.insertBlockedArtist(
            BlockedArtistEntity(
                normalizedArtist = "simon",
                artistName = "Simon & Garfunkel",
                blockedAt = 1_000L,
            ),
        )
        // Legacy blocked track with "|" key from old non-Latin stripping
        store.insertBlockedTrack(
            BlockedTrackEntity(
                videoId = "old_blocked_vid",
                normalizedKey = "|",
                artistName = "",
                blockedAt = 1_000L,
            ),
        )
        val radioSource = FakeRadioSource(
            pages = mapOf(
                ("vid_1" to null) to CompassoRadioPage(
                    candidates = listOf(
                        candidate("sg_1", "Bridge Over Troubled Water", "Simon & Garfunkel"),
                        candidate("simon_1", "Solo Track", "Simon"),
                        candidate("empty_1", "!!!", "???"),
                        candidate("good_1", "Good Track", "Good Artist"),
                    ),
                    continuationToken = null,
                ),
            ),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = createEngine(radioSource = radioSource, store = store, dispatcher = dispatcher)
        engine.setActive(true)

        val appended = mutableListOf<Song>()
        val seed = testSong(id = 1L, title = "Seed", artist = "Seed Artist")
        engine.startFromSeed(
            seedSong = seed,
            currentQueue = listOf(seed),
            onAppendTracks = { appended.addAll(it) },
        )

        val titles = appended.map { it.title }
        assertFalse("Legacy Simon & Garfunkel block must still block Simon & Garfunkel", titles.contains("Bridge Over Troubled Water"))
        assertTrue("Legacy Simon & Garfunkel block must not falsely block Simon", titles.contains("Solo Track"))
        assertTrue("Legacy '|' blocked key must not block unrelated tracks", titles.contains("!!!"))
        assertTrue(titles.contains("Good Track"))
    }

    @Test
    fun `fetch loop counts only usable waitingBuffer tracks instead of raw waitingBuffer size`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeCompassoStore()
        // Page 1 has 15 tracks from Artist A + 14 from distinct artists (29 total >= TARGET_BUFFER_AHEAD=15).
        // startFromSeed places A1 + 14 distinct (15 tracks) and leaves A2..A15 (14 tracks) in waitingBuffer,
        // stopping before fetching page2.
        val page1Candidates = buildList {
            for (i in 1..15) add(candidate("a$i", "A$i", "Artist A"))
            for (i in 1..14) add(candidate("d$i", "D$i", "Distinct $i"))
        }
        val radioSource = FakeRadioSource(
            pages = mapOf(
                ("vid_1" to null) to CompassoRadioPage(
                    candidates = page1Candidates,
                    continuationToken = "page2",
                ),
                ("vid_1" to "page2") to CompassoRadioPage(
                    candidates = listOf(
                        candidate("f1", "F1", "Artist F"),
                        candidate("g1", "G1", "Artist G"),
                        candidate("h1", "H1", "Artist H"),
                        candidate("i1", "I1", "Artist I"),
                    ),
                    continuationToken = null,
                ),
            ),
        )
        val engine = createEngine(radioSource = radioSource, store = store, dispatcher = dispatcher)
        engine.setActive(true)
        engine.setDiscoveryRatio(1.0f)

        val queue = mutableListOf(testSong(id = 1L, title = "Seed", artist = "Seed Artist"))
        engine.startFromSeed(
            seedSong = queue.first(),
            currentQueue = queue.toList(),
            onAppendTracks = { queue.addAll(it) },
        )
        // Queue now has Seed + 15 tracks (size 16), and waitingBuffer has A2..A15 (size 14).
        assertEquals(16, queue.size)

        val appendedSecond = mutableListOf<Song>()
        // Advance to index 10 (5 tracks ahead <= PRELOAD_THRESHOLD_TRACKS=5, targetCount=10).
        engine.onPlaybackPositionChanged(
            currentQueue = queue.toList(),
            currentIndex = 10,
            onAppendTracks = { appendedSecond.addAll(it) },
        )

        // Only a small fraction of the 14 buffered Artist A tracks are usable in the 1-in-5 window;
        // the engine must fetch page2 instead of short-circuiting on waitingBuffer.size == 14 >= 10.
        assertTrue(
            "Expected page2 tracks to be fetched instead of stopping at waitingBuffer.size",
            appendedSecond.any { it.artist == "Artist F" },
        )
    }

    @Test
    fun `concurrent fillAheadIfNeeded calls recompute needed count from live queue inside mutex`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeCompassoStore()
        val page1Candidates = (1..20).map { i -> candidate("c$i", "Track $i", "Artist $i") }
        val page2Candidates = (21..40).map { i -> candidate("c$i", "Track $i", "Artist $i") }
        val radioSource = FakeRadioSource(
            pages = mapOf(
                ("vid_1" to null) to CompassoRadioPage(
                    candidates = page1Candidates,
                    continuationToken = "page2",
                ),
                ("vid_1" to "page2") to CompassoRadioPage(
                    candidates = page2Candidates,
                    continuationToken = null,
                ),
            ),
        )
        val engine = createEngine(radioSource = radioSource, store = store, dispatcher = dispatcher)
        engine.setActive(true)
        engine.setDiscoveryRatio(1.0f)

        val liveQueue = mutableListOf(testSong(id = 1L, title = "Seed", artist = "Seed Artist"))
        val staleSnapshot = liveQueue.toList()
        var appendCount = 0

        coroutineScope {
            val job1 = launch(dispatcher) {
                engine.fillAheadIfNeeded(
                    currentIndex = 0,
                    currentQueue = staleSnapshot,
                    forceFetch = false,
                    queueSnapshotProvider = { 0 to liveQueue.toList() },
                    onAppendTracks = {
                        appendCount += 1
                        liveQueue.addAll(it)
                    },
                )
            }
            val job2 = launch(dispatcher) {
                engine.fillAheadIfNeeded(
                    currentIndex = 0,
                    currentQueue = staleSnapshot,
                    forceFetch = false,
                    queueSnapshotProvider = { 0 to liveQueue.toList() },
                    onAppendTracks = {
                        appendCount += 1
                        liveQueue.addAll(it)
                    },
                )
            }
            job1.join()
            job2.join()
        }

        assertEquals(1, appendCount)
        assertEquals(1 + CompassoEngine.TARGET_BUFFER_AHEAD, liveQueue.size)
    }

    @Test
    fun `DayPeriod fromHour maps all day periods accurately`() {
        assertEquals(DayPeriod.Dawn, DayPeriod.fromHour(0))
        assertEquals(DayPeriod.Dawn, DayPeriod.fromHour(5))
        assertEquals(DayPeriod.Morning, DayPeriod.fromHour(6))
        assertEquals(DayPeriod.Morning, DayPeriod.fromHour(11))
        assertEquals(DayPeriod.Afternoon, DayPeriod.fromHour(12))
        assertEquals(DayPeriod.Afternoon, DayPeriod.fromHour(17))
        assertEquals(DayPeriod.Night, DayPeriod.fromHour(18))
        assertEquals(DayPeriod.Night, DayPeriod.fromHour(23))
    }

    @Test
    fun `time-of-day weighting boosts familiar tracks played in the same DayPeriod`() {
        val engine = createEngine()
        val samePeriodCandidate = CompassoCandidate(
            videoId = "fam_same",
            song = testSong(id = 201L, title = "Morning Vibe", artist = "Artist Morning"),
            musicVideoType = CompassoEngine.MUSIC_VIDEO_TYPE_ATV,
            isFamiliar = true,
            familiarTimeMatch = true,
        )
        val offPeriodCandidate = CompassoCandidate(
            videoId = "fam_other",
            song = testSong(id = 202L, title = "Late Night Vibe", artist = "Artist Night"),
            musicVideoType = CompassoEngine.MUSIC_VIDEO_TYPE_ATV,
            isFamiliar = true,
            familiarTimeMatch = false,
        )

        val selected = engine.selectAndInterleave(
            discoveryCandidates = emptyList(),
            familiarCandidates = listOf(offPeriodCandidate, samePeriodCandidate),
            discoveryRatio = 0.0f,
            targetCount = 2,
            initialRecentArtists = emptyList(),
            excludedIds = mutableSetOf(),
            excludedKeys = mutableSetOf(),
        )

        assertEquals(listOf("fam_same", "fam_other"), selected.map { it.videoId })
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
        private val blockedArtists = mutableListOf<BlockedArtistEntity>()
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

        override suspend fun insertBlockedArtist(entry: BlockedArtistEntity) {
            blockedArtists.removeAll { it.normalizedArtist == entry.normalizedArtist }
            blockedArtists.add(entry)
        }

        override suspend fun getAllBlockedArtists(): List<BlockedArtistEntity> = blockedArtists.toList()

        override suspend fun removeBlockedArtist(normalizedArtist: String) {
            blockedArtists.removeAll { it.normalizedArtist == normalizedArtist }
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
