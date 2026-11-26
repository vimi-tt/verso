package com.vimi.k7.innertube.legacy

import com.vimi.k7.innertube.model.ArtistItem
import com.vimi.k7.innertube.model.ArtistPage
import com.vimi.k7.innertube.model.ArtistTasteSample
import com.vimi.k7.innertube.model.MusicMetadata
import com.vimi.k7.innertube.model.MusicReleaseType
import com.vimi.k7.innertube.model.MusicShelfItem
import com.vimi.k7.innertube.model.MusicShelfPage
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.auth.SessionManager
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.SongAlbumRef
import com.vimi.k7.innertube.model.newestReleasesFirst
import com.vimi.k7.innertube.model.parseMusicShelves
import com.vimi.k7.innertube.model.usableHomeRecommendations
import com.vimi.k7.innertube.model.HOME_RECOMMENDATION_POOL
import com.vimi.k7.innertube.transport.YouTubeHttp
import com.vimi.k7.innertube.transport.MusicApi
import com.vimi.k7.innertube.transport.YOUTUBE_TAG
import com.vimi.k7.innertube.transport.authenticate
import com.vimi.k7.innertube.transport.findObjectsByKey
import com.vimi.k7.innertube.transport.WEB_REMIX_VERSION
import com.vimi.k7.innertube.transport.BROWSER_USER_AGENT
import com.vimi.k7.innertube.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The pages of YouTube Music that are browsed rather than searched: Home and
 * its shelves, charts, an artist's page, a song's album, and the radio that
 * follows a song.
 */
