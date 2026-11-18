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

    private var activeLoadJob: Job? = null
    private var activeMood: YouTubeHomeItem.Mood? = null

    init {
        loadInitialHome()
    }

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

    fun retry() {
        val currentMood = activeMood
        if (currentMood == null && !_uiState.value.hasContent) {
            loadInitialHome()
        } else {
            activeMood = null
            selectMood(currentMood)
        }
    }

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
