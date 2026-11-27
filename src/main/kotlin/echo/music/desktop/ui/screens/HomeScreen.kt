package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.components.SongListItem
import echo.music.desktop.ui.components.YTItemCard
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun HomeScreen(
    appModel: DesktopAppModel,
    homeSongs: List<SongItem>,
    isLoading: Boolean,
    history: List<SongItem>,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier
) {
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadProgress by appModel.downloadProgress.collectAsState()
    val homeSections by appModel.homeSections.collectAsState()
    val isHomeLoadingMore by appModel.isHomeLoadingMore.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Greeting
        item {
            val hour = java.time.LocalTime.now().hour
            Text(
                text = when (hour) {
                    in 5..11 -> "Buenos días"
                    in 12..18 -> "Buenas tardes"
                    else -> "Buenas noches"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = NothingColors.TextPrimary
                )
            )
        }

        // Smart recommendations
        item { DiscoverRow(appModel) }

        // Recently Played Section (if any)
        if (history.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Escuchado recientemente",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(history.take(8)) { song ->
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NothingColors.Surface)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
                                    .clickable { appModel.playSong(song, history) }
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NothingColors.SurfaceElevated)
                                ) {
                                    AsyncImage(
                                        model = song.thumbnail,
                                        contentDescription = song.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NothingColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NothingColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // YouTube Music home feed (same carousels as the phone app)
        homeSections.forEachIndexed { sectionIndex, section ->
            item(key = "section_${sectionIndex}_${section.title}") {
                if (sectionIndex == homeSections.lastIndex) {
                    LaunchedEffect(homeSections.size) { appModel.loadMoreHome() }
                }
                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.offset(x = (-10).dp)) {
                        items(section.items) { ytItem ->
                            val isCurrent = currentSong?.id == ytItem.id
                            YTItemCard(
                                item = ytItem,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && playbackState.isPlaying,
                                onClick = { appModel.openItem(ytItem, section.items) }
                            )
                        }
                    }
                }
            }
        }

        if (isHomeLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (homeSections.isEmpty()) {
        // Trending / Top Hits Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = NothingColors.AccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tendencias",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
                    )
                }
                if (homeSongs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NothingColors.SurfaceElevated)
                            .clickable { appModel.playSong(homeSongs.first(), homeSongs) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = NothingColors.AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reproducir todo",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && homeSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NothingColors.AccentRed)
                }
            }
        } else {
            items(homeSongs) { song ->
                val isCurrent = currentSong?.id == song.id
                val isPlaying = isCurrent && playbackState.isPlaying
                val isFav = appModel.isFavorite(song.id)
                val isDownloaded = downloadedSongs.any { it.id == song.id }
                val prog = downloadProgress[song.id]

                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrent = isCurrent,
                    isFavorite = isFav,
                    isDownloaded = isDownloaded,
                    downloadProgress = prog,
                    onPlay = { appModel.playSong(song, homeSongs) },
                    onToggleFavorite = { appModel.toggleFavorite(song) },
                    onArtistClick = { artistId -> appModel.openArtist(artistId) },
                    onStartRadio = { appModel.startRadio(song) },
                    onAddToPlaylist = { appModel.openAddToPlaylist(listOf(song)) },
                    onDislike = { appModel.dislikeSong(song) },
                    onBlockArtist = song.artists.firstOrNull()?.let { a -> { appModel.blockArtist(a.id, a.name) } },
                    onDownload = {
                        if (isDownloaded) {
                            appModel.deleteDownload(song.id)
                        } else {
                            appModel.downloadSong(song)
                        }
                    }
                )
            }
        }

        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Music Flow and the AI playlists, right below the welcome card. */
@Composable
private fun DiscoverRow(appModel: DesktopAppModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        DiscoverCard(
            icon = androidx.compose.material.icons.Icons.Default.Waves,
            title = "Music Flow",
            subtitle = "Desliza para descubrir: radios hechas con lo que escuchas",
            modifier = Modifier.weight(1f)
        ) { appModel.openMusicFlow() }
        DiscoverCard(
            icon = androidx.compose.material.icons.Icons.Default.AutoAwesome,
            title = "De tus gustos",
            subtitle = "La IA arma una playlist con lo que más escuchas",
            modifier = Modifier.weight(1f)
        ) { appModel.createTastePlaylist() }
        DiscoverCard(
            icon = androidx.compose.material.icons.Icons.Default.Edit,
            title = "Crear con IA",
            subtitle = "Describe un momento o un estilo y la IA elige",
            modifier = Modifier.weight(1f)
        ) { appModel.openAiCreate() }
    }
}

@Composable
private fun DiscoverCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(NothingColors.AccentRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = NothingColors.AccentRed, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
            Text(subtitle, maxLines = 2, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary))
        }
    }
}
