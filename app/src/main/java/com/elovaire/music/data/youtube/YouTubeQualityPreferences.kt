package elovaire.music.droidbeauty.app.data.youtube

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vimi.k7.innertube.stream.AudioPreference
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

internal enum class LyricsProviderPreference {
    BetterLyrics,
    Lrclib,
}

internal data class YouTubeQualitySettings(
    val wifiStreamingQuality: AudioPreference = AudioPreference.HIGHEST,
    val cellularStreamingQuality: AudioPreference = AudioPreference.BALANCED,
    val downloadQuality: AudioPreference = AudioPreference.HIGHEST,
    val wifiOnlyDownloads: Boolean = false,
    val autoDownloadLikedOnWifi: Boolean = false,
    val preferredLyricsProvider: LyricsProviderPreference = LyricsProviderPreference.BetterLyrics,
    val syncPlaybackHistoryToYouTube: Boolean = true,
)

internal class YouTubeQualityPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val settingsFile = File(appContext.filesDir, FILE_NAME)
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _state = MutableStateFlow(loadSettings())
    val state: StateFlow<YouTubeQualitySettings> = _state.asStateFlow()

    fun setWifiStreamingQuality(preference: AudioPreference) {
        updateAndPersist { it.copy(wifiStreamingQuality = preference) }
    }

    fun setCellularStreamingQuality(preference: AudioPreference) {
        updateAndPersist { it.copy(cellularStreamingQuality = preference) }
    }

    fun setDownloadQuality(preference: AudioPreference) {
        updateAndPersist { it.copy(downloadQuality = preference) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        updateAndPersist { it.copy(wifiOnlyDownloads = enabled) }
    }

    fun setAutoDownloadLikedOnWifi(enabled: Boolean) {
        updateAndPersist { it.copy(autoDownloadLikedOnWifi = enabled) }
    }

    fun setPreferredLyricsProvider(provider: LyricsProviderPreference) {
        updateAndPersist { it.copy(preferredLyricsProvider = provider) }
    }

    fun setSyncPlaybackHistoryToYouTube(enabled: Boolean) {
        updateAndPersist { it.copy(syncPlaybackHistoryToYouTube = enabled) }
    }

    fun isOnWifi(): Boolean {
        val manager = connectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun currentStreamingPreference(): AudioPreference {
        val current = _state.value
        return if (isOnWifi()) current.wifiStreamingQuality else current.cellularStreamingQuality
    }

    private fun updateAndPersist(transform: (YouTubeQualitySettings) -> YouTubeQualitySettings) {
        _state.update { current ->
            val next = transform(current)
            persistSettings(next)
            next
        }
    }

    private fun loadSettings(): YouTubeQualitySettings {
        if (!settingsFile.exists()) return YouTubeQualitySettings()
        return runCatching {
            val json = JSONObject(settingsFile.readText())
            YouTubeQualitySettings(
                wifiStreamingQuality = parsePreference(json.optString(KEY_WIFI_STREAMING), AudioPreference.HIGHEST),
                cellularStreamingQuality = parsePreference(json.optString(KEY_CELLULAR_STREAMING), AudioPreference.BALANCED),
                downloadQuality = parsePreference(json.optString(KEY_DOWNLOAD_QUALITY), AudioPreference.HIGHEST),
                wifiOnlyDownloads = json.optBoolean(KEY_WIFI_ONLY_DOWNLOADS, false),
                autoDownloadLikedOnWifi = json.optBoolean(KEY_AUTO_DOWNLOAD_LIKED_ON_WIFI, false),
                preferredLyricsProvider = parseLyricsProvider(json.optString(KEY_LYRICS_PROVIDER)),
                syncPlaybackHistoryToYouTube = json.optBoolean(KEY_SYNC_YOUTUBE_HISTORY, true),
            )
        }.getOrDefault(YouTubeQualitySettings())
    }

    private fun persistSettings(settings: YouTubeQualitySettings) {
        runCatching {
            val json = JSONObject()
                .put(KEY_WIFI_STREAMING, settings.wifiStreamingQuality.name)
                .put(KEY_CELLULAR_STREAMING, settings.cellularStreamingQuality.name)
                .put(KEY_DOWNLOAD_QUALITY, settings.downloadQuality.name)
                .put(KEY_WIFI_ONLY_DOWNLOADS, settings.wifiOnlyDownloads)
                .put(KEY_AUTO_DOWNLOAD_LIKED_ON_WIFI, settings.autoDownloadLikedOnWifi)
                .put(KEY_LYRICS_PROVIDER, settings.preferredLyricsProvider.name)
                .put(KEY_SYNC_YOUTUBE_HISTORY, settings.syncPlaybackHistoryToYouTube)
            settingsFile.writeText(json.toString())
        }
    }

    private fun parseLyricsProvider(raw: String): LyricsProviderPreference {
        if (raw.isBlank()) return LyricsProviderPreference.BetterLyrics
        return runCatching { LyricsProviderPreference.valueOf(raw) }.getOrDefault(LyricsProviderPreference.BetterLyrics)
    }

    private fun parsePreference(raw: String, default: AudioPreference): AudioPreference {
        if (raw.isBlank()) return default
        return runCatching { AudioPreference.valueOf(raw) }.getOrDefault(default)
    }

    private companion object {
        const val FILE_NAME = "youtube_quality_settings.json"
        const val KEY_WIFI_STREAMING = "wifi_streaming_quality"
        const val KEY_CELLULAR_STREAMING = "cellular_streaming_quality"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        const val KEY_AUTO_DOWNLOAD_LIKED_ON_WIFI = "auto_download_liked_on_wifi"
        const val KEY_LYRICS_PROVIDER = "preferred_lyrics_provider"
        const val KEY_SYNC_YOUTUBE_HISTORY = "sync_playback_history_yt"
    }
}
