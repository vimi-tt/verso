/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt
 */

package com.vimi.k7.innertube.pages

import com.vimi.k7.innertube.models.Album
import com.vimi.k7.innertube.models.AlbumItem
import com.vimi.k7.innertube.models.Artist
import com.vimi.k7.innertube.models.ArtistItem
import com.vimi.k7.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE
import com.vimi.k7.innertube.models.EpisodeItem
import com.vimi.k7.innertube.models.MusicResponsiveListItemRenderer
import com.vimi.k7.innertube.models.PlaylistItem
import com.vimi.k7.innertube.models.PodcastItem
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.models.YTItem
import com.vimi.k7.innertube.models.splitBySeparator
import com.vimi.k7.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(
        renderer: MusicResponsiveListItemRenderer,
        fallbackArtists: List<Artist> = emptyList(),
    ): YTItem? {
        val secondaryLine =
            renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()
                .orEmpty()
        return when {
            // CRITICAL: Check isEpisode BEFORE isSong — both can match isSong (watchEndpoint or
            // null navigationEndpoint), so episodes must be identified first.
            renderer.isEpisode -> {
                val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

                // The subtitle line structure differs between filtered and unfiltered search:
                //   Unfiltered: ["Episode", "·", "Jan 2025", "·", "Podcast Name", "·", "1:00:00"]
                //     → secondaryLine = [["Episode"], ["Jan 2025"], ["Podcast Name"], ["1:00:00"]]
                //   Filtered:   ["Jan 2025", "·", "Podcast Name"]
                //     → secondaryLine = [["Jan 2025"], ["Podcast Name"]]
                //
                // Strategy: locate the podcast section by its PODCAST_SHOW_DETAIL_PAGE link;
                // the date is in the section immediately before it.
                val podcastSectionIndex = secondaryLine.indexOfFirst { section ->
                    section.any { run ->
                        run.navigationEndpoint?.browseEndpoint
                            ?.browseEndpointContextSupportedConfigs
                            ?.browseEndpointContextMusicConfig
                            ?.pageType == MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE
                    }
                }

                val podcast = if (podcastSectionIndex >= 0) {
                    secondaryLine[podcastSectionIndex].firstOrNull()?.let { run ->
                        Album(
                            name = run.text,
                            id = run.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                        )
                    }
                } else null

                val publishDateText = if (podcastSectionIndex > 0)
                    secondaryLine.getOrNull(podcastSectionIndex - 1)?.firstOrNull()?.text
                else null

                EpisodeItem(
                    id = renderer.playlistItemData?.videoId
                        ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                            ?.content?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.flexColumns.firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()
                            ?.navigationEndpoint?.watchEndpoint?.videoId
                        ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author = null,
                    podcast = podcast,
                    duration =
                        secondaryLine
                            .lastOrNull()
                            ?.firstOrNull()
                            ?.text
                            ?.parseTime(),
                    publishDateText = publishDateText,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    // In filtered search the overlay play button may be absent; fall back to the
                    // item's own watchEndpoint so the episode is always playable.
                    endpoint = renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.watchEndpoint
                        ?: renderer.navigationEndpoint?.watchEndpoint,
                    libraryAddToken = libraryTokens.addToken,
                    libraryRemoveToken = libraryTokens.removeToken,
                )
            }
            renderer.isSong -> {
                val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)
                val metadataRuns = renderer.flexColumns
                    .drop(1)
                    .flatMap { it.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty() }
                val artists = PageHelper.extractArtists(metadataRuns).ifEmpty { fallbackArtists }
                val albumRun = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ALBUM").firstOrNull()

                SongItem(
                    id = renderer.playlistItemData?.videoId
                        ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                            ?.content?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.flexColumns.firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()
                            ?.navigationEndpoint?.watchEndpoint?.videoId
                        ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    artists = artists,
                    album =
                        albumRun?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
                            )
                        },
                    duration = PageHelper.extractDuration(metadataRuns),
                    musicVideoType = renderer.musicVideoType,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    libraryAddToken = libraryTokens.addToken,
                    libraryRemoveToken = libraryTokens.removeToken,
                    isEpisode = renderer.isEpisode
                )
            }
            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: return null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    // Relaxed from `?: return null`: some artists (small/indie channels or regional
                    // restrictions) do not expose MUSIC_SHUFFLE or MIX menu items in search results,
                    // and Koda's ArtistItem only needs id, name, and thumbnailUrl.
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    subscriberCountText =
                        secondaryLine
                            .getOrNull(1)
                            ?.firstOrNull()
                            ?.text
                            ?.takeIf { it.isNotBlank() },
                )
            }
            renderer.isUserChannel -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: return null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint = renderer.menu
                        ?.menuRenderer
                        ?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.menuNavigationItemRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint,
                    radioEndpoint = renderer.menu
                        ?.menuRenderer
                        ?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                        ?.menuNavigationItemRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint,
                    isProfile = true,
                    subscriberCountText =
                        secondaryLine
                            .getOrNull(1)
                            ?.firstOrNull()
                            ?.text
                            ?.takeIf { it.isNotBlank() },
                )
            }
            renderer.isAlbum -> {
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    // Relaxed from `?: return null`: Koda's PlaylistDisplayItem addresses albums by
                    // `https://music.youtube.com/browse/$browseId`, so an album row without an
                    // overlay play button playlistId is still valid in search results.
                    playlistId =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.anyWatchEndpoint
                            ?.playlistId
                            .orEmpty(),
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    artists = PageHelper.extractArtists(secondaryLine.getOrNull(1)),
                    year =
                        secondaryLine
                            .getOrNull(2)
                            ?.firstOrNull()
                            ?.text
                            ?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    releaseTypeText =
                        secondaryLine
                            .getOrNull(0)
                            ?.firstOrNull()
                            ?.text
                            ?.takeIf { it.isNotBlank() },
                )
            }
            renderer.isPlaylist -> {
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.removePrefix("VL") ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author =
                        PageHelper
                            .extractArtists(
                                renderer.flexColumns
                                    .drop(1)
                                    .flatMap {
                                        it.musicResponsiveListItemFlexColumnRenderer.text
                                            ?.runs
                                            .orEmpty()
                                    },
                            ).firstOrNull() ?: PlaylistPage.ownerFromByline(secondaryLine.dropLast(1).lastOrNull()),
                    songCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.lastOrNull()
                            ?.text,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    // Relaxed from `?: return null`: community/editorial playlists in search may omit
                    // overlay playEndpoint, MUSIC_SHUFFLE, or MIX menu items, and Koda's
                    // PlaylistDisplayItem only requires id, name, url, uploaderName, and thumbnailUrl.
                    playEndpoint =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint,
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }
            renderer.isPodcast -> {
                PodcastItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author =
                        PageHelper.extractArtists(
                            renderer.flexColumns
                                .drop(1)
                                .flatMap { it.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty() },
                        ).firstOrNull(),
                    episodeCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.lastOrNull()
                            ?.text,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    playEndpoint =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint,
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }
            else -> null
        }
    }
}
