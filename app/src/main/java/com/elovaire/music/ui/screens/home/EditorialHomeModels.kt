package elovaire.music.droidbeauty.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.Immutable
import elovaire.music.droidbeauty.app.data.youtube.YouTubeHomeItem
import elovaire.music.droidbeauty.app.domain.model.Song

@Immutable
internal enum class EditorialSectionId(val routeKey: String) {
    Featured("featured"),
    Resume("resume"),
    NewReleases("new_releases"),
    Essentials("essentials"),
    Trending("trending"),
    Moods("moods"),
    ;

    companion object {
        fun fromRouteKey(key: String?): EditorialSectionId =
            entries.firstOrNull { it.routeKey == key } ?: Featured
    }
}

@Immutable
internal sealed interface EditorialSectionState<out T> {
    @Immutable
    data object Loading : EditorialSectionState<Nothing>

    @Immutable
    data class Content<T>(val items: List<T>) : EditorialSectionState<T>

    @Immutable
    data object Empty : EditorialSectionState<Nothing>

    @Immutable
    data object Error : EditorialSectionState<Nothing>
}

@Immutable
internal data class EditorialCollectionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artUri: Uri?,
    val isAlbum: Boolean,
    val collection: YouTubeHomeItem.Collection,
)

@Immutable
internal data class EditorialChartTrack(
    val rank: Int,
    val song: Song,
)

@Immutable
internal data class EditorialMoodItem(
    val key: String,
    val title: String,
    val mood: YouTubeHomeItem.Mood,
)

@Immutable
internal data class EditorialHomeUiState(
    val featured: EditorialSectionState<EditorialCollectionItem> = EditorialSectionState.Loading,
    val newReleases: EditorialSectionState<EditorialCollectionItem> = EditorialSectionState.Loading,
    val essentials: EditorialSectionState<EditorialCollectionItem> = EditorialSectionState.Loading,
    val trending: EditorialSectionState<EditorialChartTrack> = EditorialSectionState.Loading,
    val moods: EditorialSectionState<EditorialMoodItem> = EditorialSectionState.Loading,
    val playingHeroId: String? = null,
    val isRefreshing: Boolean = false,
)
