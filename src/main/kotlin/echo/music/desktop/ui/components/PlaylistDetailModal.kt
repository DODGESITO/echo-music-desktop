package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
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
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.UserPlaylist
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun PlaylistDetailModal(
    playlist: UserPlaylist,
    appModel: DesktopAppModel,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val playlistDownloadingMap by appModel.playlistDownloading.collectAsState()
    val isPlaylistDownloading = playlistDownloadingMap[playlist.id] == true
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadProgressMap by appModel.downloadProgress.collectAsState()
    val downloadedCount = remember(playlist.songs, downloadedSongs) {
        playlist.songs.count { appModel.isDownloaded(it.id) }
    }
    val isFullyDownloaded = playlist.songs.isNotEmpty() && downloadedCount == playlist.songs.size
    val userPlaylists by appModel.userPlaylists.collectAsState()
    // Opened from Home/search and not saved yet: offer "Guardar" instead of sync/delete.
    val isInLibrary = userPlaylists.any { it.id == playlist.id }

    // Semi-transparent backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main Content Card
        Box(
            modifier = Modifier
                .width(900.dp)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click through */ }
                )
                .padding(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top close bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (playlist.sourceType) {
                                        PlaylistSourceType.SPOTIFY -> Color(0xFF1DB954)
                                        PlaylistSourceType.YOUTUBE_MUSIC -> NothingColors.AccentRed
                                        PlaylistSourceType.LOCAL -> NothingColors.TextSecondary
                                        PlaylistSourceType.PODCAST -> Color(0xFF9C6ADE)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DETALLE DE LISTA DE REPRODUCCIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = NothingColors.TextMuted
                            )
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playlist Banner (Cover + Metadata + Actions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NothingColors.Surface)
                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NothingColors.SurfaceElevated)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!playlist.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = playlist.thumbnailUrl,
                                contentDescription = playlist.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Info and Buttons
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge
                        val (badgeText, badgeBg, badgeColor) = when (playlist.sourceType) {
                            PlaylistSourceType.SPOTIFY -> Triple("SPOTIFY", Color(0xFF1DB954).copy(alpha = 0.2f), Color(0xFF1DB954))
                            PlaylistSourceType.YOUTUBE_MUSIC -> Triple("YOUTUBE MUSIC", NothingColors.AccentRed.copy(alpha = 0.2f), NothingColors.AccentRed)
                            PlaylistSourceType.LOCAL -> Triple("LOCAL", NothingColors.SurfaceBorder, NothingColors.TextSecondary)
                            PlaylistSourceType.PODCAST -> Triple("PODCAST", Color(0xFF9C6ADE).copy(alpha = 0.2f), Color(0xFF9C6ADE))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${playlist.author} • ${playlist.songs.size} canciones",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NothingColors.TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (playlist.songs.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        appModel.playPlaylist(playlist, startIndex = 0)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingColors.AccentRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reproducir todo",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            if (playlist.sourceType == PlaylistSourceType.YOUTUBE_MUSIC && isInLibrary) {
                                OutlinedButton(
                                    onClick = {
                                        isRefreshing = true
                                        appModel.refreshYouTubePlaylist(playlist) {
                                            isRefreshing = false
                                        }
                                    },
                                    enabled = !isRefreshing,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isRefreshing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = NothingColors.AccentRed,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isRefreshing) "Sincronizando..." else "Sincronizar pistas",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                    }
                                }
                            }

                            if (playlist.songs.isNotEmpty()) {
                                if (isPlaylistDownloading) {
                                    OutlinedButton(
                                        onClick = { appModel.cancelPlaylistDownload(playlist.id) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = NothingColors.AccentRed
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.AccentRed.copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = NothingColors.AccentRed,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Descargando ($downloadedCount/${playlist.songs.size})...",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                } else if (isFullyDownloaded) {
                                    OutlinedButton(
                                        onClick = { /* Completa */ },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFF4CAF50)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.DownloadDone,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Descargada (${playlist.songs.size})",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { appModel.downloadPlaylist(playlist) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (downloadedCount > 0) "Descargar restantes (${playlist.songs.size - downloadedCount})" else "Descargar playlist",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isInLibrary) {
                                Button(
                                    onClick = { appModel.savePlaylistToLibrary(playlist) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingColors.SurfaceBorder,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LibraryAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Guardar en biblioteca", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                }
                            } else OutlinedButton(
                                onClick = {
                                    appModel.deletePlaylist(playlist.id)
                                    onClose()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = NothingColors.AccentRed
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = NothingColors.AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Eliminar",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Song List
                if (playlist.songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Esta lista de reproducción no contiene canciones disponibles.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextMuted)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(playlist.songs) { index, song ->
                            val isCurrent = currentSong?.id == song.id
                            val isPlaying = isCurrent && playbackState.isPlaying
                            val isFav = appModel.isFavorite(song.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCurrent) NothingColors.SurfaceElevated else NothingColors.Surface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        appModel.playPlaylist(playlist, startIndex = index)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Monospace index
                                Text(
                                    text = String.format("%02d", index + 1),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextMuted,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(28.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NothingColors.SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!song.thumbnail.isNullOrBlank()) {
                                        AsyncImage(
                                            model = song.thumbnail,
                                            contentDescription = song.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = NothingColors.TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Song details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artists.joinToString(", ") { it.name }.ifEmpty { "Desconocido" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = NothingColors.TextSecondary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Duration
                                val dur = song.duration ?: 0
                                if (dur > 0) {
                                    val minutes = dur / 60
                                    val seconds = dur % 60
                                    Text(
                                        text = String.format("%d:%02d", minutes, seconds),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = NothingColors.TextMuted,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }

                                // Add to another playlist
                                IconButton(
                                    onClick = { appModel.openAddToPlaylist(listOf(song)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = "Agregar a otra playlist",
                                        tint = NothingColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Remove from this playlist (local/imported copies only; YouTube needs extra ids)
                                if (playlist.sourceType != PlaylistSourceType.YOUTUBE_MUSIC && isInLibrary) {
                                    IconButton(
                                        onClick = { appModel.removeSongFromPlaylist(playlist.id, song.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Quitar de esta playlist",
                                            tint = NothingColors.TextMuted,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Download Action
                                val isSongDownloaded = appModel.isDownloaded(song.id)
                                val songProg = downloadProgressMap[song.id]

                                if (songProg != null) {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { songProg },
                                            modifier = Modifier.size(16.dp),
                                            color = NothingColors.AccentRed,
                                            trackColor = NothingColors.SurfaceBorder,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (isSongDownloaded) {
                                                appModel.deleteDownload(song.id)
                                            } else {
                                                appModel.downloadSong(song)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isSongDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                            contentDescription = if (isSongDownloaded) "Descargada (sin conexión)" else "Descargar pista",
                                            tint = if (isSongDownloaded) Color(0xFF4CAF50) else NothingColors.TextMuted,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Favorite
                                IconButton(
                                    onClick = { appModel.toggleFavorite(song) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorito",
                                        tint = if (isFav) NothingColors.AccentRed else NothingColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // More: radio, "No me gusta", block artist
                                SongMoreMenuButton(song = song, appModel = appModel)

                                // Play icon
                                IconButton(
                                    onClick = { appModel.playPlaylist(playlist, startIndex = index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir",
                                        tint = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
