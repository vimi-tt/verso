package elovaire.music.droidbeauty.app.data.youtube

import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory LRU cache with TTL and generation-based invalidation for resolved
 * YouTube stream URLs (`googlevideo.com` URLs expire after a few hours).
 */
internal class YouTubeStreamCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long,
) {
    private class Entry(val url: String, val expiresAtMs: Long)

    private val lock = Any()
    private val generationCounter = AtomicLong(0L)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxSize
    }

    fun get(videoId: String): String? = synchronized(lock) {
        val entry = entries[videoId] ?: return null
        if (entry.expiresAtMs <= nowMs()) {
            entries.remove(videoId)
            return null
        }
        entry.url
    }

    fun put(videoId: String, url: String, generation: Long? = null) {
        if (videoId.isBlank() || url.isBlank()) return
        val now = nowMs()
        synchronized(lock) {
            if (generation != null && generation != generationCounter.get()) {
                return
            }
            entries[videoId] = Entry(url, now + ttlMs)
        }
    }

    fun invalidate(videoId: String) {
        synchronized(lock) { entries.remove(videoId) }
    }

    fun invalidateAll() {
        synchronized(lock) {
            entries.clear()
            generationCounter.incrementAndGet()
        }
    }

    fun generation(): Long = generationCounter.get()

    companion object {
        const val DEFAULT_MAX_SIZE = 64
        const val DEFAULT_TTL_MS = 5L * 60L * 1000L
    }
}
