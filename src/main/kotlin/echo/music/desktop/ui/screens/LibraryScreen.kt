package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.DownloadStore
import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.UserPlaylist
import echo.music.desktop.state.LibrarySubTab
import echo.music.desktop.ui.components.SongListItem
import echo.music.desktop.ui.components.YTItemCard
import echo.music.desktop.ui.theme.NothingColors

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun LibraryScreen(
    appModel: DesktopAppModel,
    favorites: List<SongItem>,
    history: List<SongItem>,
    currentSong: SongItem?,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier
) {
    val selectedSubTab by appModel.librarySubTab.collectAsState()
    val userPlaylists by appModel.userPlaylists.collectAsState()
    val isSyncingYouTube by appModel.isSyncingYouTube.collectAsState()
    val isImportingSpotify by appModel.isImportingSpotify.collectAsState()
    val ytAccount by appModel.youtubeAccount.collectAsState()
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadProgress by appModel.downloadProgress.collectAsState()
    val libraryAlbums by appModel.libraryAlbums.collectAsState()
    val libraryArtists by appModel.libraryArtists.collectAsState()
    val isAccountLibraryLoading by appModel.isAccountLibraryLoading.collectAsState()
    val localTracks by appModel.localTracks.collectAsState()
    val spotifyPlaylists by appModel.spotifyPlaylists.collectAsState()

    val displayList = when (selectedSubTab) {
        LibrarySubTab.FAVORITES -> favorites
        LibrarySubTab.HISTORY -> history
        LibrarySubTab.DOWNLOADS -> downloadedSongs.map { it.toSongItem() }
        LibrarySubTab.PLAYLISTS, LibrarySubTab.ALBUMS, LibrarySubTab.ARTISTS, LibrarySubTab.LOCAL, LibrarySubTab.SPOTIFY -> emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Tabs across the whole width (they scroll sideways if the window is narrow).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            LibrarySubTab.entries.forEach { subTab ->
                val isSelected = selectedSubTab == subTab
                val count = when (subTab) {
                    LibrarySubTab.FAVORITES -> favorites.size
                    LibrarySubTab.HISTORY -> history.size
                    LibrarySubTab.PLAYLISTS -> userPlaylists.size
                    LibrarySubTab.DOWNLOADS -> downloadedSongs.size
                    LibrarySubTab.ALBUMS -> libraryAlbums.size
                    LibrarySubTab.ARTISTS -> libraryArtists.size
                    LibrarySubTab.LOCAL -> localTracks.size
                    LibrarySubTab.SPOTIFY -> spotifyPlaylists.size
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NothingColors.AccentRed.copy(alpha = 0.14f) else NothingColors.Surface)
                        .border(1.dp, if (isSelected) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder, RoundedCornerShape(20.dp))
                        .clickable { appModel.selectLibrarySubTab(subTab) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (subTab) {
                            LibrarySubTab.FAVORITES -> Icons.Default.Favorite
                            LibrarySubTab.HISTORY -> Icons.Default.History
                            LibrarySubTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                            LibrarySubTab.DOWNLOADS -> Icons.Default.DownloadDone
                            LibrarySubTab.ALBUMS -> Icons.Default.Album
                            LibrarySubTab.ARTISTS -> Icons.Default.Person
                            LibrarySubTab.LOCAL -> Icons.Default.Computer
                            LibrarySubTab.SPOTIFY -> Icons.Default.LibraryMusic
                        },
                        contentDescription = null,
                        tint = if (isSelected) NothingColors.AccentRed else NothingColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = subTab.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted, fontSize = 11.sp)
                        )
                    }
                }
            }
        }

        // What can be done in the selected tab (only when there is something).
        val hasActions = selectedSubTab == LibrarySubTab.PLAYLISTS || selectedSubTab == LibrarySubTab.DOWNLOADS ||
            ((selectedSubTab == LibrarySubTab.FAVORITES || selectedSubTab == LibrarySubTab.HISTORY) && displayList.isNotEmpty())
        if (hasActions) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when (selectedSubTab) {
                    LibrarySubTab.PLAYLISTS -> {
                        var newMenu by remember { mutableStateOf(false) }
                        Box {
                            LibraryAction(Icons.Default.Add, "Nueva", primary = true, trailing = Icons.Default.ArrowDropDown) { newMenu = true }
                            DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }, containerColor = NothingColors.SurfaceElevated) {
                                DropdownMenuItem(
                                    text = { Text("Playlist vacía", color = NothingColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Add, null, tint = NothingColors.TextSecondary) },
                                    onClick = { newMenu = false; appModel.openCreatePlaylist() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Crear con IA", color = NothingColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = NothingColors.AccentRed) },
                                    onClick = { newMenu = false; appModel.openAiCreate() }
                                )
                                DropdownMenuItem(
                                    text = { Text("De tus gustos (IA)", color = NothingColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = NothingColors.AccentRed) },
                                    onClick = { newMenu = false; appModel.createTastePlaylist() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Importar enlace de Spotify", color = NothingColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Download, null, tint = SpotifyGreen) },
                                    onClick = { newMenu = false; appModel.openSpotifyImportModal() }
                                )
                            }
                        }
                        LibraryAction(
                            if (ytAccount != null) Icons.Default.Sync else Icons.Default.CloudSync,
                            if (ytAccount != null) "Sincronizar YouTube" else "Conectar YouTube",
                            loading = isSyncingYouTube
                        ) {
                            if (ytAccount == null) appModel.openYouTubeLoginModal() else appModel.syncYouTubePlaylists()
                        }
                    }
                    LibrarySubTab.DOWNLOADS -> {
                        if (displayList.isNotEmpty()) {
                            LibraryAction(Icons.Default.PlayArrow, "Reproducir sin conexión", primary = true) {
                                appModel.playSong(displayList.first(), displayList)
                            }
                        }
                        Text(
                            text = DownloadStore.getFormattedDiskSpace() + " en disco",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = NothingColors.TextMuted, fontSize = 11.sp)
                        )
                    }
                    else -> LibraryAction(Icons.Default.PlayArrow, "Reproducir todo", primary = true) {
                        appModel.playSong(displayList.first(), displayList)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Content Area
        when (selectedSubTab) {
            LibrarySubTab.PLAYLISTS -> {
                if (userPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "No tienes listas de reproducción todavía",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Importa playlists públicas de Spotify o conecta tu cuenta de YouTube Music para sincronizarlas.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NothingColors.TextSecondary
                                )
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Button(
                                    onClick = { appModel.openSpotifyImportModal() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpotifyGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importar de Spotify")
                                }

                                Button(
                                    onClick = { appModel.openYouTubeLoginModal() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingColors.AccentRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Conectar YouTube Music")
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(userPlaylists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                appModel = appModel,
                                onClick = { appModel.openPlaylistDetail(playlist) },
                                onPlay = { appModel.playPlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            LibrarySubTab.LOCAL -> {
                LocalMusicContent(appModel = appModel, currentSong = currentSong, playbackState = playbackState)
            }

            LibrarySubTab.SPOTIFY -> {
                SpotifyLibraryContent(appModel = appModel, modifier = Modifier.weight(1f))
            }

            LibrarySubTab.ALBUMS, LibrarySubTab.ARTISTS -> {
                AccountLibraryContent(
                    appModel = appModel,
                    items = if (selectedSubTab == LibrarySubTab.ALBUMS) libraryAlbums else libraryArtists,
                    isAlbums = selectedSubTab == LibrarySubTab.ALBUMS,
                    isLoggedIn = ytAccount != null,
                    isLoading = isAccountLibraryLoading,
                    currentSongId = currentSong?.id
                )
            }

            LibrarySubTab.FAVORITES, LibrarySubTab.HISTORY, LibrarySubTab.DOWNLOADS -> {
                if (displayList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                when (selectedSubTab) {
                                    LibrarySubTab.FAVORITES -> Icons.Default.Favorite
                                    LibrarySubTab.HISTORY -> Icons.Default.History
                                    LibrarySubTab.DOWNLOADS -> Icons.Default.DownloadDone
                                    else -> Icons.Default.MusicNote
                                },
                                contentDescription = null,
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = when (selectedSubTab) {
                                    LibrarySubTab.FAVORITES -> "No tienes canciones favoritas aún"
                                    LibrarySubTab.HISTORY -> "Historial vacío"
                                    LibrarySubTab.DOWNLOADS -> "No tienes canciones descargadas"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (selectedSubTab) {
                                    LibrarySubTab.FAVORITES -> "Toca el ícono de corazón en cualquier canción para guardarla aquí."
                                    LibrarySubTab.HISTORY -> "Las canciones que reproduzcas aparecerán aquí automáticamente."
                                    LibrarySubTab.DOWNLOADS -> "Descarga pistas con el botón de descarga para reproducirlas al instante sin conexión."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NothingColors.TextSecondary
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayList) { song ->
                            val isCurrent = currentSong?.id == song.id
                            val isPlaying = isCurrent && playbackState.isPlaying
                            val isFav = appModel.isFavorite(song.id)
                            val isDown = appModel.isDownloaded(song.id)
                            val progress = downloadProgress[song.id]

                            SongListItem(
                                song = song,
                                isPlaying = isPlaying,
                                isCurrent = isCurrent,
                                isFavorite = isFav,
                                isDownloaded = isDown,
                                downloadProgress = progress,
                                onPlay = { appModel.playSong(song, displayList) },
                                onToggleFavorite = { appModel.toggleFavorite(song) },
                                onDownload = {
                                    if (isDown) {
                                        appModel.deleteDownload(song.id)
                                    } else {
                                        appModel.downloadSong(song)
                                    }
                                },
                                onArtistClick = { artistId -> appModel.openArtist(artistId) },
                                onStartRadio = { appModel.startRadio(song) },
                                onAddToPlaylist = { appModel.openAddToPlaylist(listOf(song)) },
                                onDislike = { appModel.dislikeSong(song) },
                                onBlockArtist = song.artists.firstOrNull()?.let { a -> { appModel.blockArtist(a.id, a.name) } }
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
}

@Composable
private fun PlaylistCard(
    playlist: UserPlaylist,
    appModel: DesktopAppModel,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlistDownloadingMap by appModel.playlistDownloading.collectAsState()
    val isDownloading = playlistDownloadingMap[playlist.id] == true
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadedCount = remember(playlist.songs, downloadedSongs) {
        playlist.songs.count { appModel.isDownloaded(it.id) }
    }
    val isFullyDownloaded = playlist.songs.isNotEmpty() && downloadedCount == playlist.songs.size

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Thumbnail + play overlay button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(NothingColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = NothingColors.TextMuted,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Quick Play Button at bottom right
            if (playlist.songs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NothingColors.AccentRed)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Source Badge at top left
            val (badgeText, badgeBg, badgeColor) = when (playlist.sourceType) {
                PlaylistSourceType.SPOTIFY -> Triple("SPOTIFY", Color(0xFF1DB954), Color.White)
                PlaylistSourceType.YOUTUBE_MUSIC -> Triple("YT MUSIC", NothingColors.AccentRed, Color.White)
                PlaylistSourceType.LOCAL -> Triple("LOCAL", NothingColors.SurfaceElevated, NothingColors.TextSecondary)
                PlaylistSourceType.PODCAST -> Triple("PODCAST", Color(0xFF9C6ADE).copy(alpha = 0.2f), Color(0xFF9C6ADE))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        fontSize = 9.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title and Download Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NothingColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (playlist.songs.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NothingColors.SurfaceElevated)
                            .clickable { appModel.cancelPlaylistDownload(playlist.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = NothingColors.AccentRed,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (isFullyDownloaded) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Descargada",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NothingColors.SurfaceElevated)
                            .clickable { appModel.downloadPlaylist(playlist) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Descargar playlist",
                            tint = NothingColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Author and track count
        Text(
            text = buildString {
                append("${playlist.author} • ${playlist.songs.size} pistas")
                if (downloadedCount > 0 && !isFullyDownloaded) {
                    append(" ($downloadedCount desc.)")
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(
                color = NothingColors.TextSecondary,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Albums or artists saved in the user's YouTube Music library. */
@Composable
private fun ColumnScope.AccountLibraryContent(
    appModel: DesktopAppModel,
    items: List<YTItem>,
    isAlbums: Boolean,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSongId: String?
) {
    val what = if (isAlbums) "álbumes" else "artistas"
    when {
        !isLoggedIn -> LibraryMessage(
            icon = if (isAlbums) Icons.Default.Album else Icons.Default.Person,
            title = "Conecta tu cuenta de YouTube Music",
            body = "Aquí aparecen los $what que guardaste en tu biblioteca de YouTube Music.",
            buttonText = "Conectar YouTube Music",
            onButton = { appModel.openYouTubeLoginModal() }
        )
        isLoading && items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NothingColors.AccentRed)
        }
        items.isEmpty() -> LibraryMessage(
            icon = if (isAlbums) Icons.Default.Album else Icons.Default.Person,
            title = "No tienes $what guardados",
            body = "Guarda $what en YouTube Music y aparecerán aquí.",
            buttonText = "Actualizar",
            onButton = { appModel.loadAccountLibrary(force = true) }
        )
        else -> {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${items.size} $what en tu biblioteca de YouTube Music",
                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { appModel.loadAccountLibrary(force = true) }, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.Sync, null, tint = NothingColors.TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Actualizar", color = NothingColors.TextSecondary)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items) { item ->
                    YTItemCard(
                        item = item,
                        isCurrent = item.id == currentSongId,
                        onClick = { appModel.openItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LibraryMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    buttonText: String,
    onButton: () -> Unit
) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = NothingColors.TextMuted, modifier = Modifier.size(54.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
            Text(body, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary))
            Button(
                onClick = onButton,
                colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) { Text(buttonText) }
        }
    }
}

@Composable
private fun LibraryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean = false,
    loading: Boolean = false,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) NothingColors.AccentRed else NothingColors.SurfaceElevated)
            .border(1.dp, if (primary) NothingColors.AccentRed else NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NothingColors.AccentRed, strokeWidth = 2.dp)
        else Icon(icon, contentDescription = null, tint = if (primary) Color.White else NothingColors.TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp))
        if (trailing != null) Icon(trailing, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
