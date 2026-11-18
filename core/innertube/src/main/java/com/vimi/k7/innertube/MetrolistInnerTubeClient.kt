/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported and adapted from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt
 */

package com.vimi.k7.innertube

import com.vimi.k7.innertube.models.AlbumItem
import com.vimi.k7.innertube.models.Artist
import com.vimi.k7.innertube.models.ArtistItem
import com.vimi.k7.innertube.models.BrowseEndpoint
import com.vimi.k7.innertube.models.MusicResponsiveListItemRenderer
import com.vimi.k7.innertube.models.MusicShelfRenderer
import com.vimi.k7.innertube.models.MusicTwoRowItemRenderer
import com.vimi.k7.innertube.models.PlaylistItem
import com.vimi.k7.innertube.models.Run
import com.vimi.k7.innertube.models.SearchSuggestions
import com.vimi.k7.innertube.models.SectionListRenderer
import com.vimi.k7.innertube.models.SongItem
import com.vimi.k7.innertube.models.WatchEndpoint
import com.vimi.k7.innertube.models.YTItem
import com.vimi.k7.innertube.models.getContinuation
import com.vimi.k7.innertube.models.getItems
import com.vimi.k7.innertube.models.response.BrowseResponse
import com.vimi.k7.innertube.models.response.GetSearchSuggestionsResponse
import com.vimi.k7.innertube.models.response.NextResponse
import com.vimi.k7.innertube.models.response.SearchResponse
import com.vimi.k7.innertube.pages.AlbumPage
import com.vimi.k7.innertube.pages.ArtistItemsContinuationPage
import com.vimi.k7.innertube.pages.ArtistItemsPage
import com.vimi.k7.innertube.pages.ArtistPage
import com.vimi.k7.innertube.pages.ChartsPage
import com.vimi.k7.innertube.pages.NewReleaseAlbumPage
import com.vimi.k7.innertube.pages.NextPage
import com.vimi.k7.innertube.pages.NextResult
import com.vimi.k7.innertube.pages.PageHelper
import com.vimi.k7.innertube.pages.PlaylistContinuationPage
import com.vimi.k7.innertube.pages.PlaylistPage
import com.vimi.k7.innertube.pages.SearchPage
import com.vimi.k7.innertube.pages.SearchResult
import com.vimi.k7.innertube.pages.SearchSuggestionPage
import com.vimi.k7.innertube.auth.YouTubeRateLimit
import com.vimi.k7.innertube.transport.MusicApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Transport and decoding layer for Metrolist's InnerTube models and page parsers
 * built directly on top of Koda's [MusicApi] / [com.vimi.k7.innertube.transport.YouTubeHttp].
 *
 * Reusing [MusicApi.postMusicMetadata] and [MusicApi.searchSuggestionsMusic] preserves:
 * - per-profile `visitorData` (`VisitorIdentity`)
 * - `YouTubeRateLimit` and `YouTubeRequestLedger`
 * - Local-Only mode kill-switch
 * - `SessionRefreshInterceptor` cookie rotation (`YouTubeSession` tag)
 * - `YouTubeHttp.noteSessionState`
 */
