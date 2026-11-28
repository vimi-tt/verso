package elovaire.music.droidbeauty.app.ui.components

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import elovaire.music.droidbeauty.app.data.artwork.AnimatedArtworkCache
import elovaire.music.droidbeauty.app.data.artwork.AnimatedArtworkMedia
import elovaire.music.droidbeauty.app.data.playback.createMutedCanvasExoPlayer
import elovaire.music.droidbeauty.app.domain.model.Song

/**
 * Renders a looping muted animated artwork / canvas video over static album artwork.
 *
 * Efficiency guarantees:
 * - No network lookup or ExoPlayer allocation unless [isActive] is true.
 * - Releases the ExoPlayer immediately when [isActive] becomes false or the screen leaves composition.
 * - Automatically pauses when playback is paused or the app lifecycle moves to background.
 * - Crossfades in only after [Player.Listener.onRenderedFirstFrame], preventing black flashes.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AnimatedArtworkOverlay(
    song: Song,
    isPlaying: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isActive) return

    var resolvedMedia by remember(song.id) { mutableStateOf<AnimatedArtworkMedia?>(null) }

    LaunchedEffect(song.id, song.title, song.artist, song.album) {
        resolvedMedia = AnimatedArtworkCache.resolve(song)
    }

    val media = resolvedMedia ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> isLifecycleStarted = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var firstFrameRendered by remember(media.videoUrl) { mutableStateOf(false) }
    var hasFailed by remember(media.videoUrl) { mutableStateOf(false) }
    var videoWidth by remember(media.videoUrl) { mutableIntStateOf(0) }
    var videoHeight by remember(media.videoUrl) { mutableIntStateOf(0) }

    if (hasFailed) return

    val exoPlayer = remember(context) {
        createMutedCanvasExoPlayer(context)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                firstFrameRendered = false
                hasFailed = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.clearVideoSurface()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, media.videoUrl) {
        firstFrameRendered = false
        hasFailed = false
        exoPlayer.setMediaItem(MediaItem.fromUri(media.videoUrl))
        exoPlayer.prepare()
    }

    val shouldPlay = isPlaying && isLifecycleStarted && !hasFailed
    LaunchedEffect(exoPlayer, shouldPlay) {
        exoPlayer.playWhenReady = shouldPlay
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "animated_artwork_alpha",
    )

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                isOpaque = false
                exoPlayer.setVideoTextureView(this)
            }
        },
        update = { textureView ->
            exoPlayer.setVideoTextureView(textureView)
            applyCenterCropTransform(
                textureView = textureView,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
            )
        },
        onRelease = { textureView ->
            exoPlayer.clearVideoTextureView(textureView)
        },
        modifier = modifier.graphicsLayer { alpha = videoAlpha },
    )
}

private fun applyCenterCropTransform(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    }
    textureView.setTransform(matrix)
}
