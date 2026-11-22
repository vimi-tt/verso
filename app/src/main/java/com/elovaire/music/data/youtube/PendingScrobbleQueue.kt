package elovaire.music.droidbeauty.app.data.youtube

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal enum class ScrobblePostResult {
    Success,
    RetryableFailure,
    PermanentFailure,
}

internal data class PendingScrobble(
    val artist: String,
    val track: String,
    val album: String,
    val durationMs: Long,
    val timestampSeconds: Long,
) {
    fun dedupeKey(): String = "${artist.lowercase()}|${track.lowercase()}|$timestampSeconds"
}

/**
 * Durable FIFO queue for scrobbles/listens that failed due to transient network errors.
 */
internal class PendingScrobbleQueue(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val items = ArrayList<PendingScrobble>()
    private var loaded = false

    @Synchronized
    fun size(): Int {
        ensureLoaded()
        return items.size
    }

    @Synchronized
    fun snapshot(): List<PendingScrobble> {
        ensureLoaded()
        return items.toList()
    }

    @Synchronized
    fun enqueue(entry: PendingScrobble): Int {
        ensureLoaded()
        val key = entry.dedupeKey()
        if (items.none { it.dedupeKey() == key }) {
            items.add(entry)
            while (items.size > maxEntries) {
                items.removeAt(0)
            }
            persist()
        }
        return items.size
    }

    @Synchronized
    fun remove(entry: PendingScrobble): Int {
        ensureLoaded()
        val key = entry.dedupeKey()
        if (items.removeAll { it.dedupeKey() == key }) {
            persist()
        }
        return items.size
    }

    @Synchronized
    fun clear() {
        items.clear()
        loaded = true
        runCatching {
            if (file.exists()) file.delete()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.isFile) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val artist = obj.optString("artist")
                val track = obj.optString("track")
                val timestampSeconds = obj.optLong("timestampSeconds", 0L)
                if (artist.isBlank() || track.isBlank() || timestampSeconds <= 0L) continue
                items.add(
                    PendingScrobble(
                        artist = artist,
                        track = track,
                        album = obj.optString("album"),
                        durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                        timestampSeconds = timestampSeconds,
                    ),
                )
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            items.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("artist", entry.artist)
                        .put("track", entry.track)
                        .put("album", entry.album)
                        .put("durationMs", entry.durationMs)
                        .put("timestampSeconds", entry.timestampSeconds),
                )
            }
            file.writeText(array.toString())
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
