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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmClientTest {

    private class FakeSessionStorage(
        private var stored: StoredLastFmSession? = null,
    ) : LastFmSessionStorage {
        override fun load(): StoredLastFmSession? = stored
        override fun save(session: StoredLastFmSession) {
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

    private fun testSong(
        id: Long = 1L,
        title: String = "Track One",
        artist: String = "Artist One",
    ): Song = Song(
        id = id,
        albumId = 10L,
        title = title,
        artist = artist,
        album = "Album One",
        albumArtist = artist,
        releaseYear = null,
        genre = "",
        audioFormat = "YouTube",
        audioQuality = null,
        durationMs = 200_000L,
        trackNumber = 0,
        discNumber = 1,
        dateAddedSeconds = 0L,
        uri = TestUri("elovaire-yt://track/vid_$id"),
        artUri = null,
        isExplicit = false,
        fileName = "",
        mediaKind = AudioMediaKind.Music,
    )

    @Test
    fun `migrates legacy plaintext session file into encrypted storage and deletes legacy file`() = runTest {
        val tempDir = Files.createTempDirectory("lastfm_mig_test").toFile()
        try {
            val legacyFile = File(tempDir, "lastfm_session.json")
            legacyFile.writeText(
                JSONObject()
                    .put("username", "alice")
                    .put("sessionKey", "sk_secret_123")
                    .put("apiKey", "api_k")
                    .put("apiSecret", "api_s")
                    .put("scrobbleEnabled", true)
                    .toString(),
            )
            val storage = FakeSessionStorage()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = LastFmClient(
                context = testContext(tempDir),
                okHttpClient = OkHttpClient(),
                scope = this,
                ioDispatcher = dispatcher,
                nowMs = { 1_700_000_000_000L },
                sessionStorage = storage,
                pendingQueueFile = File(tempDir, "pending.json"),
                registerNetworkCallback = false,
            )

            assertFalse("Legacy plaintext session file must be deleted", legacyFile.exists())
            assertTrue(client.state.value.isLoggedIn)
            assertEquals("alice", client.state.value.username)
            assertEquals("sk_secret_123", storage.load()?.sessionKey)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `queues scrobble when offline and flushes when network recovers`() = runTest {
        val tempDir = Files.createTempDirectory("lastfm_queue_test").toFile()
        try {
            var failNetwork = true
            var sentCount = 0
            val okHttp = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    if (failNetwork) {
                        throw IOException("Offline")
                    }
                    sentCount += 1
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"scrobbles":{"@attr":{"accepted":1}}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()

            val storage = FakeSessionStorage(
                StoredLastFmSession(
                    username = "bob",
                    sessionKey = "sk_bob",
                    apiKey = "api_k",
                    apiSecret = "api_s",
                    scrobbleEnabled = true,
                ),
            )
            val pendingFile = File(tempDir, "pending.json")
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = LastFmClient(
                context = testContext(tempDir),
                okHttpClient = okHttp,
                scope = this,
                ioDispatcher = dispatcher,
                nowMs = { 1_700_000_000_000L },
                sessionStorage = storage,
                pendingQueueFile = pendingFile,
                registerNetworkCallback = false,
            )

            client.scrobble(testSong())
            advanceUntilIdle()

            assertEquals(0, sentCount)
            assertEquals(1, client.state.value.pendingScrobbleCount)
            assertTrue(pendingFile.isFile)

            failNetwork = false
            client.flushPendingScrobbles()
            advanceUntilIdle()

            assertEquals(1, sentCount)
            assertEquals(0, client.state.value.pendingScrobbleCount)

            client.signOut()
            assertNull(storage.load())
            assertFalse(client.state.value.isLoggedIn)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `signIn stores session key without persisting password`() = runTest {
        val tempDir = Files.createTempDirectory("lastfm_auth_test").toFile()
        try {
            val okHttp = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"session":{"name":"carol","key":"sk_carol"}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()

            val storage = FakeSessionStorage()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val client = LastFmClient(
                context = testContext(tempDir),
                okHttpClient = okHttp,
                scope = this,
                ioDispatcher = dispatcher,
                nowMs = { 1_700_000_000_000L },
                sessionStorage = storage,
                pendingQueueFile = File(tempDir, "pending.json"),
                registerNetworkCallback = false,
            )

            client.signIn("carol", "super_secret_password")
            advanceUntilIdle()

            assertTrue(client.state.value.isLoggedIn)
            assertEquals("carol", client.state.value.username)
            assertNotNull(storage.load())
            assertEquals("sk_carol", storage.load()?.sessionKey)
            assertFalse(File(tempDir, "lastfm_session.json").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
