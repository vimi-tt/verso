package com.vimi.k7.innertube

import com.vimi.k7.innertube.model.Song
import com.vimi.k7.innertube.model.SongSource
import com.vimi.k7.innertube.model.sortedInAlbumOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTrackOrderTest {

    @Test
    fun `album order follows disc then track and leaves untagged songs last`() {
        val songs = listOf(
            song("untagged", "A title"),
            song("d2t1", "Finale", track = 1, disc = 2),
            song("d1t2b", "Beta", track = 2, disc = 1),
            song("d1t1", "Opening", track = 1, disc = 1),
            song("d1t2a", "Alpha", track = 2, disc = 1)
        )

        assertEquals(
            listOf("d1t1", "d1t2a", "d1t2b", "d2t1", "untagged"),
            songs.sortedInAlbumOrder().map { it.id }
        )
    }

    private fun song(
        id: String,
        title: String,
        track: Int? = null,
        disc: Int? = null
    ) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 0,
        source = SongSource.LOCAL,
        trackNumber = track,
        discNumber = disc
    )
}
