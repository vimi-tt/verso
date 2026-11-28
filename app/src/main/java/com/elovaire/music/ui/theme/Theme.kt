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
data class VersoCategoryColors(
    val mixes: Color,
    val discover: Color,
    val downloads: Color,
    val favorites: Color,
)

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
    val categories: VersoCategoryColors,
)

private val LightExtendedColors = VersoExtendedColors(
    destructive = DestructiveRed,
    toggleEnabled = BrandBlueLight,
    navBarSurface = LightSurfaceContainer.copy(alpha = 0.96f),
    navBarBorder = LightOutlineVariant,
    navPillContainer = BrandBlueContainerLight,
    navPillContent = BrandBlueOnContainerLight,
    navInactiveContent = InkTextSecondary,
    divider = LightOutlineVariant,
    mutedIcon = InkTextSecondary,
    miniPlayerBase = LightSurfaceContainerLow.copy(alpha = 0.92f),
    scrim = InkText.copy(alpha = 0.36f),
    playerChipContainer = LightSurfaceContainerHigh.copy(alpha = 0.88f),
    playerChipPressedOverlay = InkText.copy(alpha = 0.12f),
    categories = VersoCategoryColors(
        mixes = CategoryMixesLight,
        discover = CategoryDiscoverLight,
        downloads = CategoryDownloadsLight,
        favorites = CategoryFavoritesLight,
    ),
)

private val DarkExtendedColors = VersoExtendedColors(
    destructive = DestructiveRedDark,
    toggleEnabled = BrandBlueDark,
    navBarSurface = DarkSurfaceContainer.copy(alpha = 0.96f),
    navBarBorder = DarkOutlineVariant,
    navPillContainer = BrandBlueContainerDark,
    navPillContent = BrandBlueOnContainerDark,
    navInactiveContent = Slate,
    divider = DarkOutlineVariant,
    mutedIcon = Slate,
    miniPlayerBase = DarkSurfaceContainerLow.copy(alpha = 0.90f),
    scrim = Night.copy(alpha = 0.60f),
    playerChipContainer = DarkSurfaceContainerHigh.copy(alpha = 0.88f),
    playerChipPressedOverlay = BoneText.copy(alpha = 0.12f),
    categories = VersoCategoryColors(
        mixes = CategoryMixesDark,
        discover = CategoryDiscoverDark,
        downloads = CategoryDownloadsDark,
        favorites = CategoryFavoritesDark,
    ),
)

val LocalVersoColors = staticCompositionLocalOf { LightExtendedColors }

// Light mode palette aligned with Material Design 3 semantic roles and Verso's Brand Blue identity.
private val LightColors = lightColorScheme(
    primary = BrandBlueLight,
    onPrimary = Snow,
    primaryContainer = BrandBlueContainerLight,
    onPrimaryContainer = BrandBlueOnContainerLight,
    inversePrimary = LightInversePrimary,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = Cloud,
    onBackground = InkText,
    surface = Snow,
    onSurface = InkText,
    surfaceVariant = Frost,
    onSurfaceVariant = InkTextSecondary,
    surfaceTint = BrandBlueLight,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = DestructiveRed,
    onError = Snow,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

// Dark mode palette aligned with Material Design 3 semantic roles and Verso's Brand Blue identity.
private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = BrandBlueOnPrimaryDark,
    primaryContainer = BrandBlueContainerDark,
    onPrimaryContainer = BrandBlueOnContainerDark,
    inversePrimary = DarkInversePrimary,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = Night,
    onBackground = BoneText,
    surface = Carbon,
    onSurface = BoneText,
    surfaceVariant = Graphite,
    onSurfaceVariant = Slate,
    surfaceTint = BrandBlueDark,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DestructiveRedDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
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
