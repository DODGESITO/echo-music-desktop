package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import echo.music.desktop.ui.theme.NothingColors

/** Second line shown under an item's title. */
fun YTItem.subtitleText(): String = when (this) {
    is SongItem -> artists.joinToString(", ") { it.name }.ifEmpty { "Canción" }
    is AlbumItem -> listOfNotNull(
        "Álbum",
        artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() },
        year?.toString()
    ).joinToString(" • ")
    is PlaylistItem -> listOfNotNull(
        if (isPodcast) "Podcast" else "Playlist",
        author?.name,
        songCountText
    ).joinToString(" • ")
    is ArtistItem -> "Artista"
}

private fun YTItem.artShape(corner: Int): Shape = if (this is ArtistItem) CircleShape else RoundedCornerShape(corner.dp)

@Composable
private fun ItemArtwork(item: YTItem, size: Int, corner: Int, isCurrent: Boolean, isPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(item.artShape(corner))
            .background(NothingColors.SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!item.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                when (item) {
                    is SongItem -> Icons.Default.MusicNote
                    is AlbumItem -> Icons.Default.Album
                    is PlaylistItem -> Icons.AutoMirrored.Filled.QueueMusic
                    is ArtistItem -> Icons.Default.Person
                },
                contentDescription = null,
                tint = NothingColors.TextMuted,
                modifier = Modifier.size((size / 3).dp)
            )
        }
        if (isCurrent && isPlaying) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, "Reproduciendo", tint = NothingColors.AccentRed, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Square card used in horizontal carousels (Home). */
@Composable
fun YTItemCard(
    item: YTItem,
    onClick: () -> Unit,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .width(158.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) NothingColors.Surface else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = if (item is ArtistItem) Alignment.CenterHorizontally else Alignment.Start
    ) {
        ItemArtwork(item, size = 138, corner = 10, isCurrent = isCurrent, isPlaying = isPlaying)
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary
            )
        )
        Text(
            item.subtitleText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextSecondary)
        )
    }
}

/** Full-width row used in search results for artists, albums and playlists. */
@Composable
fun YTItemRow(
    item: YTItem,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemArtwork(item, size = 52, corner = 8, isCurrent = false, isPlaying = false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = NothingColors.TextPrimary,
                    fontSize = 14.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.subtitleText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary, fontSize = 12.sp)
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Agregar a playlist",
                    tint = NothingColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = NothingColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}
