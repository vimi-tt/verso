package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.compasso.CompassoEngine
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import kotlinx.coroutines.launch

internal val LocalCompassoEngine = staticCompositionLocalOf<CompassoEngine?> { null }

@Immutable
private data class CompassoUiCopy(
    val title: String,
    val activeLabel: String,
    val inactiveLabel: String,
    val familiarLabel: String,
    val balancedLabel: String,
    val exploreLabel: String,
    val blockTrackLabel: String,
    val blockTrackCd: String,
)

private fun compassoUiCopy(language: AppLanguage): CompassoUiCopy = when (language) {
    AppLanguage.Portuguese -> CompassoUiCopy(
        title = "Compasso",
        activeLabel = "Ativo",
        inactiveLabel = "Desativado",
        familiarLabel = "Familiar",
        balancedLabel = "70/30",
        exploreLabel = "Explorar",
        blockTrackLabel = "Nunca mais",
        blockTrackCd = "Bloquear e nunca mais tocar esta faixa",
    )
    AppLanguage.Spanish -> CompassoUiCopy(
        title = "Compasso",
        activeLabel = "Activo",
        inactiveLabel = "Inactivo",
        familiarLabel = "Familiar",
        balancedLabel = "70/30",
        exploreLabel = "Explorar",
        blockTrackLabel = "No repetir",
        blockTrackCd = "Bloquear y no volver a reproducir esta canción",
    )
    else -> CompassoUiCopy(
        title = "Compasso",
        activeLabel = "On",
        inactiveLabel = "Off",
        familiarLabel = "Familiar",
        balancedLabel = "70/30",
        exploreLabel = "Explore",
        blockTrackLabel = "Never play",
        blockTrackCd = "Block and never play this track again",
    )
}

@Composable
internal fun CompassoQueueControlBar(
    currentSong: Song?,
    queue: List<Song>,
    currentIndex: Int,
    tint: Color,
    secondaryTint: Color,
    onAppendTracks: (List<Song>) -> Unit,
    onBlockCurrentTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = LocalCompassoEngine.current ?: return
    val state by engine.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val copy = remember(language) { compassoUiCopy(language) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Compasso toggle pill
            Row(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(
                        if (state.isActive) tint.copy(alpha = 0.22f) else tint.copy(alpha = 0.08f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (state.isActive) tint.copy(alpha = 0.48f) else tint.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(ElovaireRadii.pill),
                    )
                    .semantics { role = Role.Button }
                    .elovaireClickable {
                        val nextActive = !state.isActive
                        engine.setActive(nextActive)
                        if (nextActive && currentSong != null) {
                            scope.launch {
                                engine.startFromSeed(
                                    seedSong = currentSong,
                                    currentQueue = queue,
                                    onAppendTracks = onAppendTracks,
                                )
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isLoadingMore) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.8.dp,
                        color = tint,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_audio_waveform),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = "${copy.title} · ${if (state.isActive) copy.activeLabel else copy.inactiveLabel}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Quick "Never play / Block" action for current song
            if (currentSong != null) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .background(tint.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = tint.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(ElovaireRadii.pill),
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = copy.blockTrackCd
                        }
                        .elovaireClickable {
                            scope.launch {
                                engine.blockTrack(currentSong) {
                                    onBlockCurrentTrack()
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_x),
                        contentDescription = null,
                        tint = secondaryTint,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = copy.blockTrackLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = secondaryTint,
                        maxLines = 1,
                    )
                }
            }
        }

        // Discovery level selector: Familiar (30/70) | Balanced (70/30) | Explore (90/10)
        if (state.isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompassoRatioPill(
                    label = copy.familiarLabel,
                    selected = state.discoveryRatio <= 0.45f,
                    tint = tint,
                    secondaryTint = secondaryTint,
                    onClick = { engine.setDiscoveryRatio(CompassoEngine.FAMILIAR_DISCOVERY_RATIO) },
                    modifier = Modifier.weight(1f),
                )
                CompassoRatioPill(
                    label = copy.balancedLabel,
                    selected = state.discoveryRatio in 0.46f..0.80f,
                    tint = tint,
                    secondaryTint = secondaryTint,
                    onClick = { engine.setDiscoveryRatio(CompassoEngine.DEFAULT_DISCOVERY_RATIO) },
                    modifier = Modifier.weight(1f),
                )
                CompassoRatioPill(
                    label = copy.exploreLabel,
                    selected = state.discoveryRatio > 0.80f,
                    tint = tint,
                    secondaryTint = secondaryTint,
                    onClick = { engine.setDiscoveryRatio(CompassoEngine.EXPLORE_DISCOVERY_RATIO) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompassoRatioPill(
    label: String,
    selected: Boolean,
    tint: Color,
    secondaryTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(if (selected) tint.copy(alpha = 0.24f) else tint.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) tint.copy(alpha = 0.50f) else tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(ElovaireRadii.pill),
            )
            .semantics { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) tint else secondaryTint,
            maxLines = 1,
        )
    }
}
