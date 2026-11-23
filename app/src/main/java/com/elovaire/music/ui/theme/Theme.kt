package elovaire.music.droidbeauty.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import elovaire.music.droidbeauty.app.domain.model.TextSizePreset
import elovaire.music.droidbeauty.app.domain.model.ThemeMode

@Immutable
data class VersoExtendedColors(
    val destructive: Color,
    val toggleEnabled: Color,
    val navBarSurface: Color,
    val navBarBorder: Color,
    val navPillContainer: Color,
    val navPillContent: Color,
    val navInactiveContent: Color,
    val divider: Color,
    val mutedIcon: Color,
    val miniPlayerBase: Color,
    val scrim: Color,
    val playerChipContainer: Color,
    val playerChipPressedOverlay: Color,
)

private val LightExtendedColors = VersoExtendedColors(
    destructive = DestructiveRed,
    toggleEnabled = ToggleEnabledGreen,
    navBarSurface = Snow.copy(alpha = 0.96f),
    navBarBorder = LightOutlineVariant,
    navPillContainer = LightSelectionPill,
    navPillContent = InkText,
    navInactiveContent = InkTextSecondary,
    divider = LightOutlineVariant,
    mutedIcon = InkTextSecondary,
    miniPlayerBase = Snow.copy(alpha = 0.90f),
    scrim = InkText.copy(alpha = 0.36f),
    playerChipContainer = InkText.copy(alpha = 0.2f),
    playerChipPressedOverlay = InkText.copy(alpha = 0.12f),
)

private val DarkExtendedColors = VersoExtendedColors(
    destructive = DestructiveRedDark,
    toggleEnabled = ToggleEnabledGreenDark,
    navBarSurface = Carbon.copy(alpha = 0.95f),
    navBarBorder = DarkOutlineVariant,
    navPillContainer = DarkSelectionPill,
    navPillContent = BoneText,
    navInactiveContent = Slate,
    divider = DarkOutlineVariant,
    mutedIcon = Slate,
    miniPlayerBase = Carbon.copy(alpha = 0.88f),
    scrim = Night.copy(alpha = 0.60f),
    playerChipContainer = BoneText.copy(alpha = 0.2f),
    playerChipPressedOverlay = BoneText.copy(alpha = 0.12f),
)

val LocalVersoColors = staticCompositionLocalOf { LightExtendedColors }

// Light mode palette used across the whole app.
private val LightColors = lightColorScheme(
    primary = LightFunctionalAccent,
    onPrimary = Cloud,
    primaryContainer = LightSelectionPill,
    onPrimaryContainer = InkText,
    secondary = Frost,
    onSecondary = InkText,
    secondaryContainer = Frost,
    onSecondaryContainer = InkText,
    background = Cloud,
    onBackground = InkText,
    surface = Snow,
    onSurface = InkText,
    surfaceVariant = Frost,
    onSurfaceVariant = InkTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = DestructiveRed,
    onError = Snow,
)

// Dark mode palette used across the whole app.
private val DarkColors = darkColorScheme(
    primary = DarkFunctionalAccent,
    onPrimary = Night,
    primaryContainer = DarkSelectionPill,
    onPrimaryContainer = BoneText,
    secondary = Graphite,
    onSecondary = BoneText,
    secondaryContainer = Graphite,
    onSecondaryContainer = BoneText,
    background = Night,
    onBackground = BoneText,
    surface = Carbon,
    onSurface = BoneText,
    surfaceVariant = Graphite,
    onSurfaceVariant = Slate,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DestructiveRedDark,
    onError = Night,
)

@Composable
fun ElovaireTheme(
    themeMode: ThemeMode,
    textSizePreset: TextSizePreset,
    content: @Composable () -> Unit,
) {
    // Resolves whether the app should render in dark mode for the current theme setting.
    val darkTheme = resolveDarkTheme(themeMode = themeMode, systemDark = isSystemInDarkTheme())

    // Picks the full Material color scheme and extended semantic colors for the resolved mode.
    val colorScheme = elovaireResolvedColorScheme(darkTheme)
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalTextScale provides textSizePreset.scaleFactor,
        LocalVersoColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            // Global typography scaling for the text-size setting.
            typography = elovaireTypography(textSizePreset.scaleFactor),
            // Shared shape system for artwork, pills, lists, and buttons.
            shapes = elovaireShapes(),
            content = content,
        )
    }
}

fun resolveDarkTheme(
    themeMode: ThemeMode,
    systemDark: Boolean,
): Boolean {
    // Controls how the System / Light / Dark picker maps to real theme output.
    return when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}

fun themeBackgroundForMode(
    themeMode: ThemeMode,
    systemDark: Boolean,
): Color {
    // Convenience helper used when UI needs the resolved screen background color directly.
    return elovaireResolvedColorScheme(resolveDarkTheme(themeMode, systemDark)).background
}

fun elovaireResolvedColorScheme(darkTheme: Boolean): ColorScheme {
    // Single switch point between the light and dark palettes above.
    return if (darkTheme) DarkColors else LightColors
}
