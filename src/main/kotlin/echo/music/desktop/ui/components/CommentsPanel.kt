package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.state.CommentsState
import echo.music.desktop.ui.theme.NothingColors

/** YouTube comments of the song playing (expanded player → Comentarios). */
@Composable
fun ColumnScope.CommentsPanel(
    state: CommentsState,
    songId: String?,
    onLoad: () -> Unit,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(songId) { if (songId != null) onLoad() }

    when {
        state.loading && state.comments.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
        }
        state.comments.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ChatBubbleOutline, null, tint = NothingColors.TextMuted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    state.message ?: "Esta canción no tiene comentarios.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextMuted)
                )
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(state.comments) { c ->
                Row {
                    AsyncImage(
                        model = c.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(NothingColors.SurfaceElevated)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.author,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(c.time, style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted))
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            c.text,
                            style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary, lineHeight = 19.sp)
                        )
                        if (c.likes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ThumbUp, null, tint = NothingColors.TextMuted, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(c.likes, style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted))
                            }
                        }
                    }
                }
            }
            if (state.continuation != null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.loading) {
                            CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = onLoadMore) { Text("Cargar más comentarios", color = NothingColors.AccentRed) }
                        }
                    }
                }
            }
        }
    }
}
