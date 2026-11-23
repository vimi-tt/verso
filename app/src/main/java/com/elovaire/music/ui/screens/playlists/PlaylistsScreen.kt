package elovaire.music.droidbeauty.app.ui.screens
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.interaction.elovaireCombinedClickable
import elovaire.music.droidbeauty.app.ui.interaction.rememberElovaireInteractionSource
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import elovaire.music.droidbeauty.app.ui.motion.InlineActionMotionHost
import elovaire.music.droidbeauty.app.ui.motion.MotionDuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.settings.PlaylistMutationResult
import elovaire.music.droidbeauty.app.data.smartplaylists.SmartPlaylistResult
import kotlinx.coroutines.launch
import elovaire.music.droidbeauty.app.domain.model.Playlist
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.MiscPhrase
import elovaire.music.droidbeauty.app.ui.i18n.PlaylistMainCopy
import elovaire.music.droidbeauty.app.ui.i18n.UiPhrase
import elovaire.music.droidbeauty.app.ui.i18n.localizedCountLabel
import elovaire.music.droidbeauty.app.ui.i18n.builtInSmartPlaylistTitle
import elovaire.music.droidbeauty.app.ui.i18n.miscPhrase
import elovaire.music.droidbeauty.app.ui.i18n.playlistMainCopy
import elovaire.music.droidbeauty.app.ui.i18n.rootUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.smartPlaylistSortLabel
import elovaire.music.droidbeauty.app.ui.i18n.uiPhrase
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionSpecs
import elovaire.music.droidbeauty.app.ui.motion.rememberMotionTransitions
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing

