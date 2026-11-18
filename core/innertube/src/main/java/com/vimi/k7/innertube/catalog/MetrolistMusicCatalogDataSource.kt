package com.vimi.k7.innertube.catalog

import com.vimi.k7.innertube.model.ArtistItem
import com.vimi.k7.innertube.model.ArtistPage
import com.vimi.k7.innertube.model.ArtistTasteSample
import com.vimi.k7.innertube.model.MusicReleaseType
import com.vimi.k7.innertube.model.MusicShelfPage
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.SongAlbumRef
import com.vimi.k7.innertube.model.UNKNOWN_ALBUM
import com.vimi.k7.innertube.model.UNKNOWN_ARTIST
import com.vimi.k7.innertube.model.MusicSearchSuggestions
import com.vimi.k7.innertube.legacy.FILTER_SONGS
import com.vimi.k7.innertube.auth.YouTubeRateLimit
import com.vimi.k7.innertube.auth.YouTubeRateLimitedException
import com.vimi.k7.innertube.MetrolistInnerTubeClient
import com.vimi.k7.innertube.MetrolistInnerTubeClient.SearchFilter
import com.vimi.k7.innertube.MetrolistModelsMapper.joinArtistNames
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioArtistItem
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioMusicSearchSuggestions
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioPlaylistDisplayItem
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioSimilarArtist
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioSong
import com.vimi.k7.innertube.MetrolistModelsMapper.toCurioSongAlbumRef
import com.vimi.k7.innertube.models.AlbumItem
import com.vimi.k7.innertube.models.ArtistItem as MetrolistArtistItem
import com.vimi.k7.innertube.models.PlaylistItem
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.models.WatchEndpoint
import com.vimi.k7.innertube.util.KLog
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Single feature flag controlling the Metrolist InnerTube search path in the data layer.
 * Flip to `false` to route all search calls straight to [LegacyMusicCatalogDataSource].
 */
internal const val USE_METROLIST_SEARCH: Boolean = true

/**
 * Single feature flag controlling the Metrolist InnerTube album & song-album-ref path in the data layer.
 * Flip to `false` to route `getAlbumSongs` and `getSongAlbumRef` straight to [LegacyMusicCatalogDataSource].
 */
internal const val USE_METROLIST_ALBUMS: Boolean = true

/**
 * Single feature flag controlling the Metrolist InnerTube artist path in the data layer.
 * Flip to `false` to route artist methods (`getChartArtists`, `getArtistTasteSample`, and in 5b
 * `getArtistPage` / `getArtistDetails`) straight to [LegacyMusicCatalogDataSource].
 */
internal const val USE_METROLIST_ARTISTS: Boolean = true

/**
 * Etapa 3, 4 & 5a implementation of [MusicCatalogDataSource]:
 * - Etapa 3: Migrates search (`search` when `filter == FILTER_SONGS`, `searchNext`, `searchAlbums`,
 *   `searchArtists`, `searchPlaylists`, and `getSearchSuggestions`) to [MetrolistInnerTubeClient]
 *   with automatic fallback to [LegacyMusicCatalogDataSource] on any exception or empty result.
 * - Etapa 4: Migrates `getAlbumSongs(browseId)` and `getSongAlbumRef(videoId)` to
 *   [MetrolistInnerTubeClient] with automatic fallback to [LegacyMusicCatalogDataSource].
 * - Etapa 5a: Migrates `getChartArtists(country)` and `getArtistTasteSample(artistId)` to
 *   [MetrolistInnerTubeClient] with automatic fallback to [LegacyMusicCatalogDataSource].
 * - Delegates all remaining browse/playlist/library methods outside scope directly to [legacy].
 */
