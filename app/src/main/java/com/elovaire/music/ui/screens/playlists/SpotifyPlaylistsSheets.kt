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
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
private const val SPOTIFY_OPEN_HOST = "https://open.spotify.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyLoginScreen(
    onBack: () -> Unit,
    onSessionCaptured: (spDc: String, accessToken: String?, expiresAtMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var loadProgress by remember { mutableIntStateOf(0) }
    var loginDispatched by remember { mutableStateOf(false) }

    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.saveFormData = false
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

    fun tryExtractSpotifySession(view: WebView?, currentUrl: String?) {
        if (loginDispatched) return
        if (currentUrl?.startsWith(SPOTIFY_OPEN_HOST) != true) return
        val cookies = CookieManager.getInstance().getCookie(SPOTIFY_OPEN_HOST).orEmpty()
        val spDc = cookies
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter("sp_dc=")
            ?.trim()
            .orEmpty()
        if (spDc.isBlank()) return
        loginDispatched = true
        CookieManager.getInstance().flush()
        if (view != null) {
            view.evaluateJavascript(
                "(function(){var el=document.getElementById('session');return el?el.textContent:'';})();",
            ) { raw ->
                var token: String? = null
                var exp = 0L
                runCatching {
                    val unquoted = org.json.JSONTokener(raw ?: "").nextValue()?.toString().orEmpty()
                    if (unquoted.startsWith("{")) {
                        val obj = JSONObject(unquoted)
                        token = obj.optString("accessToken").takeIf { it.isNotBlank() }
                        exp = obj.optLong("accessTokenExpirationTimestampMs", 0L)
                    }
                }
                onSessionCaptured(spDc, token, exp)
            }
        } else {
            onSessionCaptured(spDc, null, 0L)
        }
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
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                // Block non-HTTPS schemes (file://, intent://, javascript:, etc.)
                                return scheme != "https"
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                tryExtractSpotifySession(view, url)
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                tryExtractSpotifySession(view, url)
                            }
                        }
                        loadUrl(SPOTIFY_LOGIN_URL)
                    }
                },
            )
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
                                            append(localizedCountLabel(playlist.trackCount, "song", language))
                                            if (playlist.owner.isNotBlank()) {
                                                append(" • ")
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
