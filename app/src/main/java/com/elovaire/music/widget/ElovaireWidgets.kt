package elovaire.music.droidbeauty.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import elovaire.music.droidbeauty.app.ElovaireApp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.artwork.ArtworkPurpose
import elovaire.music.droidbeauty.app.data.artwork.loadArtworkBitmapAwaitingAdmission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

internal enum class WidgetDesign { Compact, Cover, Studio, Controls }

private val SurfaceBase = Color(0xFF0E0E13)
private val SurfaceCard = Color(0xFF191922)
private val SurfacePill = Color(0xFF23232F)
private val AccentBlue = Color(0xFF2F80ED)
private val SecondaryBlueButton = Color(0xFF1E2D47)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB5B5C4)
private val TextSubtle = Color(0xFF848496)

internal object WidgetRenderUpdates {
    val revision = MutableStateFlow(0L)
    fun notifyChanged() { revision.update { it + 1L } }
}

private data class WidgetRenderData(val snapshot: WidgetPlaybackSnapshot?, val artwork: Bitmap?)

private suspend fun loadWidgetArtwork(context: Context, snapshot: WidgetPlaybackSnapshot?): Bitmap? =
    withTimeoutOrNull(5_000L) {
        listOfNotNull(snapshot?.artworkUri, snapshot?.mediaUri).distinct()
            .firstNotNullOfOrNull { source ->
                loadArtworkBitmapAwaitingAdmission(context, Uri.parse(source), 384, ArtworkPurpose.UiGrid)
            }
    }

abstract class ElovaireWidget : GlanceAppWidget() {
    internal abstract val design: WidgetDesign
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialData = WidgetRenderData(AtomicWidgetSnapshotStore(context).readLatest(), null)
        val app = context.applicationContext as ElovaireApp
        val language = app.container.rootSettingsReader.appLanguage
        provideContent {
            val revision by WidgetRenderUpdates.revision.collectAsState()
            val currentLanguage by language.collectAsState()
            val lyrics by WidgetLyricsBus.state.collectAsState()
            val data by produceState(initialData, revision) {
                val snapshot = AtomicWidgetSnapshotStore(context).readLatest()
                val loadedArtwork = loadWidgetArtwork(context, snapshot)
                value = WidgetRenderData(snapshot, loadedArtwork)
            }
            WidgetContent(
                design = design,
                state = data.snapshot,
                artwork = data.artwork,
                copy = widgetCopy(currentLanguage),
                context = context,
                lyrics = lyrics,
            )
        }
    }
}

class CompactWidget : ElovaireWidget() { override val design = WidgetDesign.Compact }
class CoverWidget : ElovaireWidget() { override val design = WidgetDesign.Cover }
class StudioWidget : ElovaireWidget() { override val design = WidgetDesign.Studio }
class ControlsWidget : ElovaireWidget() { override val design = WidgetDesign.Controls }

abstract class ElovaireWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        (context.applicationContext as ElovaireApp).onWidgetProvidersChanged()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        (context.applicationContext as ElovaireApp).onWidgetProvidersChanged()
    }
}

class CompactWidgetReceiver : ElovaireWidgetReceiver() { override val glanceAppWidget = CompactWidget() }
class CoverWidgetReceiver : ElovaireWidgetReceiver() { override val glanceAppWidget = CoverWidget() }
class StudioWidgetReceiver : ElovaireWidgetReceiver() { override val glanceAppWidget = StudioWidget() }
class ControlsWidgetReceiver : ElovaireWidgetReceiver() { override val glanceAppWidget = ControlsWidget() }

