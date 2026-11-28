package elovaire.music.droidbeauty.app.ui.theme

import androidx.compose.ui.graphics.Color

// Core app background for dark mode screens (near-black neutral).
val Night = Color(0xFF0A0A0B)

// Shared elevated surface in dark mode (sheets, menus, controls).
val Carbon = Color(0xFF131315)

// Secondary dark surface for chips, inputs, and layered controls.
val Graphite = Color(0xFF1C1C1F)

// Main background for light mode screens (clean neutral off-white).
val Cloud = Color(0xFFF8F8F7)

// Elevated surface in light mode (sheets, menus, controls).
val Snow = Color(0xFFFFFFFF)

// Secondary light surface for chips, inputs, and layered controls.
val Frost = Color(0xFFEFEFED)

// Primary text/icon color in dark mode.
val BoneText = Color(0xFFF5F5F7)

// Muted text or icon color in dark mode secondary states (>= 4.5:1 on Night/Carbon/Graphite).
val Slate = Color(0xFFA8A8B0)

// Primary text color in light mode.
val InkText = Color(0xFF111113)

// Secondary text color in light mode (>= 4.5:1 on Cloud/Snow/Frost).
val InkTextSecondary = Color(0xFF55555B)

// Subtle outline/divider tones.
val DarkOutline = Color(0xFF34343A)
val DarkOutlineVariant = Color(0xFF222226)
val LightOutline = Color(0xFFD4D4D0)
val LightOutlineVariant = Color(0xFFE6E6E2)

// Selection pill container tones (functional neutral emphasis).
val DarkSelectionPill = Color(0xFF26262A)
val LightSelectionPill = Color(0xFFE4E4E0)

// Brand accent blue (reusing the app's established button blue, lightened slightly for dark mode >= 4.5:1).
val BrandBlueLight = Color(0xFF1162DB)
val BrandBlueDark = Color(0xFF4D91F5)

// Material 3 tonal container roles derived from Brand Blue and neutral slate harmony.
val BrandBlueContainerLight = Color(0xFFD8E6FF)
val BrandBlueOnContainerLight = Color(0xFF082F6E)
val BrandBlueContainerDark = Color(0xFF143366)
val BrandBlueOnContainerDark = Color(0xFFD8E6FF)
val BrandBlueOnPrimaryDark = Color(0xFF061B3A)

val SecondaryLight = Color(0xFF4B586E)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE1E8F4)
val OnSecondaryContainerLight = Color(0xFF182436)
val SecondaryDark = Color(0xFFB4C3DC)
val OnSecondaryDark = Color(0xFF1B283C)
val SecondaryContainerDark = Color(0xFF252D3B)
val OnSecondaryContainerDark = Color(0xFFDAE3F2)

val TertiaryLight = Color(0xFF6E44A8)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFEDE0FF)
val OnTertiaryContainerLight = Color(0xFF2A0E52)
val TertiaryDark = Color(0xFFB38FF0)
val OnTertiaryDark = Color(0xFF240D47)
val TertiaryContainerDark = Color(0xFF3B2362)
val OnTertiaryContainerDark = Color(0xFFEDE0FF)

// Material 3 tonal surface container ladder (neutral with subtle cool harmony, never purple).
val LightSurfaceDim = Color(0xFFE4E5E8)
val LightSurfaceBright = Snow
val LightSurfaceContainerLowest = Snow
val LightSurfaceContainerLow = Color(0xFFF3F4F6)
val LightSurfaceContainer = Color(0xFFECEEF1)
val LightSurfaceContainerHigh = Color(0xFFE4E7EC)
val LightSurfaceContainerHighest = Color(0xFFDCE0E6)

val DarkSurfaceDim = Night
val DarkSurfaceBright = Color(0xFF2B2C32)
val DarkSurfaceContainerLowest = Color(0xFF050506)
val DarkSurfaceContainerLow = Carbon
val DarkSurfaceContainer = Color(0xFF17181C)
val DarkSurfaceContainerHigh = Graphite
val DarkSurfaceContainerHighest = Color(0xFF25262B)

val LightInverseSurface = Color(0xFF1E1F23)
val LightInverseOnSurface = BoneText
val LightInversePrimary = Color(0xFF8BB6FF)
val DarkInverseSurface = Color(0xFFF2F2F4)
val DarkInverseOnSurface = Carbon
val DarkInversePrimary = BrandBlueLight

val ErrorContainerLight = Color(0xFFFDE2E2)
val OnErrorContainerLight = Color(0xFF5C0B0B)
val ErrorContainerDark = Color(0xFF5C1414)
val OnErrorContainerDark = Color(0xFFFDE2E2)
val OnErrorDark = Color(0xFF3B0606)

// Legacy symbol alias mapped to the brand accent blue.
val RoseAccent = BrandBlueLight

// Secondary accent alias for About screen actions.
val AboutCardButtonAccent = BrandBlueLight

// Closed 4-tone category palette (light / dark variants, all >= 4.5:1 against respective backgrounds).
val CategoryMixesLight = Color(0xFF6E44A8)
val CategoryMixesDark = Color(0xFFB38FF0)
val CategoryDiscoverLight = Color(0xFF0F766E)
val CategoryDiscoverDark = Color(0xFF4FD1C5)
val CategoryDownloadsLight = Color(0xFF9A5B13)
val CategoryDownloadsDark = Color(0xFFE8A849)
val CategoryFavoritesLight = Color(0xFFB4344B)
val CategoryFavoritesDark = Color(0xFFEE7B8F)

// Shared destructive action colors (>= 4.5:1 against respective theme surfaces).
val DestructiveRed = Color(0xFFC82828)
val DestructiveRedDark = Color(0xFFF05252)

// Dedicated toggle active color mapped to the brand accent.
val ToggleEnabledGreen = BrandBlueLight
val ToggleEnabledGreenDark = BrandBlueDark
