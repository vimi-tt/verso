package elovaire.music.droidbeauty.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.data.youtube.YouTubeArtistPage
import elovaire.music.droidbeauty.app.data.youtube.YouTubeArtistSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCatalogRepository
import elovaire.music.droidbeauty.app.data.youtube.YouTubeCollectionSummary
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.components.rememberArtworkPaletteAccent
import elovaire.music.droidbeauty.app.ui.i18n.LocalAppLanguage
import elovaire.music.droidbeauty.app.ui.i18n.artistFollowCopy
import elovaire.music.droidbeauty.app.ui.i18n.commonUiCopy
import elovaire.music.droidbeauty.app.ui.i18n.smartPlaylistActionCopy
import elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.screens.common.ModuleCard
import elovaire.music.droidbeauty.app.ui.screens.common.readableSecondaryTextColor
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing
import elovaire.music.droidbeauty.app.ui.theme.elovaireScaledSp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun YouTubeArtistScreen(
    artistId: String,
    youTubeCatalog: YouTubeCatalogRepository,
    favoriteSongIds: Set<Long>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlaySongs: (Song, List<Song>, String) -> Unit,
    onShuffleSongs: (List<Song>, String) -> Unit,
    onCollectionSelected: (YouTubeCollectionSummary, Boolean) -> Unit,
    onArtistSelected: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val language = LocalAppLanguage.current
    val ytCopy = remember(language) { youtubeCopy(language) }
    val followCopy = remember(language) { artistFollowCopy(language) }
    val commonCopy = remember(language) { commonUiCopy(language) }
    val shuffleLabel = remember(language) { smartPlaylistActionCopy(language).shuffle }
    val accountState by youTubeCatalog.accountState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberElovaireLazyListState("yt_artist_$artistId")

    var artistPage by remember(artistId) { mutableStateOf<YouTubeArtistPage?>(null) }
    var isLoading by remember(artistId) { mutableStateOf(true) }
    var hasError by remember(artistId) { mutableStateOf(false) }
    var retryNonce by remember(artistId) { mutableIntStateOf(0) }
    var bioExpanded by remember(artistId) { mutableStateOf(false) }
    var subscriptionOverride by remember(artistId) { mutableStateOf<Boolean?>(null) }
    var isUpdatingSubscription by remember(artistId) { mutableStateOf(false) }

    LaunchedEffect(artistId, retryNonce) {
        isLoading = true
        hasError = false
        try {
            val loaded = youTubeCatalog.getArtistPage(artistId)
            artistPage = loaded
            hasError = loaded == null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            hasError = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = ytCopy.loadingArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readableSecondaryTextColor(),
                    )
                }
            }
            hasError || artistPage == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = ytCopy.artistLoadError,
                        style = MaterialTheme.typography.bodyLarge,
                        color = readableSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ElovaireRadii.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .elovaireClickable(onClick = { retryNonce++ })
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = ytCopy.retryLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            else -> {
                val page = requireNotNull(artistPage)
                val playableSongs = page.allSongs.ifEmpty { page.topSongs }
                val bgColor = MaterialTheme.colorScheme.background
                val paletteAccent by rememberArtworkPaletteAccent(page.bannerUri)
                val accentColor = paletteAccent ?: MaterialTheme.colorScheme.primary
                val headerAccent = accentColor
                    .copy(alpha = 0.68f)
                    .compositeOver(bgColor)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(470.dp),
                    ) {
                        if (page.bannerUri != null) {
                            ArtworkImage(
                                uri = page.bannerUri,
                                title = page.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(64.dp)
                                    .alpha(0.38f),
                                cornerRadius = 0.dp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to headerAccent.copy(alpha = 0.92f),
                                        0.52f to headerAccent.copy(alpha = 0.48f),
                                        0.84f to bgColor.copy(alpha = 0.90f),
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
                            top = topBarOccupiedHeight() + 18.dp,
                            bottom = bottomPadding + 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.xl),
                    ) {
                    item(key = "yt_artist_editorial_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.lg),
                        ) {
                            Surface(
                                modifier = Modifier.size(236.dp),
                                shape = RoundedCornerShape(ElovaireRadii.module),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                shadowElevation = 24.dp,
                                tonalElevation = 0.dp,
                            ) {
                                ArtworkImage(
                                    uri = page.bannerUri,
                                    title = page.name,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = ElovaireRadii.module,
                                    showArtworkGlow = true,
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = page.name,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = elovaireScaledSp(30f),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = elovaireScaledSp(34f),
                                        letterSpacing = (-0.5).sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                page.monthlyAudience?.takeIf(String::isNotBlank)?.let { audience ->
                                    Text(
                                        text = audience,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = readableSecondaryTextColor(),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                                if (accountState.isLoggedIn) {
                                    val isFollowing = subscriptionOverride ?: (
                                        page.isSubscribed ||
                                            accountState.userArtists.any {
                                                it.id == page.id || it.id == page.channelId
                                            }
                                        )
                                    val followContainerColor = if (isFollowing) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    }
                                    val followBorderColor = if (isFollowing) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    }
                                    val followContentColor = if (isFollowing) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(ElovaireRadii.pill),
                                        color = followContainerColor,
                                        border = BorderStroke(1.dp, followBorderColor),
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .semantics {
                                                role = Role.Button
                                                contentDescription = if (isFollowing) {
                                                    followCopy.unfollowCd(page.name)
                                                } else {
                                                    followCopy.followCd(page.name)
                                                }
                                            }
                                            .elovaireClickable(enabled = !isUpdatingSubscription) {
                                                val targetSubscribed = !isFollowing
                                                subscriptionOverride = targetSubscribed
                                                isUpdatingSubscription = true
                                                scope.launch {
                                                    val ok = try {
                                                        youTubeCatalog.setArtistSubscription(
                                                            channelId = page.channelId,
                                                            artistSummary = YouTubeArtistSummary(
                                                                id = page.id,
                                                                name = page.name,
                                                                thumbnailUri = page.bannerUri,
                                                                subscriberCount = page.monthlyAudience,
                                                            ),
                                                            subscribed = targetSubscribed,
                                                        )
                                                    } finally {
                                                        isUpdatingSubscription = false
                                                    }
                                                    if (!ok) {
                                                        subscriptionOverride = null
                                                    }
                                                }
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            androidx.compose.material3.Icon(
                                                painter = painterResource(
                                                    id = if (isFollowing) R.drawable.ic_lucide_check else R.drawable.ic_lucide_plus,
                                                ),
                                                contentDescription = null,
                                                tint = followContentColor,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Text(
                                                text = if (isFollowing) followCopy.following else followCopy.follow,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = followContentColor,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (playableSongs.isNotEmpty()) {
                        item(key = "yt_artist_actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ElovaireSpacing.screenHorizontalPadding, vertical = ElovaireSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.lg, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtistCircularQuickAction(
                                    label = shuffleLabel,
                                    iconResId = R.drawable.ic_lucide_shuffle,
                                    onClick = { onShuffleSongs(playableSongs, page.name) },
                                )
                                Surface(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = ytCopy.playAll
                                        }
                                        .elovaireClickable { onPlaySongs(playableSongs.first(), playableSongs, page.name) },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 10.dp,
                                    tonalElevation = 0.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Icon(
                                            painter = painterResource(id = R.drawable.ic_lucide_play),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                ArtistCircularQuickAction(
                                    label = ytCopy.radioLabel,
                                    iconResId = R.drawable.ic_lucide_audio_waveform,
                                    onClick = {
                                        val seed = playableSongs.first()
                                        val videoId = YouTubeUri.videoId(seed.uri) ?: return@ArtistCircularQuickAction
                                        scope.launch {
                                            val related = runCatching {
                                                youTubeCatalog.getRelatedSongs(videoId)
                                            }.getOrDefault(emptyList())
                                            val queue = listOf(seed) + related.filterNot { it.id == seed.id }
                                            onPlaySongs(seed, queue, page.name)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (page.topSongs.isNotEmpty()) {
                        item(key = "yt_artist_top_songs") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                verticalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = ytCopy.topSongsTitle,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = elovaireScaledSp(21f),
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(ElovaireRadii.pill),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                        modifier = Modifier.elovaireClickable {
                                            onPlaySongs(page.topSongs.first(), playableSongs, page.name)
                                        },
                                    ) {
                                        Text(
                                            text = ytCopy.playAll,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                                ModuleCard(
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Column {
                                        page.topSongs.forEachIndexed { index, song ->
                                            HomeRecentSongRow(
                                                song = song,
                                                isFavorite = song.id in favoriteSongIds,
                                                onClick = { onPlaySongs(song, playableSongs, page.name) },
                                                onToggleFavorite = { onToggleFavorite(song.id) },
                                                showDivider = index != page.topSongs.lastIndex,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (page.albums.isNotEmpty()) {
                        item(key = "yt_artist_albums") {
                            ArtistCollectionsCarousel(
                                title = commonCopy.albums,
                                collections = page.albums,
                                onCollectionClick = { item -> onCollectionSelected(item, true) },
                            )
                        }
                    }

                    if (page.singles.isNotEmpty()) {
                        item(key = "yt_artist_singles") {
                            ArtistCollectionsCarousel(
                                title = ytCopy.singlesTitle,
                                collections = page.singles,
                                onCollectionClick = { item -> onCollectionSelected(item, true) },
                            )
                        }
                    }

                    if (page.similarArtists.isNotEmpty()) {
                        item(key = "yt_artist_similar") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = ytCopy.fansMightAlsoLike,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = elovaireScaledSp(21f),
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(ElovaireSpacing.md),
                                ) {
                                    page.similarArtists.forEach { similar ->
                                        Column(
                                            modifier = Modifier
                                                .width(104.dp)
                                                .elovaireClickable { onArtistSelected(similar.id) },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            ArtworkImage(
                                                uri = similar.thumbnailUri,
                                                title = similar.name,
                                                modifier = Modifier
                                                    .size(96.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                                        shape = CircleShape,
                                                    ),
                                                cornerRadius = 48.dp,
                                                showArtworkGlow = false,
                                            )
                                            Text(
                                                text = similar.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    page.bio?.takeIf(String::isNotBlank)?.let { bio ->
                        item(key = "yt_artist_bio") {
                            Box(modifier = Modifier.padding(horizontal = elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing.screenHorizontalPadding)) {
                                ModuleCard(
                                    contentPadding = PaddingValues(vertical = elovaire.music.droidbeauty.app.ui.theme.ElovaireSpacing.xs),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .elovaireClickable { bioExpanded = !bioExpanded },
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = ytCopy.aboutArtist,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = readableSecondaryTextColor(),
                                            maxLines = if (bioExpanded) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        PinnedBackTopBar(
            title = artistPage?.name.orEmpty(),
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ArtistCircularQuickAction(
    label: String,
    iconResId: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .elovaireClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = elovaireScaledSp(11.5f),
                fontWeight = FontWeight.Medium,
            ),
            color = readableSecondaryTextColor(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCollectionsCarousel(
    title: String,
    collections: List<YouTubeCollectionSummary>,
    onCollectionClick: (YouTubeCollectionSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = elovaireScaledSp(21f),
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = ElovaireSpacing.screenHorizontalPadding),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            collections.forEach { item ->
                Column(
                    modifier = Modifier
                        .width(152.dp)
                        .elovaireClickable { onCollectionClick(item) },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArtworkImage(
                        uri = item.thumbnailUri,
                        title = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        cornerRadius = ElovaireRadii.artwork,
                        showArtworkGlow = false,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(
                        item.releaseYear?.toString(),
                        item.subtitle.takeIf(String::isNotBlank),
                    ).joinToString(" • ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = readableSecondaryTextColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