open class MetrolistInnerTubeClient(
    val musicApi: MusicApi?,
    private val json: Json = defaultJson,
) {

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
            val FILTER_PODCAST = SearchFilter("EgWKAQJQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_EPISODE = SearchFilter("EgWKAQJYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_PROFILE = SearchFilter("EgWKAQJYAWoSEAUQCRADEAQQEBAVEAoQDhAR")
        }
    }

    private inline fun <reified T> decodeFromJsonObject(root: JSONObject?): T? =
        runCatching {
            val nonNullRoot = root ?: return null
            json.decodeFromString<T>(nonNullRoot.toString())
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    private inline fun <reified T> postAndDecode(endpoint: String, payload: JSONObject): T? =
        runCatching {
            val api = musicApi ?: return null
            val body =
                api.postMusicMetadataString(endpoint, payload) { code, retryAfter ->
                    YouTubeRateLimit.note(code, "music $endpoint", retryAfter)
                } ?: return null
            json.decodeFromString<T>(body)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun search(query: String, filter: SearchFilter? = null): SearchResult? =
        runCatching {
            val payload = JSONObject().put("query", query).apply {
                if (filter != null) put("params", filter.value)
            }
            val response = postAndDecode<SearchResponse>("search", payload) ?: return null
            extractSearchResult(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun searchContinuation(continuation: String): SearchResult? =
        runCatching {
            val payload = JSONObject().put("continuation", continuation)
            val response = postAndDecode<SearchResponse>("search", payload) ?: return null
            extractSearchContinuationResult(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun searchSuggestions(query: String): SearchSuggestions? =
        runCatching {
            val api = musicApi ?: return null
            val body =
                api.searchSuggestionsMusicString(query) { code, retryAfter ->
                    YouTubeRateLimit.note(code, "music music/get_search_suggestions", retryAfter)
                } ?: return null
            val response = json.decodeFromString<GetSearchSuggestionsResponse>(body)
            extractSearchSuggestions(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): BrowseResponse? =
        runCatching {
            val payload = JSONObject().apply {
                if (browseId != null) put("browseId", browseId)
                if (params != null) put("params", params)
                if (continuation != null) put("continuation", continuation)
            }
            postAndDecode<BrowseResponse>("browse", payload)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun album(browseId: String, withSongs: Boolean = true): AlbumPage? =
        runCatching {
            val response = browse(browseId = browseId) ?: return null
            val initialPage = extractAlbumPage(browseId, response) ?: return null
            if (!withSongs) {
                return initialPage
            }
            val needsAudioPlaylist =
                initialPage.songs.isEmpty() || hasVideoVersions(initialPage.songs)
            if (!needsAudioPlaylist || initialPage.album.playlistId.isBlank()) {
                return initialPage
            }
            val audioSongs = albumSongs(initialPage.album.playlistId, initialPage.album)
            val finalSongs =
                if (initialPage.songs.isEmpty()) {
                    audioSongs
                } else {
                    withAudioSongVersions(initialPage.songs, audioSongs)
                }
            val resolvedAlbum = resolveAlbumPerformer(initialPage.album, finalSongs)
            initialPage.copy(
                album = resolvedAlbum,
                songs = finalSongs,
            )
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun albumSongs(playlistId: String, album: AlbumItem? = null): List<SongItem> =
        runCatching {
            val cleanId = playlistId.removePrefix("VL")
            val firstResponse = browse(browseId = "VL$cleanId") ?: return emptyList()
            val (firstSongs, firstContinuation) = extractAlbumSongsInitial(firstResponse, album)
            val songs = firstSongs.toMutableList()
            var continuation = firstContinuation
            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            val maxRequests = 10

            while (continuation != null && requestCount < maxRequests) {
                if (!seenContinuations.add(continuation)) break
                requestCount++
                val contResponse = browse(continuation = continuation) ?: break
                val (moreSongs, nextCont) = extractAlbumSongsContinuation(contResponse, album)
                songs += moreSongs
                continuation = nextCont
            }
            songs
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())

    open fun artist(browseId: String): ArtistPage? =
        runCatching {
            val response = browse(browseId = browseId) ?: return null
            extractArtistPage(browseId, response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun artistItems(endpoint: BrowseEndpoint): ArtistItemsPage? =
        runCatching {
            val response = browse(browseId = endpoint.browseId, params = endpoint.params) ?: return null
            extractArtistItemsPage(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun artistItemsContinuation(continuation: String): ArtistItemsContinuationPage? =
        runCatching {
            val response = browse(continuation = continuation) ?: return null
            extractArtistItemsContinuationPage(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    fun playlist(playlistId: String): PlaylistPage? =
        runCatching {
            val cleanId = playlistId.removePrefix("VL")
            val response = browse(browseId = "VL$cleanId") ?: return null
            extractPlaylistPage(cleanId, response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    fun playlistContinuation(continuation: String): PlaylistContinuationPage? =
        runCatching {
            val response = browse(continuation = continuation) ?: return null
            extractPlaylistContinuationPage(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun charts(country: String? = null, continuation: String? = null): ChartsPage? =
        runCatching {
            val payload = JSONObject().apply {
                put("browseId", "FEmusic_charts")
                if (!country.isNullOrBlank()) {
                    put("formData", JSONObject().put("selectedValues", JSONArray().put(country)))
                }
                if (continuation != null) {
                    put("continuation", continuation)
                }
            }
            val response = postAndDecode<BrowseResponse>("browse", payload) ?: return null
            extractChartsPage(response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    open fun next(endpoint: WatchEndpoint, continuation: String? = null): NextResult? =
        runCatching {
            val payload = JSONObject().apply {
                endpoint.videoId?.let { put("videoId", it) }
                endpoint.playlistId?.let { put("playlistId", it) }
                endpoint.playlistSetVideoId?.let { put("playlistSetVideoId", it) }
                endpoint.index?.let { put("index", it) }
                endpoint.params?.let { put("params", it) }
                continuation?.let { put("continuation", it) }
            }
            val response = postAndDecode<NextResponse>("next", payload) ?: return null
            extractNextResult(endpoint, response)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }

        fun parseSearchResponse(rawJson: String, json: Json = defaultJson): SearchResult? =
            runCatching {
                val response = json.decodeFromString<SearchResponse>(rawJson)
                extractSearchResult(response)
            }.getOrNull()

        fun parseSearchContinuationResponse(rawJson: String, json: Json = defaultJson): SearchResult? =
            runCatching {
                val response = json.decodeFromString<SearchResponse>(rawJson)
                extractSearchContinuationResult(response)
            }.getOrNull()

        fun parseSearchSuggestionsResponse(rawJson: String, json: Json = defaultJson): SearchSuggestions? =
            runCatching {
                val response = json.decodeFromString<GetSearchSuggestionsResponse>(rawJson)
                extractSearchSuggestions(response)
            }.getOrNull()

        fun parseAlbumBrowseResponse(browseId: String, rawJson: String, json: Json = defaultJson): AlbumPage? =
            runCatching {
                val response = json.decodeFromString<BrowseResponse>(rawJson)
                extractAlbumPage(browseId, response)
            }.getOrNull()

        fun parseArtistBrowseResponse(browseId: String, rawJson: String, json: Json = defaultJson): ArtistPage? =
            runCatching {
                val response = json.decodeFromString<BrowseResponse>(rawJson)
                extractArtistPage(browseId, response)
            }.getOrNull()

        fun parseChartsBrowseResponse(rawJson: String, json: Json = defaultJson): ChartsPage? =
            runCatching {
                val response = json.decodeFromString<BrowseResponse>(rawJson)
                extractChartsPage(response)
            }.getOrNull()

        fun parsePlaylistBrowseResponse(playlistId: String, rawJson: String, json: Json = defaultJson): PlaylistPage? =
            runCatching {
                val response = json.decodeFromString<BrowseResponse>(rawJson)
                extractPlaylistPage(playlistId.removePrefix("VL"), response)
            }.getOrNull()

        fun parseNextResponse(endpoint: WatchEndpoint, rawJson: String, json: Json = defaultJson): NextResult? =
            runCatching {
                val response = json.decodeFromString<NextResponse>(rawJson)
                extractNextResult(endpoint, response)
            }.getOrNull()

        internal fun extractSearchResult(response: SearchResponse): SearchResult {
            val searchItems = mutableListOf<YTItem>()
            var searchContinuation: String? = null

            response.contents
                ?.tabbedSearchResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.forEach { section ->
                    if (section.musicShelfRenderer != null) {
                        val shelf = section.musicShelfRenderer
                        val items = shelf.contents?.getItems()?.mapNotNull { SearchPage.toYTItem(it) } ?: emptyList()
                        searchItems.addAll(items)
                        if (searchContinuation == null) {
                            searchContinuation = shelf.continuations?.getContinuation()
                        }
                    } else if (section.itemSectionRenderer != null) {
                        val items =
                            section.itemSectionRenderer.contents
                                ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                ?.mapNotNull { SearchPage.toYTItem(it) }
                                ?: emptyList()
                        searchItems.addAll(items)
                    }
                }

            return SearchResult(
                items = searchItems.distinctBy { it.id },
                continuation = searchContinuation,
            )
        }

        internal fun extractSearchContinuationResult(response: SearchResponse): SearchResult {
            val items =
                response.continuationContents
                    ?.musicShelfContinuation
                    ?.contents
                    ?.mapNotNull { SearchPage.toYTItem(it.musicResponsiveListItemRenderer) }
                    ?: emptyList()
            return SearchResult(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        response.continuationContents
                            ?.musicShelfContinuation
                            ?.continuations
                            ?.getContinuation()
                    },
            )
        }

        internal fun extractSearchSuggestions(response: GetSearchSuggestionsResponse): SearchSuggestions =
            SearchSuggestions(
                queries =
                    response.contents
                        ?.getOrNull(0)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            content.searchSuggestionRenderer
                                ?.suggestion
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                        }.orEmpty(),
                recommendedItems =
                    response.contents
                        ?.getOrNull(1)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }.orEmpty(),
            )

        private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")

        internal fun hasVideoVersions(songs: List<SongItem>): Boolean =
            songs.any {
                it.musicVideoType != null &&
                    it.musicVideoType != WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV
            }

        /**
         * Replicates [com.vimi.k7.innertube.model.MusicMetadata.withSongVersions]:
         * replaces each track's `id` and `duration` with the audio version from the album's
         * `OLAK5uy_...` playlist while preserving the album track's existing `thumbnail`
         * (544x544 album header artwork) unless blank.
         */
        internal fun withAudioSongVersions(
            albumSongs: List<SongItem>,
            audioSongs: List<SongItem>,
        ): List<SongItem> {
            if (audioSongs.isEmpty()) return albumSongs
            fun key(title: String) = title.trim().lowercase()
            val unused = audioSongs.toMutableList()
            return albumSongs.mapIndexed { index, track ->
                val match =
                    audioSongs.getOrNull(index)?.takeIf { it in unused && key(it.title) == key(track.title) }
                        ?: unused.firstOrNull { key(it.title) == key(track.title) }
                        ?: return@mapIndexed track
                unused.remove(match)
                track.copy(
                    id = match.id,
                    duration = match.duration?.takeIf { it > 0 } ?: track.duration,
                    thumbnail = track.thumbnail.takeIf { it.isNotBlank() } ?: match.thumbnail,
                    musicVideoType = match.musicVideoType ?: track.musicVideoType,
                )
            }
        }

        internal fun extractAlbumPage(browseId: String, response: BrowseResponse): AlbumPage? {
            val responsiveHeader =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveHeaderRenderer

            if (responsiveHeader != null) {
                val buttonPlaylistId =
                    responsiveHeader.buttons
                        .firstNotNullOfOrNull {
                            it.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId
                                ?: it.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchEndpoint
                                    ?.playlistId
                        }
                val microformatPlaylistId =
                    response.microformat
                        ?.microformatDataRenderer
                        ?.urlCanonical
                        ?.substringAfterLast('=', "")
                        ?.takeIf { it.isNotBlank() }
                val playlistId =
                    listOfNotNull(buttonPlaylistId, microformatPlaylistId)
                        .firstOrNull { it.startsWith("OLAK5uy_") }
                        ?: buttonPlaylistId
                        ?: microformatPlaylistId
                        .orEmpty()

                val title = responsiveHeader.title.runs?.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: return null
                val thumbnail =
                    responsiveHeader.thumbnail
                        ?.getThumbnailUrl()
                        .orEmpty()

                val year =
                    responsiveHeader.subtitle.runs?.firstNotNullOfOrNull { run ->
                        if (run.navigationEndpoint != null) return@firstNotNullOfOrNull null
                        run.text.trim().takeIf { it.matches(YEAR_REGEX) }?.toIntOrNull()
                    } ?: responsiveHeader.subtitle.runs?.lastOrNull()?.text?.toIntOrNull()

                val releaseTypeText =
                    responsiveHeader.subtitle.runs
                        ?.firstOrNull { run ->
                            run.navigationEndpoint == null &&
                                com.vimi.k7.innertube.model.MusicReleaseType.entries.any {
                                    it.wireLabel.equals(run.text.trim(), ignoreCase = true)
                                }
                        }?.text
                        ?: responsiveHeader.subtitle.runs?.firstOrNull()?.text?.takeIf { it.isNotBlank() }

                val albumItem =
                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title = title,
                        artists =
                            responsiveHeader.straplineTextOne
                                ?.runs
                                ?.let(PageHelper::extractArtists)
                                ?.takeIf { it.isNotEmpty() }
                                ?: PageHelper.extractArtists(responsiveHeader.subtitle.runs).takeIf { it.isNotEmpty() },
                        year = year,
                        thumbnail = thumbnail,
                        explicit = false,
                        releaseTypeText = releaseTypeText,
                    )

                val (inlineSongs, _) = extractAlbumSongsInitial(response, albumItem)
                val resolvedAlbum = resolveAlbumPerformer(albumItem, inlineSongs)

                val otherVersions =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.getOrNull(1)
                        ?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        .orEmpty()

                return AlbumPage(
                    album = resolvedAlbum,
                    songs = inlineSongs,
                    otherVersions = otherVersions,
                )
            }

            // Old header layout (`header.musicDetailHeaderRenderer` + `singleColumnBrowseResultsRenderer`)
            val detailHeader =
                response.header?.musicDetailHeaderRenderer
                    ?: response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer
                    ?: return null

            val menuPlaylistId =
                detailHeader.menu
                    ?.menuRenderer
                    ?.topLevelButtons
                    ?.firstNotNullOfOrNull {
                        it.buttonRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint
                            ?.playlistId
                            ?: it.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint
                                ?.playlistId
                    }
            val microformatPlaylistId =
                response.microformat
                    ?.microformatDataRenderer
                    ?.urlCanonical
                    ?.substringAfterLast('=', "")
                    ?.takeIf { it.isNotBlank() }
            val playlistId =
                listOfNotNull(menuPlaylistId, microformatPlaylistId)
                    .firstOrNull { it.startsWith("OLAK5uy_") }
                    ?: menuPlaylistId
                    ?: microformatPlaylistId
                    .orEmpty()

            val title = detailHeader.title.runs?.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: return null
            val thumbnail =
                detailHeader.thumbnail
                    ?.getThumbnailUrl()
                    .orEmpty()

            val year =
                detailHeader.subtitle.runs?.firstNotNullOfOrNull { run ->
                    if (run.navigationEndpoint != null) return@firstNotNullOfOrNull null
                    run.text.trim().takeIf { it.matches(YEAR_REGEX) }?.toIntOrNull()
                } ?: detailHeader.subtitle.runs?.lastOrNull()?.text?.toIntOrNull()

            val releaseTypeText =
                detailHeader.subtitle.runs
                    ?.firstOrNull { run ->
                        run.navigationEndpoint == null &&
                            com.vimi.k7.innertube.model.MusicReleaseType.entries.any {
                                it.wireLabel.equals(run.text.trim(), ignoreCase = true)
                            }
                    }?.text
                    ?: detailHeader.subtitle.runs?.firstOrNull()?.text?.takeIf { it.isNotBlank() }

            val albumItem =
                AlbumItem(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = title,
                    artists = PageHelper.extractArtists(detailHeader.subtitle.runs).takeIf { it.isNotEmpty() },
                    year = year,
                    thumbnail = thumbnail,
                    explicit = false,
                    releaseTypeText = releaseTypeText,
                )

            val trackSection =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull { section ->
                        (section.musicShelfRenderer?.contents ?: section.musicPlaylistShelfRenderer?.contents)?.isNotEmpty() == true
                    }

            val singleColumnSongs =
                (trackSection?.musicShelfRenderer?.contents ?: trackSection?.musicPlaylistShelfRenderer?.contents)
                    .orEmpty()
                    .getItems()
                    .mapNotNull { AlbumPage.getSong(it, albumItem) }

            val songs = singleColumnSongs.ifEmpty { extractAlbumSongsInitial(response, albumItem).first }
            val resolvedAlbum = resolveAlbumPerformer(albumItem, songs)
            return AlbumPage(
                album = resolvedAlbum,
                songs = songs,
                otherVersions = emptyList(),
            )
        }

        internal fun resolveAlbumPerformer(albumItem: AlbumItem, songs: List<SongItem>): AlbumItem {
            val performer =
                songs.firstOrNull()?.artists?.firstOrNull()?.takeIf { first ->
                    first.name.isNotBlank() &&
                        songs.all { it.artists.firstOrNull()?.name == first.name }
                }
            return if (performer != null && albumItem.artists?.any { it.name == performer.name } != true) {
                albumItem.copy(artists = listOf(performer))
            } else {
                albumItem
            }
        }

        internal fun extractAlbumSongsInitial(
            response: BrowseResponse,
            album: AlbumItem? = null,
        ): Pair<List<SongItem>, String?> {
            val shelf =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.secondaryContents
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
            val shelfContents =
                shelf?.musicPlaylistShelfRenderer?.contents
                    ?: shelf?.musicShelfRenderer?.contents
                    ?: emptyList()
            val songs =
                shelfContents
                    .getItems()
                    .mapNotNull { AlbumPage.getSong(it, album) }
            val continuation =
                shelfContents.getContinuation()
                    ?: shelf?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
                    ?: shelf?.musicShelfRenderer?.continuations?.getContinuation()
            return songs to continuation
        }

        internal fun extractAlbumSongsContinuation(
            response: BrowseResponse,
            album: AlbumItem? = null,
        ): Pair<List<SongItem>, String?> {
            val songs =
                response.onResponseReceivedActions
                    ?.firstOrNull()
                    ?.appendContinuationItemsAction
                    ?.continuationItems
                    ?.getItems()
                    ?.mapNotNull { AlbumPage.getSong(it, album) }
                    .orEmpty()
            val continuation =
                response.continuationContents
                    ?.musicPlaylistShelfContinuation
                    ?.continuations
                    ?.getContinuation()
            return songs to continuation
        }

        internal fun extractArtistPage(browseId: String, response: BrowseResponse): ArtistPage? {
            fun mapRuns(runs: List<Run>?): List<Run>? =
                runs?.map { run ->
                    Run(
                        text = run.text,
                        navigationEndpoint = run.navigationEndpoint,
                    )
                }

            val descriptionRuns =
                response.contents
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
                    ?.musicDescriptionShelfRenderer
                    ?.description
                    ?.runs
                    ?.let(::mapRuns)
                    ?: response.header
                        ?.musicImmersiveHeaderRenderer
                        ?.description
                        ?.runs
                        ?.let(::mapRuns)

            val immersiveSubscribed =
                response.header
                    ?.musicImmersiveHeaderRenderer
                    ?.subscriptionButton
                    ?.subscribeButtonRenderer
                    ?.subscribed
            val visualSubscribed =
                response.header
                    ?.musicVisualHeaderRenderer
                    ?.subscriptionButton
                    ?.subscribeButtonRenderer
                    ?.subscribed
            val isSubscribed = immersiveSubscribed ?: visualSubscribed ?: false

            val channelIdFromVisual =
                response.header
                    ?.musicVisualHeaderRenderer
                    ?.subscriptionButton
                    ?.subscribeButtonRenderer
                    ?.channelId

            val title =
                response.header
                    ?.musicImmersiveHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?: response.header
                        ?.musicVisualHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                    ?: response.header
                        ?.musicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                    ?: return null

            val sections =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.mapNotNull(ArtistPage::fromSectionListRendererContent)
                    ?: emptyList()

            return ArtistPage(
                artist =
                    ArtistItem(
                        id = browseId,
                        title = title,
                        thumbnail =
                            response.header
                                ?.musicImmersiveHeaderRenderer
                                ?.thumbnail
                                ?.musicThumbnailRenderer
                                ?.getThumbnailUrl()
                                ?: response.header
                                    ?.musicVisualHeaderRenderer
                                    ?.foregroundThumbnail
                                    ?.musicThumbnailRenderer
                                    ?.getThumbnailUrl()
                                ?: response.header
                                    ?.musicDetailHeaderRenderer
                                    ?.thumbnail
                                    ?.musicThumbnailRenderer
                                    ?.getThumbnailUrl(),
                        channelId =
                            response.header
                                ?.musicImmersiveHeaderRenderer
                                ?.subscriptionButton
                                ?.subscribeButtonRenderer
                                ?.channelId
                                ?: channelIdFromVisual,
                        playEndpoint =
                            response.contents
                                ?.singleColumnBrowseResultsRenderer
                                ?.tabs
                                ?.firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicShelfRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveListItemRenderer
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint,
                        shuffleEndpoint =
                            response.header
                                ?.musicImmersiveHeaderRenderer
                                ?.playButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint
                                ?: response.contents
                                    ?.singleColumnBrowseResultsRenderer
                                    ?.tabs
                                    ?.firstOrNull()
                                    ?.tabRenderer
                                    ?.content
                                    ?.sectionListRenderer
                                    ?.contents
                                    ?.firstOrNull()
                                    ?.musicShelfRenderer
                                    ?.contents
                                    ?.firstOrNull()
                                    ?.musicResponsiveListItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            response.header
                                ?.musicImmersiveHeaderRenderer
                                ?.startRadioButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint,
                    ),
                sections = sections,
                description = descriptionRuns?.joinToString(separator = "") { it.text },
                subscriberCountText =
                    response.header
                        ?.musicImmersiveHeaderRenderer
                        ?.subscriptionButton2
                        ?.subscribeButtonRenderer
                        ?.subscriberCountWithSubscribeText
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                        ?: response.header
                            ?.musicImmersiveHeaderRenderer
                            ?.subscriptionButton
                            ?.subscribeButtonRenderer
                            ?.longSubscriberCountText
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                        ?: response.header
                            ?.musicImmersiveHeaderRenderer
                            ?.subscriptionButton
                            ?.subscribeButtonRenderer
                            ?.shortSubscriberCountText
                            ?.runs
                            ?.firstOrNull()
                            ?.text,
                monthlyListenerCount =
                    response.header
                        ?.musicImmersiveHeaderRenderer
                        ?.monthlyListenerCount
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                descriptionRuns = descriptionRuns,
                isSubscribed = isSubscribed,
            )
        }

        internal fun extractArtistItemsPage(response: BrowseResponse): ArtistItemsPage {
            val sectionContent =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()

            val gridRenderer = sectionContent?.gridRenderer
            val musicCarouselShelfRenderer = sectionContent?.musicCarouselShelfRenderer
            val musicPlaylistShelfRenderer = sectionContent?.musicPlaylistShelfRenderer
            val musicShelfRenderer = sectionContent?.musicShelfRenderer

            return when {
                gridRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            gridRenderer.header
                                ?.gridHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                .orEmpty(),
                        items =
                            gridRenderer.items.mapNotNull {
                                it.musicTwoRowItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                                }
                            },
                        continuation = gridRenderer.continuations?.getContinuation(),
                    )
                }

                musicCarouselShelfRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            musicCarouselShelfRenderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                .orEmpty(),
                        items =
                            musicCarouselShelfRenderer.contents.mapNotNull { content ->
                                content.musicTwoRowItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                                } ?: content.musicResponsiveListItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(renderer)
                                }
                            },
                        continuation = null,
                    )
                }

                musicShelfRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            musicShelfRenderer.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: response.header
                                    ?.musicHeaderRenderer
                                    ?.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text
                                ?: "",
                        items =
                            musicShelfRenderer.contents?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = musicShelfRenderer.continuations?.getContinuation(),
                    )
                }

                else -> {
                    ArtistItemsPage(
                        title =
                            response.header
                                ?.musicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text ?: "",
                        items =
                            musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = musicPlaylistShelfRenderer?.contents?.getContinuation(),
                    )
                }
            }
        }

        internal fun extractArtistItemsContinuationPage(response: BrowseResponse): ArtistItemsContinuationPage =
            when {
                response.continuationContents?.gridContinuation != null -> {
                    val gridContinuation = response.continuationContents.gridContinuation
                    val items =
                        gridContinuation.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer ->
                                ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                            }
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else gridContinuation.continuations?.getContinuation(),
                    )
                }

                response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                    val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                    val items =
                        musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else musicPlaylistShelfContinuation.continuations?.getContinuation(),
                    )
                }

                else -> {
                    val continuationItems =
                        response.onResponseReceivedActions
                            ?.firstOrNull()
                            ?.appendContinuationItemsAction
                            ?.continuationItems
                    val items =
                        continuationItems?.getItems()?.mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        } ?: emptyList()
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else continuationItems?.getContinuation(),
                    )
                }
            }

        internal fun extractPlaylistPage(playlistId: String, response: BrowseResponse): PlaylistPage {
            val base =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
            val header =
                base?.musicResponsiveHeaderRenderer
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

            val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

            val description: String? =
                header?.description?.musicDescriptionShelfRenderer?.description?.runs?.joinToString("") { it.text }
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer
                        ?.header?.musicDetailHeaderRenderer
                        ?.description?.runs?.joinToString("") { it.text }
                    ?: response.header?.musicDetailHeaderRenderer
                        ?.description?.runs?.joinToString("") { it.text }

            val author: Artist? = run {
                val fromStrapline = header?.straplineTextOne?.runs
                    ?.firstOrNull()
                    ?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                if (fromStrapline != null) return@run fromStrapline

                val detailSubtitle =
                    base
                        ?.musicEditablePlaylistDetailHeaderRenderer
                        ?.header
                        ?.musicDetailHeaderRenderer
                        ?.subtitle
                        ?.runs
                        ?: response.header
                            ?.musicDetailHeaderRenderer
                            ?.subtitle
                            ?.runs
                val fromDetail = PageHelper.extractArtists(detailSubtitle).firstOrNull()
                if (fromDetail != null) return@run fromDetail

                val fromHeaderSubtitle = PageHelper.extractArtists(header?.subtitle?.runs).firstOrNull()
                if (fromHeaderSubtitle != null) return@run fromHeaderSubtitle

                val facepile = header?.facepile?.avatarStackViewModel
                if (facepile != null) {
                    val name = facepile.text?.content
                    val browseId = facepile.rendererContext?.commandContext?.onTap?.innertubeCommand?.browseEndpoint?.browseId
                    if (name != null) return@run Artist(name = name, id = browseId)
                }

                val fromMusicHeaderStrapline = response.header?.musicHeaderRenderer
                    ?.straplineTextOne?.runs?.firstOrNull()
                    ?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                if (fromMusicHeaderStrapline != null) return@run fromMusicHeaderStrapline

                PlaylistPage.ownerFromDetailSubtitle(detailSubtitle)
            }

            val authorAvatarUrl: String? = header?.facepile
                ?.avatarStackViewModel?.avatars?.firstOrNull()
                ?.avatarViewModel?.image?.sources?.firstOrNull()
                ?.url

            return PlaylistPage(
                playlist =
                    PlaylistItem(
                        id = playlistId,
                        title =
                            header?.title?.runs?.firstOrNull()?.text
                                ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                                ?: "",
                        author = author,
                        songCountText =
                            (header?.secondSubtitle
                                ?: response.header?.musicHeaderRenderer?.secondSubtitle)
                                ?.runs
                                ?.findLast {
                                    it.text.any { c -> c.isDigit() } &&
                                        !it.text.contains("view", ignoreCase = true) &&
                                        !it.text.contains("hour", ignoreCase = true) &&
                                        !it.text.contains("minute", ignoreCase = true)
                                }?.text,
                        thumbnail =
                            header?.thumbnail
                                ?.musicThumbnailRenderer
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()
                                ?.url
                                ?: response.header
                                    ?.musicHeaderRenderer
                                    ?.thumbnail
                                    ?.musicThumbnailRenderer
                                    ?.thumbnails
                                    ?.lastOrNull()
                                    ?.url
                                ?: "",
                        playEndpoint = null,
                        shuffleEndpoint =
                            header?.buttons?.lastOrNull()
                                ?.menuRenderer?.items?.firstOrNull()
                                ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                                ?: response.header?.musicHeaderRenderer?.buttons?.lastOrNull()
                                    ?.menuRenderer?.items?.firstOrNull()
                                    ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                        radioEndpoint =
                            header?.buttons?.getOrNull(2)
                                ?.menuRenderer?.items?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                                ?: response.header?.musicHeaderRenderer?.buttons?.getOrNull(2)
                                    ?.menuRenderer?.items?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                    }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                        isEditable = editable,
                        description = description,
                        authorAvatarUrl = authorAvatarUrl,
                    ),
                songs = run {
                    val twoColShelf =
                        response.contents
                            ?.twoColumnBrowseResultsRenderer
                            ?.secondaryContents
                            ?.sectionListRenderer
                            ?.contents
                            ?.firstOrNull()
                    val twoColContents =
                        twoColShelf?.musicPlaylistShelfRenderer?.contents
                            ?: twoColShelf?.musicShelfRenderer?.contents
                    val singleColShelf =
                        response.contents
                            ?.singleColumnBrowseResultsRenderer
                            ?.tabs
                            ?.firstOrNull()
                            ?.tabRenderer
                            ?.content
                            ?.sectionListRenderer
                            ?.contents
                            ?.firstOrNull()
                    val singleColContents =
                        singleColShelf?.musicPlaylistShelfRenderer?.contents
                            ?: singleColShelf?.musicShelfRenderer?.contents
                    (twoColContents ?: singleColContents)
                        ?.getItems()
                        ?.mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }
                        ?: emptyList()
                },
                songsContinuation = run {
                    val twoColShelf =
                        response.contents
                            ?.twoColumnBrowseResultsRenderer
                            ?.secondaryContents
                            ?.sectionListRenderer
                            ?.contents
                            ?.firstOrNull()
                    val twoColContents =
                        twoColShelf?.musicPlaylistShelfRenderer?.contents
                            ?: twoColShelf?.musicShelfRenderer?.contents
                    val twoColContinuations =
                        twoColShelf?.musicPlaylistShelfRenderer?.continuations
                            ?: twoColShelf?.musicShelfRenderer?.continuations
                    val singleColShelf =
                        response.contents
                            ?.singleColumnBrowseResultsRenderer
                            ?.tabs
                            ?.firstOrNull()
                            ?.tabRenderer
                            ?.content
                            ?.sectionListRenderer
                            ?.contents
                            ?.firstOrNull()
                    val singleColContents =
                        singleColShelf?.musicPlaylistShelfRenderer?.contents
                            ?: singleColShelf?.musicShelfRenderer?.contents
                    val singleColContinuations =
                        singleColShelf?.musicPlaylistShelfRenderer?.continuations
                            ?: singleColShelf?.musicShelfRenderer?.continuations
                    val mergedContents = twoColContents ?: singleColContents
                    val mergedContinuations = twoColContinuations ?: singleColContinuations
                    mergedContents?.getContinuation() ?: mergedContinuations?.getContinuation()
                },
                continuation =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.continuations
                        ?.getContinuation(),
            )
        }

        internal fun extractPlaylistContinuationPage(response: BrowseResponse): PlaylistContinuationPage {
            val mainContents: List<MusicShelfRenderer.Content> =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.mapNotNull { content: SectionListRenderer.Content ->
                        content.musicPlaylistShelfRenderer?.contents
                            ?: content.musicShelfRenderer?.contents
                    }
                    ?.flatten()
                    ?: emptyList()

            val shelfContents: List<MusicShelfRenderer.Content> =
                response.continuationContents?.musicPlaylistShelfContinuation?.contents ?: emptyList()

            val musicShelfContinuationContents: List<MusicShelfRenderer.Content> =
                response.continuationContents?.musicShelfContinuation?.contents ?: emptyList()

            val appendedContents: List<MusicShelfRenderer.Content> =
                response.onResponseReceivedActions
                    .orEmpty()
                    .flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }

            val allContents = mainContents + shelfContents + musicShelfContinuationContents + appendedContents

            val songs =
                allContents
                    .mapNotNull { content: MusicShelfRenderer.Content -> content.musicResponsiveListItemRenderer }
                    .mapNotNull { renderer -> PlaylistPage.fromMusicResponsiveListItemRenderer(renderer) }

            val nextContinuation =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.continuations
                    ?.getContinuation()
                    ?: response.continuationContents
                        ?.musicPlaylistShelfContinuation
                        ?.continuations
                        ?.getContinuation()
                    ?: response.continuationContents
                        ?.musicShelfContinuation
                        ?.continuations
                        ?.getContinuation()
                    ?: appendedContents.getContinuation()

            return PlaylistContinuationPage(
                songs = songs,
                continuation = nextContinuation,
            )
        }

        internal fun extractChartsPage(response: BrowseResponse): ChartsPage {
            val sections = mutableListOf<ChartsPage.ChartSection>()

            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.forEach { content ->
                    content.musicCarouselShelfRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@forEach

                        val items =
                            renderer.contents.mapNotNull { item ->
                                when {
                                    item.musicResponsiveListItemRenderer != null ->
                                        convertToChartItem(item.musicResponsiveListItemRenderer)
                                    item.musicTwoRowItemRenderer != null ->
                                        convertMusicTwoRowItem(item.musicTwoRowItemRenderer)
                                    else -> null
                                }
                            }

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = determineChartType(title),
                                ),
                            )
                        }
                    }

                    content.gridRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.gridHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@let

                        val items =
                            renderer.items.mapNotNull { item ->
                                item.musicTwoRowItemRenderer?.let(::convertMusicTwoRowItem)
                            }

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = ChartsPage.ChartType.NEW_RELEASES,
                                ),
                            )
                        }
                    }
                }

            return ChartsPage(
                sections = sections,
                continuation =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation(),
            )
        }

        private fun determineChartType(title: String): ChartsPage.ChartType =
            when {
                title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
                title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
                else -> ChartsPage.ChartType.GENRE
            }

        private fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? =
            runCatching {
                when {
                    renderer.flexColumns.size >= 3 && renderer.videoId != null -> {
                        val firstColumn =
                            renderer.flexColumns
                                .getOrNull(0)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text ?: return null

                        val secondColumn =
                            renderer.flexColumns
                                .getOrNull(1)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text ?: return null

                        val titleRun = firstColumn.runs?.firstOrNull() ?: return null
                        val title = titleRun.text.takeIf { it.isNotBlank() } ?: return null

                        val artists = PageHelper.extractArtists(secondColumn.runs)

                        val thirdColumn =
                            renderer.flexColumns
                                .getOrNull(2)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text

                        SongItem(
                            id = renderer.videoId ?: return null,
                            title = title,
                            artists = artists,
                            thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                            musicVideoType = renderer.musicVideoType,
                            explicit =
                                renderer.badges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                            chartPosition =
                                thirdColumn
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text
                                    ?.toIntOrNull(),
                            chartChange = thirdColumn?.runs?.getOrNull(1)?.text,
                        )
                    }
                    // Koda compatibility: in FEmusic_charts, the "Top artists" carousel uses
                    // musicResponsiveListItemRenderer with browseId starting with "UC",
                    // flexColumns[0] = artist name, flexColumns[1] = subscriber count.
                    renderer.isArtist ||
                        renderer.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true -> {
                        val browseId =
                            renderer.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId
                                ?.takeIf { it.startsWith("UC") } ?: return null
                        val name =
                            renderer.flexColumns
                                .getOrNull(0)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() } ?: return null
                        val subscriberCountText =
                            renderer.flexColumns
                                .getOrNull(1)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        ArtistItem(
                            id = browseId,
                            title = name,
                            thumbnail = renderer.thumbnail?.getThumbnailUrl(),
                            subscriberCountText = subscriberCountText,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                    }
                    else -> null
                }
            }.getOrNull()

        private fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? =
            runCatching {
                when {
                    renderer.isSong -> {
                        val subtitle = renderer.subtitle?.runs ?: return null
                        val title = renderer.title.runs?.firstOrNull()?.text ?: return null
                        val artists = PageHelper.extractArtists(subtitle)
                        val videoId = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null
                        SongItem(
                            id = videoId,
                            title = title,
                            artists = artists,
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                            musicVideoType = renderer.musicVideoType,
                            explicit =
                                renderer.subtitleBadges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                        )
                    }
                    renderer.isAlbum -> {
                        AlbumItem(
                            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            playlistId =
                                renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchPlaylistEndpoint
                                    ?.playlistId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            artists = PageHelper.extractArtists(renderer.subtitle?.runs),
                            year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                            explicit =
                                renderer.subtitleBadges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                        )
                    }
                    renderer.isArtist -> {
                        ArtistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl(),
                            subscriberCountText =
                                renderer.subtitle
                                    ?.runs
                                    ?.joinToString(separator = "") { it.text }
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() },
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                    }
                    else -> null
                }
            }.getOrNull()

        internal fun extractNextResult(endpoint: WatchEndpoint, response: NextResponse): NextResult? {
            val playlistPanelRenderer =
                response.continuationContents?.playlistPanelContinuation
                    ?: response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.musicQueueRenderer
                        ?.content
                        ?.playlistPanelRenderer
                    ?: return null

            val title =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.musicQueueRenderer
                    ?.header
                    ?.musicQueueHeaderRenderer
                    ?.subtitle
                    ?.runs
                    ?.firstOrNull()
                    ?.text

            val items =
                playlistPanelRenderer.contents.mapNotNull { content ->
                    val renderer =
                        content.playlistPanelVideoRenderer
                            ?: content.playlistPanelVideoWrapperRenderer?.let { wrapper ->
                                val candidates =
                                    listOfNotNull(wrapper.primaryRenderer?.playlistPanelVideoRenderer) +
                                        wrapper.counterpart.orEmpty().mapNotNull {
                                            it.counterpartRenderer?.playlistPanelVideoRenderer
                                        }
                                candidates.firstOrNull {
                                    it.navigationEndpoint?.musicVideoType ==
                                        WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV
                                } ?: candidates.firstOrNull()
                            }
                            ?: return@mapNotNull null
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)?.let { it to renderer.selected }
                }
            val songs = items.map { it.first }
            val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

            return NextResult(
                title = title,
                items = songs,
                currentIndex = currentIndex,
                lyricsEndpoint =
                    response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.getOrNull(1)
                        ?.tabRenderer
                        ?.endpoint
                        ?.browseEndpoint,
                relatedEndpoint =
                    response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.getOrNull(2)
                        ?.tabRenderer
                        ?.endpoint
                        ?.browseEndpoint,
                continuation = playlistPanelRenderer.continuations?.getContinuation(),
                endpoint = endpoint,
            )
        }
    }
}
