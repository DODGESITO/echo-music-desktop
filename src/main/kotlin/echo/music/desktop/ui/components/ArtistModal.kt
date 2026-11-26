package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.ArtistPage
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun ArtistModal(
    artistPage: ArtistPage?,
    isLoading: Boolean,
    appModel: DesktopAppModel,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        // Modal Card
        Box(
            modifier = Modifier
                .width(920.dp)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click through */ }
                )
                .padding(28.dp)
        ) {
            if (isLoading || artistPage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(
                            color = NothingColors.AccentRed,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Cargando perfil del artista...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NothingColors.AccentRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PERFIL DE ARTISTA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = NothingColors.TextMuted
                                )
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = NothingColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // Artist Banner
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NothingColors.Surface)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Artist Avatar
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(CircleShape)
                                        .background(NothingColors.SurfaceElevated)
                                        .border(2.dp, NothingColors.AccentRed.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!artistPage.artist.thumbnail.isNullOrBlank()) {
                                        AsyncImage(
                                            model = artistPage.artist.thumbnail,
                                            contentDescription = artistPage.artist.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = NothingColors.TextMuted,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(24.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = artistPage.artist.title,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = NothingColors.TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val listeners = artistPage.monthlyListenerCount ?: artistPage.subscriberCountText
                                    if (!listeners.isNullOrBlank()) {
                                        Text(
                                            text = listeners,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = NothingColors.TextSecondary
                                            )
                                        )
                                    }

                                    if (!artistPage.description.isNullOrBlank()) {
                                        Text(
                                            text = artistPage.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = NothingColors.TextMuted,
                                                lineHeight = 16.sp
                                            ),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Action: Start Radio
                                    val topSongs = artistPage.sections.flatMap { it.items.filterIsInstance<SongItem>() }
                                    if (topSongs.isNotEmpty()) {
                                        Button(
                                            onClick = {
                                                appModel.startRadio(topSongs.first())
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NothingColors.AccentRed,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Radio,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Radio del Artista",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }

                                    // Block / unblock this artist everywhere
                                    val blocks by appModel.contentBlocks.collectAsState()
                                    val artistId = artistPage.artist.id
                                    val artistName = artistPage.artist.title
                                    val blocked = blocks.artists.firstOrNull { it.id == artistId || it.name == artistName }
                                    OutlinedButton(
                                        onClick = {
                                            if (blocked != null) appModel.unblockArtist(blocked)
                                            else appModel.blockArtist(artistId, artistName)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextSecondary),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Block, null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (blocked != null) "Desbloquear artista" else "Bloquear artista")
                                    }
                                }
                            }
                        }

                        // Sections: Songs & Albums
                        artistPage.sections.forEach { section ->
                            val songItems = section.items.filterIsInstance<SongItem>()
                            val albumItems = section.items.filterIsInstance<AlbumItem>()

                            if (songItems.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = section.title.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp,
                                                color = NothingColors.TextSecondary
                                            )
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            songItems.take(5).forEachIndexed { index, song ->
                                                val isCurrent = currentSong?.id == song.id
                                                val isPlaying = isCurrent && playbackState.isPlaying
                                                val isFav = appModel.isFavorite(song.id)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isCurrent) NothingColors.SurfaceElevated else NothingColors.Surface)
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isCurrent) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { appModel.playSong(song, songItems) }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = String.format("%02d", index + 1),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextMuted,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        modifier = Modifier.width(28.dp)
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
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
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = song.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = song.artists.joinToString(", ") { it.name },
                                                            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    SongMoreMenuButton(song = song, appModel = appModel, withAddToPlaylist = true)

                                                    IconButton(onClick = { appModel.toggleFavorite(song) }) {
                                                        Icon(
                                                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                            contentDescription = null,
                                                            tint = if (isFav) NothingColors.AccentRed else NothingColors.TextMuted,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    IconButton(onClick = { appModel.playSong(song, songItems) }) {
                                                        Icon(
                                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            tint = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (albumItems.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = section.title.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp,
                                                color = NothingColors.TextSecondary
                                            )
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            albumItems.take(4).forEach { album ->
                                                Column(
                                                    modifier = Modifier
                                                        .width(160.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(NothingColors.Surface)
                                                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
                                                        .clickable { appModel.openAlbum(album.id) }
                                                        .padding(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(NothingColors.SurfaceElevated)
                                                    ) {
                                                        AsyncImage(
                                                            model = album.thumbnail,
                                                            contentDescription = album.title,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Text(
                                                        text = album.title,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = NothingColors.TextPrimary
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (album.year != null) {
                                                        Text(
                                                            text = album.year.toString(),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = NothingColors.TextMuted,
                                                                fontSize = 11.sp
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}