@Composable
internal fun PlaylistsScreen(
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylistResult>,
    songsById: Map<Long, Song>,
    topPadding: Dp,
    bottomPadding: Dp,
    scrollToTopRequestVersion: Long,
    onRenamePlaylist: (Long, String) -> PlaylistMutationRequest,
    onDeletePlaylists: (Set<Long>) -> PlaylistMutationRequest,
    onOpenPlaylist: (Playlist, ExpandOrigin) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylistResult, ExpandOrigin) -> Unit,
    youTubePlaylists: List<elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary> = emptyList(),
    onOpenYouTubePlaylist: (elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary) -> Unit = {},
    isYouTubeLoggedIn: Boolean = false,
    isYouTubeSyncing: Boolean = false,
    onSyncYouTube: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    importedCoverUris: Map<Long, String> = emptyMap(),
    spotifyState: elovaire.music.droidbeauty.app.data.spotify.SpotifyPlaylistsUiState =
        elovaire.music.droidbeauty.app.data.spotify.SpotifyPlaylistsUiState(),
    onSpotifySessionCaptured: (spDc: String, accessToken: String?, expiresAtMs: Long) -> Unit = { _, _, _ -> },
    onSyncSpotify: (Boolean) -> Unit = {},
    onSignOutSpotify: () -> Unit = {},
    onOpenSpotifyPlaylist: suspend (elovaire.music.droidbeauty.app.data.spotify.SpotifyPlaylistSummary) -> Boolean = { false },
) {
    val motionTransitions = rememberMotionTransitions()
    val motionSpecs = rememberMotionSpecs()
    val playlistGridColumns = adaptiveGridColumnCount(2, LocalRootWindowSizeClass.current.width)
    val scope = rememberCoroutineScope()
    var playlistBeingRenamed by remember { mutableStateOf<Playlist?>(null) }
    var selectedPlaylistIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var showSpotifyLogin by rememberSaveable { mutableStateOf(false) }
    var showSpotifyPlaylists by rememberSaveable { mutableStateOf(false) }
    var importingSpotifyPlaylistId by remember { mutableStateOf<String?>(null) }
    val playlistRows = remember(playlists, songsById) {
        buildPlaylistRowModels(
            playlists = playlists.filterNot(Playlist::isSystem),
            songsById = songsById,
        )
    }
    val gridState = rememberElovaireLazyGridState("playlists_screen")
    val editMode = selectedPlaylistIds.isNotEmpty()
    val selectionTopInset by animateDpAsState(
        targetValue = if (editMode) 50.dp else 0.dp,
        animationSpec = motionSpecs.contentSize(),
        label = "playlist_selection_top_inset",
    )
    BackHandler(enabled = editMode) {
        selectedPlaylistIds = emptySet()
    }
    LaunchedEffect(playlists) {
        val existingUserIds = playlists.asSequence()
            .filterNot(Playlist::isSystem)
            .mapTo(mutableSetOf(), Playlist::id)
        val retainedSelection = selectedPlaylistIds.filterTo(linkedSetOf()) { it in existingUserIds }
        if (retainedSelection != selectedPlaylistIds) {
            selectedPlaylistIds = retainedSelection
        }
        if (playlistBeingRenamed?.id?.let { it !in existingUserIds } == true) {
            playlistBeingRenamed = null
        }
    }
    LaunchedEffect(scrollToTopRequestVersion) {
        if (scrollToTopRequestVersion > 0L) {
            gridState.animateScrollToItem(0)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val language = LocalAppLanguage.current
        val accountCopy = remember(language) { elovaire.music.droidbeauty.app.ui.i18n.youtubeAccountCopy(language) }
        val nonEmptySmartPlaylists = remember(smartPlaylists) {
            smartPlaylists.filter { it.songs.isNotEmpty() }
        }
        LazyVerticalGrid(
            state = gridState,
            overscrollEffect = null,
            columns = GridCells.Fixed(playlistGridColumns),
            modifier = Modifier
                .fillMaxSize()
                .ensureSingleItemRubberBand(gridState),
            contentPadding = PaddingValues(
                start = ElovaireSpacing.screenHorizontalPadding,
                top = topPadding + selectionTopInset + ElovaireSpacing.sm,
                end = ElovaireSpacing.screenHorizontalPadding,
                bottom = bottomPadding + ElovaireSpacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "spotify_playlists_entry",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "spotify_entry_row",
            ) {
                val spotifySubtitle = when {
                    !spotifyState.isLoggedIn -> when (language) {
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese -> "Entrar para ver suas playlists"
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish -> "Iniciar sesión para ver tus playlists"
                        else -> "Sign in to view your playlists"
                    }
                    spotifyState.isLoading && spotifyState.playlists.isEmpty() -> when (language) {
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese -> "Carregando playlists..."
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish -> "Cargando playlists..."
                        else -> "Loading playlists..."
                    }
                    spotifyState.playlists.isNotEmpty() -> localizedCountLabel(spotifyState.playlists.size, "playlist", language)
                    else -> when (language) {
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Portuguese -> "Ver suas playlists do Spotify"
                        elovaire.music.droidbeauty.app.domain.model.AppLanguage.Spanish -> "Ver tus playlists de Spotify"
                        else -> "View your Spotify playlists"
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ElovaireRadii.card))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .elovaireClickable(
                            interactionSource = rememberElovaireInteractionSource(),
                            onClick = {
                                if (spotifyState.isLoggedIn) {
                                    showSpotifyPlaylists = true
                                    onSyncSpotify(false)
                                } else {
                                    showSpotifyLogin = true
                                }
                            },
                        )
                        .padding(horizontal = ElovaireSpacing.lg, vertical = ElovaireSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_spotify_logo),
                        contentDescription = "Spotify",
                        modifier = Modifier.size(28.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Spotify",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = spotifySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = readableSecondaryTextColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_chevron_left),
                        contentDescription = null,
                        tint = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.mutedIcon,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(180f),
                    )
                }
            }

            item(
                key = "youtube_playlists_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "playlist_section_header",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistSectionHeader(elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy(language).sectionTitle)
                    if (isYouTubeLoggedIn) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(ElovaireRadii.pill))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .elovaireClickable(
                                    interactionSource = rememberElovaireInteractionSource(),
                                    onClick = onSyncYouTube,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isYouTubeSyncing) accountCopy.syncingLabel else accountCopy.syncNowLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (!isYouTubeLoggedIn) {
                item(
                    key = "youtube_playlists_signin_prompt",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "youtube_signin_prompt",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ElovaireRadii.card))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
                            .elovaireClickable(
                                interactionSource = rememberElovaireInteractionSource(),
                                onClick = onOpenSettings,
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = accountCopy.signInToSyncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        Text(
                            text = accountCopy.signInLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else if (youTubePlaylists.isEmpty() && !isYouTubeSyncing) {
                item(
                    key = "youtube_playlists_empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "youtube_playlists_empty",
                ) {
                    Text(
                        text = accountCopy.noPlaylistsMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(
                    items = youTubePlaylists,
                    key = { "yt_playlist_${it.id}" },
                    contentType = { "youtube_playlist_tile" },
                ) { ytPlaylist ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ElovaireRadii.card))
                            .elovaireClickable(
                                interactionSource = rememberElovaireInteractionSource(),
                                onClick = { onOpenYouTubePlaylist(ytPlaylist) },
                            )
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ArtworkImage(
                            uri = ytPlaylist.thumbnailUri,
                            title = ytPlaylist.title,
                            placeholderIconResId = R.drawable.ic_lucide_list_music,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            cornerRadius = ElovaireRadii.card,
                        )
                        Text(
                            text = ytPlaylist.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (ytPlaylist.subtitle.isNotBlank()) {
                            Text(
                                text = ytPlaylist.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (playlistRows.isNotEmpty()) {
                items(
                    items = playlistRows,
                    key = { it.playlist.id },
                    contentType = { "playlist_tile" },
                ) { row ->
                    PlaylistGridTile(
                        playlist = row.playlist,
                        previewSongs = row.previewSongs,
                        coverUriOverride = importedCoverUris[row.playlist.id],
                        selected = row.playlist.id in selectedPlaylistIds,
                        selectionMode = editMode,
                        onClick = { origin ->
                            if (editMode && !row.playlist.isSystem) {
                                selectedPlaylistIds = selectedPlaylistIds.togglePlaylistSelection(row.playlist.id)
                            } else {
                                onOpenPlaylist(row.playlist, origin)
                            }
                        },
                        onLongPress = {
                            if (!row.playlist.isSystem) {
                                selectedPlaylistIds = selectedPlaylistIds.togglePlaylistSelection(row.playlist.id)
                            }
                        },
                    )
                }
            }

            if (nonEmptySmartPlaylists.isNotEmpty()) {
                item(
                    key = "smart_mix_separator",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "playlist_section_separator",
                ) {
                    SmartMixSeparator()
                }
                item(
                    key = "smart_mix_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "playlist_section_header",
                ) {
                    PlaylistSectionHeader(
                        text = playlistMainCopy(language).smartMixes,
                        categoryAccent = elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.categories.mixes,
                    )
                }
                item(
                    key = "smart_mix_rows",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "smart_mix_rows",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nonEmptySmartPlaylists.forEach { row ->
                            SmartPlaylistListRow(
                                result = row,
                                copy = playlistMainCopy(language),
                                onClick = { origin -> onOpenSmartPlaylist(row, origin) },
                            )
                        }
                    }
                }
            }
        }
        FastScrollbar(
            state = gridState,
            topInset = topPadding + selectionTopInset + 12.dp,
            bottomInset = bottomPadding + 16.dp,
        )

        AnimatedVisibility(
            visible = editMode,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(3f),
            enter = motionTransitions.verticalRevealEnter(),
            exit = motionTransitions.verticalRevealExit(),
        ) {
            TopBarDualActionMenu(
                topBarHeight = topPadding,
                leadingAction = TopBarMenuAction(
                    iconResId = R.drawable.ic_lucide_square_pen,
                    label = uiPhrase(LocalAppLanguage.current, UiPhrase.Rename),
                    tint = if (selectedPlaylistIds.size == 1) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                    },
                    enabled = selectedPlaylistIds.size == 1,
                    onClick = {
                        if (selectedPlaylistIds.size == 1) {
                            playlistBeingRenamed = playlists.firstOrNull { it.id == selectedPlaylistIds.firstOrNull() }
                        }
                    },
                ),
                trailingAction = TopBarMenuAction(
                    iconResId = R.drawable.ic_lucide_trash_2,
                    label = uiPhrase(LocalAppLanguage.current, UiPhrase.RemoveFromList),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        val ids = selectedPlaylistIds
                        scope.launch {
                            if (onDeletePlaylists(ids).await() is PlaylistMutationResult.Success) {
                                selectedPlaylistIds = emptySet()
                            }
                        }
                    },
                ),
            )
        }

        val playlistToRename = playlistBeingRenamed
        val copy = rootUiCopy(LocalAppLanguage.current)
        PlaylistNameDialog(
            visible = playlistToRename != null,
            surfaceId = "playlists.rename_dialog",
            title = copy.renamePlaylistTitle,
            confirmLabel = copy.save,
            initialName = playlistToRename?.name.orEmpty(),
            onDismiss = { playlistBeingRenamed = null },
            onConfirm = { name ->
                playlistToRename?.let { playlist ->
                    scope.launch {
                        if (onRenamePlaylist(playlist.id, name).await() is PlaylistMutationResult.Success) {
                            playlistBeingRenamed = null
                            selectedPlaylistIds = emptySet()
                        }
                    }
                }
            },
        )
        if (showSpotifyLogin) {
            SpotifyLoginDialog(
                onDismiss = { showSpotifyLogin = false },
                onSessionCaptured = { spDc, token, exp ->
                    showSpotifyLogin = false
                    onSpotifySessionCaptured(spDc, token, exp)
                    showSpotifyPlaylists = true
                },
            )
        }
        if (showSpotifyPlaylists) {
            SpotifyPlaylistsModal(
                state = spotifyState,
                importingPlaylistId = importingSpotifyPlaylistId,
                onDismiss = {
                    if (importingSpotifyPlaylistId == null) {
                        showSpotifyPlaylists = false
                    }
                },
                onRefresh = { onSyncSpotify(true) },
                onSignOut = {
                    onSignOutSpotify()
                    showSpotifyPlaylists = false
                },
                onSelectPlaylist = { spotifyPlaylist ->
                    scope.launch {
                        importingSpotifyPlaylistId = spotifyPlaylist.id
                        val opened = runCatching { onOpenSpotifyPlaylist(spotifyPlaylist) }.getOrDefault(false)
                        importingSpotifyPlaylistId = null
                        if (opened) {
                            showSpotifyPlaylists = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SmartMixSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ElovaireSpacing.sm)
            .height(1.dp)
            .background(elovaire.music.droidbeauty.app.ui.theme.LocalVersoColors.current.divider),
    )
}

@Composable
private fun PlaylistSectionHeader(
    text: String,
    categoryAccent: Color? = null,
) {
    Row(
        modifier = Modifier.padding(top = ElovaireSpacing.sm, bottom = ElovaireSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (categoryAccent != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(ElovaireRadii.pill))
                    .background(categoryAccent),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SmartPlaylistListRow(
    result: SmartPlaylistResult,
    copy: PlaylistMainCopy,
    onClick: (ExpandOrigin) -> Unit,
) {
    val language = LocalAppLanguage.current
    val title = result.playlist.builtInType?.let { builtInSmartPlaylistTitle(it, language) } ?: result.playlist.name
    val screenSizePx = screenContainerSizePx()
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val songs = result.songs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .elovaireCombinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = { onClick(bounds.toExpandOrigin(screenSizePx.width.toFloat(), screenSizePx.height.toFloat())) },
                onLongClick = {},
            )
            .padding(vertical = ElovaireSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = songs.firstOrNull()?.artUri,
            title = title,
            modifier = Modifier.size(60.dp),
            cornerRadius = ElovaireRadii.artworkSmall,
            placeholderIconSize = 20.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${localizedCountLabel(songs.size, "song", language)} • ${copy.autoUpdating}",
                style = MaterialTheme.typography.bodyMedium,
                color = readableSecondaryTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = smartPlaylistSortLabel(language, result.playlist.sort.field),
                style = MaterialTheme.typography.labelMedium,
                color = readableSecondaryTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlaylistGridTile(
    playlist: Playlist,
    previewSongs: List<Song>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: (ExpandOrigin) -> Unit,
    onLongPress: () -> Unit,
    coverUriOverride: String? = null,
) {
    val screenSizePx = screenContainerSizePx()
    val screenWidthPx = screenSizePx.width.toFloat()
    val screenHeightPx = screenSizePx.height.toFloat()
    val language = LocalAppLanguage.current
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Column(
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .elovaireCombinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = { onClick(bounds.toExpandOrigin(screenWidthPx, screenHeightPx)) },
                onLongClick = onLongPress,
            ),
        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.sm),
    ) {
        Box {
            if (!coverUriOverride.isNullOrBlank()) {
                ArtworkImage(
                    uri = android.net.Uri.parse(coverUriOverride),
                    title = playlist.name,
                    placeholderIconResId = R.drawable.ic_lucide_list_music,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = ElovaireRadii.artwork,
                    showArtworkGlow = false,
                )
            } else {
                PlaylistArtworkPreview(
                    songs = previewSongs,
                    title = playlist.name,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (selectionMode && !playlist.isSystem) {
                PlaylistSelectionIndicator(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ElovaireSpacing.sm),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedCountLabel(playlist.songIds.size, "song", language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Set<Long>.togglePlaylistSelection(playlistId: Long): Set<Long> {
    return if (playlistId in this) this - playlistId else this + playlistId
}

@Composable
internal fun PlaylistSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedBg = MaterialTheme.colorScheme.primary
    val selectedFg = MaterialTheme.colorScheme.onPrimary
    val unselectedBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
    val motionSpecs = rememberMotionSpecs()
    val background by animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = motionSpecs.tween(MotionDuration.Standard),
        label = "playlist_selection_background",
    )
    val border by animateColorAsState(
        targetValue = if (selected) selectedBg else unselectedBorder,
        animationSpec = motionSpecs.tween(MotionDuration.Standard),
        label = "playlist_selection_border",
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                width = 1.dp,
                color = border,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        InlineActionMotionHost(visible = selected, surfaceId = "playlist_selection_check") {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_check),
                contentDescription = null,
                tint = selectedFg,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun EmptyPlaylistState(
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier.fillMaxWidth(0.74f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = miscPhrase(language, MiscPhrase.NothingInHere),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = miscPhrase(language, MiscPhrase.TapPlusToCreatePlaylist),
            style = MaterialTheme.typography.bodyLarge,
            color = readableSecondaryTextColor(),
        )
    }
}
