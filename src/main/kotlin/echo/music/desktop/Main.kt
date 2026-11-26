package echo.music.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import echo.music.desktop.state.DesktopAppModel
import kotlinx.coroutines.launch
import echo.music.desktop.state.NavigationTab
import echo.music.desktop.state.PreferencesStore
import echo.music.desktop.system.GlobalMediaKeys
import echo.music.desktop.system.MediaKey
import echo.music.desktop.ui.components.AddToPlaylistModal
import echo.music.desktop.ui.components.AlbumModal
import echo.music.desktop.ui.components.CreatePlaylistModal
import echo.music.desktop.ui.components.EchoFindModal
import echo.music.desktop.ui.components.ArtistModal
import echo.music.desktop.ui.components.BottomPlayerBar
import echo.music.desktop.ui.components.EqualizerModal
import echo.music.desktop.ui.components.ExpandedPlayerModal
import echo.music.desktop.ui.components.ListenTogetherModal
import echo.music.desktop.ui.components.PlaylistDetailModal
import echo.music.desktop.ui.components.Sidebar
import echo.music.desktop.ui.components.SpotifyImportModal
import echo.music.desktop.ui.components.StatsModal
import echo.music.desktop.ui.components.YouTubeLoginModal
import echo.music.desktop.ui.screens.ExploreScreen
import echo.music.desktop.ui.screens.HomeScreen
import echo.music.desktop.ui.screens.LibraryScreen
import echo.music.desktop.ui.screens.SettingsScreen
import echo.music.desktop.ui.theme.EchoDesktopTheme
import echo.music.desktop.ui.theme.NothingColors

