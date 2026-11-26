package echo.music.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun SongListItem(
    song: SongItem,
    isPlaying: Boolean,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    val artists = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" }
    val durationFormatted = song.duration?.let { sec ->
        val m = sec / 60
        val s = sec % 60
        String.format("%d:%02d", m, s)
    } ?: "--:--"

    val firstArtistId = song.artists.firstOrNull()?.id

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) NothingColors.SurfaceElevated else NothingColors.Surface)
            .border(
                width = 1.dp,
                color = if (isCurrent) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with play overlay
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NothingColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = NothingColors.AccentRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artists,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (onArtistClick != null && firstArtistId != null) NothingColors.AccentRed.copy(alpha = 0.85f) else NothingColors.TextSecondary,
                    fontSize = 12.sp
                ),
                modifier = if (onArtistClick != null && firstArtistId != null) {
                    Modifier.clickable { onArtistClick(firstArtistId) }
                } else Modifier
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Start Radio action
        if (onStartRadio != null) {
            IconButton(
                onClick = onStartRadio,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Radio,
                    contentDescription = "Iniciar Radio de la pista",
                    tint = NothingColors.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Add to playlist
        if (onAddToPlaylist != null) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Agregar a playlist",
                    tint = NothingColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Download Action
        if (downloadProgress != null) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(16.dp),
                    color = NothingColors.AccentRed,
                    trackColor = NothingColors.SurfaceBorder,
                    strokeWidth = 2.dp
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else if (onDownload != null) {
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                    contentDescription = if (isDownloaded) "Descargada (sin conexión)" else "Descargar pista",
                    tint = if (isDownloaded) NothingColors.AccentRed else NothingColors.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Favorite Toggle
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                tint = if (isFavorite) NothingColors.AccentRed else NothingColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        // More: "No me gusta" / block artist
        SongMoreMenuButton(song = song, onDislike = onDislike, onBlockArtist = onBlockArtist)

        Spacer(modifier = Modifier.width(8.dp))

        // Duration text
        Text(
            text = durationFormatted,
            style = MaterialTheme.typography.bodySmall.copy(
                color = NothingColors.TextMuted,
                fontSize = 12.sp
            ),
            modifier = Modifier.width(42.dp)
        )
    }
}
