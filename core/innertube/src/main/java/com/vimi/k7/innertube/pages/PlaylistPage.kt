/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt
 */

package com.vimi.k7.innertube.pages

import com.vimi.k7.innertube.models.Album
import com.vimi.k7.innertube.models.Artist
import com.vimi.k7.innertube.models.MusicResponsiveListItemRenderer
import com.vimi.k7.innertube.models.PlaylistItem
import com.vimi.k7.innertube.models.Run
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.models.splitBySeparator
import com.vimi.k7.innertube.utils.parseTime
import com.vimi.k7.innertube.util.KLog

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
) {
    companion object {
        fun ownerFromDetailSubtitle(runs: List<Run>?): Artist? = ownerFromByline(runs?.splitBySeparator()?.getOrNull(1))

        fun ownerFromByline(runs: List<Run>?): Artist? =
            PageHelper.extractArtists(runs).firstOrNull()
                ?: runs
                    ?.splitBySeparator()
                    ?.firstOrNull()
                    ?.firstOrNull()
                    ?.takeIf { it.navigationEndpoint == null && it.text.isNotBlank() }
                    ?.let { Artist(it.text.trim(), null) }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            val secondaryLineRuns =
                renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs

            val title =
                renderer.flexColumns
                    .firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null

            if (secondaryLineRuns == null) {
                KLog.w("PlaylistPage", "fromMusicResponsiveListItemRenderer: Song '$title' - NO SECONDARY LINE (flexColumns[1] is null)")
            }

            val artists = PageHelper.extractArtists(secondaryLineRuns)

            return SongItem(
                id = renderer.videoId ?: return null,
                title = title,
                artists = artists,
                album =
                    renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
                        )
                    },
                duration =
                    renderer.fixedColumns
                        ?.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                        ?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                endpoint =
                    renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.watchEndpoint,
                // Relaxed from `?: return null`: public/editorial/album audio playlists (OLAK5uy_*)
                // and signed-out playlist responses omit playlistSetVideoId, which is only needed
                // to remove a track from a user-owned playlist.
                setVideoId = renderer.playlistSetVideoId,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                isEpisode = renderer.isEpisode,
            )
        }
    }
}
