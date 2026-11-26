package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import echo.music.desktop.lyrics.LyricsProviderId
import echo.music.desktop.lyrics.SongLyrics
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.player.RepeatMode
import echo.music.desktop.ui.theme.NothingColors

enum class ExpandedRightTab(val title: String) {
    QUEUE("Cola de reproducción"),
    LYRICS("Letras sincronizadas"),
    COMMENTS("Comentarios")
}

@Composable
fun ExpandedPlayerModal(
    currentSong: SongItem?,
    playbackState: PlaybackState,
    queue: List<SongItem>,
    currentIndex: Int,
    isFavorite: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    lyrics: SongLyrics?,
    isLyricsLoading: Boolean,
    lyricsSwitchingTo: LyricsProviderId? = null,
    onSelectLyricsProvider: (LyricsProviderId) -> Unit = {},
    lyricsExtras: echo.music.desktop.state.LyricsExtras = echo.music.desktop.state.LyricsExtras(),
    romanizeLyrics: Boolean = true,
    onToggleRomanize: () -> Unit = {},
    onTranslateLyrics: () -> Unit = {},
    onHideLyricsTranslation: () -> Unit = {},
    isAmbientGlowEnabled: Boolean = true,
    sleepTimerRemainingSeconds: Long? = null,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectQueueItem: (SongItem) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onStartRadio: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onDislike: () -> Unit = {},
    canvasFile: java.io.File? = null,
    comments: echo.music.desktop.state.CommentsState = echo.music.desktop.state.CommentsState(),
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    /** Actions for any song in the queue (⋮ menu). */
    onQueueAddToPlaylist: (SongItem) -> Unit = {},
    onQueueDislike: (SongItem) -> Unit = {},
    onQueueBlockArtist: (SongItem) -> Unit = {},
    onSaveQueueToPlaylist: () -> Unit = {},
    onToggleAmbientGlow: () -> Unit = {},
    onStartSleepTimer: (Int) -> Unit = {},
    onSetSleepTimerEndOfSong: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onDownloadSong: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedRightTab by remember { mutableStateOf(ExpandedRightTab.QUEUE) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NothingColors.Background.copy(alpha = 0.98f))
            // Swallow clicks on empty areas so they never reach the screen behind (Home, playlists...).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = NothingColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NothingColors.AccentRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REPRODUCIENDO AHORA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = NothingColors.TextSecondary
                        )
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = NothingColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Main Content: 2-column layout (Left: Album Art & Controls, Right: Queue / Lyrics)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // Left Column: Artwork & Player Controls
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (currentSong != null) {
                        // Big Cover Art with Ambient Glow
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            AmbientGlow(
                                enabled = isAmbientGlowEnabled,
                                size = 340.dp
                            )

                            Box(
                                modifier = Modifier
                                    .size(340.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = currentSong.thumbnail,
                                    contentDescription = currentSong.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Canvas: animated cover over the static one (fades in when ready).
                                if (canvasFile != null) {
                                    androidx.compose.runtime.key(canvasFile) {
                                        echo.music.desktop.canvas.CanvasVideoView(canvasFile, Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Title, Artist, Favorite
                        Row(
                            modifier = Modifier.width(340.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NothingColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val firstArtistId = currentSong.artists.firstOrNull()?.id
                                Text(
                                    text = currentSong.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (onArtistClick != null && firstArtistId != null) NothingColors.AccentRed else NothingColors.TextSecondary
                                    ),
                                    modifier = if (onArtistClick != null && firstArtistId != null) {
                                        Modifier.clickable { onArtistClick(firstArtistId) }
                                    } else Modifier
                                )
                            }

                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (isFavorite) NothingColors.AccentRed else NothingColors.TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Timeline Scrubber
                        val currentMs = playbackState.currentPositionMs
                        val totalMs = playbackState.durationMs.coerceAtLeast(1L)
                        val progressFraction = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                        Column(modifier = Modifier.width(340.dp)) {
                            Slider(
                                value = progressFraction,
                                onValueChange = onSeek,
                                enabled = playbackState.durationMs > 0,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = NothingColors.AccentRed,
                                    activeTrackColor = NothingColors.AccentRed,
                                    inactiveTrackColor = NothingColors.SurfaceBorder,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMs(currentMs),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingColors.TextMuted
                                    )
                                )
                                Text(
                                    text = formatMs(totalMs),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingColors.TextMuted
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Main Controls Row with Shuffle and Repeat
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(42.dp)) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = if (isShuffle) NothingColors.AccentRed else NothingColors.TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = NothingColors.TextPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(NothingColors.AccentRed)
                                    .clickable(onClick = onTogglePlayPause),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playbackState.isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = NothingColors.TextPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            IconButton(onClick = onToggleRepeat, modifier = Modifier.size(42.dp)) {
                                val repIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                                Icon(
                                    repIcon,
                                    contentDescription = "Repetir",
                                    tint = if (repeatMode != RepeatMode.OFF) NothingColors.AccentRed else NothingColors.TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Quick Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Radio Automix Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable(onClick = onStartRadio)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Radio,
                                        contentDescription = null,
                                        tint = NothingColors.AccentRed,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Iniciar Radio",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = NothingColors.TextPrimary
                                        )
                                    )
                                }
                            }

                            // Dislike Button (hides the song and skips it)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable(onClick = onDislike)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    Icons.Default.ThumbDown,
                                    contentDescription = "No me gusta (saltar y no recomendar)",
                                    tint = NothingColors.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Add to Playlist Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable(onClick = onAddToPlaylist)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        tint = NothingColors.AccentRed,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Agregar a playlist",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = NothingColors.TextPrimary
                                        )
                                    )
                                }
                            }

                            // Equalizer Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenEqualizer)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = NothingColors.TextSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ecualizador",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = NothingColors.TextPrimary
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2: Sleep Timer, Ambient Glow Aura, and Download
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sleep Timer Button with Dropdown
                            var showSleepMenu by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sleepTimerRemainingSeconds != null) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (sleepTimerRemainingSeconds != null) NothingColors.AccentRed else NothingColors.SurfaceBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { showSleepMenu = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = if (sleepTimerRemainingSeconds != null) NothingColors.AccentRed else NothingColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val timerText = if (sleepTimerRemainingSeconds != null) {
                                            val mins = sleepTimerRemainingSeconds / 60
                                            val secs = sleepTimerRemainingSeconds % 60
                                            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
                                        } else {
                                            "Temporizador"
                                        }
                                        Text(
                                            text = timerText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sleepTimerRemainingSeconds != null) NothingColors.AccentRed else NothingColors.TextPrimary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSleepMenu,
                                    onDismissRequest = { showSleepMenu = false },
                                    modifier = Modifier
                                        .background(NothingColors.SurfaceElevated)
                                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("15 minutos", color = NothingColors.TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onStartSleepTimer(15)
                                            showSleepMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("30 minutos", color = NothingColors.TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onStartSleepTimer(30)
                                            showSleepMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("45 minutos", color = NothingColors.TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onStartSleepTimer(45)
                                            showSleepMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("60 minutos", color = NothingColors.TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onStartSleepTimer(60)
                                            showSleepMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Al terminar la canción", color = NothingColors.TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onSetSleepTimerEndOfSong()
                                            showSleepMenu = false
                                        }
                                    )
                                    if (sleepTimerRemainingSeconds != null) {
                                        HorizontalDivider(color = NothingColors.SurfaceBorder)
                                        DropdownMenuItem(
                                            text = { Text("Desactivar temporizador", color = NothingColors.AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                onCancelSleepTimer()
                                                showSleepMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Ambient Glow Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAmbientGlowEnabled) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isAmbientGlowEnabled) NothingColors.AccentRed.copy(alpha = 0.5f) else NothingColors.SurfaceBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(onClick = onToggleAmbientGlow)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Flare,
                                        contentDescription = null,
                                        tint = if (isAmbientGlowEnabled) NothingColors.AccentRed else NothingColors.TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAmbientGlowEnabled) "Aura ON" else "Aura OFF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAmbientGlowEnabled) NothingColors.AccentRed else NothingColors.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // Download Current Track Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDownloaded) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isDownloaded) NothingColors.AccentRed.copy(alpha = 0.5f) else NothingColors.SurfaceBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { if (!isDownloaded) onDownloadSong() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (downloadProgress != null) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            color = NothingColors.AccentRed,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${(downloadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingColors.AccentRed,
                                                fontSize = 11.sp
                                            )
                                        )
                                    } else if (isDownloaded) {
                                        Icon(
                                            Icons.Default.DownloadDone,
                                            contentDescription = null,
                                            tint = NothingColors.AccentRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Descargada",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingColors.AccentRed,
                                                fontSize = 11.sp
                                            )
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            tint = NothingColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Descargar",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingColors.TextPrimary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No hay canción en reproducción",
                            style = MaterialTheme.typography.titleMedium.copy(color = NothingColors.TextMuted)
                        )
                    }
                }

                // Right Column: Tab Switcher (Cola de reproducción / Letras)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(NothingColors.Surface)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    // Tab Header Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpandedRightTab.entries.forEach { tab ->
                                val isSelected = selectedRightTab == tab
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NothingColors.SurfaceElevated else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (isSelected) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedRightTab = tab }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) NothingColors.TextPrimary else NothingColors.TextMuted,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (selectedRightTab == ExpandedRightTab.QUEUE && queue.isNotEmpty()) {
                            IconButton(onClick = onSaveQueueToPlaylist, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = "Guardar cola en una playlist",
                                    tint = NothingColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onClearQueue, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Limpiar cola",
                                    tint = NothingColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Content Switcher
                    when (selectedRightTab) {
                        ExpandedRightTab.QUEUE -> {
                            if (queue.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "La cola está vacía",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextMuted)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(queue) { index, item ->
                                        val isCurrent = (item.id == currentSong?.id) || (index == currentIndex)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrent) NothingColors.SurfaceElevated else Color.Transparent)
                                                .clickable { onSelectQueueItem(item) }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(NothingColors.SurfaceElevated)
                                            ) {
                                                AsyncImage(
                                                    model = item.thumbnail,
                                                    contentDescription = item.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary
                                                    )
                                                )
                                                Text(
                                                    text = item.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = NothingColors.TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }

                                            SongMoreMenuButton(
                                                song = item,
                                                onAddToPlaylist = { onQueueAddToPlaylist(item) },
                                                onDislike = { onQueueDislike(item) },
                                                onBlockArtist = item.artists.firstOrNull()?.let { { onQueueBlockArtist(item) } },
                                                buttonSize = 28
                                            )

                                            IconButton(
                                                onClick = { onRemoveFromQueue(index) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Quitar de la cola",
                                                    tint = NothingColors.TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ExpandedRightTab.COMMENTS -> {
                            CommentsPanel(
                                state = comments,
                                songId = currentSong?.id,
                                onLoad = onLoadComments,
                                onLoadMore = onLoadMoreComments
                            )
                        }

                        ExpandedRightTab.LYRICS -> {
                            LyricsPanel(
                                lyrics = lyrics,
                                isLoading = isLyricsLoading,
                                switchingTo = lyricsSwitchingTo,
                                playbackState = playbackState,
                                onSeekToMs = { ms ->
                                    if (playbackState.durationMs > 0) {
                                        onSeek((ms.toFloat() / playbackState.durationMs).coerceIn(0f, 1f))
                                    }
                                },
                                onSelectProvider = onSelectLyricsProvider,
                                extras = lyricsExtras,
                                romanizeEnabled = romanizeLyrics,
                                onToggleRomanize = onToggleRomanize,
                                onTranslate = onTranslateLyrics,
                                onHideTranslation = onHideLyricsTranslation
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
