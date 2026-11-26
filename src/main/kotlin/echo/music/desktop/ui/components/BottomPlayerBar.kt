package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.player.RepeatMode
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun BottomPlayerBar(
    currentSong: SongItem?,
    playbackState: PlaybackState,
    isFavorite: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleQueue: () -> Unit,
    onExpandPlayer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onStartRadio: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onOpenMiniPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        color = NothingColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Song Info
            Row(
                modifier = Modifier
                    .width(280.dp)
                    .clickable(onClick = onExpandPlayer),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSong != null) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NothingColors.SurfaceElevated)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = currentSong.thumbnail,
                            contentDescription = currentSong.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary,
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val firstArtistId = currentSong.artists.firstOrNull()?.id
                        Text(
                            text = currentSong.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (onArtistClick != null && firstArtistId != null) NothingColors.AccentRed.copy(alpha = 0.85f) else NothingColors.TextSecondary,
                                fontSize = 12.sp
                            ),
                            modifier = if (onArtistClick != null && firstArtistId != null) {
                                Modifier.clickable { onArtistClick(firstArtistId) }
                            } else Modifier
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) NothingColors.AccentRed else NothingColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NothingColors.SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = NothingColors.TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Selecciona una canción",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NothingColors.TextMuted,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Center: Playback Controls & Timeline Scrubber
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Control Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Shuffle button
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = if (isShuffle) "Desactivar aleatorio" else "Activar aleatorio",
                            tint = if (isShuffle) NothingColors.AccentRed else NothingColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = onPrevious,
                        enabled = currentSong != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = if (currentSong != null) NothingColors.TextPrimary else NothingColors.TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (currentSong != null) NothingColors.AccentRed else NothingColors.SurfaceElevated)
                            .clickable(enabled = currentSong != null, onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    IconButton(
                        onClick = onNext,
                        enabled = currentSong != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = if (currentSong != null) NothingColors.TextPrimary else NothingColors.TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Repeat button
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(34.dp)
                    ) {
                        val repeatIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Icon(
                            repeatIcon,
                            contentDescription = when (repeatMode) {
                                RepeatMode.OFF -> "Repetición desactivada"
                                RepeatMode.ALL -> "Repetir toda la cola"
                                RepeatMode.ONE -> "Repetir una canción"
                            },
                            tint = if (repeatMode != RepeatMode.OFF) NothingColors.AccentRed else NothingColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Scrubber line
                val currentMs = playbackState.currentPositionMs
                val totalMs = playbackState.durationMs.coerceAtLeast(1L)
                val progressFraction = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMs(currentMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = NothingColors.TextMuted,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.width(42.dp)
                    )

                    Slider(
                        value = progressFraction,
                        onValueChange = onSeek,
                        enabled = currentSong != null && playbackState.durationMs > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = NothingColors.AccentRed,
                            activeTrackColor = NothingColors.AccentRed,
                            inactiveTrackColor = NothingColors.SurfaceBorder,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )

                    Text(
                        text = formatMs(totalMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = NothingColors.TextMuted,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.width(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right: Radio, EQ, Queue, Volume & Expand Buttons
            Row(
                modifier = Modifier.width(320.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Add to playlist button
                if (onAddToPlaylist != null && currentSong != null) {
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Agregar a playlist",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Radio button
                if (onStartRadio != null && currentSong != null) {
                    IconButton(
                        onClick = onStartRadio,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Radio,
                            contentDescription = "Iniciar Radio Automix",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Equalizer button
                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Ecualizador de 10 bandas",
                        tint = NothingColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Queue toggle
                IconButton(onClick = onToggleQueue, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Cola de reproducción",
                        tint = NothingColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Volume Icon with mute toggle
                val vol = playbackState.volume
                val volIcon = when {
                    vol <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                    vol < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                }

                IconButton(
                    onClick = {
                        if (vol > 0.01f) onVolumeChange(0f) else onVolumeChange(0.8f)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        volIcon,
                        contentDescription = "Volumen",
                        tint = NothingColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                SleekVolumeSlider(
                    value = vol,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(96.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (onOpenMiniPlayer != null) {
                    IconButton(onClick = onOpenMiniPlayer, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.PictureInPictureAlt,
                            contentDescription = "Mini reproductor (Ctrl + M)",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expand Full Player
                IconButton(onClick = onExpandPlayer, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = "Expandir reproductor",
                        tint = NothingColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SleekVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press ||
                            (event.type == androidx.compose.ui.input.pointer.PointerEventType.Move && event.changes.any { it.pressed })) {
                            val position = event.changes.firstOrNull()?.position ?: continue
                            val w = size.width.toFloat()
                            if (w > 0) {
                                onValueChange((position.x / w).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthDp = maxWidth
        val fraction = value.coerceIn(0f, 1f)

        // Inactive Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NothingColors.SurfaceElevated)
                .border(0.5.dp, NothingColors.SurfaceBorder, RoundedCornerShape(2.dp))
        )

        // Active Track
        Box(
            modifier = Modifier
                .width(widthDp * fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NothingColors.TextPrimary)
        )

        // Sleek Thumb Dot
        val thumbOffset = ((widthDp * fraction) - 5.dp).coerceAtLeast(0.dp).coerceAtMost(widthDp - 10.dp)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(10.dp)
                .clip(CircleShape)
                .background(NothingColors.TextPrimary)
                .border(1.5.dp, NothingColors.Background, CircleShape)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
