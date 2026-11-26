package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.innertube.models.SongItem
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.components.SongListItem
import echo.music.desktop.ui.components.YTItemRow
import echo.music.desktop.state.SearchCategory
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun ExploreScreen(
    appModel: DesktopAppModel,
    searchResults: List<SongItem>,
    isSearching: Boolean,
    statusMessage: String?,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf(appModel.searchQuery.value) }
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadProgress by appModel.downloadProgress.collectAsState()
    val searchFocusTrigger by appModel.searchFocusTrigger.collectAsState()
    val searchCategory by appModel.searchCategory.collectAsState()
    val searchItems by appModel.searchItems.collectAsState()
    val suggestions by appModel.searchSuggestions.collectAsState()
    var showResults by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger > 0L) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            appModel.isInputFocused = false
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = searchInput,
            onValueChange = {
                searchInput = it
                appModel.updateSearchSuggestions(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingColors.Surface)
                .focusRequester(focusRequester)
                .onFocusChanged { appModel.isInputFocused = it.isFocused },
            placeholder = {
                Text(
                    text = "Buscar canciones, artistas o álbumes...",
                    color = NothingColors.TextMuted,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = NothingColors.AccentRed,
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                if (searchInput.isNotEmpty()) {
                    IconButton(onClick = {
                        searchInput = ""
                        appModel.clearSearchSuggestions()
                    }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                showResults = true
                appModel.search(searchInput)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NothingColors.AccentRed,
                unfocusedBorderColor = NothingColors.SurfaceBorder,
                focusedTextColor = NothingColors.TextPrimary,
                unfocusedTextColor = NothingColors.TextPrimary,
                cursorColor = NothingColors.AccentRed
            )
        )

        // Autocomplete suggestions
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchInput = suggestion
                                showResults = true
                                appModel.search(suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = NothingColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Descubrir (charts, moods, new releases) / Resultados (search)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Descubrir", true to "Resultados").forEach { (results, label) ->
                val selected = showResults == results
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) NothingColors.SurfaceElevated else NothingColors.Surface)
                        .border(
                            1.dp,
                            if (selected) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { showResults = results }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!showResults) {
            DiscoverPane(
                appModel = appModel,
                currentSong = currentSong,
                playbackState = playbackState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            // Result category chips (songs, videos, artists, albums, playlists)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SearchCategory.entries) { category ->
                    val selected = category == searchCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) NothingColors.AccentRed else NothingColors.SurfaceElevated)
                            .border(
                                1.dp,
                                if (selected) NothingColors.AccentRed else NothingColors.SurfaceBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { appModel.setSearchCategory(category) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selected) Color.White else NothingColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results Header / Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RESULTADOS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = NothingColors.TextMuted
                    )
                )
                if (statusMessage != null) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NothingColors.AccentRed,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Results Content
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NothingColors.AccentRed)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!searchCategory.isSongList) {
                        items(searchItems) { ytItem ->
                            YTItemRow(
                                item = ytItem,
                                onClick = { appModel.openItem(ytItem) }
                            )
                        }
                    }

                    items(searchResults) { song ->
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
                            onPlay = { appModel.playSong(song, searchResults) },
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

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
