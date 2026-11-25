package elovaire.music.droidbeauty.app.domain.search

import elovaire.music.droidbeauty.app.domain.model.Album
import elovaire.music.droidbeauty.app.domain.model.SearchHistoryEntry
import elovaire.music.droidbeauty.app.domain.model.SearchHistoryKind
import elovaire.music.droidbeauty.app.domain.model.Song

internal fun albumSearchHistoryEntry(album: Album): SearchHistoryEntry {
    return SearchHistoryEntry(
        key = "album:${album.id}",
        kind = SearchHistoryKind.Album,
        title = album.title,
        subtitle = album.artist,
        artUri = album.artUri,
        albumId = album.id,
    )
}

internal fun artistSearchHistoryEntry(song: Song): SearchHistoryEntry {
    val artist = song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist
    val normalizedArtist = normalizeSearchText(artist)
    return SearchHistoryEntry(
        key = "artist:$normalizedArtist",
        kind = SearchHistoryKind.Artist,
        title = artist.trim(),
        subtitle = song.album,
        artUri = song.artUri,
        query = artist.trim(),
    )
}

internal fun querySearchHistoryEntry(
    query: String,
    artUri: android.net.Uri? = null,
    subtitle: String = "",
): SearchHistoryEntry {
    val trimmed = query.trim()
    val normalized = normalizeSearchText(trimmed)
    return SearchHistoryEntry(
        key = "query:$normalized",
        kind = SearchHistoryKind.Query,
        title = trimmed,
        subtitle = subtitle.trim(),
        artUri = artUri,
        query = trimmed,
    )
}

internal fun sanitizeSearchHistory(
    history: List<SearchHistoryEntry>,
    index: SearchIndex,
): List<SearchHistoryEntry> {
    val sanitized = ArrayList<SearchHistoryEntry>(history.size)
    val seenKeys = HashSet<String>(history.size)
    history.forEach { entry ->
        val sanitizedEntry = when (entry.kind) {
            SearchHistoryKind.Album -> {
                entry.albumId
                    ?.let(index.albumsById::get)
                    ?.let(::albumSearchHistoryEntry)
            }

            SearchHistoryKind.Artist -> {
                val normalizedArtist = normalizeSearchText(entry.query ?: entry.title)
                val artist = index.artistsByNormalizedName[normalizedArtist] ?: return@forEach
                entry.copy(
                    key = "artist:${artist.normalizedName}",
                    title = artist.displayName,
                    artUri = artist.artUri,
                    query = artist.displayName,
                )
            }

            SearchHistoryKind.Query -> {
                val trimmed = (entry.query ?: entry.title).trim()
                if (trimmed.isBlank()) {
                    null
                } else {
                    entry.copy(
                        key = "query:${normalizeSearchText(trimmed)}",
                        title = trimmed,
                        query = trimmed,
                    )
                }
            }
        }
        if (sanitizedEntry != null && seenKeys.add(sanitizedEntry.key)) {
            sanitized += sanitizedEntry
        }
    }
    return sanitized
}
