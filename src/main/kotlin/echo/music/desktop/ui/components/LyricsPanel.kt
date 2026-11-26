package echo.music.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.desktop.lyrics.LyricLine
import echo.music.desktop.lyrics.LyricsProviderId
import echo.music.desktop.lyrics.SongLyrics
import echo.music.desktop.lyrics.LyricsTransformService
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.LyricsExtras
import echo.music.desktop.ui.theme.NothingColors

/**
 * Lyrics view for the expanded player: auto-scrolling synced lines, word-by-word highlight
 * when the provider supports it, background vocals and duets, and a source picker.
 */
@Composable
fun ColumnScope.LyricsPanel(
    lyrics: SongLyrics?,
    isLoading: Boolean,
    switchingTo: LyricsProviderId?,
    playbackState: PlaybackState,
    onSeekToMs: (Long) -> Unit,
    onSelectProvider: (LyricsProviderId) -> Unit,
    extras: LyricsExtras = LyricsExtras(),
    romanizeEnabled: Boolean = true,
    onToggleRomanize: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onHideTranslation: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { LyricsSourceBar(lyrics, isLoading, switchingTo, onSelectProvider) }
        if (lyrics != null && lyrics.lines.isNotEmpty() && !isLoading) {
            val canRomanize = remember(lyrics) { LyricsTransformService.needsRomanization(lyrics.lines.map { it.text }) }
            if (canRomanize) {
                ToolChip(
                    label = "Romanizar",
                    selected = romanizeEnabled,
                    onClick = onToggleRomanize
                )
                Spacer(Modifier.width(6.dp))
            }
            when {
                extras.isTranslating -> ToolChip(label = "Traduciendo...", selected = true, busy = true, onClick = {})
                extras.translated != null -> ToolChip(label = "Ocultar traducción", selected = true, onClick = onHideTranslation)
                else -> ToolChip(label = "Traducir", selected = false, onClick = onTranslate)
            }
        }
    }
    extras.translationError?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.AccentRed),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    Spacer(Modifier.height(10.dp))

    when {
        isLoading -> CenterMessage(loading = true, text = "Buscando letras en 7 fuentes...")
        lyrics == null || lyrics.lines.isEmpty() -> CenterMessage(
            loading = false,
            text = "Letras no disponibles para esta canción.\nPrueba otra fuente arriba."
        )
        else -> LyricsLines(
            lyrics, playbackState, onSeekToMs,
            romanized = if (romanizeEnabled) extras.romanized else null,
            translated = extras.translated
        )
    }
}

@Composable
private fun ColumnScope.CenterMessage(loading: Boolean, text: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MusicNote, null, tint = NothingColors.TextMuted, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextMuted)
            )
        }
    }
}

