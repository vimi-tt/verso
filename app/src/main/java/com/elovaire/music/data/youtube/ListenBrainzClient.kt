package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import elovaire.music.droidbeauty.app.domain.model.Song
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class ListenBrainzAccountState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val scrobbleEnabled: Boolean = true,
    val isSigningIn: Boolean = false,
    val pendingListenCount: Int = 0,
    val errorMessage: String? = null,
)

internal data class StoredListenBrainzSession(
    val username: String,
    val userToken: String,
    val scrobbleEnabled: Boolean,
)

internal interface ListenBrainzSessionStorage {
    fun load(): StoredListenBrainzSession?
    fun save(session: StoredListenBrainzSession)
    fun clear()
}

internal class EncryptedListenBrainzSessionStorage(
    context: Context,
) : ListenBrainzSessionStorage {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { error ->
            Log.e(TAG, "EncryptedSharedPreferences unavailable for ListenBrainz", error)
        }.getOrNull()
    }

    override fun load(): StoredListenBrainzSession? {
        val store = prefs ?: return null
        val username = store.getString(KEY_USERNAME, null).orEmpty()
        val token = store.getString(KEY_TOKEN, null).orEmpty()
        if (username.isBlank() || token.isBlank()) return null
        return StoredListenBrainzSession(
            username = username,
            userToken = token,
            scrobbleEnabled = store.getBoolean(KEY_SCROBBLE_ENABLED, true),
        )
    }

    override fun save(session: StoredListenBrainzSession) {
        val store = prefs ?: return
        store.edit()
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_TOKEN, session.userToken)
            .putBoolean(KEY_SCROBBLE_ENABLED, session.scrobbleEnabled)
            .apply()
    }

    override fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        const val TAG = "ListenBrainzStorage"
        const val PREFS_NAME = "listenbrainz_session_encrypted"
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "user_token"
        const val KEY_SCROBBLE_ENABLED = "scrobble_enabled"
    }
}

/**
 * Handles ListenBrainz token authentication, playing_now updates,
 * single-listen scrobbling, and an offline persistent retry queue.
 */