class MusicBrowse(
    private val http: YouTubeHttp,
    private val musicApi: MusicApi,
    private val musicSearch: MusicSearch,
    private val musicPlaylists: MusicPlaylists,
    private val sessionManager: SessionManager,
) {
    /**
     * Get personalized recommendations (Quick Picks / Home).
     * Uses Internal YTM API with Cookies for personalized content.
     */
    suspend fun getRecommendations(): List<Song> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            KLog.d(YOUTUBE_TAG, "Not logged in, fetching public home/charts tracks")
            val homeTracks = getMusicShelves("FEmusic_home")?.shelves.orEmpty()
                .flatMap { it.items }
                .filterIsInstance<MusicShelfItem.Track>()
                .map { it.song }
            if (homeTracks.size >= 5) return@withContext homeTracks.distinctBy { it.id }
            val chartTracks = getMusicShelves("FEmusic_charts")?.shelves.orEmpty()
                .flatMap { it.items }
                .filterIsInstance<MusicShelfItem.Track>()
                .map { it.song }
            if (chartTracks.isNotEmpty()) return@withContext (homeTracks + chartTracks).distinctBy { it.id }
            return@withContext musicSearch.search("top hits", FILTER_SONGS)
        }

        try {
            // Fetch personalized home page content
            KLog.d(YOUTUBE_TAG, "Fetching personalized recommendations from FEmusic_home")
            val jsonResponse = musicApi.fetchInternalApi("FEmusic_home")
            
            if (jsonResponse.isEmpty()) {
                KLog.e(YOUTUBE_TAG, "Empty response from FEmusic_home")
                return@withContext fillHomeRecommendations(emptyList())
            }
            
            // Parse songs from the home page response
            // More than Home shows at once, so a refresh has songs to rotate to.
            val items = usableHomeRecommendations(
                listOf(parseSongsFromInternalJson(jsonResponse)),
                limit = HOME_RECOMMENDATION_POOL
            )
            KLog.d(YOUTUBE_TAG, "Parsed ${items.size} songs from recommendations")

            // Classic Home needs three valid entries for its three artwork
            // shapes. A partially parsed response is still useful, but it must
            // be filled rather than accepted as complete.
            fillHomeRecommendations(items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching recommendations", e)
            fillHomeRecommendations(emptyList())
        }
    }

    private suspend fun fillHomeRecommendations(primary: List<Song>): List<Song> {
        if (primary.size >= 3) return primary

        KLog.d(YOUTUBE_TAG, "Home has ${primary.size} usable songs; filling from library")
        val liked = try {
            musicPlaylists.getLikedMusic()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "Could not use liked songs as Home fallback", e)
            emptyList()
        }
        val withLiked = usableHomeRecommendations(listOf(primary, liked))
        if (withLiked.size >= 3) return withLiked

        val trending = try {
            musicSearch.search("trending music 2026", FILTER_SONGS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "Could not use search as Home fallback", e)
            emptyList()
        }
        return usableHomeRecommendations(listOf(withLiked, trending))
    }

    /** Metadata-only WEB_REMIX calls, public when signed out. Never playback. */
    /** A YouTube Music browse page (FEmusic_home, _explore, _charts, _new_releases, a mood) as shelves. */
    suspend fun getMusicShelves(browseId: String, params: String? = null): MusicShelfPage? =
        withContext(Dispatchers.IO) {
            musicApi.postMusicMetadata("browse", JSONObject().put("browseId", browseId).apply {
                if (params != null) put("params", params)
            })?.let(::parseMusicShelves)
        }

    suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage? = withContext(Dispatchers.IO) {
        musicApi.postMusicMetadata("browse", JSONObject().put("continuation", token))?.let(::parseMusicShelves)
    }

    /**
     * The "Top artists" of YouTube Music's charts page, for [country] (an ISO
     * code, or `ZZ` for Global) or, when null, for wherever YouTube places the
     * request. The country menu on that page is a form: the same browse with
     * `formData.selectedValues` answers for the chosen country, signed out.
     * [verified October 2026: forty artists each for the default, ZZ and US]
     */
    suspend fun getChartArtists(country: String? = null): List<ArtistItem> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("browseId", "FEmusic_charts")
        if (country != null) {
            payload.put(
                "formData",
                JSONObject().put("selectedValues", JSONArray().put(country))
            )
        }
        musicApi.postMusicMetadata("browse", payload)?.let(::parseMusicShelves)?.shelves.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<MusicShelfItem.Artist>()
            .map { it.artist }
            .distinctBy { it.id }
    }

    /**
     * Full artist page for a channel browse id: identity (bio, monthly
     * audience, banner), top songs, the Albums and Singles shelves with the
     * discography More endpoint followed, similar artists and featured
     * playlists.
     *
     * Uses InnerTube /browse with the WEB_REMIX client — the same call the
     * YT Music web app makes — because NewPipe's channel extractor only sees
     * the plain-YouTube uploads tab (a few videos, no albums or top songs).
     * Verified September 2026.
     */
    suspend fun getArtistPage(artistId: String): ArtistPage? = withContext(Dispatchers.IO) {
        if (!artistId.startsWith("UC")) {
            // Not a channel browse id (e.g. Library passes the artist *name*);
            // callers fall back to a name search when we return nothing.
            return@withContext null
        }
        try {
            val body = musicApi.browseMusic(artistId)
                ?: return@withContext null
            val root = JSONObject(body)

            val header = MusicMetadata.artistHeader(root)
            val artistName = header?.name.orEmpty()

            val songs = mutableListOf<Song>()
            val albums = mutableListOf<PlaylistDisplayItem>()
            val singles = mutableListOf<PlaylistDisplayItem>()

            // --- Top songs shelf ("Songs") ---
            // The shelf itself only holds ~5 entries; its bottomEndpoint links
            // the artist's full songs playlist which we fetch below.
            var songsPlaylistBrowseId: String? = null
            val shelves = mutableListOf<JSONObject>()
            findObjectsByKey(root, "musicShelfRenderer", shelves)
            shelves.firstOrNull()?.let { shelf ->
                songsPlaylistBrowseId = shelf.optJSONObject("bottomEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotEmpty() }
                    ?: shelf.optJSONObject("moreContentButton")
                        ?.optJSONObject("buttonRenderer")
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.isNotEmpty() }
                    ?: shelf.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.isNotEmpty() }
                val contents = shelf.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        contents.optJSONObject(i)
                            ?.optJSONObject("musicResponsiveListItemRenderer")
                            ?.let { renderer ->
                                parseResponsiveListItem(renderer)?.let { songs.add(it) }
                            }
                    }
                }
            }

            // Verified September 2026: release cards carry year and optional
            // type, and the shelf's More endpoint opens a paginated discography.
            // The Albums shelf arrives complete (no More); Singles pages.
            val carousels = MusicMetadata.objects(root, "musicCarouselShelfRenderer")
            data class CarouselMoreRequest(
                val isAlbums: Boolean,
                val defaultType: MusicReleaseType,
                val moreId: String,
                val moreParams: String?
            )
            val moreRequests = mutableListOf<CarouselMoreRequest>()
            for (carousel in carousels) {
                val carouselHeader = carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                val title = MusicMetadata.text(carouselHeader?.optJSONObject("title")).trim()
                val normalizedTitle = title.lowercase()
                val isAlbums = normalizedTitle == "albums" || normalizedTitle == "álbuns" || normalizedTitle == "albuns"
                val isSingles = normalizedTitle == "singles & eps" || normalizedTitle == "singles e eps" ||
                    normalizedTitle == "singles" || normalizedTitle == "eps"
                if (!isAlbums && !isSingles) continue
                val target = if (isAlbums) albums else singles
                val defaultType = if (isAlbums) MusicReleaseType.ALBUM else MusicReleaseType.SINGLE
                val releases = MusicMetadata.releaseRows(carousel, defaultType, artistName)
                if (releases.isEmpty()) continue
                target.addAll(releases)
                val more = carouselHeader?.optJSONObject("moreContentButton")?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?: carouselHeader?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?: continue
                val moreId = more.optString("browseId").takeIf { it.isNotBlank() } ?: continue
                moreRequests.add(
                    CarouselMoreRequest(
                        isAlbums = isAlbums,
                        defaultType = defaultType,
                        moreId = moreId,
                        moreParams = more.optString("params").takeIf { it.isNotBlank() }
                    )
                )
            }

            // --- Full songs list + paginated discography in parallel ---
            // Shelf order is captured first: the merge below only appends,
            // so the top-songs order survives the full fetch.
            val topSongIds = songs.map { it.id }
            val (discographyExtra, fullList) = coroutineScope {
                val discographyDeferred = moreRequests.map { req ->
                    async(Dispatchers.IO) {
                        val collected = mutableListOf<PlaylistDisplayItem>()
                        var page = musicApi.browseMusic(req.moreId, req.moreParams)
                            ?.let { JSONObject(it) } ?: return@async req.isAlbums to collected
                        val seen = mutableSetOf<String>()
                        var pages = 0
                        val maxPages = 15
                        while (pages < maxPages) {
                            coroutineContext.ensureActive()
                            pages++
                            collected.addAll(MusicMetadata.releaseRows(page, req.defaultType, artistName))
                            val token = MusicMetadata.continuation(page) ?: break
                            if (!seen.add(token)) {
                                KLog.w(YOUTUBE_TAG, "Repeated music discography continuation for $artistId")
                                break
                            }
                            page = musicApi.postMusicMetadata("browse", JSONObject().put("continuation", token)) ?: break
                        }
                        req.isAlbums to collected
                    }
                }
                val songsDeferred = songsPlaylistBrowseId?.let { browseId ->
                    async(Dispatchers.IO) {
                        try {
                            musicPlaylists.getPlaylistInternal(browseId.removePrefix("VL"))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                discographyDeferred.awaitAll() to (songsDeferred?.await() ?: emptyList())
            }

            discographyExtra.forEach { (isAlbums, items) ->
                if (isAlbums) albums.addAll(items) else singles.addAll(items)
            }
            fullList.forEach { song ->
                val index = songs.indexOfFirst { it.id == song.id }
                if (index < 0) songs.add(song) else if (songs[index].albumId == null && song.albumId != null) {
                    songs[index] = song
                }
            }

            KLog.d(
                YOUTUBE_TAG,
                "Artist $artistId: ${songs.size} songs, ${albums.size} albums, ${singles.size} singles"
            )
            val releases = (albums + singles).distinctBy { it.id }.newestReleasesFirst()
            val byId = releases.associateBy { it.id }
            val enrichedSongs = songs.distinctBy { it.id }.map { song ->
                val release = byId[song.albumId]
                if (release == null) song else song.copy(
                    releaseYear = song.releaseYear ?: release.releaseYear,
                    releaseType = song.releaseType ?: release.releaseType,
                )
            }
            ArtistPage(
                id = artistId,
                name = header?.name?.takeIf(String::isNotBlank) ?: artistName.takeIf(String::isNotBlank) ?: artistId,
                bio = header?.bio,
                monthlyAudience = header?.monthlyAudience,
                bannerUrl = header?.bannerUrl,
                songs = enrichedSongs,
                topSongIds = topSongIds,
                albums = albums.distinctBy { it.id }.newestReleasesFirst(),
                singles = singles.distinctBy { it.id }.newestReleasesFirst(),
                similarArtists = MusicMetadata.similarArtists(root),
                featuredOn = MusicMetadata.featuredPlaylists(root),
                songsPlaylistBrowseId = songsPlaylistBrowseId,
                isSubscribed = header?.isSubscribed ?: false,
                channelId = header?.channelId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching artist page", e)
            null
        }
    }

    /**
     * Get details for a specific artist (songs plus the merged releases).
     * Backed by [getArtistPage]; kept for callers that only need the pair.
     */
    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> {
        val page = try {
            getArtistPage(artistId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching artist details", e)
            null
        } ?: return Pair(emptyList(), emptyList())
        // The page already carries the full song list and the merged,
        // release-enriched tracks; this pair is the same shape as before.
        val releases = (page.albums + page.singles).distinctBy { it.id }.newestReleasesFirst()
        return Pair(page.songs, releases)
    }

    /**
     * An artist's "Fans might also like" shelf and top songs, from a single
     * artist browse. [getArtistPage] reads the same response and then follows
     * the discography and the full songs playlist; taste setup asks this of
     * every artist tapped, where those extra requests would be wasted.
     */
    suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample? = withContext(Dispatchers.IO) {
        if (!artistId.startsWith("UC")) return@withContext null
        try {
            val root = JSONObject(musicApi.browseMusic(artistId) ?: return@withContext null)
            val songs = mutableListOf<Song>()
            val shelves = mutableListOf<JSONObject>()
            findObjectsByKey(root, "musicShelfRenderer", shelves)
            shelves.firstOrNull()?.optJSONArray("contents")?.let { contents ->
                for (i in 0 until contents.length()) {
                    contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
                        ?.let { parseResponsiveListItem(it) }
                        ?.let(songs::add)
                }
            }
            ArtistTasteSample(similar = MusicMetadata.similarArtists(root), topSongs = songs.distinctBy { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "Artist taste sample failed for $artistId", e)
            null
        }
    }

    /**
     * Resolve where a song lives from one music `/next` call: the first
     * panel renderer is the song itself and its byline runs name the artist
     * link, the album link and the year. Works anonymously. Verified
     * September 2026.
     */
    suspend fun getSongAlbumRef(videoId: String): SongAlbumRef? = withContext(Dispatchers.IO) {
        try {
            val root = musicApi.postMusicMetadata("next", JSONObject().put("videoId", videoId))
                ?: return@withContext null
            val panels = MusicMetadata.objects(root, "playlistPanelVideoRenderer")
            val self = panels.firstOrNull {
                it.optJSONObject("playlistItemData")?.optString("videoId") == videoId
            } ?: panels.firstOrNull() ?: return@withContext null
            MusicMetadata.songAlbumRef(self)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error resolving album for song $videoId", e)
            null
        }
    }

    /**
     * Get related songs ("radio") for a video via the InnerTube /next endpoint —
     * the same source YouTube Music uses for its own autoplay queue.
     * Works anonymously; when logged in, the attached cookies personalize the mix.
     */
    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song> = withContext(Dispatchers.IO) {
        try {
            val seenSignatures = HashSet<String>()
            radioPanelSongs(videoId)
                .filter { song ->
                    song.id != videoId &&
                        (song.duration <= 0L || song.duration <= MAX_RADIO_TRACK_DURATION_MS)
                }
                .filter { song ->
                    val sig = "${song.title.trim().lowercase()}|${song.artist.trim().lowercase()}"
                    seenSignatures.add(sig)
                }
                .distinctBy { it.id }
                .take(limit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            KLog.e(YOUTUBE_TAG, "Error fetching related songs for $videoId", e)
            emptyList()
        }
    }

    suspend fun getRadioPage(
        videoId: String,
        continuationToken: String? = null,
        tunerSetting: String = "AUTOMIX_SETTING_NORMAL",
    ): com.vimi.k7.innertube.model.RadioPage = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB_REMIX")
                            .put("clientVersion", WEB_REMIX_VERSION)
                            .put("hl", http.contentLanguage())
                            .put("gl", http.contentRegion()),
                    ),
                )
                if (!continuationToken.isNullOrBlank()) {
                    put("continuation", continuationToken)
                } else {
                    put("videoId", videoId)
                    put("playlistId", "RDAMVM$videoId")
                    put("isAudioOnly", true)
                    put("enablePersistentPlaylistPanel", true)
                    put("tunerSettingValue", tunerSetting)
                }
            }
            val requestBuilder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .addHeader("Origin", "https://music.youtube.com")
            requestBuilder.authenticate(sessionManager.captureSession())

            val response = http.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.use { it.body?.string() }
            if (body.isNullOrEmpty()) {
                return@withContext com.vimi.k7.innertube.model.RadioPage(emptyList(), null)
            }
            MusicMetadata.queueRadioPage(JSONObject(body))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            KLog.e(YOUTUBE_TAG, "Error fetching radio page for $videoId", e)
            com.vimi.k7.innertube.model.RadioPage(emptyList(), null)
        }
    }

    /** The song itself as the radio panel describes it: title, artist and length. */
    suspend fun getSongFromPanel(videoId: String): Song? = withContext(Dispatchers.IO) {
        try {
            radioPanelSongs(videoId).firstOrNull { it.id == videoId }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            KLog.w(YOUTUBE_TAG, "No panel entry for $videoId", e)
            null
        }
    }

    private data class CachedRadioPanel(
        val songs: List<Song>,
        val timestampMs: Long
    )

    private val radioPanelCache = ConcurrentHashMap<String, CachedRadioPanel>()

    fun clearRadioPanelCache() {
        radioPanelCache.clear()
    }

    private fun radioPanelSongs(videoId: String): List<Song> {
        val profileKey = "${sessionManager.getActiveProfile().id}:$videoId"
        val now = System.currentTimeMillis()
        radioPanelCache[profileKey]?.let { cached ->
            if (now - cached.timestampMs < RADIO_PANEL_TTL_MS) {
                return cached.songs
            }
            radioPanelCache.remove(profileKey)
        }

        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$WEB_REMIX_VERSION",
                        "hl": "${http.contentLanguage()}",
                        "gl": "${http.contentRegion()}"
                    }
                },
                "videoId": "$videoId",
                "playlistId": "RDAMVM$videoId",
                "isAudioOnly": true,
                "enablePersistentPlaylistPanel": true,
                "tunerSettingValue": "AUTOMIX_SETTING_FAMILIAR"
            }
        """.trimIndent()

        val requestBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/next")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")

        // Personalize the radio when logged in; anonymous works fine too.
        requestBuilder.authenticate(sessionManager.captureSession())

        val response = http.okHttpClient.newCall(requestBuilder.build()).execute()
        val body = response.use { it.body?.string() }
        if (body.isNullOrEmpty()) return emptyList()

        // One song per queue entry, and the song rather than its video where
        // YouTube offers both.
        val songs = MusicMetadata.queueSongs(JSONObject(body))
        if (songs.isNotEmpty()) {
            if (radioPanelCache.size >= MAX_RADIO_CACHE_ENTRIES) {
                radioPanelCache.entries.removeIf { now - it.value.timestampMs >= RADIO_PANEL_TTL_MS }
                if (radioPanelCache.size >= MAX_RADIO_CACHE_ENTRIES) {
                    radioPanelCache.clear()
                }
            }
            radioPanelCache[profileKey] = CachedRadioPanel(songs = songs, timestampMs = now)
        }
        return songs
    }

    private companion object {
        const val RADIO_PANEL_TTL_MS = 3 * 60_000L
        const val MAX_RADIO_CACHE_ENTRIES = 64
        const val MAX_RADIO_TRACK_DURATION_MS = 12 * 60_000L
    }
}
