package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.MoodAndGenres
import echo.music.desktop.explore.ExploreSection
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.components.YTItemCard
import echo.music.desktop.ui.theme.NothingColors

/** Explorar → Descubrir: new releases, charts and moods & genres, like the phone app. */
@Composable
fun DiscoverPane(
    appModel: DesktopAppModel,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier
) {
    val discover by appModel.discover.collectAsState()
    val isLoading by appModel.isDiscoverLoading.collectAsState()
    val mood by appModel.selectedMood.collectAsState()
    val isMoodLoading by appModel.isMoodLoading.collectAsState()

    LaunchedEffect(Unit) { appModel.loadDiscover() }

    Box(modifier) {
        val openMood = mood
        when {
            openMood != null -> MoodView(
                title = openMood.title,
                sections = openMood.sections,
                isLoading = isMoodLoading,
                currentSong = currentSong,
                playbackState = playbackState,
                onBack = { appModel.closeMood() },
                onOpen = { item, siblings -> appModel.openItem(item, siblings) }
            )
            discover == null && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NothingColors.AccentRed)
            }
            discover == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se pudo cargar Descubrir", color = NothingColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { appModel.loadDiscover(force = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White)
                    ) { Text("Reintentar") }
                }
            }
            else -> DiscoverContentView(
                newReleases = discover!!.newReleases.orEmpty(),
                charts = discover!!.charts.orEmpty(),
                moodGroups = discover!!.moodGroups.orEmpty(),
                isRefreshing = isLoading,
                currentSong = currentSong,
                playbackState = playbackState,
                onRefresh = { appModel.loadDiscover(force = true) },
                onOpen = { item, siblings -> appModel.openItem(item, siblings) },
                onOpenMood = { item -> appModel.openMood(item.title, item.endpoint) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverContentView(
    newReleases: List<YTItem>,
    charts: List<ExploreSection>,
    moodGroups: List<MoodAndGenres>,
    isRefreshing: Boolean,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    onRefresh: () -> Unit,
    onOpen: (YTItem, List<YTItem>) -> Unit,
    onOpenMood: (MoodAndGenres.Item) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (newReleases.isNotEmpty()) {
            item(key = "new") {
                CarouselSection(
                    label = "NOVEDADES",
                    title = "Lanzamientos nuevos",
                    items = newReleases,
                    currentSong = currentSong,
                    playbackState = playbackState,
                    onOpen = onOpen,
                    trailing = {
                        IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(30.dp)) {
                            if (isRefreshing) {
                                CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, "Actualizar", tint = NothingColors.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
        }
        charts.forEachIndexed { i, section ->
            item(key = "chart_$i") {
                CarouselSection(
                    label = "CHARTS",
                    title = section.title,
                    items = section.items,
                    currentSong = currentSong,
                    playbackState = playbackState,
                    onOpen = onOpen
                )
            }
        }
        moodGroups.forEachIndexed { i, group ->
            item(key = "mood_$i") {
                Column {
                    SectionHeader(label = "EXPLORAR", title = group.title)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        group.items.forEach { mood -> MoodChip(mood, onClick = { onOpenMood(mood) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodView(
    title: String,
    sections: List<ExploreSection>,
    isLoading: Boolean,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onOpen: (YTItem, List<YTItem>) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Descubrir", tint = NothingColors.TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = NothingColors.TextPrimary)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NothingColors.AccentRed)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                sections.forEachIndexed { i, section ->
                    item(key = "s_$i") {
                        CarouselSection(
                            label = null,
                            title = section.title,
                            items = section.items,
                            currentSong = currentSong,
                            playbackState = playbackState,
                            onOpen = onOpen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(@Suppress("UNUSED_PARAMETER") label: String?, title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun CarouselSection(
    label: String?,
    title: String,
    items: List<YTItem>,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    onOpen: (YTItem, List<YTItem>) -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        SectionHeader(label, title, trailing)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                val isCurrent = currentSong?.id == item.id
                YTItemCard(
                    item = item,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playbackState.isPlaying,
                    onClick = { onOpen(item, items) }
                )
            }
        }
    }
}

/** Mood/genre chip with YouTube's color stripe on the left, like YouTube Music. */
@Composable
private fun MoodChip(mood: MoodAndGenres.Item, onClick: () -> Unit) {
    val stripe = Color(mood.stripeColor)
    Row(
        modifier = Modifier
            .width(190.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
        Spacer(Modifier.width(12.dp))
        Text(
            mood.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = NothingColors.TextPrimary)
        )
    }
}