internal class ListenBrainzClient(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long,
    private val sessionStorage: ListenBrainzSessionStorage = EncryptedListenBrainzSessionStorage(context),
    pendingQueueFile: File = File(context.applicationContext.filesDir, "listenbrainz_pending_listens.json"),
    registerNetworkCallback: Boolean = true,
) {
    private val lock = Any()
    private val pendingQueue = PendingScrobbleQueue(pendingQueueFile)
    private var currentToken: String = ""

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<ListenBrainzAccountState> = _state.asStateFlow()

    init {
        if (registerNetworkCallback) {
            registerConnectivityFlush(context.applicationContext)
        }
        val initial = _state.value
        if (initial.isLoggedIn && initial.scrobbleEnabled && initial.pendingListenCount > 0) {
            scope.launch(ioDispatcher) { flushPendingListens() }
        }
    }

    fun signIn(userToken: String) {
        val cleanToken = userToken.trim()
        if (cleanToken.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter your ListenBrainz user token") }
            return
        }
        _state.update { it.copy(isSigningIn = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(VALIDATE_TOKEN_URL)
                    .header("Authorization", "Token $cleanToken")
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("ListenBrainz token validation failed (HTTP ${resp.code})")
                    }
                    val json = JSONObject(body)
                    if (!json.optBoolean("valid", false)) {
                        error("Invalid ListenBrainz user token")
                    }
                    json.optString("user_name").trim().ifBlank {
                        error("Missing ListenBrainz username")
                    }
                }
            }
            result.onSuccess { username ->
                val scrobbleEnabled = _state.value.scrobbleEnabled
                synchronized(lock) {
                    currentToken = cleanToken
                    sessionStorage.save(
                        StoredListenBrainzSession(
                            username = username,
                            userToken = cleanToken,
                            scrobbleEnabled = scrobbleEnabled,
                        ),
                    )
                }
                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        username = username,
                        scrobbleEnabled = scrobbleEnabled,
                        isSigningIn = false,
                        errorMessage = null,
                    )
                }
                flushPendingListens()
            }.onFailure { error ->
                runCatching { Log.w(TAG, "ListenBrainz signIn failed: ${error.message}") }
                _state.update {
                    it.copy(
                        isSigningIn = false,
                        errorMessage = error.message ?: "ListenBrainz login failed",
                    )
                }
            }
        }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        _state.update { current ->
            val updated = current.copy(scrobbleEnabled = enabled)
            synchronized(lock) {
                if (updated.isLoggedIn && currentToken.isNotBlank()) {
                    sessionStorage.save(
                        StoredListenBrainzSession(
                            username = updated.username,
                            userToken = currentToken,
                            scrobbleEnabled = enabled,
                        ),
                    )
                }
            }
            updated
        }
        if (enabled && _state.value.isLoggedIn) {
            scope.launch(ioDispatcher) { flushPendingListens() }
        }
    }

    fun signOut() {
        synchronized(lock) {
            currentToken = ""
            sessionStorage.clear()
            pendingQueue.clear()
        }
        _state.value = ListenBrainzAccountState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    suspend fun updateNowPlaying(song: Song) {
        val current = _state.value
        val token = synchronized(lock) { currentToken }
        if (!current.isLoggedIn || !current.scrobbleEnabled || token.isBlank()) return
        val artist = primaryArtist(song.artist)
        if (artist.isBlank() || song.title.isBlank()) return

        withContext(ioDispatcher) {
            val payload = buildListenPayload(
                listenType = "playing_now",
                artist = artist,
                track = song.title,
                album = song.album,
                durationMs = song.durationMs,
                listenedAtSeconds = null,
            )
            submitPayload(token, payload)
        }
    }

    suspend fun scrobble(song: Song) {
        val current = _state.value
        val token = synchronized(lock) { currentToken }
        if (!current.isLoggedIn || !current.scrobbleEnabled || token.isBlank()) return
        val artist = primaryArtist(song.artist)
        if (artist.isBlank() || song.title.isBlank()) return

        withContext(ioDispatcher) {
            val timestampSeconds = (nowMs() / 1000L).coerceAtLeast(1L)
            val entry = PendingScrobble(
                artist = artist,
                track = song.title,
                album = song.album,
                durationMs = song.durationMs,
                timestampSeconds = timestampSeconds,
            )
            val payload = buildListenPayload(
                listenType = "single",
                artist = entry.artist,
                track = entry.track,
                album = entry.album,
                durationMs = entry.durationMs,
                listenedAtSeconds = entry.timestampSeconds,
            )
            when (submitPayload(token, payload)) {
                ScrobblePostResult.Success -> flushPendingListensLocked()
                ScrobblePostResult.RetryableFailure -> {
                    val count = synchronized(lock) { pendingQueue.enqueue(entry) }
                    _state.update { it.copy(pendingListenCount = count) }
                }
                ScrobblePostResult.PermanentFailure -> Unit
            }
        }
    }

    suspend fun flushPendingListens() {
        val current = _state.value
        if (!current.isLoggedIn || !current.scrobbleEnabled) return
        withContext(ioDispatcher) {
            flushPendingListensLocked()
        }
    }

    private fun flushPendingListensLocked() {
        val token = synchronized(lock) { currentToken }
        if (token.isBlank()) return
        val snapshot = synchronized(lock) { pendingQueue.snapshot() }
        if (snapshot.isEmpty()) return

        var stopOnNetwork = false
        for (entry in snapshot) {
            if (stopOnNetwork) break
            val payload = buildListenPayload(
                listenType = "single",
                artist = entry.artist,
                track = entry.track,
                album = entry.album,
                durationMs = entry.durationMs,
                listenedAtSeconds = entry.timestampSeconds,
            )
            when (submitPayload(token, payload)) {
                ScrobblePostResult.Success,
                ScrobblePostResult.PermanentFailure -> {
                    val remaining = synchronized(lock) { pendingQueue.remove(entry) }
                    _state.update { it.copy(pendingListenCount = remaining) }
                }
                ScrobblePostResult.RetryableFailure -> {
                    stopOnNetwork = true
                }
            }
        }
    }

    private fun submitPayload(token: String, payload: JSONObject): ScrobblePostResult {
        return try {
            val request = Request.Builder()
                .url(SUBMIT_LISTENS_URL)
                .header("Authorization", "Token $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> ScrobblePostResult.Success
                    resp.code == 429 || resp.code >= 500 -> ScrobblePostResult.RetryableFailure
                    else -> {
                        runCatching { Log.w(TAG, "ListenBrainz submit HTTP ${resp.code}") }
                        ScrobblePostResult.PermanentFailure
                    }
                }
            }
        } catch (io: IOException) {
            runCatching { Log.w(TAG, "ListenBrainz network error (queued): ${io.message}") }
            ScrobblePostResult.RetryableFailure
        } catch (t: RuntimeException) {
            runCatching { Log.w(TAG, "ListenBrainz unexpected error: ${t.message}") }
            ScrobblePostResult.PermanentFailure
        }
    }

    private fun buildListenPayload(
        listenType: String,
        artist: String,
        track: String,
        album: String,
        durationMs: Long,
        listenedAtSeconds: Long?,
    ): JSONObject {
        val metadata = JSONObject()
            .put("artist_name", artist)
            .put("track_name", track)
        if (album.isNotBlank()) {
            metadata.put("release_name", album)
        }
        if (durationMs > 0L) {
            metadata.put("additional_info", JSONObject().put("duration_ms", durationMs))
        }
        val listen = JSONObject().put("track_metadata", metadata)
        if (listenedAtSeconds != null) {
            listen.put("listened_at", listenedAtSeconds)
        }
        return JSONObject()
            .put("listen_type", listenType)
            .put("payload", JSONArray().put(listen))
    }

    private fun loadInitialState(): ListenBrainzAccountState {
        synchronized(lock) {
            val stored = sessionStorage.load()
            val pendingCount = pendingQueue.size()
            if (stored == null) {
                return ListenBrainzAccountState(pendingListenCount = pendingCount)
            }
            currentToken = stored.userToken
            return ListenBrainzAccountState(
                isLoggedIn = true,
                username = stored.username,
                scrobbleEnabled = stored.scrobbleEnabled,
                pendingListenCount = pendingCount,
            )
        }
    }

    private fun registerConnectivityFlush(appContext: Context) {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val current = _state.value
                        if (current.isLoggedIn && current.scrobbleEnabled && current.pendingListenCount > 0) {
                            scope.launch(ioDispatcher) { flushPendingListens() }
                        }
                    }
                },
            )
        }
    }

    private fun primaryArtist(artist: String): String =
        artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim().orEmpty()

    private companion object {
        const val TAG = "ListenBrainzClient"
        const val VALIDATE_TOKEN_URL = "https://api.listenbrainz.org/1/validate-token"
        const val SUBMIT_LISTENS_URL = "https://api.listenbrainz.org/1/submit-listens"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
