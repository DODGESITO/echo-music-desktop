package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.PreferencesStore
import echo.music.desktop.ui.theme.EchoDesktopTheme
import echo.music.desktop.ui.theme.NothingColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Small always-on-top window with the cover, the song and the playback controls. Drag it anywhere. */
@Composable
fun MiniPlayerWindow(appModel: DesktopAppModel, onOpenMain: () -> Unit, onClose: () -> Unit) {
    val saved = remember { PreferencesStore.current }
    val windowState = rememberWindowState(
        size = DpSize(380.dp, 100.dp),
        position = if (saved.miniPlayerX != null && saved.miniPlayerY != null) {
            WindowPosition(saved.miniPlayerX.dp, saved.miniPlayerY.dp)
        } else {
            WindowPosition(Alignment.BottomEnd)
        }
    )
    // Remember where it was left (once the drag settles).
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position }.collectLatest { pos ->
            if (pos !is WindowPosition.Absolute) return@collectLatest
            delay(500)
            PreferencesStore.update { it.copy(miniPlayerX = pos.x.value.toInt(), miniPlayerY = pos.y.value.toInt()) }
        }
    }

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "Echo Music • Mini reproductor",
        icon = painterResource("icon.png"),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Spacebar) {
                appModel.togglePlayPause()
                true
            } else false
        }
    ) {
        EchoDesktopTheme {
            val song by appModel.currentSong.collectAsState()
            val playback by appModel.playbackState.collectAsState()
            val shape = RoundedCornerShape(16.dp)
            WindowDraggableArea {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(NothingColors.Surface)
                        .border(1.dp, NothingColors.SurfaceBorder, shape)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song?.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(NothingColors.SurfaceElevated)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song?.title ?: "Nada sonando",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
                            )
                            Text(
                                song?.artists?.joinToString(", ") { it.name }.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                            )
                        }
                        IconButton(onClick = { appModel.playPrevious() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Anterior", tint = NothingColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { appModel.togglePlayPause() },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(NothingColors.AccentRed)
                        ) {
                            Icon(
                                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playback.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { appModel.playNext() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.SkipNext, "Siguiente", tint = NothingColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            IconButton(onClick = onOpenMain, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.OpenInFull, "Abrir Echo Music", tint = NothingColors.TextMuted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, "Cerrar mini reproductor", tint = NothingColors.TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    val progress = if (playback.durationMs > 0) {
                        (playback.currentPositionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NothingColors.SurfaceElevated)
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(NothingColors.AccentRed))
                    }
                }
            }
        }
    }
}
