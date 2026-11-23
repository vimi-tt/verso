package elovaire.music.droidbeauty.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.spotify.SpotifyPlaylistSummary
import elovaire.music.droidbeauty.app.data.spotify.SpotifyPlaylistsUiState
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.navigationAccessibilityCopy
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing
import elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors
import org.json.JSONObject

private const val SPOTIFY_LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2Fcollection%2Fplaylists"
private const val SPOTIFY_PLAYLISTS_URL = "https://open.spotify.com/collection/playlists"
private const val SPOTIFY_OPEN_HOST = "https://open.spotify.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyLoginScreen(
    onBack: () -> Unit,
    onSessionCaptured: (
        spDc: String,
        accessToken: String?,
        expiresAtMs: Long,
        clientToken: String?,
        playlists: List<SpotifyPlaylistSummary>,
    ) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var loadProgress by remember { mutableIntStateOf(0) }
    var loginDispatched by remember { mutableStateOf(false) }
    var isSyncingFromWeb by remember { mutableStateOf(false) }
    var redirectedToPlaylists by remember { mutableStateOf(false) }
    var capturedClientToken by remember { mutableStateOf<String?>(null) }
    var capturedAccessToken by remember { mutableStateOf<String?>(null) }

    val mobileUserAgent = remember(context) {
        android.webkit.WebSettings.getDefaultUserAgent(context)
            .replace("; wv)", ")")
            .replace(" wv)", ")")
    }
    val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            @Suppress("DEPRECATION")
            settings.saveFormData = false
            // Use clean mobile Chrome UA during accounts.spotify.com login so anti-bot never blocks sign-in.
            settings.userAgentString = mobileUserAgent
            val currentWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWebView, true)
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.clearFormData()
            webView.clearCache(true)
            webView.destroy()
        }
    }

    fun dispatchCapturedSession(
        spDc: String,
        playlists: List<SpotifyPlaylistSummary>,
    ) {
        if (loginDispatched) return
        if (spDc.isBlank()) return
        loginDispatched = true
        CookieManager.getInstance().flush()
        val exp = System.currentTimeMillis() + 3_300_000L
        onSessionCaptured(spDc, capturedAccessToken, exp, capturedClientToken, playlists)
    }

    fun readSpDcCookie(): String {
        val cookies = CookieManager.getInstance().getCookie(SPOTIFY_OPEN_HOST).orEmpty()
        return cookies
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter("sp_dc=")
            ?.trim()
            .orEmpty()
    }

    fun scrapePlaylistsFromDom(view: WebView, attempt: Int = 0, stableCount: Int = -1) {
        if (loginDispatched) return
        val spDc = readSpDcCookie()
        if (spDc.isBlank()) return
        val js = """
            (function() {
                var seen = {};
                var out = [];
                function addPlaylist(id, name, owner, imgUrl) {
                    if (!id || seen[id]) return;
                    name = (name || '').split('\n')[0].trim();
                    if (!name) return;
                    seen[id] = true;
                    out.push({ id: id, name: name, owner: (owner || '').trim(), coverUrl: (imgUrl || '').trim() });
                }
                // 1. Desktop library sidebar rows (id="listrow-title-spotify:playlist:<id>")
                var titleEls = document.querySelectorAll('[id*="spotify:playlist:"]');
                for (var i = 0; i < titleEls.length; i++) {
                    var el = titleEls[i];
                    var elId = el.getAttribute('id') || '';
                    if (elId.indexOf('subtitle') !== -1) continue;
                    var m = elId.match(/spotify:playlist:([A-Za-z0-9]+)/);
                    if (!m) continue;
                    var pid = m[1];
                    var title = (el.innerText || el.textContent || '').trim();
                    var row = el.closest('[role="listitem"], li, [role="row"], [data-testid]');
                    var owner = '';
                    var imgUrl = '';
                    if (row) {
                        var sub = row.querySelector('[id*="listrow-subtitle-spotify:playlist:' + pid + '"]');
                        if (sub) {
                            var subText = (sub.innerText || '').trim();
                            var parts = subText.split('•');
                            owner = parts.length > 1 ? parts[parts.length - 1].trim() : subText;
                        }
                        var img = row.querySelector('img[src]');
                        if (img) imgUrl = img.getAttribute('src') || '';
                    }
                    addPlaylist(pid, title, owner, imgUrl);
                }
                // 2. Any anchor links pointing to /playlist/<id>
                var links = document.querySelectorAll('a[href*="/playlist/"]');
                for (var j = 0; j < links.length; j++) {
                    var a = links[j];
                    var href = a.getAttribute('href') || '';
                    var lm = href.match(/\/playlist\/([A-Za-z0-9]+)/);
                    if (!lm) continue;
                    var lid = lm[1];
                    var lTitle = (a.getAttribute('title') || a.getAttribute('aria-label') || a.innerText || '').trim();
                    var card = a.closest('[data-testid], li, [role="listitem"], [role="row"], section, div');
                    var lImg = '';
                    if (card) {
                        if (!lTitle) {
                            var p = card.querySelector('p, span');
                            if (p) lTitle = (p.innerText || '').trim();
                        }
                        var cImg = card.querySelector('img[src]');
                        if (cImg) lImg = cImg.getAttribute('src') || '';
                    }
                    addPlaylist(lid, lTitle, 'Spotify', lImg);
                }
                return JSON.stringify(out);
            })();
        """.trimIndent()
        view.evaluateJavascript(js) { raw ->
            if (loginDispatched) return@evaluateJavascript
            val parsed = runCatching {
                val unquoted = org.json.JSONTokener(raw ?: "").nextValue()?.toString().orEmpty()
                if (!unquoted.startsWith("[")) return@runCatching emptyList<SpotifyPlaylistSummary>()
                val arr = org.json.JSONArray(unquoted)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val name = obj.optString("name").trim()
                        if (id.isBlank() || name.isBlank()) continue
                        add(
                            SpotifyPlaylistSummary(
                                id = id,
                                name = name,
                                owner = obj.optString("owner").trim().ifBlank { "Spotify" },
                                trackCount = 0,
                                coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())

            // Wait one extra tick once playlists appear so the full sidebar finishes rendering.
            if ((parsed.isNotEmpty() && parsed.size == stableCount) || attempt >= 16) {
                dispatchCapturedSession(spDc, parsed)
            } else {
                val nextStable = if (parsed.isNotEmpty()) parsed.size else -1
                view.postDelayed({ scrapePlaylistsFromDom(view, attempt + 1, nextStable) }, 700L)
            }
        }
    }

    fun tryHandleOpenSpotifyPage(view: WebView?, currentUrl: String?) {
        if (loginDispatched || view == null) return
        if (currentUrl?.startsWith(SPOTIFY_OPEN_HOST) != true) return
        val spDc = readSpDcCookie()
        if (spDc.isBlank()) return
        isSyncingFromWeb = true
        // Switch to Desktop Chrome UA only AFTER login succeeds so Spotify renders the full library sidebar.
        if (view.settings.userAgentString != desktopUserAgent) {
            view.settings.userAgentString = desktopUserAgent
            redirectedToPlaylists = true
            view.loadUrl(SPOTIFY_PLAYLISTS_URL)
            return
        }
        view.postDelayed({ scrapePlaylistsFromDom(view, 0, -1) }, 900L)
    }

    val title = when (language) {
        AppLanguage.Portuguese -> "Entrar no Spotify"
        AppLanguage.Spanish -> "Iniciar sesión en Spotify"
        else -> "Sign in to Spotify"
    }
    val subtitle = when (language) {
        AppLanguage.Portuguese -> "Acesse sua conta para carregar suas playlists"
        AppLanguage.Spanish -> "Accede a tu cuenta para cargar tus playlists"
        else -> "Sign in to load your Spotify playlists"
    }

    BackHandler(onBack = {
        if (webView.canGoBack()) webView.goBack() else onBack()
    })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElovaireSpacing.md, vertical = ElovaireSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                iconResId = R.drawable.ic_lucide_chevron_left,
                contentDescription = navigationAccessibilityCopy(language).back,
                showBackground = false,
                onClick = onBack,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_spotify_logo),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    webView.apply {
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                val protectedMedia = request?.resources
                                    ?.filter { it == android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                                    ?.toTypedArray()
                                if (protectedMedia != null && protectedMedia.isNotEmpty()) {
                                    request.grant(protectedMedia)
                                } else {
                                    request?.deny()
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase().orEmpty()
                                // Allow HTTPS and internal about: frames; block deep links (spotify://, intent://, file://).
                                return scheme != "https" && scheme != "about"
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): android.webkit.WebResourceResponse? {
                                if (!loginDispatched && request != null) {
                                    val host = request.url?.host?.lowercase().orEmpty()
                                    if (host.endsWith("spotify.com")) {
                                        val headers = request.requestHeaders
                                        val authHeader = headers?.entries
                                            ?.firstOrNull { it.key.equals("authorization", ignoreCase = true) }
                                            ?.value
                                            .orEmpty()
                                        val clientTokenHeader = headers?.entries
                                            ?.firstOrNull { it.key.equals("client-token", ignoreCase = true) }
                                            ?.value
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() }
                                        if (clientTokenHeader != null) {
                                            capturedClientToken = clientTokenHeader
                                        }
                                        if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                                            val token = authHeader.substring(7).trim()
                                            if (token.isNotBlank()) {
                                                capturedAccessToken = token
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                tryHandleOpenSpotifyPage(view, url)
                            }
                        }
                        loadUrl(SPOTIFY_LOGIN_URL)
                    }
                },
            )
            if (isSyncingFromWeb) {
                val syncingMsg = when (language) {
                    AppLanguage.Portuguese -> "Carregando suas playlists do Spotify..."
                    AppLanguage.Spanish -> "Cargando tus playlists de Spotify..."
                    else -> "Loading your Spotify playlists..."
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.height(ElovaireSpacing.lg))
                    Text(
                        text = syncingMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpotifyPlaylistsModal(
    state: SpotifyPlaylistsUiState,
    importingPlaylistId: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onSelectPlaylist: (SpotifyPlaylistSummary) -> Unit,
) {
    val language = LocalAppLanguage.current
    val refreshLabel = when (language) {
        AppLanguage.Portuguese -> "Atualizar"
        AppLanguage.Spanish -> "Actualizar"
        else -> "Refresh"
    }
    val signOutLabel = when (language) {
        AppLanguage.Portuguese -> "Sair"
        AppLanguage.Spanish -> "Cerrar sesión"
        else -> "Sign out"
    }
    val emptyLabel = when (language) {
        AppLanguage.Portuguese -> "Nenhuma playlist encontrada na sua conta Spotify"
        AppLanguage.Spanish -> "No se encontraron playlists en tu cuenta de Spotify"
        else -> "No playlists found in your Spotify account"
    }
    val importingLabel = when (language) {
        AppLanguage.Portuguese -> "Abrindo playlist..."
        AppLanguage.Spanish -> "Abriendo playlist..."
        else -> "Opening playlist..."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElovaireSpacing.md, vertical = ElovaireSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderIconButton(
                    iconResId = R.drawable.ic_lucide_chevron_left,
                    contentDescription = navigationAccessibilityCopy(language).back,
                    showBackground = false,
                    onClick = onDismiss,
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_spotify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Spotify",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HeaderIconButton(
                    iconResId = R.drawable.ic_lucide_refresh_cw,
                    contentDescription = refreshLabel,
                    showBackground = false,
                    enabled = !state.isLoading,
                    onClick = onRefresh,
                )
                Surface(
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onSignOut,
                        ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = ElovaireSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = signOutLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            when {
                state.isLoading && state.playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                state.error != null && state.playlists.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(ElovaireSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = readableSecondaryTextColor(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(ElovaireSpacing.lg))
                        Surface(
                            shape = RoundedCornerShape(ElovaireRadii.pill),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(ElovaireRadii.pill))
                                .clickable(role = Role.Button, onClick = onRefresh),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = ElovaireSpacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = refreshLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
                state.playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(ElovaireSpacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = readableSecondaryTextColor(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = ElovaireSpacing.screenHorizontalPadding,
                            vertical = ElovaireSpacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
                    ) {
                        items(
                            items = state.playlists,
                            key = { it.id },
                        ) { playlist ->
                            val isImporting = importingPlaylistId == playlist.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 68.dp)
                                    .clip(RoundedCornerShape(ElovaireRadii.tile))
                                    .clickable(
                                        enabled = importingPlaylistId == null,
                                        role = Role.Button,
                                        onClick = { onSelectPlaylist(playlist) },
                                    )
                                    .padding(vertical = ElovaireSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtworkImage(
                                    uri = playlist.coverUrl?.let(Uri::parse),
                                    title = playlist.name,
                                    placeholderIconResId = R.drawable.ic_lucide_list_music,
                                    modifier = Modifier.size(56.dp),
                                    cornerRadius = ElovaireRadii.artworkSmall,
                                    showArtworkGlow = false,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.xs),
                                ) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = if (isImporting) {
                                        importingLabel
                                    } else {
                                        buildString {
                                            if (playlist.trackCount > 0) {
                                                append(localizedCountLabel(playlist.trackCount, "song", language))
                                                if (playlist.owner.isNotBlank()) {
                                                    append(" • ")
                                                    append(playlist.owner)
                                                }
                                            } else if (playlist.owner.isNotBlank()) {
                                                append(playlist.owner)
                                            }
                                        }
                                    }
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isImporting) MaterialTheme.colorScheme.primary else readableSecondaryTextColor(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_lucide_chevron_left),
                                        contentDescription = null,
                                        tint = LocalVersoColors.current.mutedIcon,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(180f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
