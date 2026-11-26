package elovaire.music.droidbeauty.app.data.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerNotificationManager
import elovaire.music.droidbeauty.app.MainActivity
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.core.backend.BackendResourceRegistry
import elovaire.music.droidbeauty.app.core.backend.BackendResourceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.i18n.localizedArtistName
import elovaire.music.droidbeauty.app.ui.i18n.localizedExternalAudioText
import elovaire.music.droidbeauty.app.ui.i18n.songFavoriteAccessibilityLabel

const val EXTRA_OPEN_PLAYER_FROM_NOTIFICATION = "elovaire.music.droidbeauty.app.extra.OPEN_PLAYER_FROM_NOTIFICATION"

internal fun localizedNotificationTitle(song: Song?, language: AppLanguage): String =
    song?.let { localizedExternalAudioText(it.title, it.id, language) }.orEmpty()

internal fun localizedNotificationArtist(song: Song?, language: AppLanguage): String =
    song?.artist?.let { if (it == "Unknown Artist") localizedArtistName(it, language) else it }.orEmpty()

internal fun notificationCustomActionIds(
    currentSongId: Long?,
    favoriteSongIds: Collection<Long>,
    supportsFavoriteAction: Boolean,
): MutableList<String> {
    val actions = mutableListOf<String>()
    if (supportsFavoriteAction && currentSongId != null) {
        val isFavorite = currentSongId in favoriteSongIds
        actions += if (isFavorite) {
            PlaybackNotificationController.ACTION_FAVORITE_ON
        } else {
            PlaybackNotificationController.ACTION_FAVORITE_OFF
        }
    }
    actions += PlaybackNotificationController.ACTION_SHUFFLE
    return actions
}

internal fun shouldKeepPlaybackServiceForeground(
    ongoing: Boolean,
    hasCurrentSong: Boolean,
    transportShowsPause: Boolean,
    hasPendingInterruptionResume: Boolean,
): Boolean {
    return hasCurrentSong &&
        ((ongoing && transportShowsPause) || hasPendingInterruptionResume)
}

