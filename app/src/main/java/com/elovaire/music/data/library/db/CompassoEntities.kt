package elovaire.music.droidbeauty.app.data.library.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "compasso_playback_history",
    indices = [
        Index(value = ["normalizedKey"]),
        Index(value = ["playedAt"]),
    ],
)
internal data class PlaybackHistoryEntity(
    @PrimaryKey val videoId: String,
    val normalizedKey: String,
    val artistId: String?,
    val artistName: String,
    val title: String,
    val album: String,
    val artUri: String?,
    val durationMs: Long,
    val playedAt: Long,
    val playDurationMs: Long,
    val wasSkippedEarly: Boolean,
)

@Entity(
    tableName = "compasso_blocked_tracks",
    indices = [
        Index(value = ["normalizedKey"]),
    ],
)
internal data class BlockedTrackEntity(
    @PrimaryKey val videoId: String,
    val normalizedKey: String,
    val artistName: String,
    val blockedAt: Long,
)

@Entity(tableName = "compasso_blocked_artists")
internal data class BlockedArtistEntity(
    @PrimaryKey val normalizedArtist: String,
    val artistName: String,
    val blockedAt: Long,
)

@Entity(tableName = "compasso_persisted_queue")
internal data class PersistedQueueEntity(
    @PrimaryKey val singletonId: Int = 1,
    val queueJson: String,
    val currentIndex: Int,
    val continuationToken: String?,
    val discoveryRatio: Float,
    val isActive: Boolean,
    val seedVideoId: String?,
    val updatedAt: Long,
)
