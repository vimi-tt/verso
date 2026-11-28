package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.interaction.consumePointersWithoutSemantics
import elovaire.music.droidbeauty.app.ui.interaction.elovairePressScale
import elovaire.music.droidbeauty.app.ui.interaction.rememberElovaireInteractionSource
import elovaire.music.droidbeauty.app.ui.motion.MotionDuration
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionSpecs
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun BottomNavigationBar(
    currentRoute: String,
    suppressEnterAnimation: Boolean,
    destinations: List<TopLevelDestination>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationInset = navigationBarInsetDp()
    val language = LocalAppLanguage.current
    val copy = remember(language) { commonUiCopy(language) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ElovaireSpacing.lg,
                end = ElovaireSpacing.lg,
                bottom = navigationInset + ElovaireSpacing.sm,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .consumePointersWithoutSemantics(),
            shape = RoundedCornerShape(ElovaireRadii.sheet),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElovaireSpacing.sm, vertical = ElovaireSpacing.xs)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val label = when (destination.route) {
                        HOME_ROUTE -> copy.home
                        ALBUMS_ROUTE -> copy.library
                        PLAYLISTS_ROUTE -> copy.playlists
                        SEARCH_ROUTE -> copy.search
                        else -> destination.contentDescription
                    }
                    BottomNavigationItemButton(
                        iconResId = destination.iconResId,
                        label = label,
                        contentDescription = label,
                        suppressEnterAnimation = suppressEnterAnimation,
                        selected = currentRoute == destination.route,
                        testTag = "bottom_nav_${destination.route}",
                        onClickAction = { onNavigate(destination.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationItemButton(
    iconResId: Int,
    label: String,
    contentDescription: String,
    suppressEnterAnimation: Boolean,
    selected: Boolean,
    testTag: String,
    onClickAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionSpecs = rememberMotionSpecs()
    val interactionSource = rememberElovaireInteractionSource()
    val transitionDuration = if (suppressEnterAnimation) 0 else MotionDuration.Fast
    val selectionTransition = updateTransition(
        targetState = selected,
        label = "BottomNavItemSelection",
    )
    val iconTint by selectionTransition.animateColor(
        transitionSpec = { motionSpecs.tween(durationMillis = transitionDuration) },
        label = "BottomNavItemIconTint",
    ) { isSelected ->
        if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
    }
    val labelTint by selectionTransition.animateColor(
        transitionSpec = { motionSpecs.tween(durationMillis = transitionDuration) },
        label = "BottomNavItemLabelTint",
    ) { isSelected ->
        if (isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant
    }
    val indicatorWidth by selectionTransition.animateDp(
        transitionSpec = {
            if (suppressEnterAnimation) {
                motionSpecs.tween(durationMillis = 0)
            } else {
                motionSpecs.spring(dampingRatio = 0.8f, stiffness = 520f)
            }
        },
        label = "BottomNavIndicatorWidth",
    ) { isSelected ->
        if (isSelected) 64.dp else 0.dp
    }
    val indicatorAlpha by selectionTransition.animateFloat(
        transitionSpec = { motionSpecs.tween(durationMillis = transitionDuration) },
        label = "BottomNavIndicatorAlpha",
    ) { isSelected ->
        if (isSelected) 1f else 0f
    }

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .elovairePressScale(
                pressedScale = 0.96f,
                animationSpec = motionSpecs.chromeRelease(),
                interactionSource = interactionSource,
                label = "${contentDescription}_bottom_nav_scale",
            )
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colorScheme.primary),
                role = Role.Tab,
                onClick = onClickAction,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.testTag = testTag
                testTagsAsResourceId = true
            }
            .padding(vertical = ElovaireSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(32.dp)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(colorScheme.primaryContainer),
            )
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = labelTint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