@UnstableApi
internal class PlaybackNotificationController(
    private val context: Context,
    private val playbackManager: PlaybackManager,
    private val scope: CoroutineScope,
    private val language: StateFlow<AppLanguage>,
    private val resourceTracker: BackendResourceTracker = BackendResourceRegistry,
    private val favoriteSongIds: StateFlow<List<Long>>? = null,
    private val onToggleFavorite: ((Song) -> Unit)? = null,
) {
    private val notificationJobs = mutableListOf<Job>()
    private val artworkLoader = NotificationArtworkLoader(context, scope, resourceTracker)

    private val notificationManager = PlayerNotificationManager.Builder(
        context,
        NOTIFICATION_ID,
        NOTIFICATION_CHANNEL_ID,
    )
        .setMediaDescriptionAdapter(NotificationDescriptionAdapter())
        .setCustomActionReceiver(NotificationCustomActionReceiver())
        .setNotificationListener(
            object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: android.app.Notification,
                    ongoing: Boolean,
                ) {
                    val state = playbackManager.state.value
                    if (
                        shouldKeepPlaybackServiceForeground(
                            ongoing = ongoing,
                            hasCurrentSong = state.currentSong != null,
                            transportShowsPause = state.transportShowsPause,
                            hasPendingInterruptionResume = playbackManager.hasPendingInterruptionResume(),
                        )
                    ) {
                        ElovaireMediaLibraryService.start(context, notificationId, notification)
                    } else {
                        ElovaireMediaLibraryService.demote(context)
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean,
                ) {
                    if (dismissedByUser) {
                        val currentState = playbackManager.state.value
                        if (currentState.currentSong != null && !currentState.isPlaying) {
                            notificationDismissedWhilePaused = true
                            pauseHideJob?.cancel()
                            updateNotificationPlayer(null)
                        }
                    }
                    ElovaireMediaLibraryService.stop(context)
                }
            },
        )
        .build()
        .apply {
            setSmallIcon(R.drawable.ic_lucide_disc_3)
            setMediaSessionToken(playbackManager.platformMediaSessionToken)
            setUseFastForwardAction(false)
            setUseRewindAction(false)
            setUsePreviousAction(true)
            setUseNextAction(true)
            setUseChronometer(false)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setPlayer(null)
        }

    private var notificationsEnabled = false
    private var pauseHideJob: Job? = null
    private var notificationDismissedWhilePaused = false
    private var lastManualPlaybackStartVersion = 0L
    private var attachedPlayer: Player? = null

    private fun startNotificationCollectorsIfNeeded() {
        if (notificationJobs.any { it.isActive }) return
        notificationJobs += scope.launch {
            language.collect {
                if (notificationsEnabled) notificationManager.invalidate()
            }
        }
        favoriteSongIds?.let { favoritesFlow ->
            notificationJobs += scope.launch {
                favoritesFlow
                    .map { it.toSet() }
                    .distinctUntilChanged()
                    .collect {
                        if (notificationsEnabled) notificationManager.invalidate()
                    }
            }
        }
        notificationJobs += scope.launch {
            playbackManager.manualPlaybackStartVersion.collect { version ->
                if (version == lastManualPlaybackStartVersion) return@collect
                lastManualPlaybackStartVersion = version
                notificationDismissedWhilePaused = false
                val currentState = playbackManager.state.value
                if (notificationsEnabled && shouldShowNotification(currentState)) {
                    updateNotificationPlayer(playbackManager.playerInstance)
                }
            }
        }
        notificationJobs += scope.launch {
            playbackManager.playerInstanceVersion.collect {
                if (!notificationsEnabled) return@collect
                val currentState = playbackManager.state.value
                if (shouldShowNotification(currentState)) {
                    updateNotificationPlayer(playbackManager.playerInstance)
                }
            }
        }
        notificationJobs += scope.launch {
            playbackManager.state
                .map { state ->
                    notificationRenderStateOf(
                        song = state.currentSong,
                        isPlaying = state.isPlaying,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { renderState ->
                    val state = playbackManager.state.value
                    if (!notificationsEnabled) return@collectLatest
                    artworkLoader.setCurrentArtwork(
                        mediaUri = renderState.mediaUri,
                        fallbackArtUri = renderState.artUri,
                    )
                    notificationManager.invalidate()
                    when {
                        state.currentSong == null -> {
                            pauseHideJob?.cancel()
                            notificationDismissedWhilePaused = false
                            updateNotificationPlayer(null)
                        }
                        state.isPlaying -> {
                            pauseHideJob?.cancel()
                            notificationDismissedWhilePaused = false
                            updateNotificationPlayer(playbackManager.playerInstance)
                        }
                        else -> {
                            pauseHideJob?.cancel()
                            if (notificationDismissedWhilePaused) {
                                updateNotificationPlayer(null)
                                return@collectLatest
                            }
                            updateNotificationPlayer(playbackManager.playerInstance)
                            pauseHideJob = launch {
                                delay(PAUSE_NOTIFICATION_TIMEOUT_MS)
                                val latestState = playbackManager.state.value
                                if (
                                    notificationsEnabled &&
                                    latestState.currentSong != null &&
                                    !latestState.isPlaying &&
                                    !notificationDismissedWhilePaused &&
                                    !playbackManager.hasPendingInterruptionResume()
                                ) {
                                    updateNotificationPlayer(null)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (notificationsEnabled == enabled) return
        notificationsEnabled = enabled
        if (!enabled) {
            stopNotificationCollectors()
            updateNotificationPlayer(null)
            ElovaireMediaLibraryService.stop(context)
            return
        }
        startNotificationCollectorsIfNeeded()
        val currentState = playbackManager.state.value
        if (shouldShowNotification(currentState)) {
            updateNotificationPlayer(playbackManager.playerInstance)
        } else {
            updateNotificationPlayer(null)
        }
    }

    private fun stopNotificationCollectors() {
        notificationJobs.forEach(Job::cancel)
        notificationJobs.clear()
        pauseHideJob?.cancel()
        pauseHideJob = null
        artworkLoader.clear()
    }

    private fun shouldShowNotification(currentState: PlaybackUiState): Boolean {
        if (currentState.currentSong == null) return false
        if (notificationDismissedWhilePaused) return false
        return true
    }

    private fun updateNotificationPlayer(player: Player?) {
        if (attachedPlayer === player) return
        attachedPlayer = player
        notificationManager.setPlayer(player)
    }

    private inner class NotificationDescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return localizedNotificationTitle(playbackManager.state.value.currentSong, language.value)
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, true)
            }
            return PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            return localizedNotificationArtist(playbackManager.state.value.currentSong, language.value)
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? {
            val song = playbackManager.state.value.currentSong ?: return null
            val mediaUri = song.uri
            val artworkUri = song.artUri
            artworkLoader.cachedBitmap(mediaUri, artworkUri)?.let { return it }
            if (notificationsEnabled) {
                artworkLoader.loadAsync(
                    mediaUri = mediaUri,
                    fallbackArtUri = artworkUri,
                    isStillCurrent = { key ->
                        notificationsEnabled &&
                            artworkLoader.isCurrent(key) &&
                            playbackManager.state.value.currentSong?.id == song.id &&
                            playbackManager.state.value.currentSong?.uri == mediaUri &&
                            playbackManager.state.value.currentSong?.artUri == artworkUri
                    },
                    callback = callback,
                    onMediaSessionArtworkLoaded = { artworkData ->
                        playbackManager.updateCurrentArtworkData(song.id, artworkData)
                    },
                )
            }
            return null
        }
    }

    private inner class NotificationCustomActionReceiver : PlayerNotificationManager.CustomActionReceiver {
        override fun createCustomActions(
            context: Context,
            instanceId: Int,
        ): MutableMap<String, NotificationCompat.Action> {
            val favoriteOnIntent = Intent(ACTION_FAVORITE_ON).setPackage(context.packageName)
            val favoriteOffIntent = Intent(ACTION_FAVORITE_OFF).setPackage(context.packageName)
            val shuffleIntent = Intent(ACTION_SHUFFLE).setPackage(context.packageName)
            val currentLanguage = language.value
            return mutableMapOf(
                ACTION_FAVORITE_ON to NotificationCompat.Action(
                    R.drawable.ic_lucide_star_filled,
                    songFavoriteAccessibilityLabel(true, currentLanguage),
                    PendingIntent.getBroadcast(
                        context,
                        instanceId * 10 + 1,
                        favoriteOnIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
                ACTION_FAVORITE_OFF to NotificationCompat.Action(
                    R.drawable.ic_lucide_star,
                    songFavoriteAccessibilityLabel(false, currentLanguage),
                    PendingIntent.getBroadcast(
                        context,
                        instanceId * 10 + 2,
                        favoriteOffIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
                ACTION_SHUFFLE to NotificationCompat.Action(
                    R.drawable.ic_lucide_shuffle,
                    "Shuffle",
                    PendingIntent.getBroadcast(
                        context,
                        instanceId * 10 + 3,
                        shuffleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
            )
        }

        override fun getCustomActions(player: Player): MutableList<String> {
            return notificationCustomActionIds(
                currentSongId = playbackManager.state.value.currentSong?.id,
                favoriteSongIds = favoriteSongIds?.value.orEmpty(),
                supportsFavoriteAction = onToggleFavorite != null,
            )
        }

        override fun onCustomAction(
            player: Player,
            action: String,
            intent: Intent,
        ) {
            when (action) {
                ACTION_SHUFFLE -> playbackManager.toggleShuffle()
                ACTION_FAVORITE_ON, ACTION_FAVORITE_OFF -> {
                    playbackManager.state.value.currentSong?.let { song ->
                        onToggleFavorite?.invoke(song)
                    }
                }
            }
        }
    }

    companion object {
        internal const val NOTIFICATION_CHANNEL_ID = "elovaire_playback"
        internal const val NOTIFICATION_ID = 1001
        internal const val ACTION_SHUFFLE = "elovaire.music.droidbeauty.app.action.SHUFFLE"
        internal const val ACTION_FAVORITE_ON = "elovaire.music.droidbeauty.app.action.FAVORITE_ON"
        internal const val ACTION_FAVORITE_OFF = "elovaire.music.droidbeauty.app.action.FAVORITE_OFF"
        private const val PAUSE_NOTIFICATION_TIMEOUT_MS = 180_000L

        fun ensureNotificationChannel(context: Context) {
            NotificationUtil.createNotificationChannel(
                context,
                NOTIFICATION_CHANNEL_ID,
                R.string.app_name,
                R.string.app_name,
                NotificationUtil.IMPORTANCE_LOW,
            )
        }

    }

}
