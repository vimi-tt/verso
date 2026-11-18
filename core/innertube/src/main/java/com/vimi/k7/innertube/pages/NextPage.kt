/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt
 */

package com.vimi.k7.innertube.pages

import com.vimi.k7.innertube.models.Album
import com.vimi.k7.innertube.models.BrowseEndpoint
import com.vimi.k7.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.vimi.k7.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.vimi.k7.innertube.models.PlaylistPanelVideoRenderer
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.models.WatchEndpoint
import com.vimi.k7.innertube.utils.parseTime

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
)

object NextPage {
    private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")

    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val runs = renderer.longBylineText?.runs ?: return null

        // Extract library tokens using the new method that properly handles multiple toggle items
        val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

        // Identify the album run by its browseEndpoint rather than positional index,
        // because collaborator artists occupy positional slots on multi-artist tracks.
        val albumRun = runs.firstOrNull { run ->
            val browseEndpoint = run.navigationEndpoint?.browseEndpoint ?: return@firstOrNull false
            val pageType = browseEndpoint.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig?.pageType
            pageType == MUSIC_PAGE_TYPE_ALBUM ||
                pageType == MUSIC_PAGE_TYPE_AUDIOBOOK ||
                (pageType.isNullOrBlank() && browseEndpoint.browseId.startsWith("MPRE"))
        }

        val year = runs.firstNotNullOfOrNull { run ->
            if (run.navigationEndpoint != null) return@firstNotNullOfOrNull null
            run.text.trim().takeIf { it.matches(YEAR_REGEX) }?.toIntOrNull()
        }

        return SongItem(
            id = renderer.videoId
                ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                ?: return null,
            title =
                renderer.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    .orEmpty(),
            artists = PageHelper.extractArtists(runs),
            album =
                albumRun?.let {
                    val browseId = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    Album(
                        name = it.text,
                        id = browseId,
                    )
                },
            // Relaxed from `?: return null`: /next panels used by getSongAlbumRef may omit
            // lengthText while still carrying the full artist/album/year byline.
            duration =
                renderer.lengthText
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.parseTime(),
            year = year,
            musicVideoType = renderer.navigationEndpoint?.musicVideoType,
            thumbnail =
                renderer.thumbnail?.thumbnails
                    ?.lastOrNull()
                    ?.url
                    .orEmpty(),
            explicit =
                renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
            libraryAddToken = libraryTokens.addToken,
            libraryRemoveToken = libraryTokens.removeToken
        )
    }
}