@Composable
internal fun WidgetContent(
    design: WidgetDesign,
    state: WidgetPlaybackSnapshot?,
    artwork: Bitmap?,
    copy: WidgetCopy,
    context: Context,
    lyrics: WidgetLyricsSnapshot = WidgetLyricsSnapshot(),
) {
    val open = actionStartActivity(WidgetLaunchIntentFactory.openNowPlaying(context))
    val title = widgetTitle(state, copy)
    val artist = state?.artist.orEmpty().let { if (it == "Unknown Artist") copy.unknownArtist else it }
    val album = state?.album.orEmpty()
    val playing = state?.transportShowsPause == true
    val size = LocalSize.current
    val activeLyrics = lyrics.takeIf { it.songId != null && it.songId == state?.currentSongId }

    val baseRoot = GlanceModifier
        .fillMaxSize()
        .cornerRadius(20.dp)
        .background(SurfaceBase)

    when (design) {
        // Verso · Aura (4x1 compact bar)
        WidgetDesign.Compact -> Row(
            modifier = baseRoot.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(artwork, 42, 10, GlanceModifier.clickable(open).semantics { contentDescription = title })
            Spacer(GlanceModifier.width(10.dp))
            TrackMeta(
                title = title,
                artist = artist,
                modifier = GlanceModifier.defaultWeight().clickable(open),
                titleSize = 13,
                artistSize = 11,
            )
            Spacer(GlanceModifier.width(6.dp))
            if (size.width >= 260.dp) {
                SkipButton(next = false, copy = copy, size = 32)
                Spacer(GlanceModifier.width(4.dp))
            }
            TransportButton(playing = playing, copy = copy, size = 38)
            if (size.width >= 210.dp) {
                Spacer(GlanceModifier.width(4.dp))
                SkipButton(next = true, copy = copy, size = 32)
            }
        }

        // Verso · Sol (compact 2x2 cover card)
        WidgetDesign.Cover -> Column(
            modifier = baseRoot.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(open)
                    .semantics { contentDescription = title },
            ) {
                ArtworkFill(artwork, radius = 12)
                Box(GlanceModifier.padding(5.dp)) {
                    VersoPill()
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackMeta(
                    title = title,
                    artist = artist,
                    modifier = GlanceModifier.defaultWeight().clickable(open),
                    titleSize = 12,
                    artistSize = 10,
                )
                Spacer(GlanceModifier.width(6.dp))
                TransportButton(playing = playing, copy = copy, size = 38)
            }
        }

        // Verso · Nova (compact 4x2 live 2-line lyrics + controls)
        WidgetDesign.Studio -> Column(
            modifier = baseRoot.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(open),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkBox(artwork, 36, 9, GlanceModifier.semantics { contentDescription = title })
                Spacer(GlanceModifier.width(8.dp))
                TrackMeta(
                    title = title,
                    artist = artist,
                    modifier = GlanceModifier.defaultWeight(),
                    titleSize = 13,
                    artistSize = 11,
                )
                Spacer(GlanceModifier.width(6.dp))
                VersoPill()
            }
            Spacer(GlanceModifier.height(6.dp))
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .cornerRadius(12.dp)
                    .background(SurfaceCard)
                    .clickable(open)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val liveLine = activeLyrics?.currentLine
                val nextLine = activeLyrics?.nextLine
                if (!liveLine.isNullOrBlank()) {
                    Text(
                        text = liveLine,
                        style = TextStyle(
                            color = ColorProvider(TextPrimary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (!nextLine.isNullOrBlank()) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = nextLine,
                            style = TextStyle(
                                color = ColorProvider(TextSecondary),
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_lucide_audio_waveform),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(AccentBlue)),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = album.ifBlank { title },
                            style = TextStyle(
                                color = ColorProvider(TextSecondary),
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            TransportDock(playing = playing, copy = copy, buttonSize = 34, height = 40)
        }

        // Verso · Tempo (compact 2x1 transport-only widget)
        WidgetDesign.Controls -> Row(
            modifier = baseRoot.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkipButton(next = false, copy = copy, size = 32)
            Spacer(GlanceModifier.width(4.dp))
            TransportButton(playing = playing, copy = copy, size = 38)
            Spacer(GlanceModifier.width(4.dp))
            SkipButton(next = true, copy = copy, size = 32)
        }
    }
}

@Composable
private fun VersoPill() {
    Row(
        modifier = GlanceModifier
            .cornerRadius(8.dp)
            .background(SurfacePill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_verso_app_icon),
            contentDescription = null,
            modifier = GlanceModifier.size(11.dp).cornerRadius(3.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = "VERSO",
            style = TextStyle(
                color = ColorProvider(TextPrimary),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun TrackMeta(
    title: String,
    artist: String,
    modifier: GlanceModifier,
    titleSize: Int,
    artistSize: Int,
) {
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(TextPrimary),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (artist.isNotBlank()) {
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text = artist,
                style = TextStyle(
                    color = ColorProvider(TextSecondary),
                    fontSize = artistSize.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArtworkBox(bitmap: Bitmap?, size: Int, radius: Int, modifier: GlanceModifier) {
    Box(modifier.size(size.dp)) { ArtworkFill(bitmap, radius) }
}

@Composable
private fun ArtworkFill(bitmap: Bitmap?, radius: Int = 10) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(radius.dp)
            .background(SurfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(radius.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_lucide_disc_3),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(ColorProvider(TextSubtle)),
            )
        }
    }
}

@Composable
private fun TransportDock(playing: Boolean, copy: WidgetCopy, buttonSize: Int, height: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .cornerRadius((height / 2).dp)
            .background(SurfaceCard)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(next = false, copy = copy, size = buttonSize - 2)
        Spacer(GlanceModifier.width(14.dp))
        TransportButton(playing = playing, copy = copy, size = buttonSize)
        Spacer(GlanceModifier.width(14.dp))
        SkipButton(next = true, copy = copy, size = buttonSize - 2)
    }
}

@Composable
private fun TransportButton(playing: Boolean, copy: WidgetCopy, size: Int) {
    val label = if (playing) copy.pause else copy.play
    Box(
        modifier = GlanceModifier
            .size(size.dp)
            .cornerRadius((size / 2).dp)
            .background(AccentBlue)
            .clickable(widgetPlaybackAction(WidgetPlaybackAction.TogglePlayback))
            .semantics {
                contentDescription = label
                testTag = "widget-playback"
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (playing) R.drawable.ic_elovaire_pause_filled else R.drawable.ic_lucide_play),
            contentDescription = null,
            modifier = GlanceModifier.size((size / 2).dp),
            colorFilter = ColorFilter.tint(ColorProvider(TextPrimary)),
        )
    }
}

@Composable
private fun SkipButton(next: Boolean, copy: WidgetCopy, size: Int) {
    val action = if (next) WidgetPlaybackAction.SkipNext else WidgetPlaybackAction.SkipPrevious
    val label = if (next) copy.next else copy.previous
    Box(
        modifier = GlanceModifier
            .size(size.dp)
            .cornerRadius((size / 2).dp)
            .background(SecondaryBlueButton)
            .clickable(widgetPlaybackAction(action))
            .semantics {
                contentDescription = label
                testTag = if (next) "widget-next" else "widget-previous"
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (next) R.drawable.ic_elovaire_forward_filled else R.drawable.ic_elovaire_backward_filled),
            contentDescription = null,
            modifier = GlanceModifier.size((size / 2).dp),
            colorFilter = ColorFilter.tint(ColorProvider(TextPrimary)),
        )
    }
}
