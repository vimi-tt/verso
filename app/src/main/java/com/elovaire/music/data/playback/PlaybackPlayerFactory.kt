package elovaire.music.droidbeauty.app.data.playback

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.common.util.UnstableApi
import elovaire.music.droidbeauty.app.core.performance.ElovaireTrace

@UnstableApi
internal class PlaybackPlayerFactory(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
    private val extractorsFactory: DefaultExtractorsFactory,
    private val playbackAudioAttributes: AudioAttributes,
    private val audioProcessorsProvider: () -> Array<AudioProcessor>,
    private val preferredOutputDevice: () -> AudioDeviceInfo?,
    private val offloadPolicyProvider: () -> PlaybackOffloadPolicy = { PlaybackOffloadPolicy.Disabled },
) {
    fun create(enableSignalProcessing: Boolean): ExoPlayer {
        return ElovaireTrace.section("playback_player_create") {
            try {
                createConfiguredPlayer(enableSignalProcessing)
            } catch (failure: Exception) {
                if (!enableSignalProcessing) throw failure
                createFallbackPlayer()
            }
        }
    }

    private fun createConfiguredPlayer(enableSignalProcessing: Boolean): ExoPlayer {
        val offloadPreferences = resolveOffloadPreferences(enableSignalProcessing)
        val audioProcessors = if (enableSignalProcessing) audioProcessorsProvider() else emptyArray()
        val configuredPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(
                ElovaireRenderersFactory(
                    context,
                    audioProcessors,
                )
                    .setEnableAudioFloatOutput(false)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context, extractorsFactory)
                    .setDataSourceFactory(dataSourceFactory),
            )
            .setAudioAttributes(playbackAudioAttributes, false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .apply {
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                applyAudioOffloadPreferences(offloadPreferences)
        }
        applyPreferredOutputDevice(configuredPlayer)
        return configuredPlayer
    }

    private fun createFallbackPlayer(): ExoPlayer {
        val fallbackPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context, extractorsFactory)
                    .setDataSourceFactory(dataSourceFactory),
            )
            .setAudioAttributes(playbackAudioAttributes, false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .apply {
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        applyPreferredOutputDevice(fallbackPlayer)
        return fallbackPlayer
    }

    private fun resolveOffloadPreferences(
        enableSignalProcessing: Boolean,
    ): TrackSelectionParameters.AudioOffloadPreferences {
        return runCatching {
            if (enableSignalProcessing) {
                offloadPolicyProvider().toAudioOffloadPreferences()
            } else {
                PlaybackOffloadPolicy.Disabled.toAudioOffloadPreferences()
            }
        }.getOrDefault(PlaybackOffloadPolicy.Disabled.toAudioOffloadPreferences())
    }

    private fun ExoPlayer.applyAudioOffloadPreferences(
        offloadPreferences: TrackSelectionParameters.AudioOffloadPreferences,
    ) {
        runCatching {
            setTrackSelectionParameters(
                trackSelectionParameters.withAudioOffloadPreferences(offloadPreferences),
            )
        }
    }

    private fun applyPreferredOutputDevice(player: ExoPlayer) {
        val preferredDevice = runCatching { preferredOutputDevice() }.getOrNull()
        runCatching { preferredDevice?.let(player::setPreferredAudioDevice) }
    }
}

@UnstableApi
private fun TrackSelectionParameters.withAudioOffloadPreferences(
    audioOffloadPreferences: TrackSelectionParameters.AudioOffloadPreferences,
): TrackSelectionParameters {
    if (this.audioOffloadPreferences == audioOffloadPreferences) return this
    return buildUpon()
        .setAudioOffloadPreferences(audioOffloadPreferences)
        .build()
}

/**
 * Lightweight, muted ExoPlayer configured strictly for looping animated album artwork / canvas.
 * Disables audio & text tracks, caps video resolution at 720p, and bounds buffering to 4 MB.
 */
@UnstableApi
internal fun createMutedCanvasExoPlayer(context: Context): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 1_500,
            /* maxBufferMs = */ 4_000,
            /* bufferForPlaybackMs = */ 500,
            /* bufferForPlaybackAfterRebufferMs = */ 1_000,
        )
        .setTargetBufferBytes(4 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setMaxVideoSize(720, 720)
                .setForceLowestBitrate(false),
        )
    }

    return ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .setAudioAttributes(AudioAttributes.DEFAULT, false)
        .setHandleAudioBecomingNoisy(false)
        .build()
        .apply {
            volume = 0f
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
}
