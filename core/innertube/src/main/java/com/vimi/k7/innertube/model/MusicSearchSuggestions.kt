package com.vimi.k7.innertube.model

/**
 * Search suggestions returned by YouTube Music (`music/get_search_suggestions`).
 */
data class MusicSearchSuggestions(
    val queries: List<String> = emptyList(),
    val songs: List<Song> = emptyList(),
    val albums: List<PlaylistDisplayItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistDisplayItem> = emptyList(),
)
