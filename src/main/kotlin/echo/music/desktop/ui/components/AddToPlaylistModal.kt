package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.UserPlaylist
import echo.music.desktop.ui.theme.NothingColors

/** Dialog that lets the user pick (or create) a playlist for [songs]. */
@Composable
fun AddToPlaylistModal(
    songs: List<SongItem>,
    appModel: DesktopAppModel,
    onClose: () -> Unit
) {
    val playlists by appModel.userPlaylists.collectAsState()
    val editable = remember(playlists) { playlists.filter { appModel.isPlaylistEditable(it) } }
    var isCreating by remember { mutableStateOf(false) }
    var busyPlaylistId by remember { mutableStateOf<String?>(null) }

    val subtitle = if (songs.size == 1) {
        "${songs.first().title} • ${songs.first().artists.joinToString(", ") { it.name }}"
    } else {
        "${songs.size} canciones"
    }

    ModalFrame(title = "AGREGAR A PLAYLIST", subtitle = subtitle, onClose = onClose) {
        if (isCreating) {
            NewPlaylistForm(
                appModel = appModel,
                confirmLabel = "Crear y agregar",
                onCancel = { isCreating = false },
                onConfirm = { name ->
                    appModel.createPlaylist(name, songs)
                    onClose()
                }
            )
            return@ModalFrame
        }

        PlaylistRow(
            title = "Nueva playlist",
            subtitle = "Crear una lista local con ${if (songs.size == 1) "esta canción" else "estas canciones"}",
            thumbnail = null,
            leadingIcon = Icons.Default.Add,
            onClick = { isCreating = true }
        )
        Spacer(Modifier.height(10.dp))

        if (editable.isEmpty()) {
            Text(
                "Todavía no tienes playlists.",
                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextMuted),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(editable, key = { it.id }) { playlist ->
                    val alreadyIn = songs.size == 1 && playlist.savedSongs.any { it.id == songs.first().id }
                    PlaylistRow(
                        title = playlist.title,
                        subtitle = "${sourceLabel(playlist)} • ${playlist.savedSongs.size} canciones",
                        thumbnail = playlist.thumbnailUrl,
                        isBusy = busyPlaylistId == playlist.id,
                        isChecked = alreadyIn,
                        enabled = busyPlaylistId == null && !alreadyIn,
                        onClick = {
                            busyPlaylistId = playlist.id
                            appModel.addSongsToPlaylist(playlist.id, songs) { ok ->
                                busyPlaylistId = null
                                if (ok) onClose()
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Dialog to create a new empty playlist from the Library. */
@Composable
fun CreatePlaylistModal(appModel: DesktopAppModel, onClose: () -> Unit) {
    ModalFrame(title = "NUEVA PLAYLIST", subtitle = "Se guarda en este equipo", onClose = onClose) {
        NewPlaylistForm(
            appModel = appModel,
            confirmLabel = "Crear",
            onCancel = onClose,
            onConfirm = { name ->
                val created = appModel.createPlaylist(name)
                onClose()
                appModel.openPlaylistDetail(created)
            }
        )
    }
}

private fun sourceLabel(playlist: UserPlaylist): String = when (playlist.sourceType) {
    PlaylistSourceType.YOUTUBE_MUSIC -> "YouTube Music"
    PlaylistSourceType.SPOTIFY -> "Spotify"
    PlaylistSourceType.LOCAL -> "Local"
    PlaylistSourceType.PODCAST -> "Podcast"
}

@Composable
private fun NewPlaylistForm(
    appModel: DesktopAppModel,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Global shortcuts (space = play/pause, etc.) must ignore keys typed in this field.
    DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }
    val submit = { if (name.isNotBlank()) onConfirm(name) }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(100) },
        label = { Text("Nombre de la playlist") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NothingColors.AccentRed,
            unfocusedBorderColor = NothingColors.SurfaceBorder,
            focusedLabelColor = NothingColors.AccentRed,
            cursorColor = NothingColors.AccentRed,
            focusedTextColor = NothingColors.TextPrimary,
            unfocusedTextColor = NothingColors.TextPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { appModel.isInputFocused = it.isFocused }
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) {
            Text("Cancelar", color = NothingColors.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { submit() },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = NothingColors.AccentRed,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(confirmLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistRow(
    title: String,
    subtitle: String,
    thumbnail: String?,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isBusy: Boolean = false,
    isChecked: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NothingColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            when {
                leadingIcon != null -> Icon(leadingIcon, null, tint = NothingColors.AccentRed, modifier = Modifier.size(22.dp))
                !thumbnail.isNullOrBlank() -> AsyncImage(
                    model = thumbnail,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = NothingColors.TextMuted, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (enabled || isChecked) NothingColors.TextPrimary else NothingColors.TextMuted
                )
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary, fontSize = 12.sp)
            )
        }
        when {
            isBusy -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = NothingColors.AccentRed,
                strokeWidth = 2.dp
            )
            isChecked -> Icon(Icons.Default.Check, "Ya está en esta playlist", tint = NothingColors.AccentRed, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ModalFrame(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* evita que el clic cierre el diálogo */ }
                )
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(NothingColors.AccentRed))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = NothingColors.TextMuted
                    )
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = NothingColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            content()
        }
    }
}
