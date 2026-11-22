package elovaire.music.droidbeauty.app.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CompassoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: PlaybackHistoryEntity)

    @Query("SELECT * FROM compasso_playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 50): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM compasso_playback_history WHERE wasSkippedEarly = 0 ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getPositiveHistory(limit: Int = 200): List<PlaybackHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedTrack(entry: BlockedTrackEntity)

    @Query("SELECT * FROM compasso_blocked_tracks ORDER BY blockedAt DESC")
    suspend fun getAllBlockedTracks(): List<BlockedTrackEntity>

    @Query("SELECT videoId FROM compasso_blocked_tracks")
    fun observeBlockedVideoIds(): Flow<List<String>>

    @Query("DELETE FROM compasso_blocked_tracks WHERE videoId = :videoId")
    suspend fun removeBlockedTrack(videoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedArtist(entry: BlockedArtistEntity)

    @Query("SELECT * FROM compasso_blocked_artists ORDER BY blockedAt DESC")
    suspend fun getAllBlockedArtists(): List<BlockedArtistEntity>

    @Query("DELETE FROM compasso_blocked_artists WHERE normalizedArtist = :normalizedArtist")
    suspend fun removeBlockedArtist(normalizedArtist: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePersistedQueue(state: PersistedQueueEntity)

    @Query("SELECT * FROM compasso_persisted_queue WHERE singletonId = 1 LIMIT 1")
    suspend fun getPersistedQueue(): PersistedQueueEntity?

    @Query("DELETE FROM compasso_persisted_queue")
    suspend fun clearPersistedQueue()
}
