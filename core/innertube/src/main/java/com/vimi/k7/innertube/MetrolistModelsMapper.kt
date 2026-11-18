package com.vimi.k7.innertube

import com.vimi.k7.innertube.model.ArtistItem as CurioArtistItem
import com.vimi.k7.innertube.model.MusicReleaseType
import com.vimi.k7.innertube.model.PlaylistDisplayItem
import com.vimi.k7.innertube.model.SimilarArtist
import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.SongAlbumRef
import com.vimi.k7.innertube.model.ThumbnailSize
import com.vimi.k7.innertube.model.toThumbnailUrl
import com.vimi.k7.innertube.model.UNKNOWN_ALBUM
import com.vimi.k7.innertube.model.UNKNOWN_ARTIST
import com.vimi.k7.innertube.models.Album
import com.vimi.k7.innertube.models.AlbumItem
import com.vimi.k7.innertube.models.Artist
import com.vimi.k7.innertube.models.ArtistItem as MetrolistArtistItem
import com.vimi.k7.innertube.models.PlaylistItem
import com.vimi.k7.innertube.models.SearchSuggestions
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.model.MusicSearchSuggestions

/**
 * Pure domain mappers from Metrolist's InnerTube models (`com.vimi.k7.innertube.models.*`)
 * to Koda/Curio's domain models (`Song`, `PlaylistDisplayItem`, `ArtistItem`, `MusicSearchSuggestions`).
 */
object MetrolistModelsMapper {

    /**
     * Joins non-blank artist names with `", "`, falling back to [fallback] when empty.
     */
    fun List<Artist>?.joinArtistNames(fallback: String = UNKNOWN_ARTIST): String {
        val joined = this
            .orEmpty()
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        return joined.ifEmpty { fallback }
    }

    /**
     * Returns the first non-null, non-blank artist browse ID from a list of [Artist]s.
     */
    fun List<Artist>?.firstArtistId(): String? =
        this?.firstOrNull { !it.id.isNullOrBlank() }?.id

    /**
     * Converts a Metrolist [SongItem] (where `duration` is in **seconds**) into a Koda [Song]
     * (where `duration` is in **milliseconds**).
     */
    fun SongItem.toCurioSong(
        defaultArtist: String = UNKNOWN_ARTIST,
        defaultAlbum: String = UNKNOWN_ALBUM,
        trackNumber: Int? = null,
        releaseYear: Int? = null,
        releaseType: MusicReleaseType? = null,
        albumIdOverride: String? = null,
    ): Song {
        val durationMs = (duration?.toLong()?.coerceAtLeast(0L) ?: 0L) * 1000L
        val artistString = artists.joinArtistNames(fallback = defaultArtist)
        val albumName = album?.name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultAlbum
        val resolvedAlbumId = (albumIdOverride ?: album?.id)?.takeIf { it.isNotBlank() }

        return Song.fromYouTube(
            videoId = id,
            title = title,
            artist = artistString,
            album = albumName,
            duration = durationMs,
            thumbnailUrl = thumbnail.takeIf { it.isNotBlank() },
        ).copy(
            trackNumber = trackNumber,
            albumId = resolvedAlbumId,
            releaseYear = releaseYear,
            releaseType = releaseType,
            isUpload = isVideoSong,
            isExplicit = explicit,
        )
    }

    /**
     * Extracts the primary artist ID (first non-null artist ID) from a [SongItem].
     */
    val SongItem.artistId: String?
        get() = artists.firstArtistId()

    /**
     * Converts a Metrolist [AlbumItem] into Koda's [PlaylistDisplayItem].
     * Preserves the `https://music.youtube.com/browse/<MPREb_...>` URL format so
     * `PlaylistDisplayItem.id` resolves to `browseId`.
     */
    fun AlbumItem.toCurioPlaylistDisplayItem(
        defaultArtist: String = "",
        releaseType: MusicReleaseType? = null,
    ): PlaylistDisplayItem {
        val resolvedReleaseType = releaseType ?: releaseTypeText?.trim()?.let { label ->
            MusicReleaseType.entries.firstOrNull { it.wireLabel.equals(label, ignoreCase = true) }
        }
        return PlaylistDisplayItem(
            name = title,
            url = "https://music.youtube.com/browse/$browseId",
            uploaderName = artists.joinArtistNames(fallback = defaultArtist),
            itemCount = -1,
            thumbnailUrl = thumbnail.takeIf { it.isNotBlank() }?.toThumbnailUrl(ThumbnailSize.MEDIUM),
            releaseType = resolvedReleaseType,
            releaseYear = year,
        )
    }

