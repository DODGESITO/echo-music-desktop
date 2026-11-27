package echo.music.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.recommend.FlowError
import echo.music.desktop.recommend.FlowItem
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors
import kotlinx.coroutines.launch

/**
 * Music Flow: one song per page, scroll (or ↑ ↓) to discover. Each song plays as it shows up and
 * the Flow keeps going on its own, like the phone app's vertical feed.
 */
@Composable
fun MusicFlowScreen(appModel: DesktopAppModel) {
    val state by appModel.musicFlow.collectAsState()
    val currentSong by appModel.currentSong.collectAsState()
    val playback by appModel.playbackState.collectAsState()
    val prefs by appModel.prefs.collectAsState()
    val canRestore by appModel.canRestoreQueue.collectAsState()
    val favorites by appModel.favorites.collectAsState()
    val canvasFile by appModel.canvasFile.collectAsState()
    val lyrics by appModel.currentLyrics.collectAsState()

    val items = state.items
    val showEnd = items.isNotEmpty() && !state.hasMore && !state.isInitialLoading
    val pageCount = items.size + if (showEnd) 1 else 0
    val pager = rememberPagerState(initialPage = state.selectedIndex.coerceAtLeast(0)) { pageCount }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Scrolling settles on a page → it becomes the Flow's song.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page < appModel.musicFlow.value.items.size) appModel.selectFlowPage(page)
        }
    }
    // The song changed by itself (auto-next) → follow it.
    LaunchedEffect(state.selectedIndex) {
        if (state.selectedIndex in 0 until pageCount && pager.currentPage != state.selectedIndex && !pager.isScrollInProgress) {
            pager.animateScrollToPage(state.selectedIndex)
        }
    }

    val selected = items.getOrNull(pager.currentPage)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown, Key.PageDown, Key.J -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pageCount - 1)) }; true }
                    Key.DirectionUp, Key.PageUp, Key.K -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    Key.Spacebar -> { appModel.toggleFlowPlayback(pager.currentPage); true }
                    Key.Escape -> { appModel.closeMusicFlow(); true }
                    else -> false
                }
            }
    ) {
        // Blurred cover of the visible song behind everything.
        AnimatedContent(
            targetState = selected?.song?.thumbnail,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "flow-bg"
        ) { url ->
            AsyncImage(
                model = url?.let(::bigCover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)))))

        when {
            state.isInitialLoading && items.size <= 1 -> CenterMessage("Buscando música para ti…", loading = true)
            items.isEmpty() -> CenterMessage(
                when (state.error) {
                    FlowError.OFFLINE -> "Estás sin conexión. Conéctate a internet para descubrir música nueva."
                    FlowError.FAILED -> "No se pudieron cargar las sugerencias."
                    else -> "Aún no hay nada que descubrir. Escucha algunas canciones o vuelve a intentarlo."
                },
                action = "Reintentar" to { appModel.retryMusicFlow() }
            )
            else -> VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> items.getOrNull(page)?.songId ?: "end" }
            ) { page ->
                val item = items.getOrNull(page)
                if (item == null) {
                    CenterMessage("Has llegado al final de este Flow", action = "Empezar un nuevo Flow" to { appModel.restartMusicFlow() })
                } else {
                    val isCurrent = currentSong?.id == item.songId
                    FlowPage(
                        item = item,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playback.isPlaying,
                        isBuffering = isCurrent && playback.isBuffering,
                        positionMs = if (isCurrent) playback.currentPositionMs else 0L,
                        durationMs = if (isCurrent) playback.durationMs else (item.song.duration ?: 0) * 1000L,
                        liked = favorites.any { it.id == item.songId },
                        canvas = if (isCurrent && prefs.showCanvas) canvasFile else null,
                        lyricLine = if (isCurrent && prefs.flowLyrics) currentLyricLine(lyrics, playback.currentPositionMs) else null,
                        appModel = appModel,
                        onPlayPause = { appModel.toggleFlowPlayback(page) }
                    )
                }
            }
        }

        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(Icons.Default.Close, "Cerrar Music Flow") { appModel.closeMusicFlow() }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Music Flow", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    if (state.isOffline) "Sin conexión · mostrando canciones descargadas" else "Desliza para descubrir",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            if (canRestore) {
                TextButton(onClick = { appModel.restoreQueueBeforeFlow() }) {
                    Icon(Icons.Default.Restore, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restaurar la cola anterior", color = Color.White)
                }
            }
        }

        // Previous / next page
        if (pageCount > 0) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoundIcon(Icons.Default.KeyboardArrowUp, "Canción anterior", enabled = pager.currentPage > 0) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
                RoundIcon(Icons.Default.KeyboardArrowDown, "Siguiente canción", enabled = pager.currentPage < pageCount - 1) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
        if (state.isLoadingMore) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(18.dp)
            )
        }
    }
}