@Composable
private fun LyricsSourceBar(
    lyrics: SongLyrics?,
    isLoading: Boolean,
    switchingTo: LyricsProviderId?,
    onSelectProvider: (LyricsProviderId) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = when {
        switchingTo != null -> "Probando ${switchingTo.displayName}..."
        isLoading -> "Buscando..."
        lyrics?.provider != null -> buildString {
            append(lyrics.provider.displayName)
            append(
                when {
                    lyrics.hasWordSync -> " • palabra por palabra"
                    lyrics.isSynced -> " • sincronizada"
                    else -> " • sin sincronizar"
                }
            )
        }
        else -> "Elegir fuente"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "FUENTE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = NothingColors.TextMuted
            )
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NothingColors.SurfaceElevated)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (switchingTo != null) {
                    CircularProgressIndicator(
                        color = NothingColors.AccentRed,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextPrimary))
                Icon(Icons.Default.ArrowDropDown, "Cambiar fuente de letras", tint = NothingColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(NothingColors.SurfaceElevated)
            ) {
                LyricsProviderId.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName, color = NothingColors.TextPrimary) },
                        trailingIcon = {
                            if (lyrics?.provider == provider) {
                                Icon(Icons.Default.Check, null, tint = NothingColors.AccentRed, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            menuOpen = false
                            onSelectProvider(provider)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Playback position that advances every frame between the player's ~200 ms updates,
 * so word highlighting moves smoothly.
 */
@Composable
private fun rememberSmoothPosition(state: PlaybackState): Long {
    var anchorPos by remember { mutableStateOf(state.currentPositionMs) }
    var anchorTime by remember { mutableStateOf(System.nanoTime()) }
    var smooth by remember { mutableStateOf(state.currentPositionMs) }

    LaunchedEffect(state.currentPositionMs, state.isPlaying) {
        anchorPos = state.currentPositionMs
        anchorTime = System.nanoTime()
        smooth = state.currentPositionMs
    }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            withFrameMillis {
                val elapsed = (System.nanoTime() - anchorTime) / 1_000_000L
                // Never run more than one update interval ahead of the real position.
                smooth = anchorPos + elapsed.coerceIn(0L, 400L)
            }
        }
    }
    return smooth
}

@Composable
private fun ColumnScope.LyricsLines(
    lyrics: SongLyrics,
    playbackState: PlaybackState,
    onSeekToMs: (Long) -> Unit,
    romanized: List<String?>? = null,
    translated: List<String?>? = null
) {
    val position = if (lyrics.isSynced) rememberSmoothPosition(playbackState) else 0L
    val lines = lyrics.lines
    val hasDuet = remember(lines) { lines.mapNotNull { it.agent }.distinct().size > 1 }
    val firstAgent = remember(lines) { lines.firstNotNullOfOrNull { it.agent } }

    // Small look-ahead so a line lights up as the singer starts it.
    val activeIdx = if (lyrics.isSynced) {
        lines.indexOfLast { !it.isBackground && it.timeMs <= position + 150L }
    } else -1

    val listState = rememberLazyListState()
    if (lyrics.isSynced) {
        LaunchedEffect(activeIdx) {
            if (activeIdx >= 0) {
                val viewport = listState.layoutInfo.viewportSize.height
                listState.animateScrollToItem(activeIdx, scrollOffset = -(viewport / 3))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        itemsIndexed(lines) { idx, line ->
            // Background vocals sit between the active line and the next main line once they start.
            val isActive = idx == activeIdx ||
                (line.isBackground && idx > activeIdx && line.timeMs <= position + 150L)
            val isPast = lyrics.isSynced && idx < activeIdx
            val alignEnd = hasDuet && line.agent != null && line.agent != firstAgent
            LyricLineView(
                line = line,
                position = position,
                isActive = isActive,
                isPast = isPast,
                isSynced = lyrics.isSynced,
                alignEnd = alignEnd,
                romanized = romanized?.getOrNull(idx),
                translated = translated?.getOrNull(idx),
                onClick = if (lyrics.isSynced && line.timeMs >= 0) ({ onSeekToMs(line.timeMs) }) else null
            )
        }
    }
}

@Composable
private fun LyricLineView(
    line: LyricLine,
    position: Long,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean,
    alignEnd: Boolean,
    romanized: String? = null,
    translated: String? = null,
    onClick: (() -> Unit)?
) {
    val base = when {
        !isSynced -> NothingColors.TextPrimary.copy(alpha = 0.85f)
        isActive -> NothingColors.TextPrimary
        isPast -> NothingColors.TextSecondary.copy(alpha = 0.45f)
        else -> NothingColors.TextSecondary.copy(alpha = 0.6f)
    }
    val color by animateColorAsState(base, tween(250))
    val fontSize = when {
        line.isBackground -> 14.sp
        isActive -> 22.sp
        else -> 17.sp
    }
    val words = line.words
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.35f).sp,
        fontWeight = if (isActive && !line.isBackground) FontWeight.ExtraBold else FontWeight.SemiBold,
        fontStyle = if (line.isBackground) FontStyle.Italic else FontStyle.Normal,
        textAlign = textAlign
    )
    val modifier = Modifier.fillMaxWidth()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        if (isActive && !words.isNullOrEmpty()) {
            // Word-by-word: sung words in accent, the current word fades in, upcoming words dimmed.
            val upcoming = NothingColors.TextSecondary.copy(alpha = 0.55f)
            val sung = NothingColors.AccentRed
            val text = buildAnnotatedString {
                words.forEachIndexed { i, word ->
                    val progress = when {
                        position >= word.endMs -> 1f
                        position <= word.startMs -> 0f
                        else -> (position - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)
                    }
                    withStyle(SpanStyle(color = lerp(upcoming, sung, progress))) { append(word.text) }
                    if (i < words.lastIndex) append(" ")
                }
            }
            Text(text = text, style = style, modifier = modifier)
        } else {
            Text(
                text = line.text,
                style = style.copy(color = if (isActive && words.isNullOrEmpty() && isSynced) NothingColors.AccentRed else color),
                modifier = modifier
            )
        }
        // Romanization (how it sounds in Latin letters) and translation, under the original line.
        val extraStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = if (line.isBackground) 12.sp else 14.sp,
            lineHeight = 19.sp,
            textAlign = textAlign
        )
        romanized?.let {
            Text(
                it,
                style = extraStyle.copy(
                    fontStyle = FontStyle.Italic,
                    color = if (isActive) NothingColors.TextPrimary.copy(alpha = 0.8f) else color
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
        translated?.let {
            Text(
                it,
                style = extraStyle.copy(color = if (isActive) NothingColors.AccentRed.copy(alpha = 0.85f) else color.copy(alpha = color.alpha * 0.9f)),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit, busy: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NothingColors.AccentRed.copy(alpha = 0.18f) else NothingColors.SurfaceElevated)
            .border(1.dp, if (selected) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
