package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
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
import androidx.compose.ui.focus.onFocusChanged
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
    val ytAccount by appModel.youtubeAccount.collectAsState()
    val canEdit = remember(playlist, userPlaylists, ytAccount) { appModel.canEditPlaylist(playlist) }
    val canRemove = remember(playlist, userPlaylists, ytAccount) { appModel.canRemoveFromPlaylist(playlist) }
    var reorderMode by remember(playlist.id) { mutableStateOf(false) }
    var renaming by remember(playlist.id) { mutableStateOf(false) }
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }

    if (confirmDelete) {
        val fromYouTube = playlist.sourceType == PlaylistSourceType.YOUTUBE_MUSIC
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NothingColors.SurfaceElevated,
            icon = { Icon(Icons.Default.Delete, null, tint = NothingColors.AccentRed) },
            title = {
                Text(
                    if (fromYouTube) "¿Quitar \"${playlist.title}\" de tu biblioteca?" else "¿Eliminar \"${playlist.title}\"?",
                    color = NothingColors.TextPrimary
                )
            },
            text = {
                Text(
                    if (fromYouTube) "Solo se quita de Echo Music; en tu cuenta de YouTube Music sigue igual y vuelve al sincronizar."
                    else "Se borra la playlist y sus ${playlist.songs.size} canciones de la lista. Las canciones descargadas no se borran.",
                    color = NothingColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    appModel.deletePlaylist(playlist.id)
                    onClose()
                }) { Text(if (fromYouTube) "Quitar" else "Eliminar", color = NothingColors.AccentRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = NothingColors.TextSecondary) } }
        )
    }

    if (renaming) {
        RenamePlaylistDialog(
            appModel = appModel,
            current = playlist.title,
            onDismiss = { renaming = false },
            onConfirm = { newTitle ->
                renaming = false
                appModel.renamePlaylist(playlist.id, newTitle)
            }
        )
    }

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
                .widthIn(max = 900.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
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
                // Close
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NothingColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }

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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (canEdit) {
                                IconButton(onClick = { renaming = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Edit, "Renombrar playlist", tint = NothingColors.TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Text(
                            text = "${playlist.author} • ${playlist.songs.size} canciones",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NothingColors.TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Actions: one main button, the rest as icons (name on hover) so they always fit.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (playlist.songs.isNotEmpty()) {
                                Button(
                                    onClick = { appModel.playPlaylist(playlist, startIndex = 0) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reproducir", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                            if (!isInLibrary) {
                                OutlinedButton(
                                    onClick = { appModel.savePlaylistToLibrary(playlist) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Guardar en biblioteca")
                                }
                            }

                            if (playlist.songs.isNotEmpty()) {
                                when {
                                    isPlaylistDownloading -> HeaderIcon(
                                        Icons.Default.Download,
                                        "Descargando $downloadedCount de ${playlist.songs.size} · clic para cancelar",
                                        loading = true
                                    ) { appModel.cancelPlaylistDownload(playlist.id) }
                                    isFullyDownloaded -> HeaderIcon(Icons.Default.DownloadDone, "Descargada completa", tint = Color(0xFF4CAF50)) {}
                                    else -> HeaderIcon(
                                        Icons.Default.Download,
                                        if (downloadedCount > 0) "Descargar las ${playlist.songs.size - downloadedCount} que faltan" else "Descargar playlist"
                                    ) { appModel.downloadPlaylist(playlist) }
                                }
                            }
                            if (playlist.sourceType == PlaylistSourceType.YOUTUBE_MUSIC && isInLibrary) {
                                HeaderIcon(Icons.Default.Sync, if (isRefreshing) "Sincronizando…" else "Traer los cambios de YouTube Music", loading = isRefreshing) {
                                    if (!isRefreshing) {
                                        isRefreshing = true
                                        appModel.refreshYouTubePlaylist(playlist) { isRefreshing = false }
                                    }
                                }
                            }
                            if (canEdit && playlist.songs.size > 1) {
                                HeaderIcon(Icons.Default.SwapVert, if (reorderMode) "Terminar de ordenar" else "Ordenar canciones", active = reorderMode) {
                                    reorderMode = !reorderMode
                                }
                            }
                            if (playlist.sourceType == PlaylistSourceType.LOCAL && isInLibrary && playlist.songs.isNotEmpty()) {
                                HeaderIcon(Icons.Default.AutoAwesome, "Modificar con IA", tint = NothingColors.AccentRed) { appModel.openAiModify(playlist.id) }
                            }
                            if (isInLibrary) {
                                HeaderIcon(
                                    Icons.Default.Delete,
                                    if (playlist.sourceType == PlaylistSourceType.YOUTUBE_MUSIC) "Quitar de tu biblioteca" else "Eliminar playlist"
                                ) { confirmDelete = true }
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
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(playlist.songs, key = { i, song -> "${song.id}#$i" }) { index, song ->
                            val isCurrent = currentSong?.id == song.id
                            val isPlaying = isCurrent && playbackState.isPlaying
                            PlaylistSongRow(
                                appModel = appModel,
                                playlist = playlist,
                                song = song,
                                index = index,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying,
                                reorderMode = reorderMode,
                                canRemove = canRemove,
                                downloadProgress = downloadProgressMap[song.id]
                            )
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

@Composable
private fun RenamePlaylistDialog(
    appModel: DesktopAppModel,
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(current) }
    DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingColors.SurfaceElevated,
        title = { Text("Renombrar playlist", color = NothingColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(150) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { appModel.isInputFocused = it.isFocused },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NothingColors.AccentRed,
                    unfocusedBorderColor = NothingColors.SurfaceBorder,
                    cursorColor = NothingColors.AccentRed,
                    focusedTextColor = NothingColors.TextPrimary,
                    unfocusedTextColor = NothingColors.TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && name.trim() != current) {
                Text("Guardar", color = NothingColors.AccentRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = NothingColors.TextSecondary) }
        }
    )
}

/** Small round icon button for the playlist header; its name shows on hover. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = NothingColors.TextSecondary,
    active: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                label,
                color = NothingColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(NothingColors.Surface)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        },
        delayMillis = 250
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (active) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                .border(1.dp, if (active) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NothingColors.AccentRed, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = label, tint = if (active) NothingColors.AccentRed else tint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * One song of the playlist: the row itself plays it. Its tools (add, download, remove, more) show
 * only on hover or for the song playing, so the list stays clean; liked and downloaded marks stay.
 */
@Composable
private fun PlaylistSongRow(
    appModel: DesktopAppModel,
    playlist: UserPlaylist,
    song: SongItem,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    reorderMode: Boolean,
    canRemove: Boolean,
    downloadProgress: Float?
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val showTools = hovered || isCurrent
    val isFav = appModel.isFavorite(song.id)
    val isDownloaded = appModel.isDownloaded(song.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isCurrent -> NothingColors.Surface
                    hovered -> NothingColors.Surface.copy(alpha = 0.7f)
                    else -> Color.Transparent
                }
            )
            .border(1.dp, if (isCurrent) NothingColors.AccentRed.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .clickable { appModel.playPlaylist(playlist, startIndex = index) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number, or play/pause while hovering / playing
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            when {
                isPlaying -> Icon(Icons.Default.GraphicEq, contentDescription = "Sonando", tint = NothingColors.AccentRed, modifier = Modifier.size(18.dp))
                hovered || isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = NothingColors.TextPrimary, modifier = Modifier.size(20.dp))
                else -> Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = NothingColors.TextMuted, fontSize = 12.sp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(NothingColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail.isNotBlank()) {
                AsyncImage(model = song.thumbnail, contentDescription = song.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = NothingColors.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artists.joinToString(", ") { it.name }.ifEmpty { "Desconocido" },
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        if (reorderMode) {
            IconButton(onClick = { appModel.moveSongInPlaylist(playlist.id, index, index - 1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (index > 0) NothingColors.TextPrimary else NothingColors.SurfaceBorder, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { appModel.moveSongInPlaylist(playlist.id, index, index + 1) }, enabled = index < playlist.songs.lastIndex, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Bajar", tint = if (index < playlist.songs.lastIndex) NothingColors.TextPrimary else NothingColors.SurfaceBorder, modifier = Modifier.size(20.dp))
            }
        } else if (showTools) {
            RowIcon(Icons.AutoMirrored.Filled.PlaylistAdd, "Agregar a otra playlist") { appModel.openAddToPlaylist(listOf(song)) }
            when {
                downloadProgress != null -> Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(16.dp), color = NothingColors.AccentRed, trackColor = NothingColors.SurfaceBorder, strokeWidth = 2.dp)
                }
                isDownloaded -> RowIcon(Icons.Default.DownloadDone, "Borrar la descarga", tint = Color(0xFF4CAF50)) { appModel.deleteDownload(song.id) }
                else -> RowIcon(Icons.Default.Download, "Descargar") { appModel.downloadSong(song) }
            }
            if (canRemove) RowIcon(Icons.Default.RemoveCircleOutline, "Quitar de esta playlist") { appModel.removeSongFromPlaylist(playlist.id, song.id) }
        } else if (isDownloaded) {
            Icon(Icons.Default.DownloadDone, contentDescription = "Descargada", tint = Color(0xFF4CAF50), modifier = Modifier.padding(horizontal = 7.dp).size(16.dp))
        }

        // Liked: always visible when it is; on hover to add it
        if (isFav || showTools) {
            RowIcon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (isFav) NothingColors.AccentRed else NothingColors.TextMuted) {
                appModel.toggleFavorite(song)
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }

        val dur = song.duration ?: 0
        Text(
            if (dur > 0) String.format("%d:%02d", dur / 60, dur % 60) else "",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = NothingColors.TextMuted, fontSize = 12.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
        if (showTools) SongMoreMenuButton(song = song, appModel = appModel) else Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = NothingColors.TextMuted, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
    }
}