@Composable
private fun FlowPage(
    item: FlowItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    canvas: java.io.File?,
    lyricLine: String?,
    appModel: DesktopAppModel,
    onPlayPause: () -> Unit
) {
    var feedbackOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 90.dp, vertical = 80.dp), contentAlignment = Alignment.Center) {
        val coverSize = minOf(maxHeight - 190.dp, maxWidth - 120.dp, 460.dp).coerceAtLeast(160.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(coverSize)) {
                Box(
                    Modifier
                        .size(coverSize)
                        .shadow(30.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(NothingColors.SurfaceElevated)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = bigCover(item.song.thumbnail),
                        contentDescription = item.song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (canvas != null) {
                        key(canvas) { echo.music.desktop.canvas.CanvasVideoView(canvas, Modifier.fillMaxSize()) }
                    }
                    if (!isPlaying) {
                        Box(
                            Modifier.size(70.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            else Icon(Icons.Default.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    item.song.title,
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                val artist = item.song.artists.firstOrNull()
                Text(
                    item.song.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = artist?.id != null) {
                        artist?.id?.let { appModel.closeMusicFlow(); appModel.openArtist(it) }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (lyricLine != null) {
                        AnimatedContent(targetState = lyricLine, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "flow-lyric") { line ->
                            Text(line, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowProgress(positionMs, durationMs, isCurrent)
            }

            Spacer(Modifier.width(36.dp))

            // Actions (like the phone app's side column)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SideAction(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "Quitar me gusta" else "Me gusta", tint = if (liked) NothingColors.AccentRed else Color.White) {
                    appModel.toggleFavorite(item.song)
                }
                SideAction(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pausar" else "Reproducir", onClick = onPlayPause)
                SideAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Añadir") { appModel.openAddToPlaylist(listOf(item.song)) }
                SideAction(Icons.Default.Radio, "Radio") { appModel.closeMusicFlow(); appModel.startRadio(item.song) }
                Box {
                    SideAction(Icons.Default.ThumbDown, "Opinión") { feedbackOpen = true }
                    DropdownMenu(expanded = feedbackOpen, onDismissRequest = { feedbackOpen = false }, containerColor = NothingColors.SurfaceElevated) {
                        DropdownMenuItem(text = { Text("No me interesa", color = NothingColors.TextPrimary) }, onClick = { feedbackOpen = false; appModel.flowNotInterested(item) })
                        DropdownMenuItem(text = { Text("Menos como esto", color = NothingColors.TextPrimary) }, onClick = { feedbackOpen = false; appModel.flowLessLikeThis(item) })
                        DropdownMenuItem(text = { Text("No recomendar nunca más", color = NothingColors.AccentRed) }, onClick = { feedbackOpen = false; appModel.flowNeverRecommend(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowProgress(positionMs: Long, durationMs: Long, active: Boolean) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(if (active) clock(positionMs) else "", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (durationMs > 0) clock(durationMs) else "", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SideAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CenterMessage(text: String, loading: Boolean = false, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
        }
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = action.second, colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed)) {
                Text(action.first, color = Color.White)
            }
        }
    }
}

private fun currentLyricLine(lyrics: echo.music.desktop.lyrics.SongLyrics?, positionMs: Long): String? {
    val lines = lyrics?.lines.orEmpty()
    if (lines.isEmpty() || lyrics?.isSynced != true) return null
    return lines.getOrNull(lines.indexOfLast { it.timeMs <= positionMs + 250 })?.text?.takeIf { it.isNotBlank() }
}

private fun clock(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/** YouTube Music covers come small (w60-h60). */
private fun bigCover(url: String): String = url.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
