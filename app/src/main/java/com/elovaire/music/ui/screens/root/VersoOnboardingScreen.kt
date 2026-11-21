package elovaire.music.droidbeauty.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingCopy(
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val getStarted: String,
    val permissionsTitle: String,
    val permissionsSubtitle: String,
    val allowPermissions: String,
    val skipPermissions: String,
    val permissionGrantedLabel: String,
    val continueLabel: String,
    val accountTitle: String,
    val accountSubtitle: String,
    val signInGoogle: String,
    val anonymousMode: String,
    val featuresTitle: String,
    val featuresSubtitle: String,
    val feature1Title: String,
    val feature1Desc: String,
    val feature2Title: String,
    val feature2Desc: String,
    val feature3Title: String,
    val feature3Desc: String,
    val feature4Title: String,
    val feature4Desc: String,
    val finishButton: String,
)

private fun onboardingCopy(language: AppLanguage): OnboardingCopy = when (language) {
    AppLanguage.Portuguese -> OnboardingCopy(
        welcomeTitle = "Bem-vindo ao Verso",
        welcomeSubtitle = "Streaming, downloads offline, letras sincronizadas e suas estatísticas em um só lugar",
        getStarted = "Começar",
        permissionsTitle = "Permissões",
        permissionsSubtitle = "Permita notificações para controles de reprodução e downloads",
        allowPermissions = "Permitir notificações",
        skipPermissions = "Agora não",
        permissionGrantedLabel = "Permissão concedida",
        continueLabel = "Continuar",
        accountTitle = "Sua conta",
        accountSubtitle = "Conecte sua conta Google / YouTube Music para sincronizar favoritos e mixes, ou use no modo anônimo",
        signInGoogle = "Entrar com Google",
        anonymousMode = "Usar no modo anônimo",
        featuresTitle = "Tudo pronto",
        featuresSubtitle = "Veja o que você encontra no Verso",
        feature1Title = "Streaming e Mix de atividades",
        feature1Desc = "Explore músicas e recomendações baseadas no que você ouve",
        feature2Title = "Downloads offline rápidos",
        feature2Desc = "Baixe faixas, álbuns e playlists para ouvir sem internet",
        feature3Title = "Letras sílaba a sílaba",
        feature3Desc = "Acompanhe cada palavra com BetterLyrics e LRCLIB",
        feature4Title = "Estatísticas e Recap",
        feature4Desc = "Integração com Last.fm, ranking de reproduções e Verso Recap",
        finishButton = "Entrar no Verso",
    )
    AppLanguage.Spanish -> OnboardingCopy(
        welcomeTitle = "Bienvenido a Verso",
        welcomeSubtitle = "Streaming, descargas offline, letras sincronizadas y tus estadísticas en un solo lugar",
        getStarted = "Comenzar",
        permissionsTitle = "Permisos",
        permissionsSubtitle = "Permite las notificaciones para los controles de reproducción y las descargas",
        allowPermissions = "Permitir notificaciones",
        skipPermissions = "Ahora no",
        permissionGrantedLabel = "Permiso concedido",
        continueLabel = "Continuar",
        accountTitle = "Tu cuenta",
        accountSubtitle = "Conecta tu cuenta de Google / YouTube Music para sincronizar favoritos y mixes, o usa el modo anónimo",
        signInGoogle = "Iniciar sesión con Google",
        anonymousMode = "Usar modo anónimo",
        featuresTitle = "Todo listo",
        featuresSubtitle = "Esto es lo que puedes hacer en Verso",
        feature1Title = "Streaming y Mix de actividad",
        feature1Desc = "Explora música y recomendaciones según tu actividad",
        feature2Title = "Descargas offline rápidas",
        feature2Desc = "Descarga canciones, álbumes y playlists para escuchar sin conexión",
        feature3Title = "Letras sílaba a sílaba",
        feature3Desc = "Sigue cada palabra con BetterLyrics y LRCLIB",
        feature4Title = "Estadísticas y Recap",
        feature4Desc = "Integración con Last.fm, ranking de reproducciones y Verso Recap",
        finishButton = "Abrir Verso",
    )
    else -> OnboardingCopy(
        welcomeTitle = "Welcome to Verso",
        welcomeSubtitle = "Streaming, offline downloads, synced lyrics, and listening stats in one place",
        getStarted = "Get started",
        permissionsTitle = "Permissions",
        permissionsSubtitle = "Allow notifications for playback controls and downloads",
        allowPermissions = "Allow notifications",
        skipPermissions = "Not now",
        permissionGrantedLabel = "Permission granted",
        continueLabel = "Continue",
        accountTitle = "Your account",
        accountSubtitle = "Sign in with Google / YouTube Music to sync favorites and mixes, or continue in anonymous mode",
        signInGoogle = "Sign in with Google",
        anonymousMode = "Use anonymous mode",
        featuresTitle = "All set",
        featuresSubtitle = "Here is what is waiting for you in Verso",
        feature1Title = "Streaming & Activity Mix",
        feature1Desc = "Discover tracks and mixes tailored to your listening activity",
        feature2Title = "Fast offline downloads",
        feature2Desc = "Save tracks, albums, and playlists for offline listening",
        feature3Title = "Word-by-word lyrics",
        feature3Desc = "Sing along with BetterLyrics and LRCLIB fallback",
        feature4Title = "Stats & Verso Recap",
        feature4Desc = "Last.fm scrobbling, play rankings, and a shareable Recap",
        finishButton = "Enter Verso",
    )
}

