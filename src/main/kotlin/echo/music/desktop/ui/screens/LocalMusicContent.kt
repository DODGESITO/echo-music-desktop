package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.innertube.models.SongItem
import echo.music.desktop.local.LocalMusicLibrary
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.components.SongListItem
import echo.music.desktop.ui.theme.NothingColors
import java.io.File

/** Biblioteca → En este PC: music files from the user's folders. */
@Composable
fun ColumnScope.LocalMusicContent(
    appModel: DesktopAppModel,
    currentSong: SongItem?,
    playbackState: PlaybackState
) {
    val prefs by appModel.prefs.collectAsState()
    val tracks by appModel.localTracks.collectAsState()
    val scanCount by appModel.localScanCount.collectAsState()
    var query by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }

    val songs = remember(tracks, query) {
        val q = query.trim().lowercase()
        tracks.filter { q.isEmpty() || q in it.title.lowercase() || q in it.artist.lowercase() || q in it.album.lowercase() }
            .map { it.toSongItem() }
    }

    // Folders + actions
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(prefs.localMusicFolders) { folder ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NothingColors.Surface)
                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
                        .padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = NothingColors.TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        File(folder).name.ifBlank { folder },
                        style = MaterialTheme.typography.labelMedium.copy(color = NothingColors.TextSecondary)
                    )
                    IconButton(onClick = { appModel.removeLocalMusicFolder(folder) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Quitar carpeta", tint = NothingColors.TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (scanCount != null) {
            CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Escaneando... $scanCount", style = MaterialTheme.typography.labelMedium.copy(color = NothingColors.TextSecondary))
            Spacer(Modifier.width(10.dp))
        } else if (prefs.localMusicFolders.isNotEmpty()) {
            TextButton(onClick = { appModel.rescanLocalMusic() }) {
                Icon(Icons.Default.Sync, null, tint = NothingColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Volver a escanear", color = NothingColors.TextSecondary)
            }
        }
        Button(
            onClick = { chooseFolder()?.let { appModel.addLocalMusicFolder(it) } },
            colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar carpeta")
        }
    }

    Spacer(Modifier.height(14.dp))

    if (prefs.localMusicFolders.isEmpty()) {
        val musicDir = File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Folder, null, tint = NothingColors.TextMuted, modifier = Modifier.size(54.dp))
                Text("Escucha la música guardada en este PC", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
                Text(
                    "Agrega una carpeta con canciones MP3, M4A, WAV o AIFF. (FLAC y OGG aún no se pueden reproducir en PC.)",
                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                )
                if (musicDir != null) {
                    OutlinedButton(
                        onClick = { appModel.addLocalMusicFolder(musicDir.absolutePath) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Usar mi carpeta Música") }
                }
            }
        }
        return
    }

    // Search + play all / shuffle
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Buscar en tu música (${tracks.size})", color = NothingColors.TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = NothingColors.TextMuted, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f).onFocusChanged { appModel.isInputFocused = it.isFocused },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NothingColors.AccentRed,
                unfocusedBorderColor = NothingColors.SurfaceBorder,
                cursorColor = NothingColors.AccentRed,
                focusedTextColor = NothingColors.TextPrimary,
                unfocusedTextColor = NothingColors.TextPrimary
            )
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { songs.firstOrNull()?.let { appModel.playSong(it, songs) } },
            enabled = songs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reproducir todo")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { if (songs.isNotEmpty()) { val shuffled = songs.shuffled(); appModel.playSong(shuffled.first(), shuffled) } },
            enabled = songs.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Aleatorio")
        }
    }

    Spacer(Modifier.height(12.dp))

    if (songs.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(
                if (scanCount != null) "Buscando canciones..." else if (query.isNotBlank()) "Sin resultados para \"$query\"" else "No se encontraron canciones compatibles en estas carpetas.",
                color = NothingColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs, key = { it.id }) { song ->
                val isCurrent = currentSong?.id == song.id
                SongListItem(
                    song = song,
                    isPlaying = isCurrent && playbackState.isPlaying,
                    isCurrent = isCurrent,
                    isFavorite = appModel.isFavorite(song.id),
                    onPlay = { appModel.playSong(song, songs) },
                    onToggleFavorite = { appModel.toggleFavorite(song) },
                    onAddToPlaylist = { appModel.openAddToPlaylist(listOf(song)) }
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/** Windows folder picker. Returns null when cancelled. */
private fun chooseFolder(): String? {
    runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    val chooser = javax.swing.JFileChooser(File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory })
    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Elegir carpeta de música"
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}
