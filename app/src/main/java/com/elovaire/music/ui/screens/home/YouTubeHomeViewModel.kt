package elovaire.music.droidbeauty.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeShelf
import elovaire.music.droidbeauty.app.domain.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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

    private var featuredJob: Job? = null
    private var newReleasesJob: Job? = null
    private var essentialsJob: Job? = null
    private var trendingJob: Job? = null
    private var moodsJob: Job? = null

    init {
        loadEditorialHome()
    }

    fun retryEditorialSection(sectionId: EditorialSectionId) {
        when (sectionId) {
            EditorialSectionId.Featured -> loadFeaturedSection()
            EditorialSectionId.NewReleases -> loadNewReleasesSection()
            EditorialSectionId.Essentials -> loadEssentialsSection()
            EditorialSectionId.Trending -> loadTrendingSection()
            EditorialSectionId.Moods -> loadMoodsSection()
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

    private fun loadEditorialHome() {
        loadFeaturedSection()
        loadNewReleasesSection()
        loadEssentialsSection()
        loadTrendingSection()
        loadMoodsSection()
    }

    /**
     * Seção 1 (Destaque): usa o endpoint de browse FEmusic_home (fallback FEmusic_explore)
     * selecionando coleções (álbuns/playlists) com capa disponível.
     */
    private fun loadFeaturedSection() {
        featuredJob?.cancel()
        _editorialState.update { it.copy(featured = EditorialSectionState.Loading) }
        featuredJob = viewModelScope.launch {
            try {
                val homeFeed = runCatching { youTubeCatalog.getHomeFeed() }.getOrNull()
                var collections = homeFeed?.shelves
                    ?.flatMap { it.items }
                    ?.filterIsInstance<YouTubeHomeItem.Collection>()
                    ?.distinctBy { it.summary.id }
                    .orEmpty()
                if (collections.isEmpty()) {
                    val exploreFeed = runCatching { youTubeCatalog.getExploreFeed() }.getOrNull()
                    collections = exploreFeed?.shelves
                        ?.flatMap { it.items }
                        ?.filterIsInstance<YouTubeHomeItem.Collection>()
                        ?.distinctBy { it.summary.id }
                        .orEmpty()
                }
                val items = collections
                    .sortedByDescending { it.summary.thumbnailUri != null }
                    .map { it.toEditorialCollectionItem() }
                _editorialState.update {
                    it.copy(
                        featured = if (items.isEmpty()) {
                            EditorialSectionState.Empty
                        } else {
                            EditorialSectionState.Content(items)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _editorialState.update { it.copy(featured = EditorialSectionState.Error) }
            }
        }
    }

    /**
     * Seção 3 (Lançamentos): usa o endpoint FEmusic_new_releases (novos álbuns e singles).
     */
    private fun loadNewReleasesSection() {
        newReleasesJob?.cancel()
        _editorialState.update { it.copy(newReleases = EditorialSectionState.Loading) }
        newReleasesJob = viewModelScope.launch {
            try {
                val feed = youTubeCatalog.getNewReleasesFeed()
                val items = feed.shelves
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Collection>()
                    .distinctBy { it.summary.id }
                    .map { it.toEditorialCollectionItem() }
                _editorialState.update {
                    it.copy(
                        newReleases = if (items.isEmpty()) {
                            EditorialSectionState.Empty
                        } else {
                            EditorialSectionState.Content(items)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _editorialState.update { it.copy(newReleases = EditorialSectionState.Error) }
            }
        }
    }

    /**
     * Seção 4 (Essenciais): usa o endpoint FEmusic_explore para playlists e álbuns de referência.
     * TODO(data): quando houver um endpoint dedicado de curadoria essencial por gênero no backend,
     * substituir ou complementar o filtro sobre FEmusic_explore.
     */
    private fun loadEssentialsSection() {
        essentialsJob?.cancel()
        _editorialState.update { it.copy(essentials = EditorialSectionState.Loading) }
        essentialsJob = viewModelScope.launch {
            try {
                val feed = youTubeCatalog.getExploreFeed()
                val items = feed.shelves
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Collection>()
                    .distinctBy { it.summary.id }
                    .map { it.toEditorialCollectionItem() }
                _editorialState.update {
                    it.copy(
                        essentials = if (items.isEmpty()) {
                            EditorialSectionState.Empty
                        } else {
                            EditorialSectionState.Content(items)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _editorialState.update { it.copy(essentials = EditorialSectionState.Error) }
            }
        }
    }

    /**
     * Seção 5 (Em alta): usa o endpoint FEmusic_charts para a parada numerada de mais ouvidas.
     * TODO(data): se FEmusic_charts não retornar faixas diretamente em alguma região/conta anônima,
     * adicionar parser dedicado da playlist Top Songs retornada pelo ChartsPage.
     */
    private fun loadTrendingSection() {
        trendingJob?.cancel()
        _editorialState.update { it.copy(trending = EditorialSectionState.Loading) }
        trendingJob = viewModelScope.launch {
            try {
                val feed = youTubeCatalog.getChartsFeed()
                val tracks = feed.shelves
                    .flatMap { it.items }
                    .filterIsInstance<YouTubeHomeItem.Track>()
                    .map { it.song }
                    .distinctBy { it.id }
                    .mapIndexed { index, song -> EditorialChartTrack(rank = index + 1, song = song) }
                _editorialState.update {
                    it.copy(
                        trending = if (tracks.isEmpty()) {
                            EditorialSectionState.Empty
                        } else {
                            EditorialSectionState.Content(tracks)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _editorialState.update { it.copy(trending = EditorialSectionState.Error) }
            }
        }
    }

    /**
     * Seção 6 (Explorar por clima): usa FEmusic_moods_and_genres.
     */
    private fun loadMoodsSection() {
        moodsJob?.cancel()
        _editorialState.update { it.copy(moods = EditorialSectionState.Loading) }
        moodsJob = viewModelScope.launch {
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

private fun YouTubeHomeItem.Collection.toEditorialCollectionItem(): EditorialCollectionItem =
    EditorialCollectionItem(
        id = summary.id,
        title = summary.title,
        subtitle = summary.subtitle.ifBlank { summary.releaseYear?.toString().orEmpty() },
        artUri = summary.thumbnailUri,
        isAlbum = isAlbum,
        collection = this,
    )