@Composable
internal fun VersoOnboardingScreen(
    youTubeCatalog: YouTubeCatalogRepository,
    onComplete: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { onboardingCopy(language) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showGoogleLogin by rememberSaveable { mutableStateOf(false) }
    var isLaunchingHome by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        step = 2
    }

    val launchScale by animateFloatAsState(
        targetValue = if (isLaunchingHome) 1.08f else 1f,
        animationSpec = tween(durationMillis = 360),
        label = "OnboardingExitScale",
    )
    val launchAlpha by animateFloatAsState(
        targetValue = if (isLaunchingHome) 0f else 1f,
        animationSpec = tween(durationMillis = 360),
        label = "OnboardingExitAlpha",
    )

    LaunchedEffect(isLaunchingHome) {
        if (isLaunchingHome) {
            delay(360L)
            onComplete()
        }
    }

    if (showGoogleLogin) {
        YouTubeLoginScreen(
            onBack = { showGoogleLogin = false },
            onLoginCookiesExtracted = { cookies ->
                scope.launch {
                    youTubeCatalog.completeLogin(cookies)
                    showGoogleLogin = false
                    step = 3
                }
            },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14132D),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .alpha(launchAlpha)
            .scale(launchScale)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // Step indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .background(
                            if (index <= step) {
                                Color(0xFFFF6E4A)
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f)
                            },
                        ),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier.align(Alignment.Center),
            label = "OnboardingStepContent",
        ) { currentStep ->
            when (currentStep) {
                0 -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    VersoOption1Icon(size = 88.dp)
                    Text(
                        text = copy.welcomeTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = copy.welcomeSubtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OnboardingPrimaryPill(
                        label = copy.getStarted,
                        onClick = { step = 1 },
                    )
                }

                1 -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_music),
                        contentDescription = null,
                        tint = Color(0xFFFF6E4A),
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = copy.permissionsTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = copy.permissionsSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OnboardingPrimaryPill(
                        label = copy.allowPermissions,
                        onClick = {
                            val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsNotif) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                step = 2
                            }
                        },
                    )
                    OnboardingSecondaryPill(
                        label = copy.skipPermissions,
                        onClick = { step = 2 },
                    )
                }

                2 -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_user_round),
                        contentDescription = null,
                        tint = Color(0xFFFF6E4A),
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = copy.accountTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = copy.accountSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OnboardingPrimaryPill(
                        label = copy.signInGoogle,
                        onClick = { showGoogleLogin = true },
                    )
                    OnboardingSecondaryPill(
                        label = copy.anonymousMode,
                        onClick = { step = 3 },
                    )
                }

                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = copy.featuresTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = copy.featuresSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                    ModuleCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OnboardingFeatureRow(
                                iconResId = R.drawable.ic_lucide_audio_waveform,
                                title = copy.feature1Title,
                                description = copy.feature1Desc,
                            )
                            OnboardingFeatureRow(
                                iconResId = R.drawable.ic_lucide_download,
                                title = copy.feature2Title,
                                description = copy.feature2Desc,
                            )
                            OnboardingFeatureRow(
                                iconResId = R.drawable.ic_lucide_music,
                                title = copy.feature3Title,
                                description = copy.feature3Desc,
                            )
                            OnboardingFeatureRow(
                                iconResId = R.drawable.ic_lucide_bar_chart_2,
                                title = copy.feature4Title,
                                description = copy.feature4Desc,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OnboardingPrimaryPill(
                        label = copy.finishButton,
                        onClick = { isLaunchingHome = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPrimaryPill(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(MaterialTheme.colorScheme.onBackground)
            .elovaireClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.background,
        )
    }
}

@Composable
private fun OnboardingSecondaryPill(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .elovaireClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OnboardingFeatureRow(
    iconResId: Int,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = Color(0xFFFF6E4A),
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = readableSecondaryTextColor(),
            )
        }
    }
}
