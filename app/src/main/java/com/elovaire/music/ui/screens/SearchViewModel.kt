package elovaire.music.droidbeauty.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import elovaire.music.droidbeauty.app.core.performance.ElovaireTrace
import elovaire.music.droidbeauty.app.data.library.LibraryReader
import elovaire.music.droidbeauty.app.data.playback.PlaybackReader
import elovaire.music.droidbeauty.app.data.settings.PlaybackHistoryStore
import elovaire.music.droidbeauty.app.data.settings.SearchHistoryStore
import elovaire.music.droidbeauty.app.data.youtube.YouTubeArtistSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Album
import elovaire.music.droidbeauty.app.domain.model.SearchHistoryEntry
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.domain.search.NormalizedSearchQuery
import elovaire.music.droidbeauty.app.domain.search.SearchArtistResult
import elovaire.music.droidbeauty.app.domain.search.SearchIndex
import elovaire.music.droidbeauty.app.domain.search.SearchLibrarySnapshot
import elovaire.music.droidbeauty.app.domain.search.SearchSortMode
import elovaire.music.droidbeauty.app.domain.search.albumSearchHistoryEntry
import elovaire.music.droidbeauty.app.domain.search.artistSearchHistoryEntry
import elovaire.music.droidbeauty.app.domain.search.buildSearchResults
import elovaire.music.droidbeauty.app.domain.search.buildSuggestedAlbums
import elovaire.music.droidbeauty.app.domain.search.limitSearchQueryInput
import elovaire.music.droidbeauty.app.domain.search.playbackSourceLabel
import elovaire.music.droidbeauty.app.domain.search.sanitizeSearchHistory
import elovaire.music.droidbeauty.app.domain.search.toSearchIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class YouTubeSearchFilter {
    All,
    Songs,
    Albums,
    Artists,
    Playlists,
}

internal data class SearchUiState(
    val query: String = "",
    val showAllSongResults: Boolean = false,
    val searchSongSortMode: SearchSongSortMode = SearchSongSortMode.Title,
    val showSearchSongSortOptions: Boolean = false,
    val contentMode: SearchContentMode = SearchContentMode.Discover,
    val recentSearches: List<SearchHistoryEntry> = emptyList(),
    val allMatchingSongs: List<Song> = emptyList(),
    val matchingSongs: List<Song> = emptyList(),
    val totalSongMatchCount: Int = 0,
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<SearchArtistResult> = emptyList(),
    val matchingAudiobooks: List<elovaire.music.droidbeauty.app.domain.model.Audiobook> = emptyList(),
    val suggestedAlbums: List<Album> = emptyList(),
    val isSearchPending: Boolean = false,
    val resultQuery: String = "",
    val youtubeSongs: List<Song> = emptyList(),
    val youtubeAlbums: List<Album> = emptyList(),
    val youtubeArtists: List<YouTubeArtistSummary> = emptyList(),
    val youtubePlaylists: List<YouTubeCollectionSummary> = emptyList(),
    val selectedYouTubeFilter: YouTubeSearchFilter = YouTubeSearchFilter.All,
    val isYouTubeSearchLoading: Boolean = false,
    val youtubeSearchFailed: Boolean = false,
    val youtubeRecommendations: List<Song> = emptyList(),
    val currentSongId: Long? = null,
    val isPlaybackActive: Boolean = false,
)

internal data class SearchInteractionConfig(
    val query: String = "",
    val showAllSongs: Boolean = false,
    val sortMode: SearchSongSortMode = SearchSongSortMode.Title,
    val showSortOptions: Boolean = false,
    val queryGeneration: Long = 0L,
) {
    fun normalized(): SearchInteractionConfig {
        val showAllSongs = showAllSongs && query.trim().isNotBlank()
        return copy(
            showAllSongs = showAllSongs,
            showSortOptions = showSortOptions && showAllSongs,
        )
    }
}

