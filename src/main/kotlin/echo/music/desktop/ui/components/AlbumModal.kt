package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun AlbumModal(
    albumPage: AlbumPage?,
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
                .width(880.dp)
                .fillMaxHeight(0.88f)
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
            if (isLoading || albumPage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(
                            color = NothingColors.AccentRed,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Cargando pistas del álbum...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
                        )
                    }
                }
            } else {
                val albumArtistName = albumPage.album.artists?.joinToString(", ") { it.name }.orEmpty()
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
                                text = "DETALLE DE ÁLBUM",
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

                    // Album Banner (Cover + Title + Author + Play all)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NothingColors.Surface)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NothingColors.SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!albumPage.album.thumbnail.isNullOrBlank()) {
                                AsyncImage(
                                    model = albumPage.album.thumbnail,
                                    contentDescription = albumPage.album.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Album,
                                    contentDescription = null,
                                    tint = NothingColors.TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(22.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = albumPage.album.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Artist name (clickable!)
                            if (albumArtistName.isNotBlank()) {
                                Text(
                                    text = albumArtistName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = NothingColors.AccentRed,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.clickable {
                                        val firstArtist = albumPage.album.artists?.firstOrNull()
                                        if (firstArtist?.id != null) {
                                            appModel.openArtist(firstArtist.id!!)
                                        }
                                    }
                                )
                            }

                            val sub = listOfNotNull(albumPage.album.year?.toString(), "${albumPage.songs.size} canciones").joinToString(" • ")
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (albumPage.songs.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        appModel.playSong(albumPage.songs.first(), albumPage.songs)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingColors.AccentRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reproducir álbum",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            if (albumPage.songs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { appModel.openAddToPlaylist(albumPage.songs) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Agregar a playlist")
                                }
                            }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tracklist
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(albumPage.songs) { index, song ->
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
                                    .clickable { appModel.playSong(song, albumPage.songs) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%02d", index + 1),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isCurrent) NothingColors.AccentRed else NothingColors.TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(28.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

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
                                        text = song.artists.joinToString(", ") { it.name }.ifEmpty { albumArtistName },
                                        style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val dur = song.duration ?: 0
                                if (dur > 0) {
                                    val minutes = dur / 60
                                    val seconds = dur % 60
                                    Text(
                                        text = String.format("%d:%02d", minutes, seconds),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = NothingColors.TextMuted,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }

                                SongMoreMenuButton(song = song, appModel = appModel, withAddToPlaylist = true)

                                IconButton(onClick = { appModel.toggleFavorite(song) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFav) NothingColors.AccentRed else NothingColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(onClick = { appModel.playSong(song, albumPage.songs) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isCurrent) NothingColors.AccentRed else NothingColors.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