    /**
     * Converts a Metrolist [MetrolistArtistItem] into Koda's [CurioArtistItem].
     * `subscriberCount` is `String?` and is strictly `null` when absent (never `"-1"`).
     */
    fun MetrolistArtistItem.toCurioArtistItem(
        subscriberCount: String? = this.subscriberCountText,
        description: String? = null,
        isVerified: Boolean = false,
    ): CurioArtistItem =
        CurioArtistItem(
            id = id,
            name = title,
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() }?.toThumbnailUrl(ThumbnailSize.MEDIUM),
            subscriberCount = subscriberCount?.trim()?.takeIf { it.isNotEmpty() && it != "-1" },
            description = description?.takeIf { it.isNotBlank() },
            isVerified = isVerified,
        )

    /**
     * Converts a Metrolist [MetrolistArtistItem] into Koda's [SimilarArtist].
     * Replicates `MusicBrowse.parseSimilarArtistItem` (`id` starting with `"UC"`, non-blank `name`,
     * optional `audienceText` and `thumbnailUrl`).
     */
    fun MetrolistArtistItem.toCurioSimilarArtist(): SimilarArtist? {
        val cleanId = id.trim()
        if (!cleanId.startsWith("UC")) return null
        val cleanName = title.trim().takeIf { it.isNotEmpty() } ?: return null
        return SimilarArtist(
            id = cleanId,
            name = cleanName,
            audienceText = subscriberCountText?.trim()?.takeIf { it.isNotEmpty() && it != "-1" },
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() }?.toThumbnailUrl(ThumbnailSize.MEDIUM),
        )
    }

    /**
     * Converts a Metrolist [PlaylistItem] into Koda's [PlaylistDisplayItem].
     */
    fun PlaylistItem.toCurioPlaylistDisplayItem(): PlaylistDisplayItem {
        val cleanId = id.removePrefix("VL")
        return PlaylistDisplayItem(
            name = title,
            url = "https://music.youtube.com/playlist?list=$cleanId",
            uploaderName = author?.name?.trim().orEmpty(),
            itemCount = parseSongCount(songCountText),
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() }?.toThumbnailUrl(ThumbnailSize.MEDIUM),
            description = description?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Converts a Metrolist [Album] reference into Koda's [SongAlbumRef].
     */
    fun Album.toCurioSongAlbumRef(
        artistId: String? = null,
        year: Int? = null,
    ): SongAlbumRef? {
        val browseId = id.trim()
        if (!browseId.startsWith("MPRE")) return null
        return SongAlbumRef(
            albumId = browseId,
            albumTitle = name.trim().takeIf { it.isNotEmpty() },
            artistId = artistId?.takeIf { it.isNotBlank() },
            year = year,
        )
    }

    /**
     * Converts a Metrolist [SongItem] into Koda's [SongAlbumRef] (including first non-null `artistId`).
     */
    fun SongItem.toCurioSongAlbumRef(year: Int? = this.year): SongAlbumRef? =
        album?.toCurioSongAlbumRef(artistId = artists.firstArtistId(), year = year)

    /**
     * Converts a Metrolist [SearchSuggestions] into Koda's data-layer [MusicSearchSuggestions].
     */
    fun SearchSuggestions.toCurioMusicSearchSuggestions(): MusicSearchSuggestions {
        val songs = mutableListOf<Song>()
        val albums = mutableListOf<PlaylistDisplayItem>()
        val artists = mutableListOf<CurioArtistItem>()
        val playlists = mutableListOf<PlaylistDisplayItem>()

        for (item in recommendedItems) {
            when (item) {
                is SongItem -> if (!item.isEpisode) songs.add(item.toCurioSong())
                is AlbumItem -> albums.add(item.toCurioPlaylistDisplayItem())
                is MetrolistArtistItem -> artists.add(item.toCurioArtistItem())
                is PlaylistItem -> playlists.add(item.toCurioPlaylistDisplayItem())
                else -> Unit
            }
        }

        return MusicSearchSuggestions(
            queries = queries,
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
        )
    }

    internal fun parseSongCount(songCountText: String?): Int {
        if (songCountText.isNullOrBlank()) return -1
        val digits = Regex("(\\d[\\d,.]*)").find(songCountText)?.groupValues?.getOrNull(1)
            ?.replace(",", "")
            ?.replace(".", "")
            ?: return -1
        return digits.toIntOrNull() ?: -1
    }
}
