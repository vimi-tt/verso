package elovaire.music.droidbeauty.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.components.rememberArtworkPaletteAccent
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.editorialHomeCopy
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy
import elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun YouTubeCollectionScreen(
    collectionId: String,
    isAlbum: Boolean,
    initialTitle: String,
    initialSubtitle: String,
    initialArtUri: String?,
    youTubeCatalog: YouTubeCatalogRepository,
    offlineCollection: YouTubeDownloadedCollection? = null,
    downloadedVideoIds: Set<String> = emptySet(),
    activeDownloadVideoIds: Set<String> = emptySet(),
    favoriteSongIds: Set<Long>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlaySongs: (Song, List<Song>, String) -> Unit,
    onShuffleSongs: (List<Song>, String) -> Unit,
    onDownloadCollection: ((List<Song>, YouTubeDownloadedCollection) -> Unit)? = null,
    onToggleFavorite: (Long) -> Unit,
) {
    val language = LocalAppLanguage.current
    val ytCopy = remember(language) { youtubeCopy(language) }
    val dlCopy = remember(language) { youtubeDownloadCopy(language) }
    val commonCopy = remember(language) { commonUiCopy(language) }
    val shuffleLabel = remember(language) { editorialHomeCopy(language).shuffle }
    val scope = rememberCoroutineScope()
    val listState = rememberElovaireLazyListState("yt_collection_$collectionId")

    var songs by remember(collectionId, isAlbum, offlineCollection) {
        mutableStateOf(offlineCollection?.songs.orEmpty())
    }
    var isLoading by remember(collectionId, isAlbum, offlineCollection) {
        mutableStateOf(offlineCollection?.songs.isNullOrEmpty())
    }
    var hasError by remember(collectionId, isAlbum) { mutableStateOf(false) }
    var retryNonce by remember(collectionId, isAlbum) { mutableIntStateOf(0) }
    var searchQuery by remember(collectionId, isAlbum) { mutableStateOf("") }
    var editMode by remember(collectionId, isAlbum) { mutableStateOf(false) }
    var showRenameDialog by remember(collectionId) { mutableStateOf(false) }
    var showAddTrackDialog by remember(collectionId) { mutableStateOf(false) }
    var showPlaylistPicker by remember(collectionId) { mutableStateOf(false) }
    var editedTitle by remember(collectionId, initialTitle) { mutableStateOf<String?>(null) }
    val songMenuActions = LocalSongMenuActions.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPt = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese
    val isEs = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish
    val canEditYouTubePlaylist = !isAlbum && youTubeCatalog.accountState.value.isLoggedIn &&
        collectionId != "LM" && collectionId != "VLLM"

    LaunchedEffect(collectionId, isAlbum, retryNonce) {
        val hasOfflineSongs = !offlineCollection?.songs.isNullOrEmpty()
        if (!hasOfflineSongs) {
            isLoading = true
        }
        hasError = false
        try {
            val loaded = youTubeCatalog.getCollectionSongs(collectionId, isAlbum)
            if (loaded.isNotEmpty()) {
                songs = loaded
                hasError = false
            } else if (!hasOfflineSongs) {
                hasError = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (!hasOfflineSongs) {
                hasError = true
            }
        } finally {
            isLoading = false
        }
    }

    val resolvedTitle = editedTitle
        ?.takeIf(String::isNotBlank)
        ?: initialTitle
            .ifBlank { offlineCollection?.title.orEmpty() }
            .ifBlank { if (isAlbum) commonCopy.albums else commonCopy.playlists }
    val resolvedSubtitle = initialSubtitle.ifBlank { offlineCollection?.subtitle.orEmpty() }
    val resolvedArtUri = remember(initialArtUri, offlineCollection, songs) {
        initialArtUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: offlineCollection?.thumbnailUri
            ?: songs.firstOrNull()?.artUri
    }
    val isCollectionDownloaded = remember(offlineCollection, songs, downloadedVideoIds) {
        if (offlineCollection != null) {
            true
        } else if (songs.isEmpty()) {
            false
        } else {
            val ids = songs.mapNotNull { YouTubeUri.videoId(it.uri) }
            ids.isNotEmpty() && ids.all { it in downloadedVideoIds }
        }
    }
    val isCollectionDownloading = remember(songs, activeDownloadVideoIds) {
        songs.any { YouTubeUri.videoId(it.uri) in activeDownloadVideoIds }
    }
    val filteredSongs = remember(songs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.title.contains(q, ignoreCase = true) ||
                    song.artist.contains(q, ignoreCase = true)
            }
        }
    }
    val totalDurationLabel = remember(songs) {
        val totalSeconds = songs.sumOf { it.durationMs.coerceAtLeast(0L) } / 1000L
        if (totalSeconds <= 0L) {
            null
        } else {
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            if (hours > 0L) {
                String.format("%02dh %02dm %02ds", hours, minutes, seconds)
            } else {
                String.format("%02dm %02ds", minutes, seconds)
            }
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val paletteAccent by rememberArtworkPaletteAccent(resolvedArtUri)
    val headerAccent = (paletteAccent ?: MaterialTheme.colorScheme.primary)
        .copy(alpha = 0.68f)
        .compositeOver(bgColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        // Editorial dynamic color atmosphere extracted from the cover artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
        ) {
            if (resolvedArtUri != null) {
                ArtworkImage(
                    uri = resolvedArtUri,
                    title = resolvedTitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp)
                        .alpha(0.32f),
                    cornerRadius = 0.dp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to headerAccent.copy(alpha = 0.92f),
                            0.55f to headerAccent.copy(alpha = 0.45f),
                            0.85f to bgColor.copy(alpha = 0.88f),
                            1.0f to bgColor,
                        ),
                    ),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(listState),
            contentPadding = PaddingValues(
                start = elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing.screenHorizontalPadding,
                end = elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing.screenHorizontalPadding,
                top = topBarOccupiedHeight() + elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing.md,
                bottom = bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (songs.isNotEmpty()) {
                item(key = "yt_collection_search") {
                    CollectionFilterSearchBar(
                        query = searchQuery,
                        placeholder = commonCopy.search,
                        clearCd = elovaire.music.droidbeauty.app.ui.i18n.searchCopy(language).clearSearch,
                        onQueryChange = { searchQuery = it },
                    )
                }
            }

            item(key = "yt_collection_header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ArtworkImage(
                        uri = resolvedArtUri,
                        title = resolvedTitle,
                        modifier = Modifier.size(204.dp),
                        cornerRadius = 16.dp,
                        showArtworkGlow = true,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val cleanedSubtitle = remember(resolvedSubtitle, songs.size) {
                            if (songs.isNotEmpty()) {
                                resolvedSubtitle
                                    .replace(Regex("\\s*[•·]\\s*\\d+\\s+\\p{L}+\\s*$"), "")
                                    .trim()
                            } else {
                                resolvedSubtitle.trim()
                            }
                        }
                        if (cleanedSubtitle.isNotBlank()) {
                            Text(
                                text = cleanedSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val statsMeta = buildList {
                            if (songs.isNotEmpty()) {
                                add(localizedCountLabel(songs.size, "song", language))
                            }
                            totalDurationLabel?.let(::add)
                        }.joinToString(" • ")
                        if (statsMeta.isNotBlank()) {
                            Text(
                                text = statsMeta,
                                style = MaterialTheme.typography.bodySmall,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "yt_collection_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CollectionQuickAction(
                            label = shuffleLabel,
                            iconResId = R.drawable.ic_lucide_shuffle,
                            onClick = { onShuffleSongs(songs, resolvedTitle) },
                        )
                        CollectionQuickAction(
                            label = ytCopy.radioLabel,
                            iconResId = R.drawable.ic_lucide_audio_waveform,
                            onClick = {
                                val seed = songs.first()
                                val videoId = YouTubeUri.videoId(seed.uri) ?: return@CollectionQuickAction
                                scope.launch {
                                    val related = runCatching {
                                        youTubeCatalog.getRelatedSongs(videoId)
                                    }.getOrDefault(emptyList())
                                    val queue = listOf(seed) + related.filterNot { it.id == seed.id }
                                    onPlaySongs(seed, queue, resolvedTitle)
                                }
                            },
                        )
                        // Centered signature circular Play button
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = ytCopy.playAll
                                }
                                .elovaireClickable { onPlaySongs(songs.first(), songs, resolvedTitle) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_play),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                        if (onDownloadCollection != null) {
                            CollectionQuickAction(
                                label = when {
                                    isCollectionDownloaded -> dlCopy.downloadedAction
                                    isCollectionDownloading -> dlCopy.downloadingAction
                                    else -> dlCopy.downloadAction
                                },
                                iconResId = if (isCollectionDownloaded) {
                                    R.drawable.ic_lucide_check
                                } else {
                                    R.drawable.ic_lucide_download
                                },
                                highlighted = isCollectionDownloaded,
                                isLoading = isCollectionDownloading && !isCollectionDownloaded,
                                onClick = {
                                    if (!isCollectionDownloaded) {
                                        onDownloadCollection(
                                            songs,
                                            YouTubeDownloadedCollection(
                                                id = collectionId,
                                                isAlbum = isAlbum,
                                                title = resolvedTitle,
                                                subtitle = resolvedSubtitle,
                                                thumbnailUri = resolvedArtUri,
                                                songs = songs,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                        CollectionQuickAction(
                            label = elovaire.music.droidbeauty.app.ui.i18n.uiPhrase(
                                language,
                                elovaire.music.droidbeauty.app.ui.i18n.UiPhrase.AddToPlaylist,
                            ),
                            iconResId = R.drawable.ic_lucide_list_plus,
                            onClick = { showPlaylistPicker = true },
                        )
                        if (canEditYouTubePlaylist) {
                            CollectionQuickAction(
                                label = when {
                                    editMode && isPt -> "Concluir"
                                    editMode && isEs -> "Listo"
                                    editMode -> "Done"
                                    isPt -> "Editar"
                                    isEs -> "Editar"
                                    else -> "Edit"
                                },
                                iconResId = if (editMode) R.drawable.ic_lucide_check else R.drawable.ic_lucide_square_pen,
                                highlighted = editMode,
                                onClick = { editMode = !editMode },
                            )
                        }
                    }
                }
                if (canEditYouTubePlaylist && editMode) {
                    item(key = "yt_collection_edit_bar") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .elovaireClickable { showRenameDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = when {
                                        isPt -> "Renomear"
                                        isEs -> "Renombrar"
                                        else -> "Rename"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .elovaireClickable { showAddTrackDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = when {
                                        isPt -> "+ Adicionar música"
                                        isEs -> "+ Añadir canción"
                                        else -> "+ Add song"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    item(key = "yt_collection_loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = ytCopy.loadingCollection,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                            )
                        }
                    }
                }
                hasError && songs.isEmpty() -> {
                    item(key = "yt_collection_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = ytCopy.collectionLoadError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .elovaireClickable { retryNonce++ }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = ytCopy.retryLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                filteredSongs.isNotEmpty() -> {
                    itemsIndexed(
                        items = filteredSongs,
                        key = { index, song -> "${index}_${song.id}" },
                    ) { index, song ->
                        val trackDownloaded = isCollectionDownloaded ||
                            (YouTubeUri.videoId(song.uri) in downloadedVideoIds)
                        NumberedCollectionTrackRow(
                            index = index + 1,
                            song = song,
                            isFavorite = song.id in favoriteSongIds,
                            isDownloaded = trackDownloaded,
                            downloadedCd = dlCopy.downloadedAction,
                            onClick = { onPlaySongs(song, filteredSongs, resolvedTitle) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                            onRemoveTrack = if (canEditYouTubePlaylist && editMode) {
                                {
                                    val videoId = YouTubeUri.videoId(song.uri)
                                    if (!videoId.isNullOrBlank()) {
                                        scope.launch {
                                            val ok = youTubeCatalog.removeVideoFromYouTubePlaylist(collectionId, videoId)
                                            if (ok) {
                                                songs = songs.filterNot { it.id == song.id }
                                                val msg = if (isPt) "Faixa removida" else if (isEs) "Canción eliminada" else "Track removed"
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (showRenameDialog) {
            var draftName by remember(resolvedTitle) { mutableStateOf(resolvedTitle) }
            var savingRename by remember { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!savingRename) showRenameDialog = false },
                title = {
                    Text(
                        when {
                            isPt -> "Renomear playlist"
                            isEs -> "Renombrar playlist"
                            else -> "Rename playlist"
                        },
                    )
                },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        singleLine = true,
                        enabled = !savingRename,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = !savingRename && draftName.isNotBlank(),
                        onClick = {
                            savingRename = true
                            scope.launch {
                                val ok = youTubeCatalog.renameYouTubePlaylist(collectionId, draftName.trim())
                                savingRename = false
                                showRenameDialog = false
                                if (ok) {
                                    editedTitle = draftName.trim()
                                    val msg = if (isPt) "Playlist renomeada" else if (isEs) "Playlist renombrada" else "Playlist renamed"
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) {
                        Text(if (savingRename) "..." else if (isPt) "Salvar" else if (isEs) "Guardar" else "Save")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        enabled = !savingRename,
                        onClick = { showRenameDialog = false },
                    ) {
                        Text(if (isPt) "Cancelar" else "Cancel")
                    }
                },
            )
        }

        if (showAddTrackDialog) {
            var addQuery by remember { mutableStateOf("") }
            var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
            var isSearching by remember { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAddTrackDialog = false },
                title = {
                    Text(
                        when {
                            isPt -> "Adicionar música"
                            isEs -> "Añadir canción"
                            else -> "Add song"
                        },
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = addQuery,
                                onValueChange = { addQuery = it },
                                placeholder = { Text(commonCopy.search) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(
                                enabled = addQuery.isNotBlank() && !isSearching,
                                onClick = {
                                    isSearching = true
                                    scope.launch {
                                        searchResults = runCatching {
                                            youTubeCatalog.searchSongs(addQuery.trim()).take(6)
                                        }.getOrDefault(emptyList())
                                        isSearching = false
                                    }
                                },
                            ) {
                                Text(if (isSearching) "..." else commonCopy.search)
                            }
                        }
                        searchResults.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .elovaireClickable {
                                        val vid = YouTubeUri.videoId(candidate.uri) ?: return@elovaireClickable
                                        scope.launch {
                                            val ok = youTubeCatalog.addVideoToYouTubePlaylist(collectionId, vid)
                                            if (ok) {
                                                if (songs.none { it.id == candidate.id }) {
                                                    songs = songs + candidate
                                                }
                                                val msg = if (isPt) "Adicionada à playlist" else if (isEs) "Añadida a la playlist" else "Added to playlist"
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                showAddTrackDialog = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ArtworkImage(
                                    uri = candidate.artUri,
                                    title = candidate.title,
                                    modifier = Modifier.size(38.dp),
                                    cornerRadius = 6.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = candidate.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = readableSecondaryTextColor(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showAddTrackDialog = false },
                    ) {
                        Text(if (isPt) "Fechar" else if (isEs) "Cerrar" else "Close")
                    }
                },
            )
        }

        PlaylistSelectionDialog(
            visible = showPlaylistPicker,
            title = elovaire.music.droidbeauty.app.ui.i18n.uiPhrase(
                language,
                elovaire.music.droidbeauty.app.ui.i18n.UiPhrase.AddToPlaylist,
            ),
            subtitle = resolvedTitle,
            playlists = songMenuActions.playlists.filterNot { it.isSystem },
            playlistSongsById = songMenuActions.songsById,
            onDismiss = { showPlaylistPicker = false },
            onPlaylistSelected = { playlistId ->
                var lastResult: elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult =
                    elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.InvalidInput
                for (song in songs) {
                    val res = songMenuActions.onAddToPlaylist(playlistId, song).await()
                    if (res is elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.Success) {
                        lastResult = res
                    }
                }
                if (lastResult is elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult.Success) {
                    showPlaylistPicker = false
                }
                lastResult
            },
            onCreatePlaylist = songMenuActions.onCreatePlaylist,
            youTubePlaylists = songMenuActions.youTubePlaylists,
            onYouTubePlaylistSelected = songMenuActions.onAddToYouTubePlaylist?.let { fn ->
                { ytId ->
                    var anyAdded = false
                    for (song in songs) {
                        if (fn(ytId, song)) anyAdded = true
                    }
                    if (anyAdded) {
                        showPlaylistPicker = false
                    }
                    anyAdded
                }
            },
            isYouTubeLoggedIn = songMenuActions.isYouTubeLoggedIn,
            isYouTubeSyncing = songMenuActions.isYouTubeSyncing,
            onRefreshYouTubePlaylists = songMenuActions.onRefreshYouTubePlaylists,
            importedCoverUris = songMenuActions.importedCoverUris,
        )

        PinnedBackTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun CollectionFilterSearchBar(
    query: String,
    placeholder: String,
    clearCd: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElovaireRadii.pill))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_x),
                contentDescription = clearCd,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(16.dp)
                    .elovaireClickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun CollectionQuickAction(
    label: String,
    iconResId: Int,
    highlighted: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (highlighted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = label,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else readableSecondaryTextColor(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NumberedCollectionTrackRow(
    index: Int,
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    downloadedCd: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveTrack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .elovaireClickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = readableSecondaryTextColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isDownloaded) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_check),
                        contentDescription = downloadedCd,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = readableSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemoveTrack != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_trash_2),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(20.dp)
                    .elovaireClickable(onClick = onRemoveTrack),
            )
        } else {
            InlineFavoriteSongButton(
                isFavorite = isFavorite,
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onToggleFavorite,
            )
            SongOverflowMenuButton(
                song = song,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
