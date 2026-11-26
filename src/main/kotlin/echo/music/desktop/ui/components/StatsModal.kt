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
import androidx.compose.material.icons.automirrored.filled.*
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
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.StatsStore
import echo.music.desktop.state.WrappedSummary
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun StatsModal(
    appModel: DesktopAppModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = remember { StatsStore.getSummary() }

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
                .width(860.dp)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click
                )
                .padding(26.dp)
        ) {
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ECHO WRAPPED • ESTADÍSTICAS DEL SISTEMA",
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

                Spacer(modifier = Modifier.height(18.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Header greeting
                    item {
                        Column {
                            Text(
                                text = "Tu Resumen Musical",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = NothingColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Análisis en tiempo real de tus hábitos de reproducción en Echo Music Desktop.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
                            )
                        }
                    }

                    // 4 Stat Cards in 2x2 Grid
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MetricCard(
                                title = "TIEMPO ESCUCHADO",
                                value = "${summary.totalHours} h",
                                subtitle = "${summary.totalMinutes} minutos totales",
                                icon = Icons.Default.AccessTime,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "REPRODUCCIONES",
                                value = "${summary.totalPlays}",
                                subtitle = "Pistas iniciadas",
                                icon = Icons.Default.PlayCircle,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "CANCIONES ÚNICAS",
                                value = "${summary.uniqueSongsCount}",
                                subtitle = "En tu biblioteca",
                                icon = Icons.Default.MusicNote,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "RACHA DIARIA",
                                value = "${summary.streakDays} d",
                                subtitle = "Días consecutivos",
                                icon = Icons.Default.LocalFireDepartment,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Section: Top Artists
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = NothingColors.AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TOP 5 ARTISTAS MÁS ESCUCHADOS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = NothingColors.TextSecondary
                                    )
                                )
                            }

                            if (summary.topArtists.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NothingColors.Surface)
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aún no hay reproducciones registradas. ¡Empieza a escuchar música para ver tus estadísticas!",
                                        style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextMuted)
                                    )
                                }
                            } else {
                                summary.topArtists.forEachIndexed { index, artist ->
                                    ArtistRankingRow(
                                        rank = index + 1,
                                        name = artist.name,
                                        minutes = artist.playTimeMinutes,
                                        percentage = artist.percentage
                                    )
                                }
                            }
                        }
                    }

                    // Section: Top Songs
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = NothingColors.AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TUS CANCIONES MÁS REPRODUCIDAS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = NothingColors.TextSecondary
                                    )
                                )
                            }

                            if (summary.topSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NothingColors.Surface)
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Sin canciones en el ranking todavía.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextMuted)
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(summary.topSongs) { idx, topSong ->
                        val isFav = appModel.isFavorite(topSong.song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NothingColors.Surface)
                                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d", idx + 1),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) NothingColors.AccentRed else NothingColors.TextMuted
                                ),
                                modifier = Modifier.width(32.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NothingColors.SurfaceElevated)
                            ) {
                                AsyncImage(
                                    model = topSong.song.thumbnail,
                                    contentDescription = topSong.song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topSong.song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NothingColors.TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = topSong.song.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Plays count badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${topSong.playCount} reps",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(onClick = { appModel.toggleFavorite(topSong.song) }) {
                                Icon(
                                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFav) NothingColors.AccentRed else NothingColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = { appModel.playSong(topSong.song) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = NothingColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
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

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingColors.TextMuted,
                        letterSpacing = 1.sp
                    )
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = NothingColors.AccentRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = NothingColors.TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = NothingColors.TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun ArtistRankingRow(
    rank: Int,
    name: String,
    minutes: Long,
    percentage: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (rank == 1) NothingColors.AccentRed else NothingColors.TextMuted
                    ),
                    modifier = Modifier.width(30.dp)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NothingColors.TextPrimary
                    )
                )
            }
            Text(
                text = "$minutes min",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = NothingColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (rank == 1) NothingColors.AccentRed else NothingColors.TextSecondary,
            trackColor = NothingColors.SurfaceElevated
        )
    }
}