fun main(args: Array<String>) = application {
    val prefs = PreferencesStore.current
    remember { echo.music.desktop.ui.theme.NothingColors.applyTheme(prefs.themeAccent, prefs.themeBackground) }
    val windowState = rememberWindowState(
        placement = if (prefs.isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = if (prefs.windowX != null && prefs.windowY != null) {
            WindowPosition(prefs.windowX.dp, prefs.windowY.dp)
        } else {
            WindowPosition(Alignment.Center)
        },
        width = prefs.windowWidth.dp,
        height = prefs.windowHeight.dp
    )
    val appModel = remember { DesktopAppModel() }
    // Started with Windows in the tray: the window stays hidden until opened from the tray icon.
    var isWindowVisible by remember { mutableStateOf(echo.music.desktop.system.WindowsStartup.MINIMIZED_ARG !in args) }
    var isMiniPlayerOpen by remember { mutableStateOf(false) }
    fun openMiniPlayer() {
        isMiniPlayerOpen = true
        isWindowVisible = false
    }

    fun saveWindowState() {
        val pos = windowState.position
        val x = if (pos is WindowPosition.Absolute) pos.x.value.toInt() else null
        val y = if (pos is WindowPosition.Absolute) pos.y.value.toInt() else null
        PreferencesStore.update {
            it.copy(
                windowWidth = windowState.size.width.value.toInt(),
                windowHeight = windowState.size.height.value.toInt(),
                windowX = x,
                windowY = y,
                isMaximized = windowState.placement == WindowPlacement.Maximized
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveWindowState()
            appModel.release()
        }
    }

    val currentSong by appModel.currentSong.collectAsState()
    val playbackState by appModel.playbackState.collectAsState()

    // Windows media panel: once connected, Windows itself sends the keyboard media keys to it.
    var mediaPanelActive by remember { mutableStateOf(false) }

    // System-wide media keys (Windows). While active, the window ignores those keys to avoid double actions.
    val globalMediaKeysEnabled by appModel.globalMediaKeysEnabled.collectAsState()
    var globalMediaKeysActive by remember { mutableStateOf(false) }
    DisposableEffect(globalMediaKeysEnabled, mediaPanelActive) {
        globalMediaKeysActive = mediaPanelActive ||
            (globalMediaKeysEnabled && GlobalMediaKeys.start { appModel.onMediaKey(it) })
        onDispose {
            GlobalMediaKeys.stop()
            globalMediaKeysActive = false
        }
    }

    val trayState = rememberTrayState()
    val appIcon = painterResource("icon.png")

    Tray(
        icon = appIcon,
        state = trayState,
        tooltip = if (currentSong != null) "${currentSong!!.title} • Echo Music" else "Echo Music Desktop",
        onAction = {
            isWindowVisible = true
        },
        menu = {
            Item("Echo Music Desktop", enabled = false, onClick = {})
            Separator()
            if (currentSong != null) {
                Item("▶ ${currentSong!!.title.take(30)}", enabled = false, onClick = {})
                Separator()
            }
            Item(
                if (playbackState.isPlaying) "Pausar" else "Reproducir",
                onClick = { appModel.togglePlayPause() }
            )
            Item("Siguiente pista", onClick = { appModel.playNext() })
            Item("Pista anterior", onClick = { appModel.playPrevious() })
            Separator()
            Item(
                if (isMiniPlayerOpen) "Cerrar mini reproductor" else "Mini reproductor",
                onClick = { if (isMiniPlayerOpen) isMiniPlayerOpen = false else openMiniPlayer() }
            )
            Item(
                if (isWindowVisible) "Ocultar en segundo plano" else "Mostrar ventana",
                onClick = { isWindowVisible = !isWindowVisible }
            )
            Item("Salir de Echo Music", onClick = {
                saveWindowState()
                exitApplication()
            })
        }
    )

    if (isMiniPlayerOpen) {
        echo.music.desktop.ui.components.MiniPlayerWindow(
            appModel = appModel,
            onOpenMain = {
                isMiniPlayerOpen = false
                isWindowVisible = true
            },
            onClose = { isMiniPlayerOpen = false }
        )
    }

    Window(
        onCloseRequest = {
            saveWindowState()
            if (PreferencesStore.current.minimizeToTray) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        state = windowState,
        title = if (currentSong != null) "${currentSong!!.title} • Echo Music" else "Echo Music • Nothing OS 5.0 Desktop",
        icon = appIcon,
        visible = isWindowVisible,
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    // Global Ctrl shortcuts
                    keyEvent.isCtrlPressed && keyEvent.key == Key.F -> {
                        appModel.triggerSearchFocus()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.W -> {
                        saveWindowState()
                        isWindowVisible = false
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.M -> {
                        saveWindowState()
                        openMiniPlayer()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Q -> {
                        saveWindowState()
                        exitApplication()
                        true
                    }

                    // Escape closes active modal
                    keyEvent.key == Key.Escape -> {
                        appModel.closeActiveModal()
                    }

                    // Hardware Media Keys (handled system-wide by GlobalMediaKeys when it is active)
                    keyEvent.key == Key.MediaPlayPause -> {
                        if (!globalMediaKeysActive) appModel.onMediaKey(MediaKey.PLAY_PAUSE)
                        true
                    }
                    keyEvent.key == Key.MediaPlay -> {
                        if (!playbackState.isPlaying) appModel.togglePlayPause()
                        true
                    }
                    keyEvent.key == Key.MediaPause -> {
                        if (playbackState.isPlaying) appModel.togglePlayPause()
                        true
                    }
                    keyEvent.key == Key.MediaNext -> {
                        if (!globalMediaKeysActive) appModel.onMediaKey(MediaKey.NEXT)
                        true
                    }
                    keyEvent.key == Key.MediaPrevious -> {
                        if (!globalMediaKeysActive) appModel.onMediaKey(MediaKey.PREVIOUS)
                        true
                    }
                    keyEvent.key == Key.MediaStop -> {
                        if (!globalMediaKeysActive) appModel.onMediaKey(MediaKey.STOP)
                        true
                    }

                    // In-app player shortcuts when NOT editing a text field
                    !appModel.isInputFocused -> {
                        when (keyEvent.key) {
                            Key.Spacebar -> {
                                appModel.togglePlayPause()
                                true
                            }
                            Key.DirectionRight -> {
                                appModel.seekBySeconds(5)
                                true
                            }
                            Key.DirectionLeft -> {
                                appModel.seekBySeconds(-5)
                                true
                            }
                            Key.DirectionUp -> {
                                appModel.adjustVolume(0.05f)
                                true
                            }
                            Key.DirectionDown -> {
                                appModel.adjustVolume(-0.05f)
                                true
                            }
                            Key.M -> {
                                appModel.toggleMute()
                                true
                            }
                            Key.N -> {
                                appModel.playNext()
                                true
                            }
                            Key.P -> {
                                appModel.playPrevious()
                                true
                            }
                            Key.L -> {
                                currentSong?.let { appModel.toggleFavorite(it) }
                                true
                            }
                            Key.S -> {
                                appModel.toggleShuffle()
                                true
                            }
                            Key.R -> {
                                appModel.toggleRepeatMode()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        // Windows media panel (title, artist, cover, ⏮ ⏯ ⏭ and progress next to the volume indicator).
        val mainWindow = window
        LaunchedEffect(Unit) {
            // Started in the tray the window was never shown: give it its native handle anyway.
            if (!mainWindow.isDisplayable) mainWindow.addNotify()
            val hwnd = mainWindow.windowHandle
            val started = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                echo.music.desktop.system.WindowsMediaControls.start(
                    hwnd,
                    onButton = { button ->
                        javax.swing.SwingUtilities.invokeLater {
                            val playing = appModel.playbackState.value.isPlaying
                            when (button) {
                                echo.music.desktop.system.MediaControlButton.PLAY -> if (!playing) appModel.togglePlayPause()
                                echo.music.desktop.system.MediaControlButton.PAUSE -> if (playing) appModel.togglePlayPause()
                                echo.music.desktop.system.MediaControlButton.STOP -> appModel.onMediaKey(MediaKey.STOP)
                                echo.music.desktop.system.MediaControlButton.NEXT -> appModel.onMediaKey(MediaKey.NEXT)
                                echo.music.desktop.system.MediaControlButton.PREVIOUS -> appModel.onMediaKey(MediaKey.PREVIOUS)
                            }
                        }
                    },
                    onSeek = { ms -> javax.swing.SwingUtilities.invokeLater { appModel.seekToPositionMs(ms) } }
                )
            }
            mediaPanelActive = started
            if (!started) return@LaunchedEffect
            launch {
                appModel.currentSong.collect { song ->
                    echo.music.desktop.system.WindowsMediaControls.updateSong(
                        songId = song?.id,
                        title = song?.title.orEmpty(),
                        artist = song?.artists?.joinToString(", ") { it.name }.orEmpty(),
                        thumbnailUrl = song?.thumbnail
                    )
                }
            }
            launch {
                appModel.playbackState.collect { state ->
                    echo.music.desktop.system.WindowsMediaControls.updatePlayback(
                        hasSong = appModel.currentSong.value != null,
                        isPlaying = state.isPlaying,
                        positionMs = state.currentPositionMs,
                        durationMs = state.durationMs
                    )
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                echo.music.desktop.system.WindowsMediaControls.stop()
                mediaPanelActive = false
            }
        }

        EchoDesktopTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = NothingColors.Background
            ) {
                val currentTab by appModel.currentTab.collectAsState()
                val playbackState by appModel.playbackState.collectAsState()
                val currentSong by appModel.currentSong.collectAsState()
                val queue by appModel.queue.collectAsState()
                val currentIndex by appModel.currentIndex.collectAsState()
                val isShuffle by appModel.isShuffle.collectAsState()
                val repeatMode by appModel.repeatMode.collectAsState()
                val lyrics by appModel.currentLyrics.collectAsState()
                val isLyricsLoading by appModel.isLyricsLoading.collectAsState()
                val lyricsSwitchingTo by appModel.lyricsSwitchingTo.collectAsState()
                val lyricsExtras by appModel.lyricsExtras.collectAsState()
                val localTracks by appModel.localTracks.collectAsState()
                val comments by appModel.comments.collectAsState()
                val canvasFile by appModel.canvasFile.collectAsState()
                val prefs by appModel.prefs.collectAsState()
                val isExpandedPlayerOpen by appModel.isExpandedPlayerOpen.collectAsState()
                val favorites by appModel.favorites.collectAsState()
                val history by appModel.history.collectAsState()
                val homeSongs by appModel.homeSongs.collectAsState()
                val isHomeLoading by appModel.isHomeLoading.collectAsState()
                val searchResults by appModel.searchResults.collectAsState()
                val isSearching by appModel.isSearching.collectAsState()
                val statusMessage by appModel.statusMessage.collectAsState()
                val userPlaylists by appModel.userPlaylists.collectAsState()
                val youtubeAccount by appModel.youtubeAccount.collectAsState()
                val librarySubTab by appModel.librarySubTab.collectAsState()
                val isYouTubeLoginModalOpen by appModel.isYouTubeLoginModalOpen.collectAsState()
                val isSpotifyImportModalOpen by appModel.isSpotifyImportModalOpen.collectAsState()
                val isSyncingYouTube by appModel.isSyncingYouTube.collectAsState()
                val isImportingSpotify by appModel.isImportingSpotify.collectAsState()
                val importProgressText by appModel.importProgressText.collectAsState()
                val selectedPlaylistDetail by appModel.selectedPlaylistDetail.collectAsState()
                val isEqualizerModalOpen by appModel.isEqualizerModalOpen.collectAsState()
                val selectedArtistDetail by appModel.selectedArtistDetail.collectAsState()
                val isArtistLoading by appModel.isArtistLoading.collectAsState()
                val selectedAlbumDetail by appModel.selectedAlbumDetail.collectAsState()
                val isAlbumLoading by appModel.isAlbumLoading.collectAsState()
                val downloadedSongs by appModel.downloadedSongs.collectAsState()
                val downloadProgress by appModel.downloadProgress.collectAsState()
                val isStatsModalOpen by appModel.isStatsModalOpen.collectAsState()
                val isAmbientGlowEnabled by appModel.isAmbientGlowEnabled.collectAsState()
                val sleepTimerRemainingSeconds by appModel.sleepTimerRemainingSeconds.collectAsState()
                val isListenTogetherModalOpen by appModel.isListenTogetherModalOpen.collectAsState()
                val listenTogetherRoomState by appModel.listenTogetherManager.roomState.collectAsState()

                val isFavorite = currentSong != null && appModel.isFavorite(currentSong!!.id)

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main layout: Sidebar + Screen Content
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 90.dp) // Space for persistent bottom player
                    ) {
                        // Left Persistent Sidebar
                        Sidebar(
                            currentTab = currentTab,
                            onTabSelected = { appModel.selectTab(it) },
                            currentLibrarySubTab = librarySubTab,
                            onSelectLibrarySubTab = { appModel.selectLibrarySubTab(it) },
                            favoritesCount = favorites.size,
                            historyCount = history.size,
                            playlistsCount = userPlaylists.size,
                            downloadsCount = downloadedSongs.size,
                            localCount = localTracks.size,
                            userAccount = youtubeAccount,
                            onOpenLoginModal = { appModel.openYouTubeLoginModal() },
                            onOpenStatsModal = { appModel.openStatsModal() },
                            isListenTogetherInRoom = appModel.listenTogetherManager.isInRoom,
                            listenTogetherRoomCode = listenTogetherRoomState?.roomCode,
                            onOpenListenTogetherModal = { appModel.openListenTogetherModal() },
                            onOpenEchoFind = { appModel.openEchoFind() }
                        )

                        // Center / Right: Screen Switching with Crossfade
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(NothingColors.Background)
                        ) {
                            when (currentTab) {
                                NavigationTab.HOME -> HomeScreen(
                                    appModel = appModel,
                                    homeSongs = homeSongs,
                                    isLoading = isHomeLoading,
                                    history = history,
                                    currentSong = currentSong,
                                    playbackState = playbackState
                                )
                                NavigationTab.EXPLORE -> ExploreScreen(
                                    appModel = appModel,
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    statusMessage = statusMessage,
                                    currentSong = currentSong,
                                    playbackState = playbackState
                                )
                                NavigationTab.LIBRARY -> LibraryScreen(
                                    appModel = appModel,
                                    favorites = favorites,
                                    history = history,
                                    currentSong = currentSong,
                                    playbackState = playbackState
                                )
                                NavigationTab.SETTINGS -> SettingsScreen(
                                    appModel = appModel
                                )
                            }
                        }
                    }

                    val updateBanner by appModel.updateBanner.collectAsState()
                    updateBanner?.let { info ->
                        echo.music.desktop.ui.components.UpdateBanner(
                            info = info,
                            onDownload = {
                                appModel.openUpdatePage()
                                appModel.dismissUpdate(skip = false)
                            },
                            onLater = { appModel.dismissUpdate(skip = false) },
                            onSkip = { appModel.dismissUpdate(skip = true) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        )
                    }

                    // Persistent Bottom Player Bar
                    BottomPlayerBar(
                        currentSong = currentSong,
                        playbackState = playbackState,
                        isFavorite = isFavorite,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        onToggleFavorite = { currentSong?.let { appModel.toggleFavorite(it) } },
                        onTogglePlayPause = { appModel.togglePlayPause() },
                        onNext = { appModel.playNext() },
                        onPrevious = { appModel.playPrevious() },
                        onToggleShuffle = { appModel.toggleShuffle() },
                        onToggleRepeat = { appModel.toggleRepeatMode() },
                        onSeek = { appModel.seekTo(it) },
                        onVolumeChange = { appModel.setVolume(it) },
                        onToggleQueue = { appModel.toggleExpandedPlayer() },
                        onExpandPlayer = { appModel.toggleExpandedPlayer() },
                        onOpenEqualizer = { appModel.openEqualizerModal() },
                        onStartRadio = { currentSong?.let { appModel.startRadio(it) } },
                        onAddToPlaylist = { currentSong?.let { appModel.openAddToPlaylist(listOf(it)) } },
                        onArtistClick = { appModel.openArtist(it) },
                        onOpenMiniPlayer = {
                            saveWindowState()
                            openMiniPlayer()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // Full Expanded Player Modal
                    AnimatedVisibility(
                        visible = isExpandedPlayerOpen,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
                        exit = fadeOut(tween(150)) + slideOutVertically(tween(250)) { it / 2 }
                    ) {
                        ExpandedPlayerModal(
                            currentSong = currentSong,
                            playbackState = playbackState,
                            queue = queue,
                            currentIndex = currentIndex,
                            isFavorite = isFavorite,
                            isShuffle = isShuffle,
                            repeatMode = repeatMode,
                            lyrics = lyrics,
                            isLyricsLoading = isLyricsLoading,
                            lyricsSwitchingTo = lyricsSwitchingTo,
                            onSelectLyricsProvider = { appModel.switchLyricsProvider(it) },
                            lyricsExtras = lyricsExtras,
                            romanizeLyrics = prefs.lyricsRomanize,
                            onToggleRomanize = { appModel.updatePreferences { p -> p.copy(lyricsRomanize = !p.lyricsRomanize) } },
                            onTranslateLyrics = { appModel.translateCurrentLyrics() },
                            onHideLyricsTranslation = { appModel.hideLyricsTranslation() },
                            isAmbientGlowEnabled = isAmbientGlowEnabled,
                            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                            isDownloaded = currentSong != null && appModel.isDownloaded(currentSong!!.id),
                            downloadProgress = currentSong?.let { downloadProgress[it.id] },
                            onToggleFavorite = { currentSong?.let { appModel.toggleFavorite(it) } },
                            onTogglePlayPause = { appModel.togglePlayPause() },
                            onNext = { appModel.playNext() },
                            onPrevious = { appModel.playPrevious() },
                            onToggleShuffle = { appModel.toggleShuffle() },
                            onToggleRepeat = { appModel.toggleRepeatMode() },
                            onSeek = { appModel.seekTo(it) },
                            onVolumeChange = { appModel.setVolume(it) },
                            onSelectQueueItem = { appModel.playSong(it, queue) },
                            onRemoveFromQueue = { appModel.removeFromQueue(it) },
                            onClearQueue = { appModel.clearQueue() },
                            onClose = { appModel.closeExpandedPlayer() },
                            onOpenEqualizer = { appModel.openEqualizerModal() },
                            onStartRadio = { currentSong?.let { appModel.startRadio(it) } },
                            onAddToPlaylist = { currentSong?.let { appModel.openAddToPlaylist(listOf(it)) } },
                            onSaveQueueToPlaylist = { appModel.openAddToPlaylist(queue) },
                            onDislike = { currentSong?.let { appModel.dislikeSong(it) } },
                            canvasFile = canvasFile,
                            comments = comments,
                            onLoadComments = { appModel.loadComments() },
                            onLoadMoreComments = { appModel.loadMoreComments() },
                            onLoadReplies = { appModel.loadReplies(it) },
                            onHideReplies = { appModel.hideReplies(it) },
                            onQueueAddToPlaylist = { appModel.openAddToPlaylist(listOf(it)) },
                            onQueueDislike = { appModel.dislikeSong(it) },
                            onQueueBlockArtist = { s -> s.artists.firstOrNull()?.let { a -> appModel.blockArtist(a.id, a.name) } },
                            onToggleAmbientGlow = { appModel.toggleAmbientGlow() },
                            onStartSleepTimer = { appModel.startSleepTimer(it) },
                            onSetSleepTimerEndOfSong = { appModel.setSleepTimerEndOfSong() },
                            onCancelSleepTimer = { appModel.cancelSleepTimer() },
                            onDownloadSong = { currentSong?.let { appModel.downloadSong(it) } },
                            onArtistClick = { appModel.openArtist(it) }
                        )
                    }

                    // Playlist Detail Modal
                    if (selectedPlaylistDetail != null) {
                        PlaylistDetailModal(
                            playlist = selectedPlaylistDetail!!,
                            appModel = appModel,
                            currentSong = currentSong,
                            playbackState = playbackState,
                            onClose = { appModel.closePlaylistDetail() }
                        )
                    }

                    // YouTube Login Modal
                    if (isYouTubeLoginModalOpen) {
                        YouTubeLoginModal(
                            appModel = appModel,
                            isSyncing = isSyncingYouTube,
                            onClose = { appModel.closeYouTubeLoginModal() }
                        )
                    }

                    // Spotify Import Modal
                    if (isSpotifyImportModalOpen) {
                        SpotifyImportModal(
                            appModel = appModel,
                            isImporting = isImportingSpotify,
                            progressText = importProgressText,
                            onClose = { appModel.closeSpotifyImportModal() }
                        )
                    }

                    // Equalizer Modal
                    if (isEqualizerModalOpen) {
                        EqualizerModal(
                            appModel = appModel,
                            onClose = { appModel.closeEqualizerModal() }
                        )
                    }

                    // Echo Wrapped / Stats Modal
                    if (isStatsModalOpen) {
                        StatsModal(
                            appModel = appModel,
                            onClose = { appModel.closeStatsModal() }
                        )
                    }

                    // Artist Detail Modal
                    if (selectedArtistDetail != null || isArtistLoading) {
                        ArtistModal(
                            artistPage = selectedArtistDetail,
                            isLoading = isArtistLoading,
                            appModel = appModel,
                            currentSong = currentSong,
                            playbackState = playbackState,
                            onClose = { appModel.closeArtistModal() }
                        )
                    }

                    // Album Detail Modal
                    if (selectedAlbumDetail != null || isAlbumLoading) {
                        AlbumModal(
                            albumPage = selectedAlbumDetail,
                            isLoading = isAlbumLoading,
                            appModel = appModel,
                            currentSong = currentSong,
                            playbackState = playbackState,
                            onClose = { appModel.closeAlbumModal() }
                        )
                    }

                    // Listen Together (Escuchar Juntos) Modal
                    if (isListenTogetherModalOpen) {
                        ListenTogetherModal(
                            appModel = appModel,
                            onClose = { appModel.closeListenTogetherModal() }
                        )
                    }

                    // Add to Playlist / Create Playlist dialogs (drawn above every other modal)
                    val addToPlaylistSongs by appModel.addToPlaylistSongs.collectAsState()
                    val isCreatePlaylistOpen by appModel.isCreatePlaylistOpen.collectAsState()

                    if (isCreatePlaylistOpen) {
                        CreatePlaylistModal(
                            appModel = appModel,
                            onClose = { appModel.closeCreatePlaylist() }
                        )
                    }

                    val echoFindState by appModel.echoFind.collectAsState()
                    val recognitionHistory by appModel.recognitionHistory.collectAsState()
                    echoFindState?.let { state ->
                        EchoFindModal(
                            state = state,
                            onStart = { appModel.startEchoFind() },
                            onPlay = { appModel.playRecognized(it) },
                            onSearch = { appModel.searchRecognized(it) },
                            onClose = { appModel.closeEchoFind() },
                            history = recognitionHistory,
                            onPlayHistory = { appModel.playRecognizedSong(it) },
                            onSearchHistory = { appModel.searchRecognizedSong(it) },
                            onRemoveHistory = { appModel.removeRecognition(it) }
                        )
                    }

                    addToPlaylistSongs?.let { songs ->
                        AddToPlaylistModal(
                            songs = songs,
                            appModel = appModel,
                            onClose = { appModel.closeAddToPlaylist() }
                        )
                    }
                }
            }
        }
    }
}
