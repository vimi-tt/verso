package com.vimi.k7.innertube.transport

import com.vimi.k7.innertube.auth.SessionManager
import com.vimi.k7.innertube.auth.YouTubeAuthUtils
import com.vimi.k7.innertube.auth.YouTubeRateLimit
import com.vimi.k7.innertube.model.toHighResThumbnailUrl
import com.vimi.k7.innertube.util.KLog
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * InnerTube on music.youtube.com as the WEB_REMIX client.
 *
 * Three families that differ in what they need from the session:
 * [postMusicMetadata] and [browseMusic] read public pages and attach the
 * account only when there is one, [fetchInternalApi] reads the account's own
 * library and answers nothing signed out, and [postMusicApi] is an account
 * write that must be signed. The SAPISIDHASH is per origin, so a session
 * signed for www.youtube.com is refused here.
 */
class MusicApi(
    private val http: YouTubeHttp,
    private val sessionManager: SessionManager,
) {
    fun musicContext(): JSONObject =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", http.contentLanguage())
                .put("gl", http.contentRegion())
        )

    /**
     * Executes a POST request to an InnerTube metadata endpoint and returns the raw JSON string.
     * The response stream is closed immediately in a [use] block. Callers decoding directly to
     * typed models avoid the extra overhead and memory pressure of an intermediate [JSONObject].
     */
    fun postMusicMetadataString(
        endpoint: String,
        payload: JSONObject,
        onHttpError: ((code: Int, retryAfterHeader: String?) -> Unit)? = null,
    ): String? {
        return try {
            val session = sessionManager.captureSession()
            val client = JSONObject().put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", http.contentLanguage())
                .put("gl", http.contentRegion())
            val visitorId = http.visitorIdentity.cachedOrNull()
            visitorId?.let { client.put("visitorData", it) }
            payload.put("context", JSONObject().put("client", client))
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/$endpoint")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            visitorId?.let { builder.addHeader("X-Goog-Visitor-Id", it) }
            builder.authenticate(session)
            http.okHttpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    if (!response.isSuccessful) {
                        onHttpError?.invoke(response.code, response.header("Retry-After"))
                    }
                    KLog.w(YOUTUBE_TAG, "Music $endpoint HTTP ${response.code}: ${body.take(200)}")
                    null
                } else {
                    http.noteSessionState(body, session)
                    if (body.contains("\"error\":")) null else body
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music $endpoint metadata request failed", e)
            null
        }
    }

    fun postMusicMetadata(
        endpoint: String,
        payload: JSONObject,
        onHttpError: ((code: Int, retryAfterHeader: String?) -> Unit)? = null,
    ): JSONObject? {
        val body = postMusicMetadataString(endpoint, payload, onHttpError) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    /**
     * POST to InnerTube /browse with the WEB_REMIX client. Works anonymously;
     * cookies are attached when logged in so results are personalized.
     * Unlike [fetchInternalApi], this does NOT require a login.
     */
    fun browseMusic(browseId: String, params: String? = null): String? =
        postMusicMetadata("browse", JSONObject().put("browseId", browseId).apply {
            if (params != null) put("params", params)
        })?.toString()

    fun searchSuggestionsMusic(
        input: String,
        onHttpError: ((code: Int, retryAfterHeader: String?) -> Unit)? = null,
    ): JSONObject? =
        postMusicMetadata(
            endpoint = "music/get_search_suggestions",
            payload = JSONObject().put("input", input),
            onHttpError = onHttpError,
        )

    fun searchSuggestionsMusicString(
        input: String,
        onHttpError: ((code: Int, retryAfterHeader: String?) -> Unit)? = null,
    ): String? =
        postMusicMetadataString(
            endpoint = "music/get_search_suggestions",
            payload = JSONObject().put("input", input),
            onHttpError = onHttpError,
        )

    fun fetchInternalApi(endpoint: String): String {
        val session = sessionManager.captureSession() ?: return ""
        val isBrowse = !endpoint.contains("/") // simple check: browseId vs endpoint path
        
        val url = if (isBrowse) {
            "https://music.youtube.com/youtubei/v1/browse"
        } else {
            "https://music.youtube.com/youtubei/v1/$endpoint"
        }
        
        // Construct complete JSON body for WEB_REMIX client
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", WEB_REMIX_VERSION)
            .put("hl", http.contentLanguage())
            .put("gl", http.contentRegion())
        val jsonBody = JSONObject()
            .put("context", JSONObject().put("client", client))
            .apply { if (isBrowse) put("browseId", endpoint) }
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            // Client name 67 is WEB_REMIX. Sent for the same reason the WEB
            // calls now send theirs: a client that never identifies itself is
            // the shape anti-abuse looks for. The visitor id rides as a header
            // rather than in the body because this endpoint's context is built
            // from raw JSON strings in five places; the header carries the same
            // identity without touching any of them.
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .apply {
                http.visitorIdentity.cachedOrNull()?.let { addHeader("X-Goog-Visitor-Id", it) }
            }
            .build()

        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(
                        response.code,
                        "music browse $endpoint",
                        response.header("Retry-After"),
                    )
                    KLog.w(YOUTUBE_TAG, "music browse $endpoint HTTP ${response.code}")
                    return ""
                }
                (response.body?.string() ?: "").also { http.noteSessionState(it, session) }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music browse request failed", e)
            ""
        }
    }

    /**
     * Fetch continuation page using continuation token.
     */
    fun fetchContinuation(continuationToken: String): String {
        // Account headers are conditional, the call is not. [verified August
        // 2026: a public playlist's continuation chain walks to the end with no
        // cookies, no auth header and no visitorData.] Returning "" without a
        // session made every signed-out continuation look like a failed fetch,
        // which capped public playlists at their first page just as the parser
        // gap did for signed-in ones - the same symptom from a second cause.
        val session = sessionManager.captureSession()

        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", WEB_REMIX_VERSION)
            .put("hl", http.contentLanguage())
            .put("gl", http.contentRegion())
        val jsonBody = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("continuation", continuationToken)
            .toString()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .authenticate(session)
            .build()

        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(response.code, request.url.toString(), response.header("Retry-After"))
                    KLog.w(YOUTUBE_TAG, "Music continuation failed: HTTP ${response.code}")
                    return ""
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank() || JSONObject(body).has("error")) return ""
                body.also { http.noteSessionState(it, session) }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music continuation request failed", e)
            ""
        }
    }

    /**
     * POST to an InnerTube endpoint on music.youtube.com with cookies and a
     * music-origin SAPISIDHASH (the hash is per-origin — a www.youtube.com
     * hash is rejected here). Returns the raw body or null on failure.
     */
    fun postMusicApi(endpoint: String, body: JSONObject): String? {
        val session = sessionManager.captureSession() ?: return null
        // Signing is not optional here: these are account writes, and an
        // unsigned one answers 200 having done nothing.
        if (YouTubeAuthUtils.getSapisid(session.cookies) == null) return null
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Origin", "https://music.youtube.com")
            .build()
        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    KLog.w(YOUTUBE_TAG, "music api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "music api $endpoint failed", e)
            null
        }
    }

    /**
     * Upload a custom playlist thumbnail image to YouTube Music using the 2-step
     * Scotty resumable upload protocol and return its `encryptedBlobId` for
     * `browse/edit_playlist` (`ACTION_SET_CUSTOM_THUMBNAIL`).
     */
    fun uploadMusicPlaylistThumbnail(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
    ): String? {
        if (imageBytes.isEmpty()) return null
        val session = sessionManager.captureSession() ?: return null
        if (YouTubeAuthUtils.getSapisid(session.cookies) == null) return null
        val uploadEndpoint = "https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail"

        // Step 1: Reserve resumable upload slot
        val startRequest = Request.Builder()
            .url(uploadEndpoint)
            .post(ByteArray(0).toRequestBody(null))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Origin", "https://music.youtube.com")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", imageBytes.size.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
            .build()
        val uploadUrl = try {
            http.okHttpClient.newCall(startRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    KLog.w(YOUTUBE_TAG, "playlist thumbnail start HTTP ${response.code}")
                    return null
                }
                response.header("X-Goog-Upload-URL")?.takeIf { it.isNotBlank() }
                    ?: response.header("x-guploader-uploadid")?.takeIf { it.isNotBlank() }?.let { id ->
                        "$uploadEndpoint?upload_id=$id&upload_protocol=resumable"
                    }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "playlist thumbnail start failed", e)
            return null
        } ?: return null

        // Step 2: Upload and finalize image bytes
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .post(imageBytes.toRequestBody(mimeType.toMediaType()))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Origin", "https://music.youtube.com")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .addHeader("X-Goog-Upload-Offset", "0")
            .build()
        return try {
            http.okHttpClient.newCall(uploadRequest).execute().use { response ->
                val raw = response.body?.string()?.trim()
                if (!response.isSuccessful || raw.isNullOrBlank()) {
                    KLog.w(YOUTUBE_TAG, "playlist thumbnail upload HTTP ${response.code}")
                    return null
                }
                if (raw.startsWith("{")) {
                    val obj = JSONObject(raw)
                    obj.optString("encryptedBlobId").takeIf { it.isNotBlank() }
                        ?: obj.optString("playlistScottyEncryptedBlobId").takeIf { it.isNotBlank() }
                } else {
                    raw
                }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "playlist thumbnail upload failed", e)
            null
        }
    }

    data class AccountProfileInfo(
        val name: String,
        val avatarUrl: String?,
        val handle: String?
    )

    /**
     * Fetch user identity (name, handle/email, avatar in high definition)
     * from InnerTube's account menu endpoint using the active authenticated session.
     */
    fun fetchAccountProfile(): AccountProfileInfo? {
        val json = postMusicMetadata("account/account_menu", JSONObject()) ?: return null
        return try {
            val actions = json.optJSONArray("actions") ?: return null
            val openPopup = actions.optJSONObject(0)?.optJSONObject("openPopupAction")
            val popup = openPopup?.optJSONObject("popup")
            val multiPage = popup?.optJSONObject("multiPageMenuRenderer")
            val header = multiPage?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")
                ?: return null

            val name = header.optJSONObject("accountName")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: header.optJSONObject("accountName")?.optString("simpleText")
                ?: ""

            val handle = header.optJSONObject("channelHandle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: header.optJSONObject("email")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

            val thumbs = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
            val rawAvatar = if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
            } else null
            val avatarUrl = rawAvatar?.takeIf { it.isNotBlank() }?.let { url ->
                val fullUrl = if (url.startsWith("//")) "https:$url" else url
                fullUrl.toHighResThumbnailUrl()
            }

            if (name.isNotBlank() || avatarUrl != null) {
                AccountProfileInfo(name = name, avatarUrl = avatarUrl, handle = handle)
            } else null
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Failed to parse account profile", e)
            null
        }
    }
}
