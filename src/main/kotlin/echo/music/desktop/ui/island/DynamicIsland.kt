package echo.music.desktop.ui.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import echo.music.desktop.lyrics.SongLyrics
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.player.RepeatMode
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.system.IslandWindowSupport
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class IslandMode { CLOSED, CLOSED_LYRICS, OPEN, OPEN_LYRICS }

private val ShoulderWidth = 12.dp
private val IslandGreen = Color(0xFF34C759)
private val TextDim = Color(0xFF8E8E93)
private val ButtonGray = Color(0xFF2C2C2E)
private const val PAGE_COUNT = 3

private fun bodySize(mode: IslandMode): DpSize = when (mode) {
    IslandMode.CLOSED -> DpSize(196.dp, 34.dp)
    IslandMode.CLOSED_LYRICS -> DpSize(420.dp, 34.dp)
    IslandMode.OPEN -> DpSize(540.dp, 164.dp)
    IslandMode.OPEN_LYRICS -> DpSize(780.dp, 140.dp)
}

/**
 * Notch-like shape glued to the top edge of the screen: flat top, the sides flare out into the
 * edge with concave "shoulders", and rounded bottom corners.
 */
private class IslandShape(private val shoulder: Dp, private val bottomRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { shoulder.toPx() }
        val r = with(density) { bottomRadius.toPx() }.coerceAtMost((size.height - s).coerceAtLeast(0f))
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            quadraticTo(s, 0f, s, s)
            lineTo(s, h - r)
            quadraticTo(s, h, s + r, h)
            lineTo(w - s - r, h)
            quadraticTo(w - s, h, w - s, h - r)
            lineTo(w - s, s)
            quadraticTo(w - s, 0f, w, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** The dynamic island: a borderless always-on-top window at the top center of the main screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DynamicIslandWindow(appModel: DesktopAppModel, onOpenApp: () -> Unit) {
    val prefs by appModel.prefs.collectAsState()
    val song by appModel.currentSong.collectAsState()
    val playback by appModel.playbackState.collectAsState()

    var hovered by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var lyricsMode by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }

    // Open shortly after the mouse arrives, close a moment after it leaves (no flicker on the edge).
    // After a click that resizes the island the mouse may end up outside it: wait a little longer.
    var holdOpenUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(hovered, holdOpenUntil) {
        if (hovered) {
            delay(90)
            open = true
        } else {
            delay(maxOf(350L, holdOpenUntil - System.currentTimeMillis()))
            open = false
        }
    }
    fun keepOpen() {
        holdOpenUntil = System.currentTimeMillis() + 1800
    }

    val mode = when {
        open && lyricsMode && page == 0 && song != null -> IslandMode.OPEN_LYRICS
        open -> IslandMode.OPEN
        lyricsMode && song != null -> IslandMode.CLOSED_LYRICS
        else -> IslandMode.CLOSED
    }
    val target = bodySize(mode)
    val motion = spring<Dp>(dampingRatio = 0.78f, stiffness = 420f)
    val width by animateDpAsState(target.width, motion)
    val height by animateDpAsState(target.height, motion)
    val radius by animateDpAsState(if (mode == IslandMode.OPEN || mode == IslandMode.OPEN_LYRICS) 30.dp else 17.dp, motion)

    // The window grows at once and shrinks after the animation, so the island is never cut.
    var windowBody by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        windowBody = DpSize(maxOf(windowBody.width, target.width), maxOf(windowBody.height, target.height))
        delay(500)
        windowBody = target
    }

    // Hidden while a game, video or presentation is full screen.
    var fullScreenApp by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.islandHideFullscreen) {
        while (true) {
            fullScreenApp = prefs.islandHideFullscreen && IslandWindowSupport.isFullScreenAppRunning()
            delay(1500)
        }
    }
    val visible = !fullScreenApp && (prefs.islandShowIdle || song != null)

    val screen = remember {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    }
    val windowWidth = windowBody.width + ShoulderWidth * 2
    val windowState = rememberWindowState(
        size = DpSize(windowWidth, windowBody.height),
        position = WindowPosition(screen.x.dp + (screen.width.dp - windowWidth) / 2, screen.y.dp)
    )
    LaunchedEffect(windowBody) {
        val w = windowBody.width + ShoulderWidth * 2
        windowState.size = DpSize(w, windowBody.height)
        windowState.position = WindowPosition(screen.x.dp + (screen.width.dp - w) / 2, screen.y.dp)
    }

    Window(
        onCloseRequest = {},
        state = windowState,
        title = "Echo Music • Isla dinámica",
        icon = painterResource("icon.png"),
        visible = visible,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true
    ) {
        val islandWindow = window
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            // The native window exists only once it has been shown.
            while (islandWindow.windowHandle == 0L) delay(50)
            IslandWindowSupport.makeOverlay(islandWindow.windowHandle)
            // Other always-on-top windows (and the taskbar) can cover it: stay first in line.
            while (true) {
                delay(2000)
                if (!hovered) IslandWindowSupport.keepOnTop(islandWindow.windowHandle)
            }
        }

        // Background: black, and in lyrics mode a fade into the cover's color (like the reference).
        var coverColor by remember { mutableStateOf(Color(0xFF5A0F1E)) }
        val lyricsTint by animateColorAsState(
            if (mode == IslandMode.OPEN_LYRICS) coverColor else Color.Black,
            tween(450)
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val shape = IslandShape(ShoulderWidth, radius)
            Box(
                Modifier
                    .size(width + ShoulderWidth * 2, height)
                    .clip(shape)
                    .background(Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black, 1f to lyricsTint))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .padding(horizontal = ShoulderWidth)
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(tween(220, delayMillis = 110)) togetherWith fadeOut(tween(90))
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "island"
                ) { m ->
                    Box(Modifier.requiredSize(bodySize(m)), contentAlignment = Alignment.TopCenter) {
                        when (m) {
                            IslandMode.CLOSED -> ClosedPill(playback)
                            IslandMode.CLOSED_LYRICS -> LyricPill(appModel, playback)
                            IslandMode.OPEN -> OpenCard(
                                appModel = appModel,
                                song = song,
                                playback = playback,
                                page = page,
                                onPage = { page = (it + PAGE_COUNT) % PAGE_COUNT },
                                onLyrics = { lyricsMode = true; page = 0; keepOpen() },
                                onOpenApp = onOpenApp,
                                onCoverColor = { coverColor = it }
                            )
                            IslandMode.OPEN_LYRICS -> LyricsCard(
                                appModel = appModel,
                                song = song,
                                playback = playback,
                                onLyricsOff = { lyricsMode = false; keepOpen() },
                                onOpenApp = onOpenApp,
                                onCoverColor = { coverColor = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ closed states

@Composable
private fun ClosedPill(playback: PlaybackState) {
    val now = rememberNow()
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressRing(playback, 15.dp)
        Spacer(Modifier.weight(1f))
        Text(now.format(TIME), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        WaveBars(playback.isPlaying, Modifier.size(width = 16.dp, height = 12.dp))
    }
}

@Composable
private fun LyricPill(appModel: DesktopAppModel, playback: PlaybackState) {
    val lyrics by appModel.currentLyrics.collectAsState()
    val loading by appModel.isLyricsLoading.collectAsState()
    val line = currentLyricText(lyrics, loading, playback.currentPositionMs)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
        AnimatedContent(
            targetState = line,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            modifier = Modifier.weight(1f),
            label = "pill-line"
        ) { text ->
            Text(
                text,
                color = if (lyrics?.lines.isNullOrEmpty()) TextDim else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
        Spacer(Modifier.width(15.dp))
    }
}

// ------------------------------------------------------------------ open card (pages)

@Composable
private fun OpenCard(
    appModel: DesktopAppModel,
    song: SongItem?,
    playback: PlaybackState,
    page: Int,
    onPage: (Int) -> Unit,
    onLyrics: () -> Unit,
    onOpenApp: () -> Unit,
    onCoverColor: (Color) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 6.dp)) {
            when (page) {
                0 -> PlayerPage(appModel, song, playback, onLyrics, onOpenApp, onCoverColor)
                1 -> QueuePage(appModel, playback)
                else -> ControlsPage(appModel, song, playback)
            }
        }
        PageArrow(Icons.Default.ChevronLeft, "Página anterior", Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) { onPage(page - 1) }
        PageArrow(Icons.Default.ChevronRight, "Página siguiente", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { onPage(page + 1) }
        PageDots(page, Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp))
    }
}

@Composable
private fun PlayerPage(
    appModel: DesktopAppModel,
    song: SongItem?,
    playback: PlaybackState,
    onLyrics: () -> Unit,
    onOpenApp: () -> Unit,
    onCoverColor: (Color) -> Unit
) {
    val now = rememberNow()
    Column(Modifier.fillMaxSize()) {
        Row {
            Cover(song, 74.dp, onOpenApp, onCoverColor)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(playback, 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(now.format(DATE).replace(".", ""), color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(now.format(TIME), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    WaveBars(playback.isPlaying, Modifier.size(width = 14.dp, height = 10.dp))
                }
                Spacer(Modifier.height(9.dp))
                SongTitles(song, titleSize = 15.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        ProgressRow(appModel, playback)
        TransportRow(appModel, playback, onLyrics, lyricsOn = false)
    }
}

@Composable
private fun QueuePage(appModel: DesktopAppModel, playback: PlaybackState) {
    val queue by appModel.queue.collectAsState()
    val index by appModel.currentIndex.collectAsState()
    val next = if (queue.isEmpty()) emptyList() else (1..3).mapNotNull { queue.getOrNull(index + it) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("A continuación", playback)
        Spacer(Modifier.height(6.dp))
        if (next.isEmpty()) {
            Text("No hay más canciones en la cola", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        }
        next.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { appModel.playSong(item, queue) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(ButtonGray)
                )
                Spacer(Modifier.width(10.dp))
                Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(
                    "  ·  " + item.artists.joinToString(", ") { it.name },
                    color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ControlsPage(appModel: DesktopAppModel, song: SongItem?, playback: PlaybackState) {
    val shuffle by appModel.isShuffle.collectAsState()
    val repeat by appModel.repeatMode.collectAsState()
    val timer by appModel.sleepTimerRemainingSeconds.collectAsState()
    val favorites by appModel.favorites.collectAsState()
    val liked = song != null && favorites.any { it.id == song.id }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Controles", playback)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonSmall(Icons.AutoMirrored.Filled.VolumeDown, "Bajar volumen") { appModel.adjustVolume(-0.1f) }
            ThinBar(
                fraction = playback.volume,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                onChange = { appModel.setVolume(it) }
            )
            IconButtonSmall(Icons.AutoMirrored.Filled.VolumeUp, "Subir volumen") { appModel.adjustVolume(0.1f) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Chip(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Me gusta", liked, Modifier.weight(1f)) {
                song?.let { appModel.toggleFavorite(it) }
            }
            Chip(Icons.Default.Shuffle, "Aleatorio", shuffle, Modifier.weight(1f)) { appModel.toggleShuffle() }
            Chip(
                if (repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                when (repeat) { RepeatMode.OFF -> "Repetir"; RepeatMode.ALL -> "Repetir todo"; RepeatMode.ONE -> "Repetir una" },
                repeat != RepeatMode.OFF,
                Modifier.weight(1f)
            ) { appModel.toggleRepeatMode() }
            Chip(
                Icons.Default.Timer,
                timer?.let { "%d:%02d".format(it / 60, it % 60) } ?: "Temporizador",
                timer != null,
                Modifier.weight(1f)
            ) {
                // Off → 15 → 30 → 60 min → off
                val minutes = timer?.let { (it + 59) / 60 } ?: 0
                when {
                    timer == null -> appModel.startSleepTimer(15)
                    minutes <= 15 -> appModel.startSleepTimer(30)
                    minutes <= 30 -> appModel.startSleepTimer(60)
                    else -> appModel.cancelSleepTimer()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ open with lyrics

@Composable
private fun LyricsCard(
    appModel: DesktopAppModel,
    song: SongItem?,
    playback: PlaybackState,
    onLyricsOff: () -> Unit,
    onOpenApp: () -> Unit,
    onCoverColor: (Color) -> Unit
) {
    Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 28.dp, top = 12.dp, bottom = 8.dp)) {
        Column(Modifier.width(400.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(song, 66.dp, onOpenApp, onCoverColor)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { SongTitles(song, titleSize = 15.sp) }
            }
            Spacer(Modifier.height(6.dp))
            ProgressRow(appModel, playback)
            TransportRow(appModel, playback, onLyricsOff, lyricsOn = true)
        }
        Spacer(Modifier.width(26.dp))
        LyricsColumn(appModel, playback, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun LyricsColumn(appModel: DesktopAppModel, playback: PlaybackState, modifier: Modifier) {
    val lyrics by appModel.currentLyrics.collectAsState()
    val loading by appModel.isLyricsLoading.collectAsState()
    val lines = lyrics?.lines.orEmpty()
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (lines.isEmpty()) {
            Text(if (loading) "Cargando letra…" else "Letra no disponible", color = TextDim, fontSize = 12.sp)
            return@Box
        }
        val index = if (lyrics?.isSynced == true) {
            lines.indexOfLast { it.timeMs <= playback.currentPositionMs + 250 }.coerceAtLeast(0)
        } else 0
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "lyrics"
        ) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LyricLineText(lines.getOrNull(i - 1)?.text, dim = true)
                LyricLineText(lines.getOrNull(i)?.text, dim = false)
                LyricLineText(lines.getOrNull(i + 1)?.text, dim = true)
            }
        }
    }
}

@Composable
private fun LyricLineText(text: String?, dim: Boolean) {
    if (text.isNullOrBlank()) {
        Spacer(Modifier.height(if (dim) 14.dp else 18.dp))
        return
    }
    Text(
        text,
        color = if (dim) Color.White.copy(alpha = 0.38f) else Color.White,
        fontSize = if (dim) 11.sp else 14.sp,
        fontWeight = if (dim) FontWeight.Normal else FontWeight.Bold,
        maxLines = if (dim) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = if (dim) 14.sp else 18.sp
    )
}

// ------------------------------------------------------------------ shared pieces

@Composable
private fun Cover(song: SongItem?, size: Dp, onClick: () -> Unit, onColor: (Color) -> Unit) {
    AsyncImage(
        model = song?.thumbnail?.let(::bigCover),
        contentDescription = "Abrir Echo Music",
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            runCatching { dominantColor(state.result.image) }.getOrNull()?.let(onColor)
        },
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(ButtonGray)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SongTitles(song: SongItem?, titleSize: TextUnit) {
    Text(
        song?.title ?: "Nada sonando",
        color = Color.White,
        fontSize = titleSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    val subtitle = listOfNotNull(
        song?.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() },
        song?.album?.name?.takeIf { it.isNotBlank() } ?: song?.title
    ).joinToString(" - ")
    if (subtitle.isNotBlank()) {
        Text(subtitle, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProgressRow(appModel: DesktopAppModel, playback: PlaybackState) {
    val duration = playback.durationMs
    val position = playback.currentPositionMs.coerceAtMost(duration.coerceAtLeast(0))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
        Text(formatTime(position), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        ThinBar(
            fraction = if (duration > 0) position.toFloat() / duration else 0f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            onChange = { appModel.seekTo(it) }
        )
        Text("-" + formatTime((duration - position).coerceAtLeast(0)), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(46.dp))
    }
}

@Composable
private fun TransportRow(appModel: DesktopAppModel, playback: PlaybackState, onLyrics: () -> Unit, lyricsOn: Boolean) {
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        IconButtonSmall(
            Icons.Default.Lyrics,
            if (lyricsOn) "Ocultar letra" else "Mostrar letra",
            tint = if (lyricsOn) Color.White else Color(0xFFD1D1D6),
            modifier = Modifier.align(Alignment.CenterStart)
        ) { onLyrics() }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButtonSmall(Icons.Default.FastRewind, "Anterior", size = 22.dp) { appModel.playPrevious() }
            IconButtonSmall(
                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playback.isPlaying) "Pausar" else "Reproducir",
                size = 26.dp
            ) { appModel.togglePlayPause() }
            IconButtonSmall(Icons.Default.FastForward, "Siguiente", size = 22.dp) { appModel.playNext() }
        }
    }
}

@Composable
private fun PageHeader(title: String, playback: PlaybackState) {
    val now = rememberNow()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(playback, 14.dp)
        Spacer(Modifier.width(7.dp))
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(now.format(TIME), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        WaveBars(playback.isPlaying, Modifier.size(width = 14.dp, height = 10.dp))
    }
}

/** Thin white bar on a gray track; click or drag to change it. */
@Composable
private fun ThinBar(fraction: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(14.dp)
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(onChange); dragging = null },
                    onDragCancel = { dragging = null }
                ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val y = size.height / 2
        val stroke = 3.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, y), Offset(size.width * shown, y), stroke, StrokeCap.Round)
    }
}

/** Green ring of the reference; the bright arc is the song's progress. */
@Composable
private fun ProgressRing(playback: PlaybackState, size: Dp) {
    val fraction = if (playback.durationMs > 0) (playback.currentPositionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 1f
    Canvas(Modifier.size(size)) {
        val stroke = 2.2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(IslandGreen.copy(alpha = 0.35f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(IslandGreen, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** The little audio-wave icon; it moves while music plays. */
@Composable
private fun WaveBars(playing: Boolean, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 90, 270).map { offset ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, delayMillis = offset / 3), AnimRepeatMode.Reverse),
            label = "bar$offset"
        )
    }
    val idle = listOf(0.45f, 0.8f, 0.6f, 0.35f)
    Canvas(modifier) {
        val gap = size.width / 7
        val barWidth = gap
        for (i in 0 until 4) {
            val h = size.height * (if (playing) phases[i].value else idle[i])
            val x = gap * (i * 2) + barWidth / 2
            drawLine(Color.White, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), barWidth, StrokeCap.Round)
        }
    }
}

@Composable
private fun PageArrow(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(ButtonGray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color(0xFFAEAEB2), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PageDots(page: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { i ->
            val active = i == page
            val w by animateDpAsState(if (active) 16.dp else 4.dp, tween(250))
            Box(
                Modifier
                    .size(width = w, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun IconButtonSmall(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) Color.White else ButtonGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val color = if (active) Color.Black else Color.White
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ helpers

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.forLanguageTag("es"))

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    return now
}

internal fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

/** Current line of the lyrics for the closed pill. */
internal fun currentLyricText(lyrics: SongLyrics?, loading: Boolean, positionMs: Long): String {
    val lines = lyrics?.lines.orEmpty()
    if (lines.isEmpty()) return if (loading) "Cargando letra…" else "Letra no disponible"
    if (lyrics?.isSynced != true) return lines.first().text
    val i = lines.indexOfLast { it.timeMs <= positionMs + 250 }
    return lines.getOrNull(i)?.text?.takeIf { it.isNotBlank() } ?: "♪"
}

/** YouTube Music covers come small (w60-h60). */
private fun bigCover(url: String): String = url.replace(Regex("""=w\d+-h\d+"""), "=w226-h226")

/** Average of the cover's most colorful pixels, darkened to sit behind white text. */
private fun dominantColor(image: coil3.Image): Color? {
    val bitmap = image.toSkiaBitmap() ?: return null
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var weight = 0.0
    val steps = 24
    for (yi in 0 until steps) for (xi in 0 until steps) {
        val argb = bitmap.getColor(xi * (bitmap.width - 1) / (steps - 1), yi * (bitmap.height - 1) / (steps - 1))
        val pr = (argb shr 16 and 0xFF) / 255.0
        val pg = (argb shr 8 and 0xFF) / 255.0
        val pb = (argb and 0xFF) / 255.0
        val max = maxOf(pr, pg, pb)
        val min = minOf(pr, pg, pb)
        val saturation = if (max == 0.0) 0.0 else (max - min) / max
        val w = 0.05 + saturation * saturation * max
        r += pr * w; g += pg * w; b += pb * w; weight += w
    }
    if (weight == 0.0) return null
    val scale = 0.55 / maxOf(r / weight, g / weight, b / weight, 0.01)
    fun c(v: Double) = (v / weight * scale.coerceAtMost(1.6)).coerceIn(0.0, 0.6).toFloat()
    return Color(c(r), c(g), c(b))
}

private fun coil3.Image.toSkiaBitmap(): org.jetbrains.skia.Bitmap? = runCatching {
    (this as? coil3.BitmapImage)?.bitmap
}.getOrNull()
