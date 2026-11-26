package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.music.innertube.models.SongItem
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

/**
 * "⋮" button with the song's extra actions. Each action is optional; the button is hidden
 * when none is given.
 */
@Composable
fun SongMoreMenuButton(
    song: SongItem,
    onAddToPlaylist: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null,
    buttonSize: Int = 32
) {
    if (onAddToPlaylist == null && onStartRadio == null && onDislike == null && onBlockArtist == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(buttonSize.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = NothingColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(NothingColors.SurfaceElevated)
        ) {
            onAddToPlaylist?.let { MenuEntry("Agregar a playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { open = false; it() } }
            onStartRadio?.let { MenuEntry("Iniciar radio de esta canción", Icons.Default.Radio) { open = false; it() } }
            onDislike?.let { MenuEntry("No me gusta esta canción", Icons.Default.ThumbDown) { open = false; it() } }
            onBlockArtist?.let {
                MenuEntry("Bloquear a ${song.artists.firstOrNull()?.name ?: "este artista"}", Icons.Default.Block) { open = false; it() }
            }
        }
    }
}

/** Same menu wired to the app model (radio, "No me gusta", block first artist). */
@Composable
fun SongMoreMenuButton(
    song: SongItem,
    appModel: DesktopAppModel,
    withRadio: Boolean = true,
    withAddToPlaylist: Boolean = false,
    buttonSize: Int = 32
) {
    SongMoreMenuButton(
        song = song,
        onAddToPlaylist = if (withAddToPlaylist) ({ appModel.openAddToPlaylist(listOf(song)) }) else null,
        onStartRadio = if (withRadio) ({ appModel.startRadio(song) }) else null,
        onDislike = { appModel.dislikeSong(song) },
        onBlockArtist = song.artists.firstOrNull()?.takeIf { it.name.isNotBlank() }?.let { a -> { appModel.blockArtist(a.id, a.name) } },
        buttonSize = buttonSize
    )
}

@Composable
private fun MenuEntry(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = NothingColors.TextPrimary) },
        leadingIcon = { Icon(icon, null, tint = NothingColors.TextSecondary, modifier = Modifier.size(16.dp)) },
        onClick = onClick
    )
}