class MetrolistMusicCatalogDataSource(
    private val metrolistClient: MetrolistInnerTubeClient,
    private val legacy: MusicCatalogDataSource,
    private val useMetrolistSearch: Boolean = USE_METROLIST_SEARCH,
    private val useMetrolistAlbums: Boolean = USE_METROLIST_ALBUMS,
    private val useMetrolistArtists: Boolean = USE_METROLIST_ARTISTS,
) : MusicCatalogDataSource {

    /**
     * Thread-safe continuation map keyed by normalized query (`query.trim().lowercase(Locale.ROOT)`).
     * Stores only Metrolist continuation tokens (or [EXHAUSTED_CONTINUATION] when a Metrolist
     * search has no further pages), keeping Metrolist and Legacy continuations strictly isolated.
     */
    private val searchContinuations = ConcurrentHashMap<String, String>()

    internal fun continuationTokenForTest(query: String): String? =
        searchContinuations[normalizeQuery(query)]?.takeIf { it.isNotEmpty() }

    override fun clearCaches() {
        searchContinuations.clear()
        legacy.clearCaches()
    }

    private val negativeSearchCache = ConcurrentHashMap<String, Long>()

    private fun isNegativeSearchCached(key: String): Boolean {
        val ts = negativeSearchCache[key] ?: return false
        if (System.currentTimeMillis() - ts < NEGATIVE_SEARCH_TTL_MS) return true
        negativeSearchCache.remove(key)
        return false
    }

    private fun recordNegativeSearch(key: String) {
        if (negativeSearchCache.size >= 128) {
            val now = System.currentTimeMillis()
            negativeSearchCache.entries.removeIf { now - it.value >= NEGATIVE_SEARCH_TTL_MS }
            if (negativeSearchCache.size >= 128) negativeSearchCache.clear()
        }
        negativeSearchCache[key] = System.currentTimeMillis()
    }

    override suspend fun search(query: String, filter: String): List<Song> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        val negativeKey = "$filter:$normalizedQuery"
        if (isNegativeSearchCached(negativeKey)) {
            return@withContext emptyList()
        }
        if (!useMetrolistSearch || filter != FILTER_SONGS) {
            searchContinuations.remove(normalizedQuery)
            val legacyItems = legacy.search(query, filter)
            if (legacyItems.isEmpty()) recordNegativeSearch(negativeKey)
            logTrace(
                method = "search",
                path = PATH_FALLBACK_EMPTY,
                count = legacyItems.size,
                normalizedQuery = normalizedQuery,
            )
            return@withContext legacyItems
        }

        searchContinuations.remove(normalizedQuery)
        val attempt = runCatching {
            checkRateLimit()
            val result = metrolistClient.search(query, SearchFilter.FILTER_SONG)
                ?: return@runCatching null
            val songs = result.items
                .filterIsInstance<SongItem>()
                .filterNot { it.isEpisode }
                .map { it.toCurioSong() }
            songs to result.continuation
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { pair ->
                if (pair == null) {
                    searchContinuations.remove(normalizedQuery)
                    logTrace(
                        method = "search",
                        path = PATH_FALLBACK_ERROR,
                        count = 0,
                        normalizedQuery = normalizedQuery,
                    )
                    emptyList()
                } else {
                    val (songs, continuation) = pair
                    if (songs.isNotEmpty()) {
                        searchContinuations[normalizedQuery] =
                            continuation?.takeIf { it.isNotBlank() } ?: EXHAUSTED_CONTINUATION
                        logTrace(
                            method = "search",
                            path = PATH_METROLIST,
                            count = songs.size,
                            normalizedQuery = normalizedQuery,
                        )
                        songs
                    } else {
                        searchContinuations.remove(normalizedQuery)
                        val fallback = legacy.search(query, filter)
                        if (fallback.isEmpty()) recordNegativeSearch(negativeKey)
                        logTrace(
                            method = "search",
                            path = PATH_FALLBACK_EMPTY,
                            count = fallback.size,
                            normalizedQuery = normalizedQuery,
                        )
                        fallback
                    }
                }
            },
            onFailure = {
                searchContinuations.remove(normalizedQuery)
                val fallback = legacy.search(query, filter)
                logTrace(
                    method = "search",
                    path = PATH_FALLBACK_ERROR,
                    count = fallback.size,
                    normalizedQuery = normalizedQuery,
                )
                fallback
            },
        )
    }

    override suspend fun searchNext(query: String): List<Song> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (!useMetrolistSearch) {
            searchContinuations.remove(normalizedQuery)
            val fallback = legacy.searchNext(query)
            logTrace(
                method = "searchNext",
                path = PATH_FALLBACK_EMPTY,
                count = fallback.size,
                normalizedQuery = normalizedQuery,
            )
            return@withContext fallback
        }

        val token = searchContinuations[normalizedQuery]
        if (token == null) {
            // Initial search was served by Legacy (or never ran on Metrolist): never mix tokens.
            val fallback = legacy.searchNext(query)
            logTrace(
                method = "searchNext",
                path = PATH_FALLBACK_EMPTY,
                count = fallback.size,
                normalizedQuery = normalizedQuery,
            )
            return@withContext fallback
        }

        if (token == EXHAUSTED_CONTINUATION) {
            // Initial search was served by Metrolist and its continuation chain is exhausted.
            logTrace(
                method = "searchNext",
                path = PATH_METROLIST,
                count = 0,
                normalizedQuery = normalizedQuery,
            )
            return@withContext emptyList()
        }

        val attempt = runCatching {
            checkRateLimit()
            val result = metrolistClient.searchContinuation(token)
            val songs = result?.items.orEmpty()
                .filterIsInstance<SongItem>()
                .filterNot { it.isEpisode }
                .map { it.toCurioSong() }
            val nextToken = result?.continuation?.takeIf { it.isNotBlank() && it != token }
            songs to nextToken
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { (songs, nextToken) ->
                if (songs.isNotEmpty()) {
                    searchContinuations[normalizedQuery] = nextToken ?: EXHAUSTED_CONTINUATION
                    logTrace(
                        method = "searchNext",
                        path = PATH_METROLIST,
                        count = songs.size,
                        normalizedQuery = normalizedQuery,
                    )
                    songs
                } else {
                    searchContinuations[normalizedQuery] = EXHAUSTED_CONTINUATION
                    val fallback = legacy.searchNext(query)
                    logTrace(
                        method = "searchNext",
                        path = PATH_FALLBACK_EMPTY,
                        count = fallback.size,
                        normalizedQuery = normalizedQuery,
                    )
                    fallback
                }
            },
            onFailure = {
                searchContinuations[normalizedQuery] = EXHAUSTED_CONTINUATION
                val fallback = legacy.searchNext(query)
                logTrace(
                    method = "searchNext",
                    path = PATH_FALLBACK_ERROR,
                    count = fallback.size,
                    normalizedQuery = normalizedQuery,
                )
                fallback
            },
        )
    }

    override suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (!useMetrolistSearch) {
            val fallback = legacy.searchAlbums(query)
            logTrace("searchAlbums", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            val result = metrolistClient.search(query, SearchFilter.FILTER_ALBUM)
                ?: return@runCatching null
            result.items
                .filterIsInstance<AlbumItem>()
                .map { it.toCurioPlaylistDisplayItem() }
                .distinctBy { it.id }
        }.onFailure { if (it is CancellationException) throw it }

        val negativeKey = "albums:$normalizedQuery"
        if (isNegativeSearchCached(negativeKey)) {
            return@withContext emptyList()
        }

        attempt.fold(
            onSuccess = { albums ->
                if (albums == null) {
                    logTrace("searchAlbums", PATH_FALLBACK_ERROR, 0, normalizedQuery)
                    emptyList()
                } else if (albums.isNotEmpty()) {
                    logTrace("searchAlbums", PATH_METROLIST, albums.size, normalizedQuery)
                    albums
                } else {
                    val fallback = legacy.searchAlbums(query)
                    if (fallback.isEmpty()) recordNegativeSearch(negativeKey)
                    logTrace("searchAlbums", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.searchAlbums(query)
                logTrace("searchAlbums", PATH_FALLBACK_ERROR, fallback.size, normalizedQuery)
                fallback
            },
        )
    }

    override suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (!useMetrolistSearch) {
            val fallback = legacy.searchArtists(query)
            logTrace("searchArtists", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            metrolistClient.search(query, SearchFilter.FILTER_ARTIST)
                ?.items
                .orEmpty()
                .filterIsInstance<MetrolistArtistItem>()
                .map { it.toCurioArtistItem() }
                .distinctBy { it.id }
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { artists ->
                if (artists.isNotEmpty()) {
                    logTrace("searchArtists", PATH_METROLIST, artists.size, normalizedQuery)
                    artists
                } else {
                    val fallback = legacy.searchArtists(query)
                    logTrace("searchArtists", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.searchArtists(query)
                logTrace("searchArtists", PATH_FALLBACK_ERROR, fallback.size, normalizedQuery)
                fallback
            },
        )
    }

    override suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (!useMetrolistSearch) {
            val fallback = legacy.searchPlaylists(query)
            logTrace("searchPlaylists", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            coroutineScope {
                val communityDeferred = async {
                    metrolistClient.search(query, SearchFilter.FILTER_COMMUNITY_PLAYLIST)?.items.orEmpty()
                }
                val featuredDeferred = async {
                    metrolistClient.search(query, SearchFilter.FILTER_FEATURED_PLAYLIST)?.items.orEmpty()
                }
                (communityDeferred.await() + featuredDeferred.await())
                    .filterIsInstance<PlaylistItem>()
                    .map { it.toCurioPlaylistDisplayItem() }
                    .distinctBy { it.id }
            }
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { playlists ->
                if (playlists.isNotEmpty()) {
                    logTrace("searchPlaylists", PATH_METROLIST, playlists.size, normalizedQuery)
                    playlists
                } else {
                    val fallback = legacy.searchPlaylists(query)
                    logTrace("searchPlaylists", PATH_FALLBACK_EMPTY, fallback.size, normalizedQuery)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.searchPlaylists(query)
                logTrace("searchPlaylists", PATH_FALLBACK_ERROR, fallback.size, normalizedQuery)
                fallback
            },
        )
    }

    override suspend fun getSearchSuggestions(query: String): MusicSearchSuggestions = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (!useMetrolistSearch) {
            val fallback = legacy.getSearchSuggestions(query)
            logTrace("getSearchSuggestions", PATH_FALLBACK_EMPTY, fallback.totalCount(), normalizedQuery)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            metrolistClient.searchSuggestions(query)?.toCurioMusicSearchSuggestions()
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { suggestions ->
                val count = suggestions?.totalCount() ?: 0
                if (suggestions != null && count > 0) {
                    logTrace("getSearchSuggestions", PATH_METROLIST, count, normalizedQuery)
                    suggestions
                } else {
                    val fallback = legacy.getSearchSuggestions(query)
                    logTrace("getSearchSuggestions", PATH_FALLBACK_EMPTY, fallback.totalCount(), normalizedQuery)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getSearchSuggestions(query)
                logTrace("getSearchSuggestions", PATH_FALLBACK_ERROR, fallback.totalCount(), normalizedQuery)
                fallback
            },
        )
    }

    // --- Etapa 4: Album tracks & song album reference ---

    override suspend fun getAlbumSongs(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanId = browseId.trim()
        if (!useMetrolistAlbums) {
            val fallback = legacy.getAlbumSongs(browseId)
            logTrace("getAlbumSongs", PATH_FALLBACK_EMPTY, fallback.size, cleanId)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            val albumPage = metrolistClient.album(cleanId, withSongs = true)
                ?: return@runCatching emptyList<Song>()
            val album = albumPage.album
            val defaultArtist = album.artists.joinArtistNames(fallback = UNKNOWN_ARTIST)
            val defaultAlbum = album.title.trim().ifEmpty { UNKNOWN_ALBUM }
            val albumArtwork = album.thumbnail.takeIf { it.isNotBlank() }
            val resolvedReleaseType = album.releaseTypeText?.trim()?.let { label ->
                MusicReleaseType.entries.firstOrNull { it.wireLabel.equals(label, ignoreCase = true) }
            }
            albumPage.songs.mapIndexed { index, item ->
                item.toCurioSong(
                    defaultArtist = defaultArtist,
                    defaultAlbum = defaultAlbum,
                    trackNumber = index + 1,
                    releaseYear = album.year,
                    releaseType = resolvedReleaseType,
                    albumIdOverride = cleanId,
                ).copy(
                    album = defaultAlbum,
                    thumbnailUrl = albumArtwork ?: item.thumbnail.takeIf { it.isNotBlank() },
                )
            }
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { songs ->
                if (songs.isNotEmpty()) {
                    logTrace("getAlbumSongs", PATH_METROLIST, songs.size, cleanId)
                    songs
                } else {
                    val fallback = legacy.getAlbumSongs(browseId)
                    logTrace("getAlbumSongs", PATH_FALLBACK_EMPTY, fallback.size, cleanId)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getAlbumSongs(browseId)
                logTrace("getAlbumSongs", PATH_FALLBACK_ERROR, fallback.size, cleanId)
                fallback
            },
        )
    }

    override suspend fun getSongAlbumRef(videoId: String): SongAlbumRef? = withContext(Dispatchers.IO) {
        val cleanVideoId = videoId.trim()
        if (!useMetrolistAlbums) {
            val fallback = legacy.getSongAlbumRef(videoId)
            logTrace("getSongAlbumRef", PATH_FALLBACK_EMPTY, if (fallback != null) 1 else 0, cleanVideoId)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            val nextResult = metrolistClient.next(WatchEndpoint(videoId = cleanVideoId))
            val songItem = nextResult?.items?.firstOrNull { it.id == cleanVideoId }
                ?: nextResult?.items?.firstOrNull()
            songItem?.toCurioSongAlbumRef()
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { ref ->
                if (ref != null) {
                    logTrace("getSongAlbumRef", PATH_METROLIST, 1, cleanVideoId)
                    ref
                } else {
                    val fallback = legacy.getSongAlbumRef(videoId)
                    logTrace("getSongAlbumRef", PATH_FALLBACK_EMPTY, if (fallback != null) 1 else 0, cleanVideoId)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getSongAlbumRef(videoId)
                logTrace("getSongAlbumRef", PATH_FALLBACK_ERROR, if (fallback != null) 1 else 0, cleanVideoId)
                fallback
            },
        )
    }

    // --- Etapa 5a: Chart artists & artist taste sample ---

    override suspend fun getChartArtists(country: String?): List<ArtistItem> = withContext(Dispatchers.IO) {
        val cleanCountry = country?.trim().orEmpty()
        if (!useMetrolistArtists) {
            val fallback = legacy.getChartArtists(country)
            logTrace("getChartArtists", PATH_FALLBACK_EMPTY, fallback.size, cleanCountry)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            val chartsPage = metrolistClient.charts(country = country)
            chartsPage
                ?.sections
                .orEmpty()
                .flatMap { it.items }
                .filterIsInstance<MetrolistArtistItem>()
                .filter { it.id.startsWith("UC") }
                .map { it.toCurioArtistItem() }
                .distinctBy { it.id }
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { artists ->
                if (artists.isNotEmpty()) {
                    logTrace("getChartArtists", PATH_METROLIST, artists.size, cleanCountry)
                    artists
                } else {
                    val fallback = legacy.getChartArtists(country)
                    logTrace("getChartArtists", PATH_FALLBACK_EMPTY, fallback.size, cleanCountry)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getChartArtists(country)
                logTrace("getChartArtists", PATH_FALLBACK_ERROR, fallback.size, cleanCountry)
                fallback
            },
        )
    }

    override suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample? = withContext(Dispatchers.IO) {
        val cleanId = artistId.trim()
        if (!useMetrolistArtists) {
            val fallback = legacy.getArtistTasteSample(artistId)
            val fallbackCount = fallback?.let { it.topSongs.size + it.similar.size } ?: 0
            logTrace("getArtistTasteSample", PATH_FALLBACK_EMPTY, fallbackCount, cleanId)
            return@withContext fallback
        }

        val attempt = runCatching {
            checkRateLimit()
            // Strictly ONE request: never follow bottomEndpoint or moreContentButton.
            val page = metrolistClient.artist(cleanId) ?: return@runCatching null
            val defaultArtist = page.artist.title.trim().ifEmpty { UNKNOWN_ARTIST }

            // Classify sections by item type first (SongItem vs MetrolistArtistItem), using title as tie-breaker support.
            val songSections = page.sections.filter { section ->
                section.items.any { it is SongItem && !it.isEpisode }
            }
            val selectedSongSection =
                songSections.firstOrNull { isSongSectionTitle(it.title) }
                    ?: songSections.firstOrNull()
            val topSongs =
                selectedSongSection
                    ?.items
                    .orEmpty()
                    .filterIsInstance<SongItem>()
                    .filterNot { it.isEpisode }
                    .distinctBy { it.id }
                    .take(5)
                    .map { it.toCurioSong(defaultArtist = defaultArtist) }

            val artistSections = page.sections.filter { section ->
                section.items.any { it is MetrolistArtistItem }
            }
            val preferredArtistSections =
                artistSections.filter { isSimilarArtistSectionTitle(it.title) }
                    .ifEmpty { artistSections }
            val similar =
                preferredArtistSections
                    .flatMap { it.items }
                    .filterIsInstance<MetrolistArtistItem>()
                    .mapNotNull { it.toCurioSimilarArtist() }
                    .distinctBy { it.id }

            if (topSongs.isEmpty() && similar.isEmpty()) {
                null
            } else {
                ArtistTasteSample(
                    similar = similar,
                    topSongs = topSongs,
                )
            }
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { sample ->
                val count = sample?.let { it.topSongs.size + it.similar.size } ?: 0
                if (sample != null && count > 0) {
                    logTrace("getArtistTasteSample", PATH_METROLIST, count, cleanId)
                    sample
                } else {
                    val fallback = legacy.getArtistTasteSample(artistId)
                    val fallbackCount = fallback?.let { it.topSongs.size + it.similar.size } ?: 0
                    logTrace("getArtistTasteSample", PATH_FALLBACK_EMPTY, fallbackCount, cleanId)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getArtistTasteSample(artistId)
                val fallbackCount = fallback?.let { it.topSongs.size + it.similar.size } ?: 0
                logTrace("getArtistTasteSample", PATH_FALLBACK_ERROR, fallbackCount, cleanId)
                fallback
            },
        )
    }

    // --- Out of scope in Etapa 3, 4 & 5a: delegate 100% to Legacy ---

    override suspend fun getRecommendations(): List<Song> =
        legacy.getRecommendations()

    override suspend fun getMusicShelves(browseId: String, params: String?): MusicShelfPage? =
        legacy.getMusicShelves(browseId, params)

    override suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage? =
        legacy.getMusicShelvesContinuation(token)

    override suspend fun getArtistPage(artistId: String): ArtistPage? =
        legacy.getArtistPage(artistId)

    override suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> =
        legacy.getArtistDetails(artistId)

    override suspend fun getRelatedSongs(videoId: String, limit: Int): List<Song> =
        legacy.getRelatedSongs(videoId, limit)

    override suspend fun getSongFromPanel(videoId: String): Song? =
        legacy.getSongFromPanel(videoId)

    override suspend fun getPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.trim()
        if (cleanId.isEmpty()) return@withContext emptyList()
        if (cleanId.startsWith("PLrock_") || cleanId.startsWith("PLelectro_") || cleanId.startsWith("PL2000s_") || cleanId.startsWith("PLsample_")) {
            return@withContext emptyList()
        }
        if (cleanId == "LM" || cleanId == "VLLM" || cleanId.startsWith("MPRE")) {
            return@withContext legacy.getPlaylist(cleanId)
        }
        val attempt = runCatching {
            checkRateLimit()
            val page = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                metrolistClient.playlist(cleanId)
            }
            page?.songs?.map { it.toCurioSong() } ?: emptyList()
        }.onFailure { if (it is CancellationException) throw it }

        attempt.fold(
            onSuccess = { songs ->
                if (songs.isNotEmpty()) {
                    logTrace("getPlaylist", PATH_METROLIST, songs.size, cleanId)
                    songs
                } else {
                    val fallback = legacy.getPlaylist(cleanId)
                    logTrace("getPlaylist", PATH_FALLBACK_EMPTY, fallback.size, cleanId)
                    fallback
                }
            },
            onFailure = {
                val fallback = legacy.getPlaylist(cleanId)
                logTrace("getPlaylist", PATH_FALLBACK_ERROR, fallback.size, cleanId)
                fallback
            }
        )
    }

    override suspend fun getLikedMusic(): List<Song> =
        legacy.getLikedMusic()

    override suspend fun getLikedMusicPage(continuationToken: String?): Pair<List<Song>, String?> =
        legacy.getLikedMusicPage(continuationToken)

    override suspend fun getUserPlaylists(): List<PlaylistDisplayItem> =
        legacy.getUserPlaylists()

    override suspend fun getUserLikedAlbums(): List<PlaylistDisplayItem> =
        legacy.getUserLikedAlbums()

    override suspend fun getUserSubscribedArtists(): List<PlaylistDisplayItem> =
        legacy.getUserSubscribedArtists()

    private fun checkRateLimit() {
        if (YouTubeRateLimit.isHeld()) {
            throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
        }
    }

    private fun logTrace(
        method: String,
        path: String,
        count: Int,
        normalizedQuery: String,
    ) {
        KLog.i(TAG, "method=$method path=$path items=$count query=\"$normalizedQuery\"")
    }

    companion object {
        internal const val TAG = "MusicCatalog"
        internal const val PATH_METROLIST = "metrolist"
        internal const val PATH_FALLBACK_ERROR = "fallback-legacy-erro"
        internal const val PATH_FALLBACK_EMPTY = "fallback-legacy-vazio"
        private const val EXHAUSTED_CONTINUATION = ""
        private const val NEGATIVE_SEARCH_TTL_MS = 60_000L

        internal fun normalizeQuery(query: String): String =
            query.trim().lowercase(Locale.ROOT)

        internal fun MusicSearchSuggestions.totalCount(): Int =
            queries.size + songs.size + albums.size + artists.size + playlists.size

        internal fun isSongSectionTitle(title: String): Boolean {
            val lower = title.trim().lowercase(Locale.ROOT)
            return lower.contains("song") ||
                lower.contains("track") ||
                lower.contains("música") ||
                lower.contains("musica") ||
                lower.contains("faixa")
        }

        internal fun isSimilarArtistSectionTitle(title: String): Boolean {
            val lower = title.trim().lowercase(Locale.ROOT)
            return lower.contains("fan") ||
                lower.contains("like") ||
                lower.contains("similar") ||
                lower.contains("parecid") ||
                lower.contains("também") ||
                lower.contains("tambem")
        }
    }
}
