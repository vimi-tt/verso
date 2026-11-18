package elovaire.music.droidbeauty.app.ui.screens

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import elovaire.music.droidbeauty.app.R
import elovaire.music.droidbeauty.app.core.safeOutputDevices
import elovaire.music.droidbeauty.app.data.youtube.YouTubeUri
import elovaire.music.droidbeauty.app.domain.model.AppLanguage
import elovaire.music.droidbeauty.app.domain.model.Song
import elovaire.music.droidbeauty.app.ui.components.ArtworkImage
import elovaire.music.droidbeauty.app.ui.i18n.NowPlayingDeckCopy
import elovaire.music.droidbeauty.app.ui.i18n.localizedArtistName
import elovaire.music.droidbeauty.app.ui.i18n.localizedAudioFormat
import elovaire.music.droidbeauty.app.ui.i18n.localizedExternalAudioText
import elovaire.music.droidbeauty.app.ui.i18n.nowPlayingDeckCopy
import elovaire.music.droidbeauty.app.ui.i18n.youtubeCopy
import elovaire.music.droidbeauty.app.ui.i18n.youtubeDownloadCopy
import elovaire.music.droidbeauty.app.ui.interaction.elovaireClickable
import elovaire.music.droidbeauty.app.ui.interaction.rememberElovaireInteractionSource
import elovaire.music.droidbeauty.app.ui.theme.ElovaireRadii
import elovaire.music.droidbeauty.app.ui.theme.elovaireScaledSp

@Immutable
internal data class ActiveAudioOutputUi(
    val label: String,
    @param:DrawableRes val iconResId: Int,
)

@Composable
internal fun rememberActiveAudioOutputDevice(
    language: AppLanguage,
    songKey: Long?,
): ActiveAudioOutputUi {
    val context = LocalContext.current
    val copy = remember(language) { nowPlayingDeckCopy(language) }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    return remember(audioManager, copy, songKey) {
        resolveActiveAudioOutputUi(audioManager, copy)
    }
}

private fun resolveActiveAudioOutputUi(
    audioManager: AudioManager?,
    copy: NowPlayingDeckCopy,
): ActiveAudioOutputUi {
    val outputs = audioManager?.safeOutputDevices()?.filter { it.isSink }.orEmpty()
    val preferred = outputs.firstOrNull { isUsbDeviceType(it.type) }
        ?: outputs.firstOrNull { isBluetoothDeviceType(it.type) }
        ?: outputs.firstOrNull { isWiredHeadsetType(it.type) }
        ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        ?: outputs.firstOrNull()

    if (preferred == null) {
        return ActiveAudioOutputUi(
            label = copy.speakerOutputLabel,
            iconResId = R.drawable.ic_lucide_smartphone,
        )
    }

    val productName = runCatching { preferred.productName?.toString()?.trim().orEmpty() }.getOrDefault("")
    val fallbackLabel = when {
        isUsbDeviceType(preferred.type) -> copy.usbAudioOutputLabel
        isBluetoothDeviceType(preferred.type) -> copy.bluetoothOutputLabel
        isWiredHeadsetType(preferred.type) -> copy.headphonesOutputLabel
        else -> copy.speakerOutputLabel
    }
    val label = if (
        productName.isNotBlank() &&
        !productName.equals(Build.MODEL, ignoreCase = true) &&
        preferred.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    ) {
        productName
    } else {
        fallbackLabel
    }
    val iconResId = when {
        isUsbDeviceType(preferred.type) -> R.drawable.ic_lucide_audio_waveform
        isBluetoothDeviceType(preferred.type) || isWiredHeadsetType(preferred.type) -> R.drawable.ic_lucide_disc_3
        else -> R.drawable.ic_lucide_smartphone
    }
    return ActiveAudioOutputUi(label = label, iconResId = iconResId)
}

private fun isUsbDeviceType(type: Int): Boolean {
    return type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        type == AudioDeviceInfo.TYPE_USB_ACCESSORY
}

private fun isBluetoothDeviceType(type: Int): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
}

