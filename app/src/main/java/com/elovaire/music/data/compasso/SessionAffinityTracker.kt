package elovaire.music.droidbeauty.app.data.compasso

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory real-time session affinity map tracking artist weights and well-received seeds:
 * - Early skip (< 30s): negative penalty on the artist for this session
 * - Completed play (> 80%) or Like: positive boost on the artist + seed recorded for fallbacks
 */
internal class SessionAffinityTracker {
    private val artistWeights = ConcurrentHashMap<String, Float>()
    private val positiveSeeds = ArrayDeque<String>()
    private val lock = Any()

    fun artistWeight(artistName: String): Float {
        val key = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (key.isEmpty()) return 0f
        return artistWeights[key] ?: 0f
    }

    fun recordEarlySkip(artistName: String) {
        val key = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (key.isEmpty()) return
        artistWeights.compute(key) { _, current ->
            ((current ?: 0f) + EARLY_SKIP_PENALTY).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        }
    }

    fun recordCompletedPlay(videoId: String?, artistName: String) {
        val key = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (key.isNotEmpty()) {
            artistWeights.compute(key) { _, current ->
                ((current ?: 0f) + COMPLETED_PLAY_BONUS).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            }
        }
        if (!videoId.isNullOrBlank()) {
            pushPositiveSeed(videoId)
        }
    }

    fun recordLike(videoId: String?, artistName: String) {
        val key = TrackNormalizer.normalizePrimaryArtist(artistName)
        if (key.isNotEmpty()) {
            artistWeights.compute(key) { _, current ->
                ((current ?: 0f) + LIKE_BONUS).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            }
        }
        if (!videoId.isNullOrBlank()) {
            pushPositiveSeed(videoId)
        }
    }

    fun bestFallbackSeed(excludingVideoIds: Set<String> = emptySet()): String? = synchronized(lock) {
        positiveSeeds.firstOrNull { it !in excludingVideoIds }
    }

    fun reset() {
        artistWeights.clear()
        synchronized(lock) { positiveSeeds.clear() }
    }

    private fun pushPositiveSeed(videoId: String) = synchronized(lock) {
        positiveSeeds.remove(videoId)
        positiveSeeds.addFirst(videoId)
        while (positiveSeeds.size > MAX_POSITIVE_SEEDS) {
            positiveSeeds.removeLast()
        }
    }

    companion object {
        const val EARLY_SKIP_THRESHOLD_MS = 30_000L
        const val COMPLETED_PLAY_RATIO = 0.80f
        const val EARLY_SKIP_PENALTY = -1.5f
        const val COMPLETED_PLAY_BONUS = 1.2f
        const val LIKE_BONUS = 2.0f
        private const val MIN_WEIGHT = -6.0f
        private const val MAX_WEIGHT = 6.0f
        private const val MAX_POSITIVE_SEEDS = 20
    }
}