internal data class SearchResultKey(
    val queryGeneration: Long,
    val sortMode: SearchSongSortMode,
    val includeAllSongs: Boolean,
    val indexRevision: String,
) {
    fun matches(config: SearchInteractionConfig, indexRevision: String): Boolean {
        return queryGeneration == config.queryGeneration &&
            sortMode == config.sortMode &&
            includeAllSongs == config.showAllSongs &&
            this.indexRevision == indexRevision
    }
}

internal class SearchViewModel(
    libraryRepository: LibraryReader,
    private val playbackHistory: PlaybackHistoryStore,
    private val searchHistory: SearchHistoryStore,
    playbackReader: PlaybackReader,
    private val youTubeCatalog: YouTubeCatalogRepository? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val searchUiConfig = MutableStateFlow(
        SearchInteractionConfig(
            query = limitSearchQueryInput(savedStateHandle.get<String>(KEY_QUERY).orEmpty()),
            showAllSongs = savedStateHandle[KEY_SHOW_ALL_SONGS] ?: false,
            sortMode = savedStateHandle.get<String>(KEY_SORT_MODE)
                ?.let { saved -> SearchSongSortMode.entries.firstOrNull { it.name == saved } }
                ?: SearchSongSortMode.Title,
            showSortOptions = savedStateHandle[KEY_SHOW_SORT_OPTIONS] ?: false,
        ).normalized(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchIndex = libraryRepository.contentState
        .distinctUntilChangedBy { content ->
            content.contentRevision.takeIf(String::isNotBlank) ?: content
        }
        .map { content ->
            SearchLibrarySnapshot(
                songs = content.songs,
                albums = content.albums,
                audiobooks = content.audiobooks,
                revision = content.contentRevision,
            )
        }
        .map { snapshot -> snapshot to snapshot.signature() }
        .transformLatest { (snapshot, revision) ->
            val coroutineContext = currentCoroutineContext()
            emit(
                ElovaireTrace.section("search_index_build") {
                    snapshot.toSearchIndex(revision) {
                        coroutineContext.ensureActive()
                    }
                },
            )
        }
        .flowOn(defaultDispatcher)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
                replayExpirationMillis = SEARCH_DERIVED_STATE_REPLAY_EXPIRATION_MS,
            ),
            replay = 1,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val settledQuery = searchUiConfig
        .map { config -> SearchQueryToken(config.query, config.queryGeneration) }
        .distinctUntilChanged()
        .transformLatest { token ->
            if (token.rawQuery.trim().isNotBlank()) {
                delay(SEARCH_QUERY_DEBOUNCE_MS)
            }
            emit(token)
        }
        .flowOn(defaultDispatcher)

    private val uiConfigWithIndex = combine(searchUiConfig, searchIndex) { config, index ->
        SearchUiConfigWithIndex(config = config, indexRevision = index.revision)
    }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults: Flow<SearchResultSnapshot?> = combine(
        searchUiConfig,
        searchIndex,
        settledQuery,
    ) { config, index, settled ->
        if (settled.matches(config)) {
            SearchRequest(
                key = SearchResultKey(
                    queryGeneration = config.queryGeneration,
                    sortMode = config.sortMode,
                    includeAllSongs = config.showAllSongs,
                    indexRevision = index.revision,
                ),
                rawQuery = config.query,
                sortMode = config.sortMode.toSearchSortMode(),
                index = index,
                includeAllSongs = config.showAllSongs,
            )
        } else {
            null
        }
    }
        .distinctUntilChanged()
        .transformLatest<SearchRequest?, SearchResultSnapshot?> { request ->
            if (request != null) {
                val coroutineContext = currentCoroutineContext()
                emit(
                    SearchResultSnapshot(
                        key = request.key,
                        rawQuery = request.rawQuery,
                        results = ElovaireTrace.section(
                            if (request.includeAllSongs) "search_query_full" else "search_query_preview",
                        ) {
                            buildSearchResults(
                                query = NormalizedSearchQuery.from(request.rawQuery),
                                sortMode = request.sortMode,
                                index = request.index,
                                includeAllSongs = request.includeAllSongs,
                                cancellationCheck = {
                                    coroutineContext.ensureActive()
                                },
                            )
                        },
                    ),
                )
            } else {
                emit(null)
            }
        }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val playbackSnapshot = combine(
        playbackReader.nowPlayingState,
        playbackReader.transportState,
    ) { nowPlaying, transport ->
        PlaybackSearchSnapshot(
            currentSongId = nowPlaying.currentSong?.id,
            isPlaybackActive = transport.isPlaying,
        )
    }
        .distinctUntilChanged()

    private val suggestedAlbums = combine(
        searchIndex,
        playbackHistory.albumPlayCounts,
        playbackReader.recentPlaybackState.map { it.recentAlbumIds }.distinctUntilChanged(),
        searchUiConfig.map { it.query.isNotBlank() }.distinctUntilChanged(),
    ) { index, albumPlayCounts, recentAlbumIds, hasQuery ->
        if (hasQuery) {
            emptyList()
        } else {
            buildSuggestedAlbums(
                albums = index.albums,
                albumPlayCounts = albumPlayCounts,
                recentAlbumIds = recentAlbumIds,
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val recentSearches = combine(
        searchHistory.searchHistory,
        searchIndex,
    ) { history, index ->
        sanitizeSearchHistory(
            history = history,
            index = index,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    @Volatile
    private var youtubeAlbumBrowseIds: Map<Long, String> = emptyMap()

    @Volatile
    private var youtubeAlbumSummaries: Map<Long, YouTubeCollectionSummary> = emptyMap()

    private val youtubeRetryNonce = MutableStateFlow(0L)
    private val selectedYouTubeFilterFlow = MutableStateFlow(YouTubeSearchFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val youtubeSearchState: Flow<YouTubeSearchSnapshot> = combine(
        settledQuery.map { it.rawQuery.trim() }.distinctUntilChanged(),
        youtubeRetryNonce,
    ) { query, _ -> query }
        .transformLatest { query ->
            if (query.isBlank() || youTubeCatalog == null) {
                emit(YouTubeSearchSnapshot())
                return@transformLatest
            }
            emit(YouTubeSearchSnapshot(query = query, isLoading = true))
            val result = try {
                coroutineScope {
                    val songsDeferred = async { youTubeCatalog.searchSongs(query) }
                    val albumsDeferred = async {
                        runCatching { youTubeCatalog.searchAlbums(query) }.getOrDefault(emptyList())
                    }
                    val artistsDeferred = async {
                        runCatching { youTubeCatalog.searchArtists(query) }.getOrDefault(emptyList())
                    }
                    val playlistsDeferred = async {
                        runCatching { youTubeCatalog.searchPlaylists(query) }.getOrDefault(emptyList())
                    }
                    val songs = songsDeferred.await()
                    val albumSummaries = albumsDeferred.await()
                    val artists = artistsDeferred.await()
                    val playlists = playlistsDeferred.await()
                    val browseIds = mutableMapOf<Long, String>()
                    val summariesById = mutableMapOf<Long, YouTubeCollectionSummary>()
                    val albums = albumSummaries.map { summary ->
                        val album = summary.toSearchAlbum()
                        browseIds[album.id] = summary.id
                        summariesById[album.id] = summary
                        album
                    }
                    youtubeAlbumBrowseIds = browseIds
                    youtubeAlbumSummaries = summariesById
                    YouTubeSearchSnapshot(
                        query = query,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        playlists = playlists,
                        isLoading = false,
                        failed = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                YouTubeSearchSnapshot(query = query, isLoading = false, failed = true)
            }
            emit(result)
        }
        .distinctUntilChanged()

    private val youtubeRecommendationsFlow: Flow<List<Song>> = flow {
        if (youTubeCatalog == null) {
            emit(emptyList())
            return@flow
        }
        val picks = try {
            youTubeCatalog.getRecommendations()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
        emit(picks)
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000L,
            replayExpirationMillis = SEARCH_DERIVED_STATE_REPLAY_EXPIRATION_MS,
        ),
        replay = 1,
    )

    private val playbackAndYouTubeSnapshot = combine(
        playbackSnapshot,
        youtubeSearchState,
        youtubeRecommendationsFlow,
        selectedYouTubeFilterFlow,
    ) { playback, youtube, recommendations, filter ->
        Quad(playback, youtube, recommendations, filter)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        uiConfigWithIndex,
        recentSearches,
        searchResults,
        suggestedAlbums,
        playbackAndYouTubeSnapshot,
    ) { configWithIndex, history, resultSnapshot, suggested, (playback, youtube, recommendations, filter) ->
        val config = configWithIndex.config
        val trimmedQuery = config.query.trim()
        val results = resultSnapshot?.takeIf {
            it.key.matches(config, configWithIndex.indexRevision)
        }
        val isSearchPending = trimmedQuery.isNotBlank() && results == null
        val matchesYouTubeQuery = trimmedQuery.isNotBlank() && youtube.query == trimmedQuery
        SearchUiState(
            query = config.query,
            showAllSongResults = config.showAllSongs,
            searchSongSortMode = config.sortMode,
            showSearchSongSortOptions = config.showSortOptions,
            contentMode = when {
                config.showAllSongs && trimmedQuery.isNotBlank() -> SearchContentMode.AllSongs
                trimmedQuery.isBlank() -> SearchContentMode.Discover
                else -> SearchContentMode.Results
            },
            recentSearches = history,
            allMatchingSongs = results?.results?.allMatchingSongs.orEmpty(),
            matchingSongs = results?.results?.matchingSongs.orEmpty(),
            totalSongMatchCount = results?.results?.totalSongMatchCount ?: 0,
            matchingAlbums = results?.results?.matchingAlbums.orEmpty(),
            matchingArtists = results?.results?.matchingArtists.orEmpty(),
            matchingAudiobooks = results?.results?.matchingAudiobooks.orEmpty(),
            suggestedAlbums = if (trimmedQuery.isBlank()) suggested else emptyList(),
            isSearchPending = isSearchPending,
            resultQuery = results?.rawQuery.orEmpty(),
            youtubeSongs = if (matchesYouTubeQuery) youtube.songs else emptyList(),
            youtubeAlbums = if (matchesYouTubeQuery) youtube.albums else emptyList(),
            youtubeArtists = if (matchesYouTubeQuery) youtube.artists else emptyList(),
            youtubePlaylists = if (matchesYouTubeQuery) youtube.playlists else emptyList(),
            selectedYouTubeFilter = filter,
            isYouTubeSearchLoading = trimmedQuery.isNotBlank() && (!matchesYouTubeQuery || youtube.isLoading),
            youtubeSearchFailed = matchesYouTubeQuery && youtube.failed,
            youtubeRecommendations = if (trimmedQuery.isBlank()) recommendations else emptyList(),
            currentSongId = playback.currentSongId,
            isPlaybackActive = playback.isPlaybackActive,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
                replayExpirationMillis = SEARCH_DERIVED_STATE_REPLAY_EXPIRATION_MS,
            ),
            initialValue = SearchUiState(),
        )

    fun selectYouTubeFilter(filter: YouTubeSearchFilter) {
        selectedYouTubeFilterFlow.value = filter
    }

    fun retryYouTubeSearch() {
        youtubeRetryNonce.update { it + 1L }
    }

    fun youtubeAlbumSummary(albumId: Long): YouTubeCollectionSummary? =
        youtubeAlbumSummaries[albumId]

    suspend fun loadYouTubeAlbumSongs(albumId: Long): List<Song> {
        val browseId = youtubeAlbumBrowseIds[albumId] ?: return emptyList()
        val catalog = youTubeCatalog ?: return emptyList()
        return runCatching { catalog.getAlbumSongs(browseId) }.getOrDefault(emptyList())
    }

    suspend fun loadYouTubePlaylistSongs(playlistId: String): List<Song> {
        val catalog = youTubeCatalog ?: return emptyList()
        return runCatching { catalog.getPlaylistSongs(playlistId) }.getOrDefault(emptyList())
    }

    fun onQueryChange(query: String) {
        val boundedQuery = limitSearchQueryInput(query)
        updateConfig { config ->
            if (config.query == boundedQuery) config else config.copy(
                query = boundedQuery,
                queryGeneration = config.queryGeneration + 1,
            )
        }
    }

    fun clearQuery() = updateConfig { config ->
        config.copy(
            query = "",
            queryGeneration = if (config.query.isBlank()) config.queryGeneration else config.queryGeneration + 1,
        )
    }

    fun onShowAllSongResultsChange(show: Boolean) {
        updateConfig { it.copy(showAllSongs = show) }
    }

    fun onSearchSongSortModeChange(mode: SearchSongSortMode) {
        updateConfig { it.copy(sortMode = mode, showSortOptions = false) }
    }

    fun onShowSearchSongSortOptionsChange(show: Boolean) {
        updateConfig { it.copy(showSortOptions = show) }
    }

    fun resetSearchUi() {
        clearQuery()
    }

    fun clearSearchHistory() {
        searchHistory.clearSearchHistoryEntries()
    }

    fun rememberAlbumSearch(album: Album) {
        searchHistory.addSearchHistoryEntry(albumSearchHistoryEntry(album))
    }

    fun rememberArtistSearch(song: Song) {
        searchHistory.addSearchHistoryEntry(artistSearchHistoryEntry(song))
    }

    fun playbackSourceLabelFor(queue: List<Song>, fallbackAlbum: String): String {
        return queue.playbackSourceLabel(fallbackAlbum = fallbackAlbum)
    }

    private companion object {
        data class SearchQueryToken(
            val rawQuery: String,
            val generation: Long,
        ) {
            fun matches(config: SearchInteractionConfig): Boolean {
                return rawQuery == config.query && generation == config.queryGeneration
            }
        }

        data class SearchResultSnapshot(
            val key: SearchResultKey,
            val rawQuery: String,
            val results: elovaire.music.droidbeauty.app.domain.search.SearchResults,
        )

        data class SearchUiConfigWithIndex(
            val config: SearchInteractionConfig,
            val indexRevision: String,
        )

        data class PlaybackSearchSnapshot(
            val currentSongId: Long?,
            val isPlaybackActive: Boolean,
        )

        class SearchRequest(
            val key: SearchResultKey,
            val rawQuery: String,
            val sortMode: SearchSortMode,
            val index: SearchIndex,
            val includeAllSongs: Boolean,
        ) {
            override fun equals(other: Any?): Boolean {
                return other is SearchRequest && key == other.key
            }

            override fun hashCode(): Int = key.hashCode()
        }

        const val SEARCH_QUERY_DEBOUNCE_MS = 150L
        const val SEARCH_DERIVED_STATE_REPLAY_EXPIRATION_MS = 30_000L
        const val KEY_QUERY = "search.query"
        const val KEY_SHOW_ALL_SONGS = "search.show_all_songs"
        const val KEY_SORT_MODE = "search.sort_mode"
        const val KEY_SHOW_SORT_OPTIONS = "search.show_sort_options"
    }

    private fun updateConfig(transform: (SearchInteractionConfig) -> SearchInteractionConfig) {
        searchUiConfig.update { current ->
            transform(current).normalized().also { next ->
                savedStateHandle[KEY_QUERY] = next.query
                savedStateHandle[KEY_SHOW_ALL_SONGS] = next.showAllSongs
                savedStateHandle[KEY_SORT_MODE] = next.sortMode.name
                savedStateHandle[KEY_SHOW_SORT_OPTIONS] = next.showSortOptions
            }
        }
    }

}

private data class YouTubeSearchSnapshot(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<YouTubeArtistSummary> = emptyList(),
    val playlists: List<YouTubeCollectionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun YouTubeCollectionSummary.toSearchAlbum(): Album = Album(
    id = YouTubeUri.trackIdFor("album:$id"),
    title = title,
    artist = subtitle,
    artUri = thumbnailUri,
    songCount = itemCount.coerceAtLeast(0),
    durationMs = 0L,
    songs = emptyList(),
)

private fun SearchSongSortMode.toSearchSortMode(): SearchSortMode {
    return when (this) {
        SearchSongSortMode.Title -> SearchSortMode.Title
        SearchSongSortMode.Artist -> SearchSortMode.Artist
    }
}
