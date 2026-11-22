package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.content.ContextWrapper
import android.net.TestUri
import elovaire.music.droidbeauty.app.domain.model.AudioMediaKind
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzClientTest {

    private class FakeSessionStorage(
        private var stored: StoredListenBrainzSession? = null,
    ) : ListenBrainzSessionStorage {
        override fun load(): StoredListenBrainzSession? = stored
        override fun save(session: StoredListenBrainzSession) {
            stored = session
        }
        override fun clear() {
            stored = null
        }
    }

    private fun testContext(filesDir: File): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = filesDir
    }

    private fun testSong(): Song = Song(
        id = 7L,
        albumId = 20L,
        title = "Open Song",
        artist = "Open Artist",
        album = "Open Album",
        albumArtist = "Open Artist",
        releaseYear = null,
        genre = "",
        audioFormat = "YouTube",
        audioQuality = null,
        durationMs = 180_000L,
        trackNumber = 0,
        discNumber = 1,
        dateAddedSeconds = 0L,
        uri = TestUri("elovaire-yt://track/vid_7"),
        artUri = null,
        isExplicit = false,
        fileName = "",
        mediaKind = AudioMediaKind.Music,
    )

    @Test
    fun `validates token on signIn and queues listens when offline`() = runTest {
        val tempDir = Files.createTempDirectory("lb_test").toFile()
        try {
            var failSubmit = true
            var submitCount = 0
            val okHttp = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    if (url.endsWith("validate-token")) {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"valid":true,"user_name":"lb_user"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    } else {
                        if (failSubmit) throw IOException("Offline")
                        submitCount += 1
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"status":"ok"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
                .build()

            val storage = FakeSessionStorage()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = ListenBrainzClient(
                context = testContext(tempDir),
                okHttpClient = okHttp,
                scope = this,
                ioDispatcher = dispatcher,
                nowMs = { 1_700_000_000_000L },
                sessionStorage = storage,
                pendingQueueFile = File(tempDir, "lb_pending.json"),
                registerNetworkCallback = false,
            )

            client.signIn("token-xyz")
            advanceUntilIdle()

            assertTrue(client.state.value.isLoggedIn)
            assertEquals("lb_user", client.state.value.username)
            assertEquals("token-xyz", storage.load()?.userToken)

            client.scrobble(testSong())
            advanceUntilIdle()
            assertEquals(0, submitCount)
            assertEquals(1, client.state.value.pendingListenCount)

            failSubmit = false
            client.flushPendingListens()
            advanceUntilIdle()
            assertEquals(1, submitCount)
            assertEquals(0, client.state.value.pendingListenCount)

            client.signOut()
            assertNull(storage.load())
            assertFalse(client.state.value.isLoggedIn)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
