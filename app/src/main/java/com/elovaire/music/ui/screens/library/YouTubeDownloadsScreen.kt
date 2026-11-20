package elovaire.music.droidbeauty.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadStatus
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadTask
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadsState
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.screens.common.readableMutedIconColor
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

private enum class DownloadsTab {
    Collections,
    Songs,
}

private enum class DownloadsSongFilter {
    Alphabet,
    Album,
    Artist,
}

@Immutable
private data class DerivedDownloadedAlbum(
    val key: String,
    val title: String,
    val subtitle: String,
    val artUri: Uri?,
    val songs: List<Song>,
)

@Immutable
private sealed interface DownloadSongListEntry {
    val key: String

    @Immutable
    data class Header(val title: String, val count: Int) : DownloadSongListEntry {
        override val key: String get() = "dl_hdr_$title"
    }

    @Immutable
    data class Track(val song: Song) : DownloadSongListEntry {
        override val key: String get() = "dl_trk_${song.id}"
    }
}

@Composable
internal fun YouTubeDownloadsScreen(
    downloadsState: YouTubeDownloadsState,
    favoriteSongIds: Set<Long>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, String) -> Unit,
    onOpenCollection: (YouTubeDownloadedCollection) -> Unit = {},
    onCancelDownload: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val language = LocalAppLanguage.current
    val copy = remember(language) { youtubeDownloadCopy(language) }
    val commonCopy = remember(language) { commonUiCopy(language) }
    val listState = rememberElovaireLazyListState("yt_downloads_screen")

    var activeTab by rememberSaveable { mutableStateOf(DownloadsTab.Collections) }
    var songFilter by rememberSaveable { mutableStateOf(DownloadsSongFilter.Alphabet) }
    var selectedLetter by rememberSaveable { mutableStateOf<String?>(null) }
    var showInProgress by rememberSaveable { mutableStateOf(downloadsState.activeDownloads.isNotEmpty()) }

    val unknownAlbumLabel = stringResource(R.string.downloads_unknown_album)
    val unknownArtistLabel = stringResource(R.string.downloads_unknown_artist)

    // Group loose downloaded songs by album for the editorial grid without touching disk or rendering all tracks upfront
    val derivedAlbums = remember(downloadsState.downloadedSongs, downloadsState.downloadedCollections, unknownAlbumLabel, unknownArtistLabel) {
        val collectionSongIds = downloadsState.downloadedCollections
            .flatMap { it.songs }
            .mapTo(HashSet()) { it.id }
        val looseSongs = downloadsState.downloadedSongs.filterNot { it.id in collectionSongIds }
        val sourceSongs = looseSongs.ifEmpty {
            if (downloadsState.downloadedCollections.isEmpty()) downloadsState.downloadedSongs else emptyList()
        }
        sourceSongs
            .groupBy { it.album.trim().ifBlank { unknownAlbumLabel } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (albumTitle, songs) ->
                val primaryArtist = songs.firstOrNull { it.artist.isNotBlank() }?.artist ?: unknownArtistLabel
                DerivedDownloadedAlbum(
                    key = "derived_album_${albumTitle.lowercase()}",
                    title = albumTitle,
                    subtitle = primaryArtist,
                    artUri = songs.firstOrNull { it.artUri != null }?.artUri,
                    songs = songs,
                )
            }
    }

    val savedCollectionRows = remember(downloadsState.downloadedCollections) {
        downloadsState.downloadedCollections.chunked(2)
    }
    val derivedAlbumRows = remember(derivedAlbums) {
        derivedAlbums.chunked(2)
    }

    val availableLetters = remember(downloadsState.downloadedSongs) {
        downloadsState.downloadedSongs
            .map { song ->
                val ch = song.title.trim().firstOrNull()?.uppercaseChar()
                if (ch != null && ch in 'A'..'Z') ch.toString() else "#"
            }
            .distinct()
            .sortedWith(compareBy { if (it == "#") "ZZZ" else it })
    }

    // Build virtualized flat list of headers + songs only when the Songs tab is active
    val songListEntries = remember(
        activeTab,
        downloadsState.downloadedSongs,
        songFilter,
        selectedLetter,
        unknownAlbumLabel,
        unknownArtistLabel,
    ) {
        if (activeTab != DownloadsTab.Songs) return@remember emptyList<DownloadSongListEntry>()
        val baseSongs = downloadsState.downloadedSongs
        when (songFilter) {
            DownloadsSongFilter.Alphabet -> {
                val sorted = baseSongs.sortedBy { it.title.trim().lowercase() }
                val filtered = if (selectedLetter == null) {
                    sorted
                } else {
                    sorted.filter { song ->
                        val ch = song.title.trim().firstOrNull()?.uppercaseChar()
                        val bucket = if (ch != null && ch in 'A'..'Z') ch.toString() else "#"
                        bucket == selectedLetter
                    }
                }
                filtered.map { DownloadSongListEntry.Track(it) }
            }
            DownloadsSongFilter.Album -> {
                baseSongs
                    .groupBy { it.album.trim().ifBlank { unknownAlbumLabel } }
                    .entries
                    .sortedBy { it.key.lowercase() }
                    .flatMap { (album, tracks) ->
                        val ordered = tracks.sortedBy { it.title.trim().lowercase() }
                        buildList {
                            add(DownloadSongListEntry.Header(album, ordered.size))
                            ordered.forEach { add(DownloadSongListEntry.Track(it)) }
                        }
                    }
            }
            DownloadsSongFilter.Artist -> {
                baseSongs
                    .groupBy { it.artist.trim().ifBlank { unknownArtistLabel } }
                    .entries
                    .sortedBy { it.key.lowercase() }
                    .flatMap { (artist, tracks) ->
                        val ordered = tracks.sortedBy { it.title.trim().lowercase() }
                        buildList {
                            add(DownloadSongListEntry.Header(artist, ordered.size))
                            ordered.forEach { add(DownloadSongListEntry.Track(it)) }
                        }
                    }
            }
        }
    }

    val activeQueueForSongs = remember(songListEntries, downloadsState.downloadedSongs) {
        songListEntries.filterIsInstance<DownloadSongListEntry.Track>().map { it.song }
            .ifEmpty { downloadsState.downloadedSongs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(listState),
            contentPadding = PaddingValues(
                top = topBarOccupiedHeight() + 14.dp,
                bottom = bottomPadding + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Editorial header summary & Play / Shuffle actions
            item(key = "dl_header", contentType = "dl_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloads_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = readableSecondaryTextColor(),
                    )
                    if (downloadsState.downloadedSongs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EditorialActionPill(
                                label = stringResource(R.string.editorial_home_play),
                                iconResId = R.drawable.ic_lucide_play,
                                primary = true,
                                onClick = {
                                    val first = downloadsState.downloadedSongs.firstOrNull() ?: return@EditorialActionPill
                                    onPlaySong(first, downloadsState.downloadedSongs, copy.downloadsTitle)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            EditorialActionPill(
                                label = stringResource(R.string.downloads_shuffle),
                                iconResId = R.drawable.ic_lucide_shuffle,
                                primary = false,
                                onClick = {
                                    val shuffled = downloadsState.downloadedSongs.shuffled()
                                    val first = shuffled.firstOrNull() ?: return@EditorialActionPill
                                    onPlaySong(first, shuffled, copy.downloadsTitle)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Active downloads banner (compact, shown when there are active tasks)
            if (downloadsState.activeDownloads.isNotEmpty()) {
                item(key = "dl_active_banner", contentType = "dl_active_banner") {
                    Column(
                        modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModuleCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .elovaireClickable { showInProgress = !showInProgress },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lucide_download),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = copy.downloadsInProgress,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = localizedCountLabel(downloadsState.activeDownloads.size, "song", language),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = readableSecondaryTextColor(),
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lucide_chevron_left),
                                    contentDescription = null,
                                    tint = readableMutedIconColor(),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = if (showInProgress) 270f else 180f },
                                )
                            }
                        }
                        AnimatedVisibility(visible = showInProgress) {
                            ModuleCard(contentPadding = PaddingValues(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    downloadsState.activeDownloads.forEach { task ->
                                        ActiveDownloadRow(
                                            task = task,
                                            onCancel = { onCancelDownload(task.videoId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Editorial Tabs: Coleções | Músicas
            if (downloadsState.downloadedSongs.isNotEmpty() || downloadsState.downloadedCollections.isNotEmpty()) {
                item(key = "dl_tabs", contentType = "dl_tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        EditorialFilterPill(
                            label = stringResource(R.string.downloads_tab_collections),
                            selected = activeTab == DownloadsTab.Collections,
                            onClick = { activeTab = DownloadsTab.Collections },
                        )
                        EditorialFilterPill(
                            label = "${stringResource(R.string.downloads_tab_songs)} (${downloadsState.downloadedSongs.size})",
                            selected = activeTab == DownloadsTab.Songs,
                            onClick = { activeTab = DownloadsTab.Songs },
                        )
                    }
                }
            }

            // Empty state when nothing is downloaded
            if (downloadsState.downloadedSongs.isEmpty() && downloadsState.downloadedCollections.isEmpty()) {
                item(key = "dl_empty", contentType = "dl_empty") {
                    EditorialSectionEmptyState(message = copy.noDownloadedSongs)
                }
            } else if (activeTab == DownloadsTab.Collections) {
                // TAB 1: Editorial 2-column grids (instant load, zero upfront track list jank)
                if (savedCollectionRows.isNotEmpty()) {
                    item(key = "dl_saved_collections_title", contentType = "dl_section_title") {
                        Text(
                            text = stringResource(R.string.downloads_collections_saved_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        )
                    }
                    items(
                        items = savedCollectionRows,
                        key = { pair -> "dl_col_row_${pair.first().id}" },
                        contentType = { "dl_collection_grid_row" },
                    ) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            pair.forEach { collection ->
                                DownloadGridCard(
                                    title = collection.title.ifBlank {
                                        if (collection.isAlbum) commonCopy.albums else commonCopy.playlists
                                    },
                                    subtitle = localizedCountLabel(collection.songs.size, "song", language),
                                    artUri = collection.thumbnailUri,
                                    onClick = { onOpenCollection(collection) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (derivedAlbumRows.isNotEmpty()) {
                    item(key = "dl_derived_albums_title", contentType = "dl_section_title") {
                        Text(
                            text = stringResource(R.string.downloads_collections_grouped_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                        )
                    }
                    items(
                        items = derivedAlbumRows,
                        key = { pair -> "dl_alb_row_${pair.first().key}" },
                        contentType = { "dl_album_grid_row" },
                    ) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            pair.forEach { album ->
                                DownloadGridCard(
                                    title = album.title,
                                    subtitle = "${album.subtitle} · ${localizedCountLabel(album.songs.size, "song", language)}",
                                    artUri = album.artUri,
                                    onClick = {
                                        val first = album.songs.firstOrNull() ?: return@DownloadGridCard
                                        onPlaySong(first, album.songs, album.title)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // TAB 2: Virtualized Songs tab with Alphabet / Album / Artist filters
                item(key = "dl_song_filters", contentType = "dl_song_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "flt_az") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_alphabet),
                                    selected = songFilter == DownloadsSongFilter.Alphabet,
                                    onClick = { songFilter = DownloadsSongFilter.Alphabet },
                                )
                            }
                            item(key = "flt_album") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_album),
                                    selected = songFilter == DownloadsSongFilter.Album,
                                    onClick = {
                                        songFilter = DownloadsSongFilter.Album
                                        selectedLetter = null
                                    },
                                )
                            }
                            item(key = "flt_artist") {
                                EditorialFilterPill(
                                    label = stringResource(R.string.downloads_filter_artist),
                                    selected = songFilter == DownloadsSongFilter.Artist,
                                    onClick = {
                                        songFilter = DownloadsSongFilter.Artist
                                        selectedLetter = null
                                    },
                                )
                            }
                        }

                        if (songFilter == DownloadsSongFilter.Alphabet && availableLetters.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item(key = "letter_all") {
                                    AlphabetFilterChip(
                                        label = stringResource(R.string.downloads_letter_all),
                                        selected = selectedLetter == null,
                                        onClick = { selectedLetter = null },
                                    )
                                }
                                items(
                                    items = availableLetters,
                                    key = { "letter_$it" },
                                    contentType = { "alphabet_chip" },
                                ) { letter ->
                                    AlphabetFilterChip(
                                        label = letter,
                                        selected = selectedLetter == letter,
                                        onClick = {
                                            selectedLetter = if (selectedLetter == letter) null else letter
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = songListEntries,
                    key = { it.key },
                    contentType = { entry ->
                        when (entry) {
                            is DownloadSongListEntry.Header -> "dl_group_header"
                            is DownloadSongListEntry.Track -> "dl_song_row"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is DownloadSongListEntry.Header -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = ElovaireSpacing.screenHorizontalPadding,
                                        end = ElovaireSpacing.screenHorizontalPadding,
                                        top = 8.dp,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = localizedCountLabel(entry.count, "song", language),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = readableSecondaryTextColor(),
                                )
                            }
                        }
                        is DownloadSongListEntry.Track -> {
                            EditorialLibrarySongRow(
                                song = entry.song,
                                isFavorite = entry.song.id in favoriteSongIds,
                                onClick = { onPlaySong(entry.song, activeQueueForSongs, copy.downloadsTitle) },
                                onToggleFavorite = { onToggleFavorite(entry.song.id) },
                                modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            )
                        }
                    }
                }
            }
        }

        PinnedBackTopBar(
            title = copy.downloadsTitle,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun EditorialActionPill(
    label: String,
    iconResId: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (primary) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val fg = if (primary) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(bg)
            .then(
                if (primary) Modifier else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                ),
            )
            .semantics { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
internal fun EditorialFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(bg)
            .then(
                if (selected) Modifier else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                ),
            )
            .semantics { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlphabetFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(bg)
            .semantics { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun DownloadGridCard(
    title: String,
    subtitle: String,
    artUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ElovaireRadii.artwork))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkImage(
            uri = artUri,
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 320,
            showArtworkGlow = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle.ifBlank { " " },
                style = MaterialTheme.typography.labelLarge,
                color = readableSecondaryTextColor(),
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EditorialLibrarySongRow(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val toggleFavCd = stringResource(R.string.downloads_toggle_favorite_cd, song.title)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(ElovaireRadii.artwork))
                .elovaireClickable(onClick = onClick)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                uri = song.artUri,
                title = song.title,
                modifier = Modifier.size(52.dp),
                cornerRadius = ElovaireRadii.artwork,
                requestedSizePx = 144,
                showArtworkGlow = false,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist.ifBlank { song.album },
                    style = MaterialTheme.typography.labelLarge,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.durationMs > 0L) {
                Text(
                    text = formatDuration(song.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = readableSecondaryTextColor(),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .semantics {
                        role = Role.Button
                        contentDescription = toggleFavCd
                    }
                    .elovaireClickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isFavorite) R.drawable.ic_lucide_star_filled else R.drawable.ic_lucide_star,
                    ),
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else readableMutedIconColor(),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    task: YouTubeDownloadTask,
    onCancel: () -> Unit,
) {
    val cancelCd = stringResource(R.string.downloads_cancel_cd, task.song.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            uri = task.song.artUri,
            title = task.song.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = ElovaireRadii.artwork,
            requestedSizePx = 128,
            showArtworkGlow = false,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = task.song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (task.status) {
                YouTubeDownloadStatus.Queued -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(ElovaireRadii.pill)),
                    )
                }
                YouTubeDownloadStatus.Downloading -> {
                    if (task.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(ElovaireRadii.pill)),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(ElovaireRadii.pill)),
                        )
                    }
                }
                YouTubeDownloadStatus.Failed -> {
                    Text(
                        text = task.song.artist,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(ElovaireRadii.pill))
                .semantics {
                    role = Role.Button
                    contentDescription = cancelCd
                }
                .elovaireClickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_trash_2),
                contentDescription = null,
                tint = readableMutedIconColor(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
