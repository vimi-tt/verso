package elovaire.music.droidbeauty.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
object ElovaireSpacing {
    // 4dp spacing scale tokens.
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    // Main top bar height on home/library/playlists/search.
    val topBarContentHeight: Dp = 56.dp

    // Top bar height on detail screens like album, playlist, artist, and changelog.
    val detailTopBarContentHeight: Dp = 56.dp

    // Default vertical gap between a top bar and the first content block on main screens.
    val topBarToFirstContentGap: Dp = 16.dp

    // Space above large hero artwork/headers on drill-in screens.
    val albumHeaderTopGap: Dp = 24.dp

    // Total reserved height for the compact now playing bar area.
    val miniPlayerReservedHeight: Dp = 84.dp

    // Visible height of the bottom navigation dock itself.
    val bottomNavigationBodyHeight: Dp = 76.dp

    // Extra bottom lift for the dock when it is not fully edge-to-edge.
    val bottomNavigationOuterPadding: Dp = 8.dp

    // Shared extra scroll room below the last list item.
    val scrollTailPadding: Dp = 24.dp

    // Default top gap before the first list/grid block on detail screens.
    val detailListTopGap: Dp = 16.dp

    // Tighter top gap for compact control rows and chips under detail headers.
    val detailCompactTopGap: Dp = 12.dp

    // Larger top gap before full grouped sections/modules on detail screens.
    val detailSectionTopGap: Dp = 24.dp

    // Standard left/right screen padding used across most screens.
    val screenHorizontalPadding: Dp = 16.dp

    // Standard gap between major stacked sections/modules.
    val sectionVerticalGap: Dp = 24.dp

    // Shared gap between a media title and its secondary text.
    val mediaTextStackGap: Dp = 4.dp

    // Top gap between now playing artwork and the title/artist row (preserved for player).
    val nowPlayingTitleTopGap: Dp = 5.dp

    // Bottom gap between the title/artist row and the progress bar in now playing (preserved for player).
    val nowPlayingTitleBottomGap: Dp = 0.dp
}