private fun isWiredHeadsetType(type: Int): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
}

@Composable
internal fun AudioOutputRoutePill(
    output: ActiveAudioOutputUi,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(ElovaireRadii.pill)).clickable(onClick = onClick),
        shape = RoundedCornerShape(ElovaireRadii.pill),
        color = tint.copy(alpha = 0.2f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = output.iconResId),
                contentDescription = null,
                tint = tint.copy(alpha = 0.82f),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = output.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = elovaireScaledSp(11f),
                    fontWeight = FontWeight.Medium,
                ),
                color = tint.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NowPlayingMoreActionsSheet(
    song: Song,
    language: AppLanguage,
    surfaceColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    isYouTubeDownloaded: Boolean = false,
    isYouTubeDownloading: Boolean = false,
    onDownloadSong: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onAddToPlaylist: () -> Unit,
    onSleepTimer: () -> Unit,
    onEqualizer: () -> Unit,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)?,
    onOpenCredits: () -> Unit,
    onShareCard: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val copy = remember(language) { nowPlayingDeckCopy(language) }
    val downloadCopy = remember(language) { youtubeDownloadCopy(language) }
    val isYouTubeTrack = remember(song.uri) { YouTubeUri.isYouTubeUri(song.uri) }
    val backdropInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(ElovaireRadii.module),
            color = surfaceColor,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        uri = song.artUri,
                        title = localizedExternalAudioText(song.title, song.id, language),
                        placeholderIconResId = R.drawable.ic_lucide_music,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = ElovaireRadii.artworkSmall,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = localizedExternalAudioText(song.title, song.id, language),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = localizedArtistName(song.artist, language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isYouTubeTrack) {
                    when {
                        isYouTubeDownloaded && onRemoveDownload != null -> {
                            PlayerSheetActionRow(
                                iconResId = R.drawable.ic_lucide_trash_2,
                                label = downloadCopy.removeDownloadAction,
                                tint = contentColor,
                                onClick = {
                                    onDismiss()
                                    onRemoveDownload()
                                },
                            )
                        }
                        isYouTubeDownloading -> {
                            PlayerSheetActionRow(
                                iconResId = R.drawable.ic_lucide_download,
                                label = downloadCopy.downloadingAction,
                                tint = secondaryContentColor,
                                onClick = onDismiss,
                            )
                        }
                        onDownloadSong != null -> {
                            PlayerSheetActionRow(
                                iconResId = R.drawable.ic_lucide_download,
                                label = downloadCopy.downloadAction,
                                tint = contentColor,
                                onClick = {
                                    onDismiss()
                                    onDownloadSong()
                                },
                            )
                        }
                    }
                }
                if (onShareCard != null) {
                    PlayerSheetActionRow(
                        iconResId = R.drawable.ic_lucide_share_2,
                        label = when (language) {
                            AppLanguage.Portuguese -> "Compartilhar card"
                            AppLanguage.Spanish -> "Compartir tarjeta"
                            else -> "Share card"
                        },
                        tint = contentColor,
                        onClick = {
                            onDismiss()
                            onShareCard()
                        },
                    )
                }
                PlayerSheetActionRow(
                    iconResId = R.drawable.ic_lucide_list_plus,
                    label = copy.addToPlaylistLabel,
                    tint = contentColor,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    },
                )
                PlayerSheetActionRow(
                    iconResId = R.drawable.ic_lucide_timer,
                    label = copy.sleepTimerLabel,
                    tint = contentColor,
                    onClick = {
                        onDismiss()
                        onSleepTimer()
                    },
                )
                PlayerSheetActionRow(
                    iconResId = R.drawable.ic_lucide_sliders_vertical,
                    label = copy.equalizerLabel,
                    tint = contentColor,
                    onClick = {
                        onDismiss()
                        onEqualizer()
                    },
                )
                if (onViewArtist != null) {
                    PlayerSheetActionRow(
                        iconResId = R.drawable.ic_lucide_user_round,
                        label = copy.artistRoleLabel,
                        tint = contentColor,
                        onClick = {
                            onDismiss()
                            onViewArtist()
                        },
                    )
                }
                if (onViewAlbum != null) {
                    PlayerSheetActionRow(
                        iconResId = R.drawable.ic_lucide_disc_album,
                        label = copy.viewAlbumLabel,
                        tint = contentColor,
                        onClick = {
                            onDismiss()
                            onViewAlbum()
                        },
                    )
                }
                PlayerSheetActionRow(
                    iconResId = R.drawable.ic_lucide_info,
                    label = copy.creditsLabel,
                    tint = contentColor,
                    onClick = {
                        onDismiss()
                        onOpenCredits()
                    },
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingDestinationPopup(
    song: Song,
    language: AppLanguage,
    surfaceColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    resolvedAlbumTitle: String? = null,
    onOpenArtist: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val copy = remember(language) { nowPlayingDeckCopy(language) }
    val backdropInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val displayTitle = localizedExternalAudioText(song.title, song.id, language)
    val displayArtist = localizedArtistName(song.artist, language)
    val displayAlbum = song.album.takeIf { it.isNotBlank() && !it.equals("Unknown Album", ignoreCase = true) }
        ?: resolvedAlbumTitle?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(ElovaireRadii.module),
            color = surfaceColor,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.14f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        uri = song.artUri,
                        title = displayTitle,
                        placeholderIconResId = R.drawable.ic_lucide_music,
                        modifier = Modifier.size(40.dp),
                        cornerRadius = ElovaireRadii.artworkSmall,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onOpenArtist != null) {
                    DestinationPopupOptionRow(
                        iconResId = R.drawable.ic_lucide_user_round,
                        overline = copy.artistRoleLabel,
                        title = displayArtist,
                        contentColor = contentColor,
                        secondaryColor = secondaryContentColor,
                        onClick = {
                            onDismiss()
                            onOpenArtist()
                        },
                    )
                }
                if (onOpenAlbum != null && displayAlbum != null) {
                    DestinationPopupOptionRow(
                        iconResId = R.drawable.ic_lucide_disc_album,
                        overline = copy.albumRoleLabel,
                        title = displayAlbum,
                        contentColor = contentColor,
                        secondaryColor = secondaryContentColor,
                        onClick = {
                            onDismiss()
                            onOpenAlbum()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationPopupOptionRow(
    @DrawableRes iconResId: Int,
    overline: String,
    title: String,
    contentColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = rememberElovaireInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .background(contentColor.copy(alpha = 0.06f))
            .elovaireClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerSheetActionRow(
    @DrawableRes iconResId: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = rememberElovaireInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .elovaireClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = tint.copy(alpha = 0.88f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NowPlayingCreditsOverlay(
    song: Song,
    language: AppLanguage,
    surfaceColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    resolvedStreamFormat: String? = null,
    resolvedStreamQuality: String? = null,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val copy = remember(language) { nowPlayingDeckCopy(language) }
    val ytCopy = remember(language) { youtubeCopy(language) }
    val isYouTube = YouTubeUri.isYouTubeUri(song.uri)
    val displayTitle = localizedExternalAudioText(song.title, song.id, language)
    val displayArtist = localizedArtistName(song.artist, language)
    val displayFormat = localizedAudioFormat(resolvedStreamFormat ?: song.audioFormat, language)
    val displayQuality = resolvedStreamQuality?.takeIf { it.isNotBlank() }
        ?: song.audioQuality?.takeIf { it.isNotBlank() }
        ?: "--"
    val displayDuration = formatDuration(song.durationMs)
    val sourceLabel = if (isYouTube) ytCopy.sourceLabel else copy.speakerOutputLabel.takeIf { false } ?: displayFormat

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copy.creditsLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                )
                HeaderIconButton(
                    iconResId = R.drawable.ic_lucide_x,
                    contentDescription = "",
                    showBackground = true,
                    tint = contentColor,
                    onClick = onDismiss,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ElovaireRadii.card))
                        .background(contentColor.copy(alpha = 0.08f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        uri = song.artUri,
                        title = displayTitle,
                        placeholderIconResId = R.drawable.ic_lucide_music,
                        modifier = Modifier.size(60.dp),
                        cornerRadius = ElovaireRadii.artworkSmall,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = copy.aboutTrackLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                CreditMetadataRow(label = copy.artistRoleLabel, value = displayArtist, contentColor = contentColor, secondaryColor = secondaryContentColor)
                if (song.album.isNotBlank()) {
                    CreditMetadataRow(label = copy.albumRoleLabel, value = song.album, contentColor = contentColor, secondaryColor = secondaryContentColor)
                }
                CreditMetadataRow(label = copy.formatRoleLabel, value = displayFormat, contentColor = contentColor, secondaryColor = secondaryContentColor)
                CreditMetadataRow(label = copy.qualityRoleLabel, value = displayQuality, contentColor = contentColor, secondaryColor = secondaryContentColor)
                CreditMetadataRow(label = copy.durationRoleLabel, value = displayDuration, contentColor = contentColor, secondaryColor = secondaryContentColor)
                if (isYouTube) {
                    CreditMetadataRow(label = copy.sourceRoleLabel, value = sourceLabel, contentColor = contentColor, secondaryColor = secondaryContentColor)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .clickable(onClick = onOpenEqualizer),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                    color = contentColor.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_sliders_vertical),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = copy.equalizerLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ElovaireRadii.pill))
                        .clickable(onClick = onAddToPlaylist),
                    shape = RoundedCornerShape(ElovaireRadii.pill),
                    color = contentColor.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_list_plus),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = copy.addToPlaylistLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditMetadataRow(
    label: String,
    value: String,
    contentColor: Color,
    secondaryColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElovaireRadii.card))
            .border(1.dp, contentColor.copy(alpha = 0.08f), RoundedCornerShape(ElovaireRadii.card))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NowPlayingExploreOverlay(
    currentSong: Song,
    queue: List<Song>,
    allSongsById: Map<Long, Song>,
    language: AppLanguage,
    surfaceColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val copy = remember(language) { nowPlayingDeckCopy(language) }
    val displayArtist = localizedArtistName(currentSong.artist, language)
    val relatedTracks = remember(currentSong.id, currentSong.artist, currentSong.album, queue, allSongsById) {
        val candidates = LinkedHashMap<Long, Song>()
        allSongsById.values.forEach { candidate ->
            if (candidate.id != currentSong.id &&
                (candidate.artist.equals(currentSong.artist, ignoreCase = true) ||
                    (currentSong.album.isNotBlank() && candidate.album.equals(currentSong.album, ignoreCase = true)))
            ) {
                candidates[candidate.id] = candidate
            }
        }
        queue.forEach { candidate ->
            if (candidate.id != currentSong.id && !candidates.containsKey(candidate.id)) {
                candidates[candidate.id] = candidate
            }
        }
        candidates.values.take(30)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.exploreLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = contentColor,
                    )
                    Text(
                        text = String.format(copy.moreFromArtistFormat, displayArtist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HeaderIconButton(
                    iconResId = R.drawable.ic_lucide_x,
                    contentDescription = "",
                    showBackground = true,
                    tint = contentColor,
                    onClick = onDismiss,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (relatedTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = copy.noRelatedTracks,
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondaryContentColor,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(relatedTracks, key = { it.id }) { song ->
                        val interactionSource = rememberElovaireInteractionSource()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElovaireRadii.card))
                                .background(contentColor.copy(alpha = 0.06f))
                                .elovaireClickable(interactionSource = interactionSource) {
                                    onPlaySong(song, relatedTracks)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkImage(
                                uri = song.artUri,
                                title = localizedExternalAudioText(song.title, song.id, language),
                                placeholderIconResId = R.drawable.ic_lucide_music,
                                modifier = Modifier.size(48.dp),
                                cornerRadius = ElovaireRadii.artworkSmall,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = localizedExternalAudioText(song.title, song.id, language),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = localizedArtistName(song.artist, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryContentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = formatDuration(song.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryContentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
