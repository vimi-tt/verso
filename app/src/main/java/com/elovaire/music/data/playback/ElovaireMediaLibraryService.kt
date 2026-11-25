package elovaire.music.droidbeauty.app.data.playback

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import elovaire.music.droidbeauty.app.ElovaireApp
import elovaire.music.droidbeauty.app.BuildConfig
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.core.getParcelableExtraCompat

@OptIn(UnstableApi::class)
class ElovaireMediaLibraryService : MediaLibraryService() {
    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(mainLooper) }
    private var removeForegroundNotificationOnDestroy = true
    private val mediaButtonStateCheck = Runnable {
        val player = (application as ElovaireApp).container.playbackPlayer
        if (!player.playWhenReady && !isPlaybackOngoing) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                removeForegroundNotificationOnDestroy = true
                val requestGeneration = intent.getLongExtra(EXTRA_REQUEST_GENERATION, 0L)
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)
                val notification = takePendingNotification(requestGeneration)
                    ?: intent.getParcelableExtraCompat<Notification>(EXTRA_NOTIFICATION)
                    ?: createFallbackNotification()
                runCatching {
                    ServiceCompat.startForeground(
                        this,
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                }.onSuccess {
                    if (foregroundRequests.isCurrentStart(notificationId, requestGeneration)) {
                        foregroundRequests.completeStart(notificationId, requestGeneration)
                    } else if (!foregroundRequests.hasActiveRequest()) {
                        removeForegroundNotificationOnDestroy = true
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }.onFailure { throwable ->
                    logForegroundFailure("Unable to promote media library service", throwable)
                    foregroundRequests.invalidate(requestGeneration)
                    if (!foregroundRequests.hasActiveRequest()) stopSelf(startId)
                }
            }

            ACTION_DEMOTE -> {
                if (foregroundRequests.isCurrentDemotion(intent.getLongExtra(EXTRA_REQUEST_GENERATION, 0L))) {
                    removeForegroundNotificationOnDestroy = false
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf(startId)
                }
            }

            Intent.ACTION_MEDIA_BUTTON -> {
                mainHandler.removeCallbacks(mediaButtonStateCheck)
                mainHandler.post(mediaButtonStateCheck)
            }
        }
        return result
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        sessions.toList().forEach { session ->
            if (isSessionAdded(session)) {
                removeSession(session)
            }
        }
        if (removeForegroundNotificationOnDestroy) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        foregroundRequests.serviceDestroyed()
        super.onDestroy()
    }

    /**
     * The app's custom notification controller is the only foreground owner. Keeping Media3's
     * default provider disabled prevents this session-only service from creating a second
     * notification or foreground-service lifecycle.
     */
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) = Unit

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? {
        return (application as ElovaireApp).container
            .also { it.startPlayback() }
            .playbackRuntime
            .mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val playbackManager = (application as ElovaireApp).container
            .also { it.startPlayback() }
            .playbackRuntime
        if (!playbackManager.state.value.transportShowsPause) {
            stopSelf()
            super.onTaskRemoved(rootIntent)
        }
    }

    companion object {
        private const val ACTION_START = "elovaire.music.droidbeauty.app.action.PLAYBACK_SERVICE_START"
        private const val ACTION_DEMOTE = "elovaire.music.droidbeauty.app.action.PLAYBACK_SERVICE_DEMOTE"
        private const val EXTRA_NOTIFICATION = "elovaire.music.droidbeauty.app.extra.PLAYBACK_NOTIFICATION"
        private const val EXTRA_NOTIFICATION_ID = "elovaire.music.droidbeauty.app.extra.PLAYBACK_NOTIFICATION_ID"
        private const val EXTRA_REQUEST_GENERATION = "elovaire.music.droidbeauty.app.extra.PLAYBACK_REQUEST_GENERATION"
        private const val DEFAULT_NOTIFICATION_ID = 4101

        private val foregroundRequests = PlaybackForegroundRequests()
        @Volatile
        private var pendingNotificationGeneration: Long = 0L
        @Volatile
        private var pendingNotification: Notification? = null

        private fun takePendingNotification(requestGeneration: Long): Notification? {
            if (requestGeneration == 0L || pendingNotificationGeneration != requestGeneration) return null
            val notification = pendingNotification
            pendingNotification = null
            return notification
        }

        fun start(
            context: Context,
            notificationId: Int,
            notification: Notification,
        ) {
            val generation = foregroundRequests.requestStart(notificationId) ?: return
            pendingNotificationGeneration = generation
            pendingNotification = notification
            val intent = Intent(context, ElovaireMediaLibraryService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_REQUEST_GENERATION, generation)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { throwable ->
                if (pendingNotificationGeneration == generation) pendingNotification = null
                foregroundRequests.startDispatchFailed(generation)
                logForegroundFailure("Unable to start media library foreground service", throwable)
            }
        }

        fun stop(context: Context) {
            val hadPendingStart = foregroundRequests.hasPendingStart()
            pendingNotification = null
            foregroundRequests.cancel()
            if (!hadPendingStart) {
                context.stopService(Intent(context, ElovaireMediaLibraryService::class.java))
            }
        }

        fun demote(context: Context) {
            val generation = foregroundRequests.requestDemotion() ?: return
            val intent = Intent(context, ElovaireMediaLibraryService::class.java).apply {
                action = ACTION_DEMOTE
                putExtra(EXTRA_REQUEST_GENERATION, generation)
            }
            runCatching { context.startService(intent) }
                .onFailure { throwable ->
                    runCatching {
                        foregroundRequests.demotionDispatchFailed(generation) {
                            context.stopService(Intent(context, ElovaireMediaLibraryService::class.java))
                        }
                    }.onFailure { logForegroundFailure("Unable to stop media library service", it) }
                    logForegroundFailure("Unable to demote media library foreground service", throwable)
                }
        }

        private fun logForegroundFailure(message: String, throwable: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
        }

        private const val TAG = "MediaLibraryService"
    }

    private fun createFallbackNotification(): Notification =
        NotificationCompat.Builder(this, PlaybackNotificationController.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lucide_disc_3)
            .setOngoing(true)
            .setSilent(true)
            .build()
}

