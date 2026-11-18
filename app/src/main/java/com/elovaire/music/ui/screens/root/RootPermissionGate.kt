package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.ui.performance.PerformanceState
import java.io.File

@Composable
internal fun RootPermissionGate(
    permissionState: RootPermissionState,
    youTubeCatalog: YouTubeCatalogRepository,
    onRequestAudioPermission: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val markerFile = remember(context) { File(context.applicationContext.filesDir, "verso_onboarding_v1") }
    var onboardingDone by remember(markerFile) { mutableStateOf(markerFile.isFile) }
    if (!onboardingDone) {
        PerformanceState("screen", "onboarding")
        VersoOnboardingScreen(
            hasAudioPermission = permissionState.hasAudioPermission,
            youTubeCatalog = youTubeCatalog,
            onRequestAudioPermission = onRequestAudioPermission,
            onComplete = {
                runCatching { markerFile.writeText("1") }
                onboardingDone = true
            },
        )
        return
    }
    content()
}
