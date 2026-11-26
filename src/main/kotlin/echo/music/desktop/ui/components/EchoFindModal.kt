package echo.music.desktop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.shazamkit.models.RecognitionResult
import echo.music.desktop.state.EchoFindState
import echo.music.desktop.ui.theme.NothingColors

/** Echo Find: listens with the microphone and shows the recognized song. */
@Composable
fun EchoFindModal(
    state: EchoFindState,
    onStart: () -> Unit,
    onPlay: (RecognitionResult) -> Unit,
    onSearch: (RecognitionResult) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ECHO FIND",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp, color = NothingColors.TextMuted
                    )
                )
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = NothingColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            when (state) {
                is EchoFindState.Found -> FoundView(state.result, onPlay, onSearch, onStart)
                else -> {
                    val listening = state is EchoFindState.Listening
                    val busy = listening || state is EchoFindState.Processing
                    val pulse by rememberInfiniteTransition().animateFloat(
                        initialValue = 1f, targetValue = if (listening) 1.12f else 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse)
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(NothingColors.AccentRed.copy(alpha = if (busy) 1f else 0.85f))
                            .clickable(enabled = !busy, onClick = onStart),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state is EchoFindState.Processing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Default.Mic, "Escuchar", tint = Color.White, modifier = Modifier.size(52.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    val text = when (state) {
                        is EchoFindState.Ready -> "Pon la canción cerca del micrófono y pulsa el botón."
                        is EchoFindState.Listening -> "Escuchando..."
                        is EchoFindState.Processing -> "Identificando la canción..."
                        is EchoFindState.NoMatch -> "No se encontró la canción. Acércala al micrófono y vuelve a intentarlo."
                        is EchoFindState.Failed -> state.message
                        is EchoFindState.Found -> ""
                    }
                    Text(
                        text,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (state is EchoFindState.Failed) NothingColors.AccentRed else NothingColors.TextSecondary
                        )
                    )
                    if (state is EchoFindState.Listening) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = NothingColors.AccentRed,
                            trackColor = NothingColors.SurfaceBorder
                        )
                    }
                    if (state is EchoFindState.NoMatch || state is EchoFindState.Failed) {
                        Spacer(Modifier.height(14.dp))
                        TextButton(onClick = onStart) { Text("Intentar de nuevo", color = NothingColors.AccentRed) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundView(
    result: RecognitionResult,
    onPlay: (RecognitionResult) -> Unit,
    onSearch: (RecognitionResult) -> Unit,
    onRetry: () -> Unit
) {
    AsyncImage(
        model = result.coverArtHqUrl ?: result.coverArtUrl,
        contentDescription = result.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(NothingColors.Surface)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        result.title,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = NothingColors.TextPrimary)
    )
    Text(result.artist, style = MaterialTheme.typography.bodyLarge.copy(color = NothingColors.AccentRed))
    val details = listOfNotNull(result.album, result.releaseDate?.take(4), result.genre).filter { it.isNotBlank() }
    if (details.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary))
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onPlay(result) },
            colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reproducir")
        }
        OutlinedButton(
            onClick = { onSearch(result) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Buscar")
        }
    }
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onRetry) { Text("Reconocer otra", color = NothingColors.TextSecondary) }
}