internal class PlaybackForegroundRequests {
    private var generation = 0L
    private var pendingId: Int? = null
    private var runningId: Int? = null
    private var demotingId: Int? = null

    @Synchronized
    fun requestStart(notificationId: Int): Long? {
        if (pendingId == notificationId || (pendingId == null && runningId == notificationId)) return null
        generation += 1L
        demotingId = null
        pendingId = notificationId
        return generation
    }

    @Synchronized
    fun requestDemotion(): Long? {
        if (runningId == null && pendingId == null) return null
        generation += 1L
        demotingId = runningId
        pendingId = null
        runningId = null
        return generation
    }

    @Synchronized
    fun isCurrentStart(notificationId: Int, requestGeneration: Long): Boolean =
        requestGeneration == generation && (pendingId == notificationId || runningId == notificationId)

    @Synchronized
    fun isCurrentDemotion(requestGeneration: Long): Boolean =
        requestGeneration != 0L && requestGeneration == generation && pendingId == null && runningId == null

    @Synchronized
    fun completeStart(notificationId: Int, requestGeneration: Long) {
        if (generation == requestGeneration && pendingId == notificationId) {
            pendingId = null
            runningId = notificationId
        }
    }

    @Synchronized
    fun startDispatchFailed(requestGeneration: Long) {
        if (generation == requestGeneration) pendingId = null
    }

    @Synchronized
    fun demotionDispatchFailed(requestGeneration: Long, stopService: () -> Unit) {
        if (generation == requestGeneration) {
            try {
                stopService()
                runningId = null
            } catch (failure: RuntimeException) {
                runningId = demotingId
                throw failure
            } finally {
                demotingId = null
            }
        }
    }

    @Synchronized
    fun invalidate(requestGeneration: Long) {
        if (generation == requestGeneration && pendingId != null) {
            pendingId = null
            runningId = null
        }
    }

    @Synchronized
    fun hasPendingStart(): Boolean = pendingId != null

    @Synchronized
    fun hasActiveRequest(): Boolean = pendingId != null || runningId != null

    @Synchronized
    fun cancel() {
        generation += 1L
        pendingId = null
        runningId = null
        demotingId = null
    }

    @Synchronized
    fun serviceDestroyed() {
        runningId = null
        demotingId = null
    }
}
