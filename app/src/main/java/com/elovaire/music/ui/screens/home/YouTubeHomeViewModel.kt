package elovaire.music.droidbeauty.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeFeed
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeShelf
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class YouTubeHomeUiState(
    val moods: List<YouTubeHomeItem.Mood> = emptyList(),
    val selectedMoodKey: String? = null,
    val speedDialTracks: List<Song> = emptyList(),
    val shelves: List<YouTubeHomeShelf> = emptyList(),
    val continuation: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasError: Boolean = false,
) {
    val hasContent: Boolean
        get() = speedDialTracks.isNotEmpty() || shelves.isNotEmpty()
}

internal class YouTubeHomeViewModel(
    private val youTubeCatalog: YouTubeCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(YouTubeHomeUiState(isLoading = true))
    val uiState: StateFlow<YouTubeHomeUiState> = _uiState.asStateFlow()

    private val _editorialState = MutableStateFlow(EditorialHomeUiState())
    val editorialState: StateFlow<EditorialHomeUiState> = _editorialState.asStateFlow()

    private var activeLoadJob: Job? = null
    private var activeMood: YouTubeHomeItem.Mood? = null

    private var curatedSectionsJob: Job? = null
    private var trendingJob: Job? = null
    private var moodsJob: Job? = null
    private var refreshJob: Job? = null
    private var smartQueueJob: Job? = null

    init {
        loadEditorialHome()
    }

    fun refreshEditorialHome() {
        if (_editorialState.value.isRefreshing) return
        refreshJob?.cancel()
        _editorialState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                youTubeCatalog.clearCaches()
                coroutineScope {
                    val j1 = async { fetchCuratedSections(showSkeleton = false) }
                    val j2 = async { fetchTrendingSection(showSkeleton = false) }
                    val j3 = async { fetchMoodsSection(showSkeleton = false) }
                    awaitAll(j1, j2, j3)
                }
            } finally {
                _editorialState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun retryEditorialSection(sectionId: EditorialSectionId) {
        when (sectionId) {
            EditorialSectionId.Featured,
            EditorialSectionId.NewReleases,
            EditorialSectionId.Essentials,
            -> {
                curatedSectionsJob?.cancel()
                curatedSectionsJob = viewModelScope.launch { fetchCuratedSections(showSkeleton = true) }
            }
            EditorialSectionId.Trending -> {
                trendingJob?.cancel()
                trendingJob = viewModelScope.launch { fetchTrendingSection(showSkeleton = true) }
            }
            EditorialSectionId.Moods -> {
                moodsJob?.cancel()
                moodsJob = viewModelScope.launch { fetchMoodsSection(showSkeleton = true) }
            }
            EditorialSectionId.Resume -> Unit
        }
    }

    fun playHeroCollection(
        item: EditorialCollectionItem,
        onQueueReady: (Song, List<Song>) -> Unit,
        onFallbackOpen: (YouTubeHomeItem.Collection) -> Unit,
    ) {
        if (_editorialState.value.playingHeroId != null) return
        _editorialState.update { it.copy(playingHeroId = item.id) }
        viewModelScope.launch {
            try {
                val songs = loadCollectionSongs(item.collection)
                if (songs.isNotEmpty()) {
                    onQueueReady(songs.first(), songs)
                } else {
                    onFallbackOpen(item.collection)
                }
            } finally {
                _editorialState.update { state ->
                    if (state.playingHeroId == item.id) state.copy(playingHeroId = null) else state
                }
            }
        }
    }

    /**
     * Resolves a coherent related queue in the background when the user plays a single
     * YouTube track (e.g. from Para retomar or Search) so upcoming songs match the seed's style.
     */
    fun loadSmartRelatedForQueue(
        seed: Song,
        onRelatedReady: (List<Song>) -> Unit,
    ) {
        smartQueueJob?.cancel()
        smartQueueJob = viewModelScope.launch {
            val related = runCatching {
                youTubeCatalog.getSmartRelatedSongs(seed, limit = 20)
            }.getOrDefault(emptyList())
            if (related.isNotEmpty()) {
                onRelatedReady(related)
            }
        }
    }

    private fun loadEditorialHome() {
        curatedSectionsJob?.cancel()
        curatedSectionsJob = viewModelScope.launch { fetchCuratedSections(showSkeleton = true) }
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch { fetchTrendingSection(showSkeleton = true) }
        moodsJob?.cancel()
        moodsJob = viewModelScope.launch { fetchMoodsSection(showSkeleton = true) }
    }

    /**
     * Coordena Destaque (FEmusic_home), Lançamentos (FEmusic_new_releases) e
     * Essenciais (playlists e álbuns de referência em FEmusic_explore + FEmusic_home),
     * garantindo ZERO duplicação entre as 3 seções.
     */
    private suspend fun fetchCuratedSections(showSkeleton: Boolean) {
        if (showSkeleton) {
            _editorialState.update {
                it.copy(
                    featured = EditorialSectionState.Loading,
                    newReleases = EditorialSectionState.Loading,
                    essentials = EditorialSectionState.Loading,
                )
            }
        }
        try {
            coroutineScope {
                val homeDeferred = async { runCatching { youTubeCatalog.getHomeFeed() }.getOrNull() }
                val releasesDeferred = async { runCatching { youTubeCatalog.getNewReleasesFeed() }.getOrNull() }
                val exploreDeferred = async { runCatching { youTubeCatalog.getExploreFeed() }.getOrNull() }

                val homeFeed = homeDeferred.await()
                val releasesFeed = releasesDeferred.await()
                val exploreFeed = exploreDeferred.await()

                val homeCollections = homeFeed.extractCollections()
                val releaseCollections = releasesFeed.extractCollections()
                val exploreNonReleaseCollections = exploreFeed?.shelves.orEmpty()
                    .filterNot { shelf -> shelf.title.isReleaseOrChartShelfTitle() }
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Collection>()
                    .distinctBy { it.summary.id }

                // 1. Destaque: primeira coleção personalizada de FEmusic_home (fallback explore/releases)
                val featuredPool = (homeCollections + exploreNonReleaseCollections + releaseCollections)
                    .distinctBy { it.summary.id }
                    .sortedByDescending { it.summary.thumbnailUri != null }
                    .map { it.toEditorialCollectionItem() }
                val heroId = featuredPool.firstOrNull()?.id

                // 3. Lançamentos: exclusivamente de FEmusic_new_releases (priorizando álbuns/singles)
                val releaseAlbums = releaseCollections.filter { it.isAlbum }
                val newReleasesPool = (if (releaseAlbums.isNotEmpty()) releaseAlbums else releaseCollections)
                    .filterNot { it.summary.id == heroId }
                    .distinctBy { it.summary.id }
                    .map { it.toEditorialCollectionItem() }
                val newReleaseIds = newReleasesPool.mapTo(HashSet()) { it.id }

                // 4. Essenciais: playlists editoriais e álbuns de referência distintos de Destaque e Lançamentos
                val excludedForEssentials = HashSet<String>(newReleaseIds).apply {
                    if (heroId != null) add(heroId)
                }
                val homeNonReleaseCollections = homeFeed?.shelves.orEmpty()
                    .filterNot { shelf -> shelf.title.isReleaseOrChartShelfTitle() }
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Collection>()
                val rawEssentials = (exploreNonReleaseCollections + homeNonReleaseCollections)
                    .filterNot { it.summary.id in excludedForEssentials }
                    .distinctBy { it.summary.id }
                // Prioriza playlists editoriais (!isAlbum) para contrastar com os álbuns de Lançamentos
                val editorialPlaylists = rawEssentials.filter { !it.isAlbum }
                val editorialAlbums = rawEssentials.filter { it.isAlbum }
                val essentialsPool = (editorialPlaylists + editorialAlbums)
                    .map { it.toEditorialCollectionItem() }

                _editorialState.update {
                    it.copy(
                        featured = featuredPool.toSectionState(failed = homeFeed == null && exploreFeed == null),
                        newReleases = newReleasesPool.toSectionState(failed = releasesFeed == null),
                        essentials = essentialsPool.toSectionState(failed = exploreFeed == null && homeFeed == null),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _editorialState.update {
                it.copy(
                    featured = EditorialSectionState.Error,
                    newReleases = EditorialSectionState.Error,
                    essentials = EditorialSectionState.Error,
                )
            }
        }
    }

    /**
     * Seção 5 (Em alta): parada numerada de mais ouvidas.
     * 1. Extrai faixas diretas de FEmusic_charts.
     * 2. Se FEmusic_charts retornar playlists de parada (ex: Top 100 Songs / Top Music Videos),
     *    carrega as faixas reais da playlist principal de parada via getPlaylistSongs.
     * 3. Fallback: busca shelf de "Em alta / Trending / Top" em FEmusic_explore.
     */
    private suspend fun fetchTrendingSection(showSkeleton: Boolean) {
        if (showSkeleton) {
            _editorialState.update { it.copy(trending = EditorialSectionState.Loading) }
        }
        try {
            val chartsFeed = runCatching { youTubeCatalog.getChartsFeed() }.getOrNull()
            var songs = chartsFeed.extractTracks()

            if (songs.isEmpty() && chartsFeed != null) {
                val chartPlaylists = chartsFeed.extractCollections().filter { !it.isAlbum }
                val preferredPlaylist = chartPlaylists.firstOrNull { col ->
                    val t = col.summary.title.lowercase()
                    t.contains("song") || t.contains("música") || t.contains("musica") ||
                        t.contains("top") || t.contains("alta") || t.contains("trending")
                } ?: chartPlaylists.firstOrNull()

                if (preferredPlaylist != null) {
                    songs = runCatching {
                        youTubeCatalog.getPlaylistSongs(preferredPlaylist.summary.id)
                    }.getOrDefault(emptyList())
                }
            }

            if (songs.isEmpty()) {
                val exploreFeed = runCatching { youTubeCatalog.getExploreFeed() }.getOrNull()
                val trendingShelves = exploreFeed?.shelves.orEmpty().filter { shelf ->
                    val t = shelf.title.lowercase()
                    t.contains("alta") || t.contains("trending") || t.contains("top") || t.contains("parada")
                }
                songs = trendingShelves
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Track>()
                    .map { it.song }
                if (songs.isEmpty()) {
                    val trendingPlaylist = trendingShelves
                        .flatMap { it.items }
                        .filterIsInstance<YouTubeHomeItem.Collection>()
                        .firstOrNull { !it.isAlbum }
                    if (trendingPlaylist != null) {
                        songs = runCatching {
                            youTubeCatalog.getPlaylistSongs(trendingPlaylist.summary.id)
                        }.getOrDefault(emptyList())
                    }
                }
            }

            val ranked = songs
                .distinctBy { it.id }
                .mapIndexed { index, song -> EditorialChartTrack(rank = index + 1, song = song) }

            _editorialState.update {
                it.copy(
                    trending = when {
                        ranked.isNotEmpty() -> EditorialSectionState.Content(ranked)
                        chartsFeed == null -> EditorialSectionState.Error
                        else -> EditorialSectionState.Empty
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _editorialState.update { it.copy(trending = EditorialSectionState.Error) }
        }
    }

    /**
     * Seção 6 (Explorar por clima): usa FEmusic_moods_and_genres.
     */
    private suspend fun fetchMoodsSection(showSkeleton: Boolean) {
        if (showSkeleton) {
            _editorialState.update { it.copy(moods = EditorialSectionState.Loading) }
        }
        try {
            val moods = youTubeCatalog.getMoodsAndGenres()
                .distinctBy { it.title }
                .map { EditorialMoodItem(key = it.key, title = it.title, mood = it) }
            _editorialState.update {
                it.copy(
                    moods = if (moods.isEmpty()) {
                        EditorialSectionState.Empty
                    } else {
                        EditorialSectionState.Content(moods)
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _editorialState.update { it.copy(moods = EditorialSectionState.Error) }
        }
    }

    @Deprecated("Substituído pela Home editorial; mantido temporariamente para compatibilidade.")
    fun selectMood(mood: YouTubeHomeItem.Mood?) {
        val nextMood = if (mood?.key == _uiState.value.selectedMoodKey) null else mood
        activeMood = nextMood
        _uiState.update {
            it.copy(
                selectedMoodKey = nextMood?.key,
                isLoading = true,
                hasError = false,
                continuation = null,
            )
        }
        activeLoadJob?.cancel()
        activeLoadJob = viewModelScope.launch {
            try {
                val feed = if (nextMood == null) {
                    youTubeCatalog.getHomeFeed()
                } else {
                    youTubeCatalog.getHomeFeed(nextMood.browseId, nextMood.params)
                }
                _uiState.update { state ->
                    state.copy(
                        shelves = feed.shelves,
                        continuation = feed.continuation,
                        isLoading = false,
                        hasError = !state.hasContent && feed.shelves.isEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoading = false, hasError = !it.hasContent) }
            }
        }
    }

    @Deprecated("Substituído por retryEditorialSection; mantido temporariamente para compatibilidade.")
    fun retry() {
        val currentMood = activeMood
        if (currentMood == null && !_uiState.value.hasContent) {
            loadInitialHome()
        } else {
            activeMood = null
            selectMood(currentMood)
        }
    }

    @Deprecated("Substituído pela Home editorial sem feed infinito; mantido temporariamente.")
    fun loadMore() {
        val state = _uiState.value
        val token = state.continuation ?: return
        if (state.isLoading || state.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val next = youTubeCatalog.getHomeFeedContinuation(token)
                _uiState.update { current ->
                    current.copy(
                        shelves = current.shelves + next.shelves,
                        continuation = next.continuation,
                        isLoadingMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    suspend fun loadCollectionSongs(collection: YouTubeHomeItem.Collection): List<Song> {
        return runCatching {
            youTubeCatalog.getCollectionSongs(collection.summary.id, collection.isAlbum)
        }.getOrDefault(emptyList())
    }

    private fun loadInitialHome() {
        activeLoadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        activeLoadJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val feedDeferred = async {
                        runCatching { youTubeCatalog.getHomeFeed() }.getOrNull()
                    }
                    val moodsDeferred = async {
                        runCatching { youTubeCatalog.getMoodsAndGenres() }.getOrDefault(emptyList())
                    }
                    val recommendationsDeferred = async {
                        runCatching { youTubeCatalog.getRecommendations() }.getOrDefault(emptyList())
                    }

                    val feed = feedDeferred.await()
                    val fetchedMoods = moodsDeferred.await()
                    val shelfMoods = feed?.shelves
                        ?.flatMap { it.items }
                        ?.filterIsInstance<YouTubeHomeItem.Mood>()
                        .orEmpty()
                    val moods = (shelfMoods + fetchedMoods).distinctBy { it.title }.take(14)

                    val shelfTracks = feed?.shelves
                        ?.flatMap { it.items }
                        ?.filterIsInstance<YouTubeHomeItem.Track>()
                        ?.map { it.song }
                        .orEmpty()
                    val recommendations = recommendationsDeferred.await()
                    val speedDial = (recommendations + shelfTracks)
                        .distinctBy { it.id }
                        .take(27)

                    val filteredShelves = feed?.shelves
                        ?.filterNot { shelf -> shelf.items.all { it is YouTubeHomeItem.Mood } }
                        .orEmpty()

                    _uiState.update {
                        it.copy(
                            moods = moods,
                            speedDialTracks = speedDial,
                            shelves = filteredShelves,
                            continuation = feed?.continuation,
                            isLoading = false,
                            hasError = speedDial.isEmpty() && filteredShelves.isEmpty(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoading = false, hasError = !it.hasContent) }
            }
        }
    }
}

private fun YouTubeHomeFeed?.extractCollections(): List<YouTubeHomeItem.Collection> =
    this?.shelves
        ?.flatMap { it.items }
        ?.filterIsInstance<YouTubeHomeItem.Collection>()
        ?.distinctBy { it.summary.id }
        .orEmpty()

private fun YouTubeHomeFeed?.extractTracks(): List<Song> =
    this?.shelves
        ?.flatMap { it.items }
        ?.filterIsInstance<YouTubeHomeItem.Track>()
        ?.map { it.song }
        ?.distinctBy { it.id }
        .orEmpty()

private fun String.isReleaseOrChartShelfTitle(): Boolean {
    val lower = lowercase()
    return lower.contains("new") ||
        lower.contains("release") ||
        lower.contains("lançamento") ||
        lower.contains("lancamento") ||
        lower.contains("novidade") ||
        lower.contains("trending") ||
        lower.contains("em alta") ||
        lower.contains("chart") ||
        lower.contains("parada")
}

private fun <T> List<T>.toSectionState(failed: Boolean): EditorialSectionState<T> = when {
    isNotEmpty() -> EditorialSectionState.Content(this)
    failed -> EditorialSectionState.Error
    else -> EditorialSectionState.Empty
}

private fun YouTubeHomeItem.Collection.toEditorialCollectionItem(): EditorialCollectionItem =
    EditorialCollectionItem(
        id = summary.id,
        title = summary.title,
        subtitle = summary.subtitle.ifBlank { summary.releaseYear?.toString().orEmpty() },
        artUri = summary.thumbnailUri,
        isAlbum = isAlbum,
        collection = this,
    )
