package elovaire.music.droidbeauty.app.ui.screens
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.interaction.elovaireCombinedClickable
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.domain.model.sumDurationMs
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.components.rememberArtworkPaletteAccent
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.localizedArtistName
import elovaire.music.droidbeauty.app.ui.i18n.localizedAlbumTitle
import elovaire.music.droidbeauty.app.ui.i18n.playlistAccessibilityCopy
import elovaire.music.droidbeauty.app.ui.i18n.shuffleAccessibilityCopy
import elovaire.music.droidbeauty.app.ui.i18n.MiscPhrase
import elovaire.music.droidbeauty.app.ui.i18n.UiPhrase
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.formatCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.miscPhrase
import elovaire.music.droidbeauty.app.ui.i18n.rootUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.uiPhrase
import elovaire.music.droidbeauty.app.ui.motion.ElovaireAnimatedVisibility
import elovaire.music.droidbeauty.app.ui.motion.MotionDuration
import elovaire.music.droidbeauty.app.ui.motion.MotionEasing
import elovaire.music.droidbeauty.app.ui.motion.elovaireListReveal
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionRevealRegistry
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionSpecs
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionTransitions
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing
import elovaire.music.droidbeauty.app.ui.theme.RoseAccent
import elovaire.music.droidbeauty.app.ui.theme.elovaireScaledSp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
internal fun PlaylistDetailScreen(
    playlist: Playlist?,
    librarySongs: List<Song>,
    songsById: Map<Long, Song>,
    favoriteSongIds: Set<Long>,
    currentSongId: Long?,
    isCurrentSongPlaying: Boolean,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlayPlaylist: (List<Song>, String) -> Unit,
    onShufflePlaylist: (List<Song>, String) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onUpdateSongOrder: (List<Long>) -> PlaylistMutationRequest,
    onRenamePlaylist: (Long, String) -> PlaylistMutationRequest,
    onToggleFavorite: (Long) -> Unit,
    coverUriOverride: String? = null,
    downloadedVideoIds: Set<String> = emptySet(),
    onDownloadPlaylist: ((List<Song>, elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection) -> Unit)? = null,
    onDeletePlaylist: ((Long) -> PlaylistMutationRequest)? = null,
    onUpdateCoverImage: ((Long, String) -> Unit)? = null,
    onExportToYouTube: (suspend (String, List<Song>) -> Boolean)? = null,
) {
    val revealRegistry = rememberMotionRevealRegistry()
    val motionTransitions = rememberMotionTransitions()
    val motionSpecs = rememberMotionSpecs()
    val detailState = remember(playlist, songsById) {
        buildPlaylistDetailState(playlist, songsById)
    }
    if (detailState.playlist == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DetailListTopBar(
                title = commonUiCopy(LocalAppLanguage.current).playlists,
                subtitle = null,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = ElovaireSpacing.screenHorizontalPadding,
                        top = detailTopBarOccupiedHeight() + ElovaireSpacing.detailListTopGap,
                        end = ElovaireSpacing.screenHorizontalPadding,
                        bottom = bottomPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rootUiCopy(LocalAppLanguage.current).playlistNotFound,
                    style = MaterialTheme.typography.bodyLarge,
                    color = readableSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val playlistState = detailState.playlist
    val defaultSongMenuActions = LocalSongMenuActions.current
    var editMode by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var showAddSongsPicker by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    val draftSaver = remember(playlistState.id) {
        listSaver<PlaylistEditDraft, Any>(
            save = { draft -> listOf(draft.playlistId, draft.originalSongIds, draft.songIds, draft.markedForRemoval.toList()) },
            restore = restore@{ values ->
                val playlistId = values.getOrNull(0) as? Long ?: return@restore null
                fun songIds(index: Int): List<Long>? {
                    val savedIds = values.getOrNull(index) as? List<*> ?: return null
                    return savedIds.filterIsInstance<Long>().takeIf { it.size == savedIds.size }
                }
                val originalSongIds = songIds(1) ?: return@restore null
                val songIds = songIds(2) ?: return@restore null
                val markedForRemoval = songIds(3) ?: return@restore null
                PlaylistEditDraft(
                    playlistId = playlistId,
                    originalSongIds = originalSongIds,
                    songIds = songIds,
                    markedForRemoval = markedForRemoval.toSet(),
                )
            },
        )
    }
    var editDraft by rememberSaveable(playlistState.id, stateSaver = draftSaver) {
        mutableStateOf(PlaylistEditDraft.fromPersisted(playlistState))
    }
    var activelyDraggedSongId by rememberSaveable(playlistState.id) { mutableStateOf<Long?>(null) }
    var showEditModeMenu by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var isSaving by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var isExportingToYouTube by rememberSaveable(playlistState.id) { mutableStateOf(false) }
    var pendingCropUri by remember(playlistState.id) { mutableStateOf<android.net.Uri?>(null) }
    var isSavingCover by remember(playlistState.id) { mutableStateOf(false) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val language = LocalAppLanguage.current
    val rootCopy = rootUiCopy(language)
    val editCopy = remember(language) { elovaire.music.droidbeauty.app.ui.i18n.playlistEditActionCopy(language) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { pickedUri ->
        if (pickedUri != null && !playlistState.isSystem && onUpdateCoverImage != null) {
            pendingCropUri = pickedUri
        }
    }
    LaunchedEffect(playlistState.id, editMode) {
        if (!editMode) {
            editDraft = PlaylistEditDraft.fromPersisted(playlistState)
            activelyDraggedSongId = null
        }
    }
    val displayedSongIds = if (editMode) editDraft.songIds else playlistState.songIds
    val playlistSongMenuActions = remember(defaultSongMenuActions, playlistState.id, playlistState.songIds) {
        defaultSongMenuActions.copy(
            onDeleteFromLibrary = { song ->
                scope.launch {
                    onUpdateSongOrder(playlistState.songIds.filterNot { it == song.id }).await()
                }
            },
            deletePhrase = UiPhrase.RemoveFromList,
        )
    }
    val playlistSongs = remember(displayedSongIds, songsById) {
        displayedSongIds.mapNotNull(songsById::get)
    }
    val playlistDurationMs = remember(playlistSongs) { playlistSongs.sumDurationMs() }
    val detailTopPadding = detailTopBarOccupiedHeight()
    val editMenuTopInset by animateDpAsState(
        targetValue = if (editMode && showEditModeMenu) 50.dp else 0.dp,
        animationSpec = motionSpecs.contentSize(),
        label = "playlist_edit_menu_top_inset",
    )
    val deleteLabel = uiPhrase(language, UiPhrase.Delete)
    val topBarActions = remember(
        editMode,
        editDraft,
        playlistState.isSystem,
        rootCopy,
        deleteLabel,
    ) {
        buildList {
            if (editMode && !playlistState.isSystem) {
                add(
                    TopBarActionSpec(
                        iconResId = R.drawable.ic_lucide_plus,
                        contentDescription = rootCopy.addSongsTitle,
                        onClick = { showAddSongsPicker = true },
                    ),
                )
            }
            if (!playlistState.isSystem) {
                if (!editMode && onDeletePlaylist != null) {
                    add(
                        TopBarActionSpec(
                            iconResId = R.drawable.ic_lucide_trash_2,
                            contentDescription = deleteLabel,
                            onClick = { showDeleteConfirm = true },
                        ),
                    )
                }
                add(
                    TopBarActionSpec(
                        iconResId = if (editMode) R.drawable.ic_lucide_check else R.drawable.ic_lucide_square_pen,
                        contentDescription = if (editMode) rootCopy.savePlaylistChanges else rootCopy.editPlaylist,
                        onClick = {
                            if (editMode && !isSaving) {
                                val updatedSongIds = editDraft.finalSongIds
                                isSaving = true
                                scope.launch {
                                    try {
                                        if (onUpdateSongOrder(updatedSongIds).await() is PlaylistMutationResult.Success) {
                                            editDraft = editDraft.copy(
                                                originalSongIds = updatedSongIds,
                                                songIds = updatedSongIds,
                                                markedForRemoval = emptySet(),
                                            )
                                            activelyDraggedSongId = null
                                            editMode = false
                                            showEditModeMenu = false
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            } else {
                                editDraft = PlaylistEditDraft.fromPersisted(playlistState)
                                editMode = true
                                showEditModeMenu = true
                            }
                        },
                    ),
                )
            }
        }
    }
    BackHandler(enabled = showAddSongsPicker || editMode) {
        if (showAddSongsPicker) {
            showAddSongsPicker = false
        } else if (editMode) {
            editDraft = PlaylistEditDraft.fromPersisted(playlistState)
            activelyDraggedSongId = null
            editMode = false
            showEditModeMenu = false
        }
    }
    CompositionLocalProvider(LocalSongMenuActions provides playlistSongMenuActions) {
        val bgColor = MaterialTheme.colorScheme.background
        val headerArtworkUri = remember(coverUriOverride, playlistSongs) {
            coverUriOverride?.let(android.net.Uri::parse) ?: playlistSongs.firstOrNull()?.artUri
        }
        val headerTint by elovaire.music.droidbeauty.app.ui.components.rememberArtworkHeaderWash(headerArtworkUri)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to headerTint.copy(alpha = 0.34f),
                            0.65f to headerTint.copy(alpha = 0.12f),
                            1.0f to bgColor.copy(alpha = 0f),
                        ),
                    ),
            )
            val listState = rememberElovaireLazyListState(playlistState.id, "playlist_detail")
            val density = androidx.compose.ui.platform.LocalDensity.current
            LazyColumn(
                state = listState,
                overscrollEffect = null,
                userScrollEnabled = true,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .ensureSingleItemRubberBand(listState),
                contentPadding = PaddingValues(
                    start = ElovaireSpacing.screenHorizontalPadding,
                    top = detailTopPadding + ElovaireSpacing.albumHeaderTopGap + editMenuTopInset,
                    end = ElovaireSpacing.screenHorizontalPadding,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val artworkWidth = (maxWidth * 0.38f) - 30.dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.then(
                                        if (editMode && !playlistState.isSystem && onUpdateCoverImage != null) {
                                            Modifier.elovaireClickable {
                                                coverPickerLauncher.launch("image/*")
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                ) {
                                    if (!coverUriOverride.isNullOrBlank()) {
                                        ArtworkImage(
                                            uri = android.net.Uri.parse(coverUriOverride),
                                            title = playlistState.name,
                                            placeholderIconResId = R.drawable.ic_lucide_list_music,
                                            modifier = Modifier.size(artworkWidth),
                                            cornerRadius = ElovaireRadii.card,
                                        )
                                    } else {
                                        PlaylistArtworkPreview(
                                            songs = playlistSongs,
                                            title = playlistState.name,
                                            modifier = Modifier.width(artworkWidth),
                                            placeholderIconSize = 30.dp,
                                            cornerRadius = 8.dp,
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = playlistState.name,
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontSize = elovaireScaledSp(NOW_PLAYING_TITLE_TEXT_SIZE_SP),
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = elovaireScaledSp(28f),
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                            ) {
                                                append(formatCountLabel(playlistSongs.size, "track", language))
                                            }
                                            append("  •  ")
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                            ) {
                                                append(formatPlaylistDuration(playlistDurationMs, language))
                                            }
                                        },
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = elovaireScaledSp(NOW_PLAYING_ARTIST_TEXT_SIZE_SP),
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AlbumHeaderPlayButton(
                                tint = MaterialTheme.colorScheme.onPrimary,
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                onClick = { onPlayPlaylist(playlistSongs, playlistState.name) },
                            )
                            AlbumHeaderActionButton(
                                iconResId = R.drawable.ic_lucide_shuffle,
                                contentDescription = shuffleAccessibilityCopy(language).playlist,
                                tint = MaterialTheme.colorScheme.onSurface,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                iconSize = 18.dp,
                                onClick = { onShufflePlaylist(playlistSongs, playlistState.name) },
                            )
                            val downloadableYouTubeSongs = remember(playlistSongs) {
                                playlistSongs.filter { elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.isYouTubeUri(it.uri) }
                            }
                            if (onDownloadPlaylist != null && downloadableYouTubeSongs.isNotEmpty()) {
                                val allDownloaded = remember(downloadableYouTubeSongs, downloadedVideoIds) {
                                    downloadableYouTubeSongs.all { song ->
                                        elovaire.music.droidbeauty.app.data.youtube.YouTubeUri.videoId(song.uri) in downloadedVideoIds
                                    }
                                }
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val dlCopy = remember(language) { elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy(language) }
                                AlbumHeaderActionButton(
                                    iconResId = if (allDownloaded) R.drawable.ic_lucide_check else R.drawable.ic_lucide_download,
                                    contentDescription = if (allDownloaded) dlCopy.downloadedAction else dlCopy.downloadAction,
                                    tint = if (allDownloaded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    backgroundColor = if (allDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    iconSize = 18.dp,
                                    onClick = {
                                        if (!allDownloaded) {
                                            onDownloadPlaylist(
                                                downloadableYouTubeSongs,
                                                elovaire.music.droidbeauty.app.data.youtube.YouTubeDownloadedCollection(
                                                    id = "local_pl_${playlistState.id}",
                                                    isAlbum = false,
                                                    title = playlistState.name,
                                                    subtitle = formatCountLabel(downloadableYouTubeSongs.size, "track", language),
                                                    thumbnailUri = headerArtworkUri,
                                                    songs = downloadableYouTubeSongs,
                                                ),
                                            )
                                            val isPt = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese
                                            val isEs = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish
                                            val msg = when {
                                                isPt -> "Baixando ${downloadableYouTubeSongs.size} faixas"
                                                isEs -> "Descargando ${downloadableYouTubeSongs.size} canciones"
                                                else -> "Downloading ${downloadableYouTubeSongs.size} tracks"
                                            }
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                            if (!playlistState.isSystem && onExportToYouTube != null && playlistSongs.isNotEmpty()) {
                                AlbumHeaderActionButton(
                                    iconResId = R.drawable.ic_lucide_share_2,
                                    contentDescription = editCopy.addToYouTube,
                                    tint = if (isExportingToYouTube) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    backgroundColor = if (isExportingToYouTube) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    iconSize = 18.dp,
                                    onClick = {
                                        if (!isExportingToYouTube) {
                                            isExportingToYouTube = true
                                            android.widget.Toast.makeText(
                                                context,
                                                editCopy.addingToYouTube,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                            scope.launch {
                                                val ok = try {
                                                    onExportToYouTube(playlistState.name, playlistSongs)
                                                } finally {
                                                    isExportingToYouTube = false
                                                }
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (ok) editCopy.addedToYouTube else editCopy.exportToYouTubeFailed,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        if (editMode && !playlistState.isSystem && onUpdateCoverImage != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .elovaireClickable { coverPickerLauncher.launch("image/*") }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = editCopy.changeImage,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                if (playlistSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.size(18.dp))
                    }
                }

                if (playlistSongs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 34.dp, bottom = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = miscPhrase(LocalAppLanguage.current, MiscPhrase.NoSongsYet),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = miscPhrase(LocalAppLanguage.current, MiscPhrase.AddSongsViaEdit),
                                style = MaterialTheme.typography.bodyLarge,
                                color = readableSecondaryTextColor(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = playlistSongs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "playlist_song_row" },
                    ) { index, song ->
                        GroupedListRowContainer(
                            index = index,
                            lastIndex = playlistSongs.lastIndex,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = motionSpecs.listPlacement(),
                                )
                                .elovaireListReveal(
                                    itemKey = "${song.id}_$index",
                                    index = index,
                                    registry = revealRegistry,
                                    enabled = !editMode,
                                ),
                        ) {
                            PlaylistSongRow(
                                song = song,
                                isFavorite = song.id in favoriteSongIds,
                                isCurrentSong = song.id == currentSongId,
                                isPlaybackActive = isCurrentSongPlaying,
                                editMode = editMode,
                                onClick = {
                                    if (!editMode) {
                                        onSongSelected(song, playlistSongs)
                                    }
                                },
                                markedForRemoval = song.id in editDraft.markedForRemoval,
                                onLongPress = {
                                    if (!playlistState.isSystem && playlistSongs.isNotEmpty() && !editMode) {
                                        editDraft = PlaylistEditDraft.fromPersisted(playlistState)
                                        editMode = true
                                        showEditModeMenu = true
                                    }
                                },
                                onToggleMarkedForRemoval = {
                                    editDraft = editDraft.toggleRemoval(song.id)
                                },
                                isDragged = activelyDraggedSongId == song.id,
                                onDragActiveChanged = { isActive ->
                                    if (!isActive) {
                                        autoScrollJob?.cancel()
                                        autoScrollJob = null
                                    }
                                    activelyDraggedSongId = when {
                                        isActive -> song.id
                                        activelyDraggedSongId == song.id -> null
                                        else -> activelyDraggedSongId
                                    }
                                },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                onMoveBy = { delta ->
                                    if (editMode && delta != 0) {
                                        editDraft = editDraft.move(song.id, delta)
                                    }
                                },
                                onReorderDrag = { dragAmount ->
                                    if (editMode && editDraft.songIds.size > 1) {
                                        val layoutInfo = listState.layoutInfo
                                        val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.id }
                                        if (draggedItem != null) {
                                            val edgeThresholdPx = with(density) { 84.dp.toPx() }
                                            val canScrollUp =
                                                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                                            val canScrollDown =
                                                layoutInfo.visibleItemsInfo.lastOrNull()?.index != layoutInfo.totalItemsCount - 1
                                            val nearTop = draggedItem.offset <= layoutInfo.viewportStartOffset + edgeThresholdPx
                                            val nearBottom = draggedItem.offset + draggedItem.size >=
                                                layoutInfo.viewportEndOffset - edgeThresholdPx
                                            when {
                                                dragAmount < 0f && nearTop && canScrollUp -> {
                                                    autoScrollJob?.cancel()
                                                    autoScrollJob = scope.launch {
                                                        listState.scrollBy((dragAmount * 0.72f).coerceAtLeast(-22f))
                                                    }
                                                }
                                                dragAmount > 0f && nearBottom && canScrollDown -> {
                                                    autoScrollJob?.cancel()
                                                    autoScrollJob = scope.launch {
                                                        listState.scrollBy((dragAmount * 0.72f).coerceAtMost(22f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                showOverflowMenu = !editMode,
                                showDivider = index != playlistSongs.lastIndex,
                            )
                        }
                    }
                }
            }

            DetailListTopBar(
                title = playlistState.name,
                subtitle = localizedCountLabel(playlistSongs.size, "song", LocalAppLanguage.current),
                onBack = {
                    if (showAddSongsPicker) {
                        showAddSongsPicker = false
                    } else if (editMode) {
                        editDraft = PlaylistEditDraft.fromPersisted(playlistState)
                        activelyDraggedSongId = null
                        editMode = false
                        showEditModeMenu = false
                    } else {
                        onBack()
                    }
                },
                actions = topBarActions,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            AnimatedVisibility(
                visible = editMode && showEditModeMenu && !playlistState.isSystem,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(3f),
                enter = motionTransitions.verticalRevealEnter(),
                exit = motionTransitions.verticalRevealExit(),
            ) {
                TopBarDualActionMenu(
                    topBarHeight = detailTopPadding,
                    leadingAction = TopBarMenuAction(
                        iconResId = R.drawable.ic_lucide_square_pen,
                        label = uiPhrase(LocalAppLanguage.current, UiPhrase.Rename),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { showRenameDialog = true },
                    ),
                    trailingAction = TopBarMenuAction(
                        iconResId = R.drawable.ic_lucide_trash_2,
                        label = uiPhrase(LocalAppLanguage.current, UiPhrase.RemoveFromList),
                        tint = MaterialTheme.colorScheme.error,
                        enabled = editDraft.markedForRemoval.isNotEmpty(),
                        onClick = {
                            if (editDraft.markedForRemoval.isNotEmpty()) {
                                editDraft = editDraft.removeMarked()
                                activelyDraggedSongId = null
                            }
                        },
                    ),
                )
            }
        }
    }

    pendingCropUri?.let { cropUri ->
        PlaylistCoverCropDialog(
            imageUri = cropUri,
            isSaving = isSavingCover,
            onDismiss = { pendingCropUri = null },
            onConfirmCropped = { bytes ->
                isSavingCover = true
                scope.launch {
                    val savedUri = elovaire.music.droidbeauty.app.data.settings.PlaylistCoverImageHelper
                        .saveLocalPlaylistCoverBytes(context, playlistState.id, bytes)
                    if (savedUri != null) {
                        onUpdateCoverImage?.invoke(playlistState.id, savedUri)
                    }
                    isSavingCover = false
                    pendingCropUri = null
                }
            },
        )
    }

    if (showAddSongsPicker && !playlistState.isSystem) {
        AddSongsToPlaylistOverlay(
            availableSongs = librarySongs,
            existingSongIds = editDraft.songIds.toSet(),
            onDismiss = { showAddSongsPicker = false },
            onAddSongs = { selectedSongIds ->
                editDraft = editDraft.addSongs(selectedSongIds)
                showAddSongsPicker = false
            },
        )
    }
    PlaylistNameDialog(
        visible = showRenameDialog && !playlistState.isSystem,
        surfaceId = "playlists.rename_dialog",
        title = rootUiCopy(LocalAppLanguage.current).renamePlaylistTitle,
        confirmLabel = rootUiCopy(LocalAppLanguage.current).save,
        initialName = playlistState.name,
        onDismiss = { showRenameDialog = false },
        onConfirm = { name ->
            scope.launch {
                if (onRenamePlaylist(playlistState.id, name).await() is PlaylistMutationResult.Success) {
                    showRenameDialog = false
                }
            }
        },
    )
    if (showDeleteConfirm && !playlistState.isSystem && onDeletePlaylist != null) {
        val language = LocalAppLanguage.current
        val isPt = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese
        val isEs = language == elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = uiPhrase(language, UiPhrase.Delete),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = when {
                        isPt -> "Excluir a playlist \"${playlistState.name}\"?"
                        isEs -> "¿Eliminar la playlist \"${playlistState.name}\"?"
                        else -> "Delete playlist \"${playlistState.name}\"?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(uiPhrase(language, UiPhrase.Cancel))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            if (onDeletePlaylist(playlistState.id).await() is PlaylistMutationResult.Success) {
                                onBack()
                            }
                        }
                    },
                ) {
                    Text(
                        text = uiPhrase(language, UiPhrase.Delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
internal fun PlaylistSongRow(
    song: Song,
    isFavorite: Boolean,
    isCurrentSong: Boolean = false,
    isPlaybackActive: Boolean = false,
    editMode: Boolean = false,
    onClick: () -> Unit,
    markedForRemoval: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleMarkedForRemoval: () -> Unit = {},
    isDragged: Boolean = false,
    onDragActiveChanged: (Boolean) -> Unit = {},
    onToggleFavorite: () -> Unit,
    onMoveBy: (Int) -> Unit = {},
    onReorderDrag: (Float) -> Unit = {},
    showOverflowMenu: Boolean = false,
    showDivider: Boolean,
) {
    val motionSpecs = rememberMotionSpecs()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reorderStepPx = remember(density) { with(density) { 18.dp.toPx() } }
    var reorderDragAccumulator by remember(song.id, editMode) { mutableFloatStateOf(0f) }
    var handleDragActive by remember(song.id, editMode) { mutableStateOf(false) }
    val visualDragOffsetY = reorderDragAccumulator.coerceIn(-20f, 20f)
    val trailingWidth by animateDpAsState(
        targetValue = if (editMode) 36.dp else if (showOverflowMenu) 96.dp else 64.dp,
        animationSpec = motionSpecs.contentSize(),
        label = "playlist_row_trailing_width",
    )
    val handleTint by animateColorAsState(
        targetValue = if (handleDragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        },
        animationSpec = motionSpecs.tween(
            durationMillis = MotionDuration.Fast,
            easing = MotionEasing.SoftOut,
        ),
        label = "playlist_reorder_handle_tint",
    )
    val handleScale by animateFloatAsState(
        targetValue = if (handleDragActive) 1.1f else 1f,
        animationSpec = motionSpecs.spring(dampingRatio = 0.82f, stiffness = 560f),
        label = "playlist_reorder_handle_scale",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isDragged) 1.018f else 1f,
        animationSpec = if (isDragged) {
            motionSpecs.spring(
                dampingRatio = 0.5f,
                stiffness = 210f,
            )
        } else {
            motionSpecs.spring(dampingRatio = 0.82f, stiffness = 560f)
        },
        label = "playlist_drag_row_scale",
    )
    val rowTranslationY by animateFloatAsState(
        targetValue = if (isDragged) visualDragOffsetY else 0f,
        animationSpec = motionSpecs.spring(
            dampingRatio = 0.42f,
            stiffness = 250f,
        ),
        label = "playlist_drag_row_translation",
    )
    val dragHighlight by animateColorAsState(
        targetValue = if (handleDragActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = motionSpecs.tween(
            durationMillis = MotionDuration.Fast,
            easing = MotionEasing.SoftOut,
        ),
        label = "playlist_reorder_drag_highlight",
    )
    val rowInteractionModifier = if (editMode) {
        Modifier
    } else {
        Modifier.elovaireCombinedClickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick,
            onLongClick = onLongPress,
        )
    }
    val playingAccent = if (isCurrentSong) {
        elovaire.music.droidbeauty.app.ui.components.rememberReadableArtworkAccent(song.artUri).value
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rowBackground = if (handleDragActive) {
        dragHighlight
    } else if (isCurrentSong) {
        playingAccent.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .graphicsLayer {
                    scaleX = rowScale
                    scaleY = rowScale
                    translationY = rowTranslationY
                }
                .then(rowInteractionModifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElovaireAnimatedVisibility(
                visible = editMode,
                enter = fadeIn(animationSpec = motionSpecs.fadeIn()) +
                    slideInHorizontally(
                        initialOffsetX = { -it / 2 },
                        animationSpec = motionSpecs.tween(
                            durationMillis = MotionDuration.ScreenSlide,
                            easing = MotionEasing.SoftOut,
                        ),
                    ),
                exit = fadeOut(animationSpec = motionSpecs.fadeOut()) +
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = motionSpecs.tween(
                            durationMillis = MotionDuration.Micro,
                            easing = MotionEasing.SoftOut,
                        ),
                    ),
                label = "playlist_song_reorder_handle",
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = handleScale
                            scaleY = handleScale
                        }
                        .pointerInput(song.id, editMode) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    reorderDragAccumulator = 0f
                                    handleDragActive = true
                                    onDragActiveChanged(true)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    reorderDragAccumulator += dragAmount
                                    onReorderDrag(dragAmount)
                                    while (reorderDragAccumulator <= -reorderStepPx) {
                                        onMoveBy(-1)
                                        reorderDragAccumulator += reorderStepPx
                                    }
                                    while (reorderDragAccumulator >= reorderStepPx) {
                                        onMoveBy(1)
                                        reorderDragAccumulator -= reorderStepPx
                                    }
                                },
                                onDragEnd = {
                                    reorderDragAccumulator = 0f
                                    handleDragActive = false
                                    onDragActiveChanged(false)
                                },
                                onDragCancel = {
                                    reorderDragAccumulator = 0f
                                    handleDragActive = false
                                    onDragActiveChanged(false)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_chevrons_up_down),
                        contentDescription = playlistAccessibilityCopy(LocalAppLanguage.current).reorderSong,
                        tint = handleTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    uri = song.artUri,
                    title = localizedAlbumTitle(song.album, LocalAppLanguage.current),
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = ElovaireRadii.artworkSmall,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCurrentSong && isPlaybackActive,
                    enter = fadeIn(animationSpec = motionSpecs.tween(60)),
                    exit = fadeOut(animationSpec = motionSpecs.tween(60)),
                ) {
                    PlaybackActiveArtworkOverlay(
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.mediaTextStackGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExplicitTitleText(
                        title = song.title,
                        isExplicit = song.isExplicit,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isCurrentSong) playingAccent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = localizedArtistName(song.artist, LocalAppLanguage.current),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.width(trailingWidth),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ElovaireAnimatedVisibility(
                    visible = !editMode,
                    enter = fadeIn(animationSpec = motionSpecs.fadeIn()),
                    exit = fadeOut(animationSpec = motionSpecs.fadeOut()),
                    label = "playlist_song_metadata_visibility",
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatDuration(song.durationMs),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(40.dp),
                        )
                        InlineFavoriteSongButton(
                            isFavorite = isFavorite,
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onToggleFavorite,
                        )
                        if (showOverflowMenu) {
                            SongOverflowMenuButton(
                                song = song,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                ElovaireAnimatedVisibility(
                    visible = editMode,
                    enter = fadeIn(animationSpec = motionSpecs.fadeIn()) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = motionSpecs.tween(easing = MotionEasing.SoftOut),
                        ),
                    exit = fadeOut(animationSpec = motionSpecs.fadeOut()) +
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = motionSpecs.fadeOut(),
                        ),
                    label = "playlist_song_remove_toggle",
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .elovaireClickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                onClick = onToggleMarkedForRemoval,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SelectionIndicatorIcon(selected = markedForRemoval)
                    }
                }
            }
        }
        if (showDivider) {
            DividerLine()
        }
    }
}
