package elovaire.music.droidbeauty.app.data.youtube

import android.net.Uri
import com.vimi.k7.innertube.model.Song as InnerTubeSong
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song

/** Maps InnerTube catalog tracks into Elovaire's domain [Song] model. */
internal fun InnerTubeSong.toElovaireSong(): Song {
    val trackUri = YouTubeUri.trackUri(id)
    val trackId = YouTubeUri.trackIdFor(id)
    val artwork = albumArtUri ?: thumbnailUrl?.takeIf(String::isNotBlank)?.let(Uri::parse)
    val normalizedAlbum = album.takeUnless { it.equals("Unknown Album", ignoreCase = true) }.orEmpty()
    return Song(
        id = trackId,
        title = title,
        isExplicit = false,
        artist = artist,
        album = normalizedAlbum,
        releaseYear = null,
        genre = "",
        audioFormat = "YouTube",
        audioQuality = null,
        fileName = "$id.m4a",
        albumId = albumId?.takeIf { it.isNotBlank() }?.let { YouTubeUri.trackIdFor("album:$it") } ?: 0L,
        durationMs = duration.coerceAtLeast(0L),
        trackNumber = trackNumber ?: 0,
        discNumber = discNumber ?: 0,
        dateAddedSeconds = (dateAdded ?: 0L) / 1000L,
        dateModifiedSeconds = null,
        libraryPath = null,
        uri = trackUri,
        artUri = artwork,
        metadataResolved = true,
        albumArtist = null,
        volumeNormalization = null,
        mediaKind = AudioMediaKind.Music,
        bookmarkMs = null,
        description = null,
    )
}
