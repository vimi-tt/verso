package elovaire.music.data.youtube

import elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.data.youtube.canMutateYouTubePlaylist
import elovaire.music.droidbeauty.app.data.youtube.isEditableYouTubePlaylistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistMutationGuardTest {
    @Test
    fun isEditableYouTubePlaylistId_rejectsSystemAndBlankPlaylists() {
        assertFalse(isEditableYouTubePlaylistId(""))
        assertFalse(isEditableYouTubePlaylistId("   "))
        assertFalse(isEditableYouTubePlaylistId("VL"))
        assertFalse(isEditableYouTubePlaylistId("LM"))
        assertFalse(isEditableYouTubePlaylistId("lm"))
        assertFalse(isEditableYouTubePlaylistId("VLLM"))
        assertFalse(isEditableYouTubePlaylistId("vllm"))

        assertTrue(isEditableYouTubePlaylistId("PL1234567890"))
        assertTrue(isEditableYouTubePlaylistId("VLPL1234567890"))
    }

    @Test
    fun canMutateYouTubePlaylist_requiresAuthenticatedSessionAndEditableId() {
        assertFalse(canMutateYouTubePlaylist(isLoggedIn = false, playlistId = "PL1234567890"))
        assertFalse(canMutateYouTubePlaylist(isLoggedIn = true, playlistId = "LM"))
        assertFalse(canMutateYouTubePlaylist(isLoggedIn = true, playlistId = "VLLM"))
        assertFalse(canMutateYouTubePlaylist(isLoggedIn = true, playlistId = ""))

        assertTrue(canMutateYouTubePlaylist(isLoggedIn = true, playlistId = "PL1234567890"))
    }

    @Test
    fun youTubeUri_videoIdRejectsNonYouTubeUris() {
        assertNull(YouTubeUri.videoId(android.net.TestUri("content://media/external/audio/media/42")))
        assertNull(YouTubeUri.videoId(android.net.TestUri("file:///storage/emulated/0/Music/song.mp3")))
        assertFalse(YouTubeUri.isYouTubeUri(android.net.TestUri("content://media/external/audio/media/42")))
    }

    @Test
    fun editableYouTubePlaylistsFilter_excludesLikedMusicPlaylists() {
        val accountPlaylists = listOf(
            YouTubeCollectionSummary("LM", "Liked Music", "", 50, null, null),
            YouTubeCollectionSummary("VLLM", "Liked Music", "", 50, null, null),
            YouTubeCollectionSummary("PL_USER_1", "Road Trip", "", 12, null, null),
        )
        val filtered = accountPlaylists.filter { isEditableYouTubePlaylistId(it.id) }
        assertEquals(listOf("PL_USER_1"), filtered.map { it.id })
    }
}
