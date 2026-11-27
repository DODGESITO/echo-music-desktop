package echo.music.desktop.state

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.YouTubeLocale
import com.music.innertube.models.filterBlockedArtists
import com.music.innertube.models.filterDislikedSongs
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import echo.music.desktop.account.YouTubeSyncService
import echo.music.desktop.discord.DiscordRpcClient
import echo.music.desktop.explore.DiscoverContent
import echo.music.desktop.explore.ExploreService
import echo.music.desktop.explore.MoodPage
import echo.music.desktop.importer.SpotifyImporter
import echo.music.desktop.lyrics.LyricsProviderId
import echo.music.desktop.lyrics.LyricsRequest
import echo.music.desktop.local.LocalMusicLibrary
import echo.music.desktop.local.LocalTrack
import echo.music.desktop.lyrics.LyricsService
import echo.music.desktop.lyrics.LyricsTransformService
import echo.music.desktop.lyrics.TranslationProvider
import echo.music.desktop.lyrics.TranslationSettings
import echo.music.desktop.lyrics.SongLyrics
import echo.music.desktop.player.AudioPlayerEngine
import echo.music.desktop.player.DesktopDownloadManager
import echo.music.desktop.player.DesktopStreamResolver
import echo.music.desktop.player.JavaFxPlayerEngine
import echo.music.desktop.player.PlaybackState
import echo.music.desktop.player.RepeatMode
import echo.music.desktop.state.AccountStore
import echo.music.desktop.state.PlaylistStore
import echo.music.desktop.state.UserAccountData
import echo.music.desktop.state.UserPlaylist
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class DesktopAppModel(
    val playerEngine: AudioPlayerEngine = JavaFxPlayerEngine()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val playbackState: StateFlow<PlaybackState> = playerEngine.state

    private val _currentTab = MutableStateFlow(NavigationTab.HOME)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _librarySubTab = MutableStateFlow(LibrarySubTab.PLAYLISTS)
    val librarySubTab: StateFlow<LibrarySubTab> = _librarySubTab.asStateFlow()

    fun selectLibrarySubTab(subTab: LibrarySubTab) {
        _librarySubTab.value = subTab
        _currentTab.value = NavigationTab.LIBRARY
        if (subTab == LibrarySubTab.ALBUMS || subTab == LibrarySubTab.ARTISTS) loadAccountLibrary()
    }

    // --- Biblioteca de la cuenta de YouTube Music (álbumes y artistas guardados) ---
    private val _libraryAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val libraryAlbums: StateFlow<List<AlbumItem>> = _libraryAlbums.asStateFlow()

    private val _libraryArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val libraryArtists: StateFlow<List<ArtistItem>> = _libraryArtists.asStateFlow()

    private val _isAccountLibraryLoading = MutableStateFlow(false)
    val isAccountLibraryLoading: StateFlow<Boolean> = _isAccountLibraryLoading.asStateFlow()
    private var accountLibraryLoaded = false

    /** Loads the albums and artists saved in the user's YouTube Music library (needs login). */
    fun loadAccountLibrary(force: Boolean = false) {
        if (_youtubeAccount.value == null || _isAccountLibraryLoading.value) return
        if (accountLibraryLoaded && !force) return
        scope.launch {
            _isAccountLibraryLoading.value = true
            try {
                val (albums, artists) = withContext(Dispatchers.IO) {
                    val albums = YouTube.library("FEmusic_liked_albums").completed().getOrNull()
                        ?.items?.filterIsInstance<AlbumItem>()
                    // Followed artists + artists of the songs in the library (like YouTube Music's Artists tab).
                    val followed = YouTube.library("FEmusic_library_corpus_artists").completed().getOrNull()
                        ?.items?.filterIsInstance<ArtistItem>()
                    val fromSongs = YouTube.library("FEmusic_library_corpus_track_artists").completed().getOrNull()
                        ?.items?.filterIsInstance<ArtistItem>()
                    val artists = if (followed == null && fromSongs == null) null else followed.orEmpty() + fromSongs.orEmpty()
                    albums to artists
                }
                if (albums == null && artists == null) {
                    _statusMessage.value = "No se pudo cargar tu biblioteca de YouTube Music"
                } else {
                    _libraryAlbums.value = albums.orEmpty().distinctBy { it.id }
                    _libraryArtists.value = artists.orEmpty().distinctBy { it.id }
                    accountLibraryLoaded = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = "No se pudo cargar tu biblioteca: ${e.message}"
            } finally {
                _isAccountLibraryLoading.value = false
            }
        }
    }

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val initialRepeatMode = when (PreferencesStore.current.repeatMode) {
        "ONE" -> RepeatMode.ONE
        "ALL" -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
    private val _repeatMode = MutableStateFlow(initialRepeatMode)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(PreferencesStore.current.isShuffle)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private var lastNonZeroVolume: Float = if (PreferencesStore.current.volume > 0.05f) PreferencesStore.current.volume else 0.8f
    var isInputFocused: Boolean = false

    private val _isExpandedPlayerOpen = MutableStateFlow(false)
    val isExpandedPlayerOpen: StateFlow<Boolean> = _isExpandedPlayerOpen.asStateFlow()

    // --- Preferencias de reproducción y contenido (grupo "como en el celular") ---
    private val _prefs = MutableStateFlow(PreferencesStore.current)
    val prefs: StateFlow<DesktopPreferences> = _prefs.asStateFlow()

    /** Saves a preference change and applies it right away (speed, volume, language...). */
    fun updatePreferences(transform: (DesktopPreferences) -> DesktopPreferences) {
        val before = _prefs.value
        PreferencesStore.update(transform)
        val after = PreferencesStore.current
        _prefs.value = after
        if (after.playbackSpeed != before.playbackSpeed) playerEngine.setPlaybackRate(after.playbackSpeed)
        if (after.showCanvas != before.showCanvas) {
            if (after.showCanvas) _currentSong.value?.let { loadCanvas(it) } else _canvasFile.value = null
        }
        if (after.themeAccent != before.themeAccent || after.themeBackground != before.themeBackground) {
            echo.music.desktop.ui.theme.NothingColors.applyTheme(after.themeAccent, after.themeBackground)
        }
        if (after.lyricsRomanize != before.lyricsRomanize) {
            if (after.lyricsRomanize) onLyricsLoaded(_currentLyrics.value)
            else _lyricsExtras.update { it.copy(romanized = null) }
        }
        if (after.normalizeAudio != before.normalizeAudio || after.loudnessPreset != before.loudnessPreset) {
            _currentSong.value?.let { applyVolumeGain(it.id) }
        }
        val localeChanged = after.contentLanguage != before.contentLanguage || after.contentCountry != before.contentCountry
        val filtersChanged = after.hideExplicit != before.hideExplicit ||
            after.hideVideoSongs != before.hideVideoSongs || after.hideShorts != before.hideShorts
        if (localeChanged) applyContentLocale()
        if (localeChanged || filtersChanged) {
            loadHomeContent()
            if (_discover.value != null) loadDiscover(force = true)
            if (_searchQuery.value.isNotBlank()) search(_searchQuery.value)
        }
    }

    private fun applyContentLocale() {
        val p = _prefs.value
        val system = java.util.Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = p.contentCountry.ifBlank { system.country.ifBlank { "US" } },
            hl = p.contentLanguage.ifBlank { system.toLanguageTag() }
        )
    }

    /** Hides explicit songs, music videos and Shorts according to the user's content settings. */
    private fun <T : YTItem> List<T>.applyContentFilters(keepVideos: Boolean = false): List<T> {
        val p = _prefs.value
        return filterExplicit(p.hideExplicit)
            .filterVideoSongs(p.hideVideoSongs && !keepVideos)
            .filterYoutubeShorts(p.hideShorts)
            .filterBlockedArtists()
            .filterDislikedSongs()
    }

    // --- Artistas bloqueados y "No me gusta" ---
    private val _contentBlocks = MutableStateFlow(ContentBlockStore.current)
    val contentBlocks: StateFlow<ContentBlocks> = _contentBlocks.asStateFlow()

    /** Re-applies filters to what is already on screen (no network). */
    private fun refilterVisibleContent() {
        _homeSections.value = _homeSections.value
            .map { it.copy(items = it.items.applyContentFilters()) }
            .filter { it.items.isNotEmpty() }
        _homeSongs.value = _homeSongs.value.applyContentFilters()
        _searchResults.value = _searchResults.value.applyContentFilters(keepVideos = true)
        _searchItems.value = _searchItems.value.applyContentFilters(keepVideos = true)
        _discover.value = _discover.value?.let { d ->
            d.copy(
                newReleases = d.newReleases?.applyContentFilters(),
                charts = d.charts?.map { it.copy(items = it.items.applyContentFilters()) }
            )
        }
    }

    fun blockArtist(id: String?, name: String) {
        if (name.isBlank()) return
        ContentBlockStore.update { blocks ->
            if (blocks.artists.any { (id != null && it.id == id) || it.name == name }) blocks
            else blocks.copy(artists = blocks.artists + BlockedArtist(id, name))
        }
        _contentBlocks.value = ContentBlockStore.current
        refilterVisibleContent()
        _statusMessage.value = "$name bloqueado: no aparecerá en Inicio, búsquedas ni radios"
    }

    fun unblockArtist(artist: BlockedArtist) {
        ContentBlockStore.update { it.copy(artists = it.artists - artist) }
        _contentBlocks.value = ContentBlockStore.current
        _statusMessage.value = "${artist.name} desbloqueado"
    }

    /** "No me gusta": hides the song from recommendations, removes it from the queue and skips it if playing. */
    fun dislikeSong(song: SongItem) {
        ContentBlockStore.update { blocks ->
            if (blocks.songs.any { it.id == song.id }) blocks
            else blocks.copy(
                songs = blocks.songs + DislikedSong(song.id, song.title, song.artists.joinToString(", ") { it.name })
            )
        }
        _contentBlocks.value = ContentBlockStore.current
        refilterVisibleContent()
        if (_currentSong.value?.id == song.id) {
            playNext()
        }
        val q = _queue.value
        val idx = q.indexOfFirst { it.id == song.id }
        if (idx >= 0 && idx != _currentIndex.value) removeFromQueue(idx)
        _statusMessage.value = "No te mostraremos más \"${song.title}\""
    }

    fun undislikeSong(song: DislikedSong) {
        ContentBlockStore.update { it.copy(songs = it.songs - song) }
        _contentBlocks.value = ContentBlockStore.current
        _statusMessage.value = "\"${song.title}\" vuelve a aparecer en recomendaciones"
    }

    // --- Música local (Biblioteca → En este PC) ---
    private val _localTracks = MutableStateFlow(LocalMusicLibrary.tracks)
    val localTracks: StateFlow<List<LocalTrack>> = _localTracks.asStateFlow()

    private val _localScanCount = MutableStateFlow<Int?>(null)
    /** Files found so far while scanning (null when not scanning). */
    val localScanCount: StateFlow<Int?> = _localScanCount.asStateFlow()
    private var localScanJob: Job? = null

    fun rescanLocalMusic() {
        val folders = _prefs.value.localMusicFolders
        localScanJob?.cancel()
        localScanJob = scope.launch {
            _localScanCount.value = 0
            try {
                val found = LocalMusicLibrary.scan(folders, excluded = listOf(DownloadStore.downloadsDir)) { n ->
                    _localScanCount.value = n
                }
                _localTracks.value = found
                if (folders.isNotEmpty()) _statusMessage.value = "Música local: ${found.size} canciones"
            } finally {
                _localScanCount.value = null
            }
        }
    }

    fun addLocalMusicFolder(path: String) {
        if (path.isBlank() || path in _prefs.value.localMusicFolders) return
        updatePreferences { it.copy(localMusicFolders = it.localMusicFolders + path) }
        rescanLocalMusic()
    }

    fun removeLocalMusicFolder(path: String) {
        updatePreferences { it.copy(localMusicFolders = it.localMusicFolders - path) }
        rescanLocalMusic()
    }

    // --- Registro de escuchas: Last.fm y ListenBrainz ---
    private fun reveal(value: String) =
        runCatching { echo.music.desktop.system.SecretProtector.unprotect(value) }.getOrDefault("")
    private fun protect(value: String) = echo.music.desktop.system.SecretProtector.protect(value.trim())

    private var scrobbleCfgCache: Pair<DesktopPreferences, echo.music.desktop.scrobble.ScrobbleConfig>? = null

    /** Decrypted scrobbling credentials, cached until preferences change. */
    private fun scrobbleConfig(): echo.music.desktop.scrobble.ScrobbleConfig {
        val p = _prefs.value
        scrobbleCfgCache?.takeIf { it.first === p }?.let { return it.second }
        val cfg = echo.music.desktop.scrobble.ScrobbleConfig(
            listenBrainzToken = reveal(p.listenBrainzToken),
            lastFmApiKey = p.lastFmApiKey,
            lastFmSecret = reveal(p.lastFmSecret),
            lastFmSessionKey = reveal(p.lastFmSessionKey)
        )
        scrobbleCfgCache = p to cfg
        return cfg
    }

    fun setListenBrainzToken(token: String) {
        updatePreferences { it.copy(listenBrainzToken = protect(token)) }
        _statusMessage.value = if (token.isBlank()) "ListenBrainz desconectado" else "ListenBrainz conectado"
    }

    fun setLastFmCredentials(apiKey: String, secret: String) {
        updatePreferences {
            it.copy(
                lastFmApiKey = apiKey.trim(),
                lastFmSecret = if (secret.isBlank()) it.lastFmSecret else protect(secret)
            )
        }
    }

    private val _lastFmPendingToken = MutableStateFlow<String?>(null)
    /** Set while waiting for the user to approve Echo Music on last.fm. */
    val lastFmPendingToken: StateFlow<String?> = _lastFmPendingToken.asStateFlow()

    /** Opens last.fm in the browser so the user approves Echo Music (step 1). */
    fun startLastFmLogin() {
        val p = _prefs.value
        val secret = reveal(p.lastFmSecret)
        if (p.lastFmApiKey.isBlank() || secret.isBlank()) {
            _statusMessage.value = "Primero guarda tu API key y secret de Last.fm"
            return
        }
        scope.launch {
            echo.music.desktop.scrobble.ScrobbleService.lastFmStartAuth(p.lastFmApiKey, secret)
                .onSuccess { (token, url) ->
                    _lastFmPendingToken.value = token
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
                    _statusMessage.value = "Autoriza Echo Music en la página de Last.fm y luego pulsa \"Ya lo autoricé\""
                }
                .onFailure { _statusMessage.value = "Last.fm rechazó la API key o el secret: ${it.message}" }
        }
    }

    /** Exchanges the approved token for a session (step 2). */
    fun finishLastFmLogin() {
        val token = _lastFmPendingToken.value ?: return
        val p = _prefs.value
        scope.launch {
            echo.music.desktop.scrobble.ScrobbleService.lastFmFinishAuth(p.lastFmApiKey, reveal(p.lastFmSecret), token)
                .onSuccess { (name, sessionKey) ->
                    updatePreferences { it.copy(lastFmSessionKey = protect(sessionKey), lastFmUsername = name) }
                    _lastFmPendingToken.value = null
                    _statusMessage.value = "Last.fm conectado como $name"
                }
                .onFailure { _statusMessage.value = "Todavía no aparece la autorización en Last.fm. Apruébala y vuelve a intentarlo." }
        }
    }

    fun disconnectLastFm() {
        updatePreferences { it.copy(lastFmSessionKey = "", lastFmUsername = "") }
        _lastFmPendingToken.value = null
        _statusMessage.value = "Last.fm desconectado"
    }

    /** Sends "now playing" and, once enough of the song was heard, the listen itself. */
    private fun startScrobbleTracking() = scope.launch {
        var videoId = ""
        var startedAt = 0L
        var listenedMs = 0L
        var lastPos = 0L
        var scrobbled = false
        var announced = false
        playbackState.collect { st ->
            if (st.videoId.isBlank()) return@collect
            // New song, or the same song starting over (repeat one).
            val restarted = st.videoId == videoId && st.currentPositionMs < 1_500 && lastPos > st.durationMs - 5_000 && st.durationMs > 0
            if (st.videoId != videoId || restarted) {
                videoId = st.videoId; startedAt = System.currentTimeMillis(); listenedMs = 0; scrobbled = false; announced = false
                lastPos = st.currentPositionMs
            }
            val delta = st.currentPositionMs - lastPos
            lastPos = st.currentPositionMs
            val cfg = scrobbleConfig()
            if (!cfg.lastFmOn && !cfg.listenBrainzOn) return@collect
            val song = _currentSong.value?.takeIf { it.id == st.videoId } ?: return@collect
            val durationSec = (song.duration ?: (st.durationMs / 1000L).toInt())
            val track = echo.music.desktop.scrobble.ScrobbleTrack(
                title = song.title,
                artist = song.artists.firstOrNull()?.name.orEmpty(),
                allArtists = song.artists.joinToString(", ") { it.name },
                album = song.album?.name?.takeIf { it.isNotBlank() },
                durationSec = durationSec
            )
            if (track.artist.isBlank() || track.artist == "Artista desconocido") return@collect
            if (song.isPodcastEpisode || song.artists.any { com.music.innertube.pages.PodcastPage.isShowBrowseId(it.id) }) return@collect
            if (st.isPlaying && !announced) {
                announced = true
                launch { runCatching { echo.music.desktop.scrobble.ScrobbleService.nowPlaying(track, cfg) } }
            }
            // Only real listening counts (seeking forward does not).
            if (st.isPlaying && delta in 1..2_000) listenedMs += delta
            val threshold = echo.music.desktop.scrobble.ScrobbleService.scrobbleThresholdMs(durationSec)
            if (!scrobbled && threshold != null && listenedMs >= threshold) {
                scrobbled = true
                val started = startedAt
                launch { runCatching { echo.music.desktop.scrobble.ScrobbleService.scrobble(track, started, cfg) } }
            }
        }
    }

    // --- Comentarios de YouTube (reproductor expandido) ---
    private val _comments = MutableStateFlow(CommentsState())
    val comments: StateFlow<CommentsState> = _comments.asStateFlow()

    private fun toComment(t: com.music.innertube.models.comment.CommentThreadRenderer): SongComment? {
        val c = t.comment?.commentRenderer ?: return null
        // Same lookup as the phone app's comment sheet for the "view replies" continuation.
        val replies = t.replies?.commentRepliesRenderer
        val token = replies?.contents?.firstOrNull()?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
            ?: replies?.viewReplies?.buttonRenderer?.command?.continuationCommand?.token
            ?: replies?.viewReplies?.buttonRenderer?.navigationEndpoint?.continuationCommand?.token
        return toSongComment(c, token)
    }

    private fun toSongComment(c: com.music.innertube.models.comment.CommentRenderer, replyToken: String? = null): SongComment? {
        val text = c.contentText?.runs?.joinToString("") { it.text }.orEmpty()
        if (text.isBlank()) return null
        return SongComment(
            id = c.commentId ?: "${c.authorText?.runs?.firstOrNull()?.text}|${text.hashCode()}",
            author = c.authorText?.runs?.firstOrNull()?.text.orEmpty(),
            avatar = c.authorThumbnail?.thumbnails?.lastOrNull()?.url,
            time = c.publishedTimeText?.runs?.firstOrNull()?.text.orEmpty(),
            text = text,
            likes = c.voteCount?.runs?.firstOrNull()?.text.orEmpty().takeIf { it != "0" }.orEmpty(),
            replyCount = c.replyCount ?: 0,
            replyToken = replyToken
        )
    }

    /** Shows (or loads more of) the replies of a comment. */
    fun loadReplies(comment: SongComment) {
        val state = _comments.value
        val current = state.replies[comment.id]
        if (current?.loading == true) return
        val token = if (current == null) comment.replyToken else current.next
        if (token == null) return
        val videoId = state.videoId
        _comments.value = state.copy(replies = state.replies + (comment.id to (current ?: ReplyThread()).copy(loading = true)))
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.commentReplies(token) }
            val now = _comments.value
            if (now.videoId != videoId) return@launch
            val before = now.replies[comment.id] ?: ReplyThread()
            val (renderers, next) = result.getOrNull() ?: (emptyList<com.music.innertube.models.comment.CommentRenderer>() to null)
            _comments.value = now.copy(
                replies = now.replies + (comment.id to before.copy(
                    items = before.items + renderers.mapNotNull { toSongComment(it) },
                    next = next,
                    loading = false,
                    failed = result.isFailure
                ))
            )
        }
    }

    fun hideReplies(comment: SongComment) {
        _comments.value = _comments.value.let { it.copy(replies = it.replies - comment.id) }
    }

    /** Loads the first page of comments of the song playing (once per song). */
    fun loadComments() {
        val song = _currentSong.value ?: return
        if (_comments.value.videoId == song.id) return
        if (LocalMusicLibrary.isLocal(song.id)) {
            _comments.value = CommentsState(videoId = song.id, message = "Las canciones de este PC no tienen comentarios.")
            return
        }
        _comments.value = CommentsState(videoId = song.id, loading = true)
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.comments(song.id) }
            if (_comments.value.videoId != song.id) return@launch
            _comments.value = result.fold(
                onSuccess = { (threads, next) ->
                    CommentsState(videoId = song.id, comments = threads.mapNotNull(::toComment), continuation = next)
                },
                onFailure = { CommentsState(videoId = song.id, message = "No se pudieron cargar los comentarios.") }
            )
        }
    }

    fun loadMoreComments() {
        val state = _comments.value
        val next = state.continuation ?: return
        if (state.loading) return
        _comments.value = state.copy(loading = true)
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.commentContinuation(next) }
            if (_comments.value.videoId != state.videoId) return@launch
            val (threads, newNext) = result.getOrNull() ?: (emptyList<com.music.innertube.models.comment.CommentThreadRenderer>() to null)
            _comments.value = state.copy(
                comments = state.comments + threads.mapNotNull(::toComment),
                continuation = newNext,
                loading = false
            )
        }
    }

    // --- Echo Find: reconocer la canción que suena cerca ---
    private val _echoFind = MutableStateFlow<EchoFindState?>(null)
    /** null = panel closed. */
    val echoFind: StateFlow<EchoFindState?> = _echoFind.asStateFlow()
    private var echoFindJob: Job? = null

    fun openEchoFind() {
        _echoFind.value = EchoFindState.Ready
    }

    fun closeEchoFind() {
        echoFindJob?.cancel()
        _echoFind.value = null
    }

    fun startEchoFind() {
        echoFindJob?.cancel()
        echoFindJob = scope.launch {
            _echoFind.value = EchoFindState.Listening(0f)
            val pcm = try {
                echo.music.desktop.recognition.EchoFindService.record { progress ->
                    _echoFind.value = EchoFindState.Listening(progress)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _echoFind.value = EchoFindState.Failed(
                    e.message ?: "No se pudo usar el micrófono."
                )
                return@launch
            }
            if (echo.music.desktop.recognition.EchoFindService.rms(pcm) < 30.0) {
                _echoFind.value = EchoFindState.Failed(
                    "No se escuchó sonido. Revisa que el micrófono esté conectado y que Windows permita " +
                        "usarlo a las apps de escritorio (Configuración → Privacidad y seguridad → Micrófono)."
                )
                return@launch
            }
            _echoFind.value = EchoFindState.Processing
            val result = echo.music.desktop.recognition.EchoFindService.recognize(pcm)
            _echoFind.value = result.fold(
                onSuccess = {
                    if (!_prefs.value.pauseHistory) {
                        _recognitionHistory.value = echo.music.desktop.recognition.RecognitionHistoryStore.add(toRecognized(it))
                    }
                    EchoFindState.Found(it)
                },
                onFailure = { e ->
                    if (e.message?.contains("match", ignoreCase = true) == true) EchoFindState.NoMatch
                    else EchoFindState.Failed("No se pudo identificar: ${e.message}")
                }
            )
        }
    }

    private val _recognitionHistory = MutableStateFlow(echo.music.desktop.recognition.RecognitionHistoryStore.load())
    /** Songs identified with Echo Find, newest first. */
    val recognitionHistory: StateFlow<List<echo.music.desktop.recognition.RecognizedSong>> = _recognitionHistory.asStateFlow()

    private fun toRecognized(r: com.music.shazamkit.models.RecognitionResult) = echo.music.desktop.recognition.RecognizedSong(
        title = r.title,
        artist = r.artist,
        album = r.album,
        coverUrl = r.coverArtHqUrl ?: r.coverArtUrl,
        youtubeVideoId = r.youtubeVideoId
    )

    fun playRecognized(result: com.music.shazamkit.models.RecognitionResult) = playRecognizedSong(toRecognized(result))

    fun searchRecognized(result: com.music.shazamkit.models.RecognitionResult) = searchRecognizedSong(toRecognized(result))

    /** Plays a recognized song: by its YouTube id when Shazam knows it, otherwise the best search match. */
    fun playRecognizedSong(song: echo.music.desktop.recognition.RecognizedSong) {
        closeEchoFind()
        scope.launch {
            val videoId = song.youtubeVideoId
            if (!videoId.isNullOrBlank()) {
                playSong(
                    SongItem(
                        id = videoId,
                        title = song.title,
                        artists = listOf(com.music.innertube.models.Artist(name = song.artist, id = null)),
                        thumbnail = song.coverUrl ?: ""
                    )
                )
                return@launch
            }
            val match = withContext(Dispatchers.IO) {
                YouTube.search("${song.title} ${song.artist}", YouTube.SearchFilter.FILTER_SONG)
            }.getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            if (match != null) playSong(match) else _statusMessage.value = "No se encontró \"${song.title}\" en YouTube Music"
        }
    }

    fun searchRecognizedSong(song: echo.music.desktop.recognition.RecognizedSong) {
        closeEchoFind()
        selectTab(NavigationTab.EXPLORE)
        search("${song.title} ${song.artist}")
    }

    fun removeRecognition(song: echo.music.desktop.recognition.RecognizedSong) {
        _recognitionHistory.value = echo.music.desktop.recognition.RecognitionHistoryStore.remove(song)
    }

    fun clearRecognitionHistory() {
        echo.music.desktop.recognition.RecognitionHistoryStore.clear()
        _recognitionHistory.value = emptyList()
        _statusMessage.value = "Historial de Echo Find borrado"
    }

    // --- Canvas (portada animada) ---
    private val _canvasFile = MutableStateFlow<java.io.File?>(null)
    /** Canvas video of the current song, when it has one (shown in the expanded player). */
    val canvasFile: StateFlow<java.io.File?> = _canvasFile.asStateFlow()
    private var canvasJob: Job? = null

    private fun loadCanvas(song: SongItem) {
        canvasJob?.cancel()
        _canvasFile.value = null
        val isPodcast = song.isPodcastEpisode || song.artists.any { com.music.innertube.pages.PodcastPage.isShowBrowseId(it.id) }
        if (!_prefs.value.showCanvas || LocalMusicLibrary.isLocal(song.id) || isPodcast) return
        val artist = song.artists.joinToString(", ") { it.name }
        if (artist.isBlank()) return
        canvasJob = scope.launch {
            val file = runCatching {
                echo.music.desktop.canvas.CanvasService.canvasFor(song.id, song.title, artist, song.album?.name)
            }.getOrNull()
            if (_currentSong.value?.id == song.id) _canvasFile.value = file
        }
    }

    // --- Copia de seguridad ---
    fun exportBackup(target: java.io.File) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { BackupService.export(target) }
            _statusMessage.value = result.fold(
                onSuccess = { "Copia guardada en ${target.name} (${it.files.size} archivos)" },
                onFailure = { "No se pudo crear la copia: ${it.message}" }
            )
        }
    }

    fun importBackup(source: java.io.File) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { BackupService.import(source) }
            result.onSuccess {
                reloadAllStores()
                _statusMessage.value = "Copia restaurada: ${it.files.joinToString { f -> f.removeSuffix(".json") }}"
            }.onFailure {
                _statusMessage.value = "No se pudo restaurar la copia: ${it.message}"
            }
        }
    }

    /** Re-reads every restored file and refreshes what the app shows, without restarting. */
    private fun reloadAllStores() {
        PreferencesStore.reload()
        FavoritesStore.reload()
        StatsStore.reload()
        ContentBlockStore.reload()
        _prefs.value = PreferencesStore.current
        _favorites.value = FavoritesStore.getFavorites()
        _history.value = FavoritesStore.getHistory()
        _userPlaylists.value = PlaylistStore.getPlaylists()
        _contentBlocks.value = ContentBlockStore.current
        _equalizerSettings.value = EqualizerStore.getSettings()
        _recognitionHistory.value = echo.music.desktop.recognition.RecognitionHistoryStore.load()
        playerEngine.setEqualizerEnabled(_equalizerSettings.value.enabled)
        playerEngine.setEqualizerBands(_equalizerSettings.value.bandGains)
        playerEngine.setPlaybackRate(_prefs.value.playbackSpeed)
        _currentSong.value?.let { applyVolumeGain(it.id) }
        applyContentLocale()
        echo.music.desktop.ui.theme.NothingColors.applyTheme(_prefs.value.themeAccent, _prefs.value.themeBackground)
        loadHomeContent()
        if (_discover.value != null) loadDiscover(force = true)
    }

    /**
     * Loudness normalization: lowers loud songs and raises quiet ones (as far as the volume allows),
     * plus the preset offset. Same formula as the phone app (gain = -loudnessDb + preset).
     */
    private fun applyVolumeGain(videoId: String) {
        val p = _prefs.value
        playerEngine.setVolumeGain(
            echo.music.desktop.player.Loudness.gainFor(
                loudnessDb = DesktopStreamResolver.loudnessDbFor(videoId),
                normalize = p.normalizeAudio,
                preset = echo.music.desktop.player.LoudnessPreset.from(p.loudnessPreset)
            )
        )
    }

    // --- SponsorBlock ---
    @Volatile private var skipSegments: List<echo.music.desktop.player.SkipSegment> = emptyList()
    @Volatile private var skipSegmentsVideoId: String = ""
    private val skippedSegments = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun loadSkipSegments(song: SongItem) {
        skipSegments = emptyList()
        skipSegmentsVideoId = song.id
        skippedSegments.clear()
        if (!_prefs.value.sponsorBlock || LocalMusicLibrary.isLocal(song.id)) return
        scope.launch {
            val segments = echo.music.desktop.player.SponsorBlockService.segmentsFor(song.id)
            if (skipSegmentsVideoId == song.id) skipSegments = segments
        }
    }

    // Consecutive songs that failed to play (stops auto-skip from looping over a broken queue).
    private var consecutiveFailures = 0

    /** Called when a song cannot be loaded or played. */
    private fun onPlaybackFailed(song: SongItem, reason: String) {
        consecutiveFailures++
        if (!_prefs.value.autoSkipOnError || consecutiveFailures > 5) {
            _statusMessage.value = "No se pudo reproducir ${song.title}: $reason"
            return
        }
        val next = nextIndex(wrap = _repeatMode.value == RepeatMode.ALL)
        if (next == null) {
            _statusMessage.value = "No se pudo reproducir ${song.title}"
            return
        }
        _statusMessage.value = "No se pudo reproducir ${song.title}, pasando a la siguiente..."
        val requestId = playRequestId
        scope.launch {
            delay(1_500)
            if (requestId == playRequestId && _currentSong.value?.id == song.id) advanceTo(next)
        }
    }

    private val _globalMediaKeysEnabled = MutableStateFlow(PreferencesStore.current.globalMediaKeys)
    val globalMediaKeysEnabled: StateFlow<Boolean> = _globalMediaKeysEnabled.asStateFlow()

    fun setGlobalMediaKeysEnabled(enabled: Boolean) {
        _globalMediaKeysEnabled.value = enabled
        PreferencesStore.update { it.copy(globalMediaKeys = enabled) }
    }

    /** Handles a hardware media key (from the window or the system-wide listener). */
    fun onMediaKey(key: echo.music.desktop.system.MediaKey) {
        when (key) {
            echo.music.desktop.system.MediaKey.PLAY_PAUSE -> togglePlayPause()
            echo.music.desktop.system.MediaKey.NEXT -> playNext()
            echo.music.desktop.system.MediaKey.PREVIOUS -> playPrevious()
            echo.music.desktop.system.MediaKey.STOP -> if (playbackState.value.isPlaying) togglePlayPause()
        }
    }

    private val _isQueueOpen = MutableStateFlow(false)
    val isQueueOpen: StateFlow<Boolean> = _isQueueOpen.asStateFlow()

    private val _currentLyrics = MutableStateFlow<SongLyrics?>(null)
    val currentLyrics: StateFlow<SongLyrics?> = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    /** Romanized and translated lines for [currentLyrics] (same order as its lines). */
    private val _lyricsExtras = MutableStateFlow(LyricsExtras())
    val lyricsExtras: StateFlow<LyricsExtras> = _lyricsExtras.asStateFlow()
    private var lyricsExtrasJob: Job? = null

    /** Provider currently being tried when the user picks another lyrics source (null = idle). */
    private val _lyricsSwitchingTo = MutableStateFlow<LyricsProviderId?>(null)
    val lyricsSwitchingTo: StateFlow<LyricsProviderId?> = _lyricsSwitchingTo.asStateFlow()

    private val _youtubeAccount = MutableStateFlow<UserAccountData?>(AccountStore.getYouTubeAccount())
    val youtubeAccount: StateFlow<UserAccountData?> = _youtubeAccount.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(PlaylistStore.getPlaylists())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    private val _isSyncingYouTube = MutableStateFlow(false)
    val isSyncingYouTube: StateFlow<Boolean> = _isSyncingYouTube.asStateFlow()

    private val _isImportingSpotify = MutableStateFlow(false)
    val isImportingSpotify: StateFlow<Boolean> = _isImportingSpotify.asStateFlow()

    private val _isYouTubeLoginModalOpen = MutableStateFlow(false)
    val isYouTubeLoginModalOpen: StateFlow<Boolean> = _isYouTubeLoginModalOpen.asStateFlow()

    private val _isSpotifyImportModalOpen = MutableStateFlow(false)
    val isSpotifyImportModalOpen: StateFlow<Boolean> = _isSpotifyImportModalOpen.asStateFlow()

    private val _importProgressText = MutableStateFlow<String?>(null)
    val importProgressText: StateFlow<String?> = _importProgressText.asStateFlow()

    private val _selectedPlaylistDetail = MutableStateFlow<UserPlaylist?>(null)
    val selectedPlaylistDetail: StateFlow<UserPlaylist?> = _selectedPlaylistDetail.asStateFlow()

    // Equalizer
    private val _equalizerSettings = MutableStateFlow(EqualizerStore.getSettings())
    val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    private val _isEqualizerModalOpen = MutableStateFlow(false)
    val isEqualizerModalOpen: StateFlow<Boolean> = _isEqualizerModalOpen.asStateFlow()

    // Listen Together (Escuchar Juntos)
    val listenTogetherManager = echo.music.desktop.listentogether.DesktopListenTogetherManager()

    // --- Echo Connect (Echo Music para Android en la misma Wi-Fi) ---
    val echoConnect = echo.music.desktop.connect.EchoConnectManager(object : echo.music.desktop.connect.EchoConnectApp {
        override fun playback(): echo.music.desktop.connect.EchoConnectApp.Playback {
            val state = playbackState.value
            val song = _currentSong.value
            val loaded = song != null && state.videoId == song.id
            return echo.music.desktop.connect.EchoConnectApp.Playback(
                queue = _queue.value,
                index = _currentIndex.value,
                current = song,
                playing = loaded && state.isPlaying,
                positionMs = currentPositionForSession(),
                durationMs = if (loaded) state.durationMs else 0L,
                volume = state.volume,
                shuffle = _isShuffle.value,
                repeat = when (_repeatMode.value) {
                    RepeatMode.ONE -> "one"
                    RepeatMode.ALL -> "all"
                    RepeatMode.OFF -> "off"
                }
            )
        }

        override fun play() {
            if (!playbackState.value.isPlaying) togglePlayPause()
        }

        override fun pause() {
            if (playbackState.value.isPlaying) togglePlayPause()
        }

        override fun togglePlay() = togglePlayPause()
        override fun next() = playNext()
        override fun previous() = playPrevious(allowRestart = false)
        override fun seek(positionMs: Long) = seekToPositionMs(positionMs)
        override fun setVolume(volume: Float) = this@DesktopAppModel.setVolume(volume)

        override fun setShuffle(enabled: Boolean) {
            if (_isShuffle.value != enabled) toggleShuffle()
        }

        override fun setRepeat(mode: String) {
            _repeatMode.value = when (mode) {
                "one" -> RepeatMode.ONE
                "all" -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            PreferencesStore.update { it.copy(repeatMode = _repeatMode.value.name) }
        }

        override fun playIndex(index: Int) {
            val q = _queue.value
            q.getOrNull(index)?.let { playSong(it, q) }
        }

        override fun addToQueue(song: SongItem) {
            if (_queue.value.none { it.id == song.id }) {
                _queue.value = _queue.value + song
                savePlaybackSession()
            }
        }

        override fun playQueue(songs: List<SongItem>, index: Int, positionMs: Long, play: Boolean) {
            val song = songs.getOrNull(index) ?: return
            pendingResume = song.id to positionMs
            if (play) {
                playSongInternal(song, songs, resetShuffle = true)
            } else {
                if (playbackState.value.isPlaying) playerEngine.pause()
                _queue.value = songs
                _currentIndex.value = index
                _currentSong.value = song
                playRequestId = 0L
                savePlaybackSession()
            }
        }

        override fun account(): echo.music.desktop.connect.AccountSyncDto? {
            val account = AccountStore.getYouTubeAccount()?.takeIf { it.cookie.isNotBlank() } ?: return null
            return echo.music.desktop.connect.AccountSyncDto(
                cookie = account.cookie,
                name = account.name,
                email = account.email,
                channelHandle = account.channelHandle,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        override suspend fun signIn(cookie: String): Result<String> {
            val result = YouTubeSyncService.loginWithCookie(cookie)
            result.onSuccess { account ->
                withContext(Dispatchers.Main) {
                    _youtubeAccount.value = account
                    _statusMessage.value = "Echo Connect: conectado a YouTube Music como ${account.name}"
                    syncYouTubePlaylists()
                }
            }
            return result.map { it.name }
        }

        override fun localPlaylists(): List<echo.music.desktop.connect.PlaylistSyncDto> =
            echo.music.desktop.connect.PlaylistSync.outgoing(PlaylistStore.getPlaylists(), System.currentTimeMillis())

        override fun mergePlaylists(incoming: List<echo.music.desktop.connect.PlaylistSyncDto>, from: String): Int {
            val changed = echo.music.desktop.connect.PlaylistSync.merge(PlaylistStore.getPlaylists(), incoming, from, System.currentTimeMillis())
            if (changed.isEmpty()) return 0
            changed.forEach { PlaylistStore.savePlaylist(it) }
            _userPlaylists.value = PlaylistStore.getPlaylists()
            return changed.size
        }

        override fun bringToFront() = echo.music.desktop.system.SingleInstance.requestShow()

        override fun notify(message: String) {
            _statusMessage.value = message
        }
    })
    private val _isListenTogetherModalOpen = MutableStateFlow(false)
    val isListenTogetherModalOpen: StateFlow<Boolean> = _isListenTogetherModalOpen.asStateFlow()

    fun openListenTogetherModal() {
        _isListenTogetherModalOpen.value = true
    }

    fun closeListenTogetherModal() {
        _isListenTogetherModalOpen.value = false
    }

    fun toggleListenTogetherModal() {
        _isListenTogetherModalOpen.value = !_isListenTogetherModalOpen.value
    }

    // Crossfade (seconds)
    private val _crossfadeSeconds = MutableStateFlow(3)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    // Automix / Infinite Radio
    private val _isAutomixEnabled = MutableStateFlow(true)
    val isAutomixEnabled: StateFlow<Boolean> = _isAutomixEnabled.asStateFlow()

    // Artist & Album details
    private val _selectedArtistDetail = MutableStateFlow<ArtistPage?>(null)
    val selectedArtistDetail: StateFlow<ArtistPage?> = _selectedArtistDetail.asStateFlow()
    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    private val _selectedAlbumDetail = MutableStateFlow<AlbumPage?>(null)
    val selectedAlbumDetail: StateFlow<AlbumPage?> = _selectedAlbumDetail.asStateFlow()
    private val _isAlbumLoading = MutableStateFlow(false)
    val isAlbumLoading: StateFlow<Boolean> = _isAlbumLoading.asStateFlow()

    // Prioridad 3: Descargas / Modo Offline
    private val _downloadedSongs = MutableStateFlow<List<DownloadedSongRecord>>(DownloadStore.getDownloadedSongs())
    val downloadedSongs: StateFlow<List<DownloadedSongRecord>> = _downloadedSongs.asStateFlow()
    val downloadProgress: StateFlow<Map<String, Float>> = DesktopDownloadManager.downloadProgress
    private val _playlistDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val playlistDownloading: StateFlow<Map<String, Boolean>> = _playlistDownloading.asStateFlow()
    private val _downloadsDirectory = MutableStateFlow<File>(DownloadStore.downloadsDir)
    val downloadsDirectory: StateFlow<File> = _downloadsDirectory.asStateFlow()

    // Prioridad 3: Estadísticas / Echo Wrapped
    private val _isStatsModalOpen = MutableStateFlow(false)
    val isStatsModalOpen: StateFlow<Boolean> = _isStatsModalOpen.asStateFlow()

    // Prioridad 3: Resplandor Ambiental (Ambient Glow)
    private val _isAmbientGlowEnabled = MutableStateFlow(true)
    val isAmbientGlowEnabled: StateFlow<Boolean> = _isAmbientGlowEnabled.asStateFlow()

    // Prioridad 3: Temporizador de Apagado (Sleep Timer)
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var pauseOnTrackEnd: Boolean = false

    private val _favorites = MutableStateFlow<List<SongItem>>(FavoritesStore.getFavorites())
    val favorites: StateFlow<List<SongItem>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<SongItem>>(FavoritesStore.getHistory())
    val history: StateFlow<List<SongItem>> = _history.asStateFlow()

    private val _homeSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val homeSongs: StateFlow<List<SongItem>> = _homeSongs.asStateFlow()

    private val _isHomeLoading = MutableStateFlow(false)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongItem>>(emptyList())
    val searchResults: StateFlow<List<SongItem>> = _searchResults.asStateFlow()

    // Results for the non-song categories (artists, albums, playlists).
    private val _searchItems = MutableStateFlow<List<YTItem>>(emptyList())
    val searchItems: StateFlow<List<YTItem>> = _searchItems.asStateFlow()

    private val _searchCategory = MutableStateFlow(SearchCategory.SONGS)
    val searchCategory: StateFlow<SearchCategory> = _searchCategory.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()
    private var suggestionsJob: Job? = null
    private var searchJob: Job? = null

    // YouTube Music home feed (carousels), like the phone app.
    private val _homeSections = MutableStateFlow<List<HomePage.Section>>(emptyList())
    val homeSections: StateFlow<List<HomePage.Section>> = _homeSections.asStateFlow()
    private var homeContinuation: String? = null
    private val _isHomeLoadingMore = MutableStateFlow(false)
    val isHomeLoadingMore: StateFlow<Boolean> = _isHomeLoadingMore.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Listo")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _searchFocusTrigger = MutableStateFlow(0L)
    val searchFocusTrigger: StateFlow<Long> = _searchFocusTrigger.asStateFlow()

    fun triggerSearchFocus() {
        selectTab(NavigationTab.EXPLORE)
        _searchFocusTrigger.value = System.currentTimeMillis()
    }

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    init {
        // Restore YouTube Music cookie if account exists
        val savedAccount = _youtubeAccount.value
        if (savedAccount != null && savedAccount.cookie.isNotBlank()) {
            YouTube.cookie = savedAccount.cookie
            if (savedAccount.avatarUrl.isNullOrBlank()) {
                scope.launch {
                    val ref = YouTubeSyncService.refreshAccountInfo()
                    if (ref.isSuccess) {
                        _youtubeAccount.value = ref.getOrNull()
                    }
                }
            }
        }

        // Start Discord Rich Presence in background
        DiscordRpcClient.start()

        // Attach Listen Together sync manager
        listenTogetherManager.attachAppModel(this)

        // Hook automatic track end transition
        playerEngine.setOnTrackEnded {
            scope.launch {
                handleTrackEnded()
            }
        }

        // Initialize native AudioEqualizer and Crossfade
        val eq = _equalizerSettings.value
        playerEngine.setEqualizerEnabled(eq.enabled)
        playerEngine.setEqualizerBands(eq.bandGains)
        playerEngine.setCrossfadeDuration(_crossfadeSeconds.value)
        playerEngine.setVolume(PreferencesStore.current.volume)

        // Sync Discord Activity with playback state
        scope.launch {
            playbackState.collect { state ->
                if (state.videoId.isNotBlank()) {
                    DiscordRpcClient.updateActivity(
                        title = state.title,
                        artist = state.artist,
                        isPlaying = state.isPlaying,
                        currentPosMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        artworkUrl = state.artworkUrl
                    )
                } else {
                    DiscordRpcClient.clearActivity()
                }
            }
        }

        // Pre-warm visitorData and load Home content
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (YouTube.visitorData.isNullOrBlank()) {
                        YouTube.refreshVisitorData()
                    }
                } catch (ignored: Throwable) {}
            }
            loadHomeContent()
            if (_searchQuery.value.isNotBlank()) search(_searchQuery.value)
        }
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun toggleExpandedPlayer() {
        _isExpandedPlayerOpen.value = !_isExpandedPlayerOpen.value
    }

    fun closeExpandedPlayer() {
        _isExpandedPlayerOpen.value = false
    }

    fun toggleQueue() {
        _isQueueOpen.value = !_isQueueOpen.value
    }

    fun loadHomeContent() {
        scope.launch {
            _isHomeLoading.value = true
            try {
                val home = withContext(Dispatchers.IO) { YouTube.home() }.getOrNull()
                val sections = home?.sections.orEmpty()
                    .map { it.copy(items = it.items.applyContentFilters()) }
                    .filter { it.items.isNotEmpty() }
                _homeSections.value = sections
                homeContinuation = home?.continuation

                // Fallback list (also used when YouTube does not return a home feed).
                if (sections.isEmpty()) {
                    val result = withContext(Dispatchers.IO) {
                        YouTube.search("Trending Hits 2026", YouTube.SearchFilter.FILTER_SONG)
                    }
                    _homeSongs.value = result.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = "No se pudo cargar Inicio: ${e.message}"
            } finally {
                _isHomeLoading.value = false
            }
        }
    }

    /** Loads the next page of home carousels (called when the user scrolls to the end). */
    fun loadMoreHome() {
        val continuation = homeContinuation ?: return
        if (_isHomeLoadingMore.value) return
        scope.launch {
            _isHomeLoadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) { YouTube.home(continuation = continuation) }.getOrNull()
                homeContinuation = page?.continuation
                val more = page?.sections.orEmpty()
                    .map { it.copy(items = it.items.applyContentFilters()) }
                    .filter { it.items.isNotEmpty() }
                if (more.isNotEmpty()) _homeSections.value = _homeSections.value + more
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                homeContinuation = null
            } finally {
                _isHomeLoadingMore.value = false
            }
        }
    }

    // --- Explorar → Descubrir: Novedades, Charts, Estados de ánimo y géneros ---
    private val _discover = MutableStateFlow<DiscoverContent?>(null)
    val discover: StateFlow<DiscoverContent?> = _discover.asStateFlow()

    private val _isDiscoverLoading = MutableStateFlow(false)
    val isDiscoverLoading: StateFlow<Boolean> = _isDiscoverLoading.asStateFlow()

    private val _selectedMood = MutableStateFlow<MoodPage?>(null)
    val selectedMood: StateFlow<MoodPage?> = _selectedMood.asStateFlow()

    private val _isMoodLoading = MutableStateFlow(false)
    val isMoodLoading: StateFlow<Boolean> = _isMoodLoading.asStateFlow()

    /** Loads the Discover content once (or again with [force], e.g. after changing country). */
    fun loadDiscover(force: Boolean = false) {
        if (_isDiscoverLoading.value || (_discover.value != null && !force)) return
        scope.launch {
            _isDiscoverLoading.value = true
            try {
                val content = ExploreService.loadDiscover()
                _discover.value = content.copy(
                    newReleases = content.newReleases?.applyContentFilters(),
                    charts = content.charts?.map { it.copy(items = it.items.applyContentFilters()) }
                )
                if (content.newReleases == null && content.charts == null && content.moodGroups == null) {
                    _statusMessage.value = "No se pudo cargar Descubrir"
                }
            } finally {
                _isDiscoverLoading.value = false
            }
        }
    }

    fun openMood(title: String, endpoint: com.music.innertube.models.BrowseEndpoint) {
        scope.launch {
            _isMoodLoading.value = true
            _selectedMood.value = MoodPage(title, emptyList())
            try {
                val page = ExploreService.loadMood(title, endpoint).getOrNull()
                if (page == null) {
                    _selectedMood.value = null
                    _statusMessage.value = "No se pudo abrir $title"
                } else {
                    _selectedMood.value = page.copy(sections = page.sections.map { it.copy(items = it.items.applyContentFilters()) })
                }
            } finally {
                _isMoodLoading.value = false
            }
        }
    }

    fun closeMood() {
        _selectedMood.value = null
    }

    fun setSearchCategory(category: SearchCategory) {
        if (_searchCategory.value == category) return
        _searchCategory.value = category
        if (_searchQuery.value.isNotBlank()) search(_searchQuery.value)
    }

    /** Fetches autocomplete suggestions while the user types (debounced). */
    fun updateSearchSuggestions(input: String) {
        suggestionsJob?.cancel()
        if (input.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        suggestionsJob = scope.launch {
            delay(250)
            val res = withContext(Dispatchers.IO) { YouTube.searchSuggestions(input) }
            _searchSuggestions.value = res.getOrNull()?.queries.orEmpty().take(6)
        }
    }

    fun clearSearchSuggestions() {
        suggestionsJob?.cancel()
        _searchSuggestions.value = emptyList()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        clearSearchSuggestions()
        val category = _searchCategory.value
        searchJob?.cancel()
        searchJob = scope.launch {
            _isSearching.value = true
            _statusMessage.value = "Buscando \"$query\"..."
            try {
                val result = withContext(Dispatchers.IO) {
                    YouTube.search(query, category.filter)
                }
                if (result.isSuccess) {
                    val items = result.getOrNull()?.items.orEmpty()
                        .applyContentFilters(keepVideos = category == SearchCategory.VIDEOS)
                    val count: Int
                    if (category.isSongList) {
                        val songs = items.filterIsInstance<SongItem>()
                        _searchResults.value = songs
                        _searchItems.value = emptyList()
                        count = songs.size
                    } else {
                        _searchItems.value = items.filterNot { it is SongItem }
                        _searchResults.value = emptyList()
                        count = _searchItems.value.size
                    }
                    _statusMessage.value = if (count == 0) "Sin resultados" else null
                } else {
                    _statusMessage.value = "Error: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Opens any YouTube Music item: plays songs, opens albums/artists/playlists. */
    fun openItem(item: YTItem, siblings: List<YTItem> = emptyList()) {
        when (item) {
            is SongItem -> {
                val songs = siblings.filterIsInstance<SongItem>().ifEmpty { listOf(item) }
                playSong(item, songs)
            }
            is AlbumItem -> openAlbum(item.browseId)
            is ArtistItem -> openArtist(item.id)
            is PlaylistItem -> if (item.isPodcast) openPodcast(item.id, item.title) else openRemotePlaylist(item)
        }
    }

    /**
     * Opens a podcast show with its episodes (first pages). [showId] is the show's playlist id,
     * with or without the `MPSP` browse prefix.
     */
    fun openPodcast(showId: String, title: String? = null) {
        val id = com.music.innertube.pages.PodcastPage.showId(showId)
        val localId = "podcast_$id"
        PlaylistStore.getPlaylist(localId)?.let {
            openPlaylistDetail(it)
            return
        }
        scope.launch {
            _statusMessage.value = "Cargando podcast${title?.let { " $it" } ?: ""}..."
            val page = withContext(Dispatchers.IO) { YouTube.podcast(id) }.getOrNull()
            if (page == null) {
                _statusMessage.value = "No se pudo abrir el podcast"
                return@launch
            }
            // A few more pages of episodes (newest first), like the phone's show screen.
            val episodes = page.songs.toMutableList()
            var continuation = page.songsContinuation
            var pages = 0
            while (continuation != null && pages < 4) {
                val next = withContext(Dispatchers.IO) { YouTube.playlistContinuation(continuation!!) }.getOrNull() ?: break
                episodes += next.songs
                continuation = next.continuation
                pages++
            }
            _statusMessage.value = null
            openPlaylistDetail(
                UserPlaylist.fromSongs(
                    id = localId,
                    title = page.playlist.title.ifBlank { title ?: "Podcast" },
                    author = page.playlist.author?.name ?: "Podcast",
                    thumbnailUrl = page.playlist.thumbnail,
                    sourceType = PlaylistSourceType.PODCAST,
                    songs = episodes.distinctBy { it.id }
                )
            )
        }
    }

    /** Shows a YouTube Music playlist that is not (yet) in the library. */
    fun openRemotePlaylist(item: PlaylistItem) {
        val localId = "yt_${item.id.removePrefix("VL")}"
        PlaylistStore.getPlaylist(localId)?.let {
            openPlaylistDetail(it)
            return
        }
        scope.launch {
            _statusMessage.value = "Cargando playlist ${item.title}..."
            val songs = YouTubeSyncService.fetchPlaylistSongs(item.id)
            if (songs.isEmpty()) {
                _statusMessage.value = "No se pudo cargar la playlist ${item.title}"
                return@launch
            }
            _statusMessage.value = null
            openPlaylistDetail(
                UserPlaylist.fromSongs(
                    id = localId,
                    title = item.title,
                    author = item.author?.name ?: "YouTube Music",
                    thumbnailUrl = item.thumbnail,
                    sourceType = PlaylistSourceType.YOUTUBE_MUSIC,
                    songs = songs
                )
            )
        }
    }

    fun isPlaylistInLibrary(playlistId: String): Boolean = _userPlaylists.value.any { it.id == playlistId }

    /** Saves a playlist that is only being viewed (e.g. opened from Home or search) into the library. */
    fun savePlaylistToLibrary(playlist: UserPlaylist) {
        PlaylistStore.savePlaylist(playlist)
        _userPlaylists.value = PlaylistStore.getPlaylists()
        _statusMessage.value = "\"${playlist.title}\" guardada en tu biblioteca"
    }

    fun seekToPositionMs(positionMs: Long) {
        val totalMs = playbackState.value.durationMs
        val target = if (totalMs > 0) positionMs.coerceIn(0L, totalMs) else positionMs
        playerEngine.seekTo(target)
    }

    // --- Sesión de reproducción (continuar al reiniciar) ---
    /** Song restored from the last session and the position to resume it at, until it is played. */
    private var pendingResume: Pair<String, Long>? = null

    private fun restorePlaybackSession() {
        val session = PlaybackSessionStore.load() ?: return
        val songs = session.queue.map { it.toSongItem() }
        val current = songs[session.currentIndex]
        _queue.value = songs
        _currentIndex.value = session.currentIndex
        _currentSong.value = current
        shufflePlayedIds.add(current.id)
        pendingResume = current.id to session.positionMs
        _statusMessage.value = "Listo para continuar: ${current.title}"
    }

    private fun currentPositionForSession(): Long {
        val song = _currentSong.value ?: return 0L
        val state = playbackState.value
        return when {
            state.videoId == song.id -> state.currentPositionMs
            pendingResume?.first == song.id -> pendingResume!!.second
            else -> 0L
        }
    }

    private fun savePlaybackSession() {
        val queue = _queue.value
        val index = _currentIndex.value
        val position = currentPositionForSession()
        scope.launch(Dispatchers.IO) { PlaybackSessionStore.save(queue, index, position) }
    }

    // Jobs of the song currently being loaded. Cancelled when another song starts so a slow
    // stream/lyrics lookup for an older song can never overwrite the newer one.
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var playRequestId = 0L

    // Shuffle bookkeeping: songs already played in this shuffle pass and the order they were played in.
    private val shufflePlayedIds = mutableSetOf<String>()
    private val shuffleHistory = ArrayDeque<String>()

    fun playSong(song: SongItem, customQueue: List<SongItem>? = null) {
        val isNewQueue = customQueue != null && customQueue !== _queue.value
        playSongInternal(song, customQueue, resetShuffle = isNewQueue)
    }

    private fun playSongInternal(song: SongItem, customQueue: List<SongItem>?, resetShuffle: Boolean) {
        if (resetShuffle) {
            shufflePlayedIds.clear()
            shuffleHistory.clear()
        }
        shufflePlayedIds.add(song.id)

        _currentSong.value = song
        val newQueue = customQueue
            ?: if (_queue.value.any { it.id == song.id }) _queue.value else (_queue.value + song)
        val index = newQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _queue.value = newQueue
        _currentIndex.value = index

        val requestId = ++playRequestId
        playJob?.cancel()
        lyricsJob?.cancel()
        loadCanvas(song)

        val resumeAtMs = pendingResume?.takeIf { it.first == song.id }?.second ?: 0L
        pendingResume = null
        savePlaybackSession()

        // Broadcast track change to Listen Together room if active
        if (!LocalMusicLibrary.isLocal(song.id)) listenTogetherManager.broadcastTrackChange(song)

        // Record history (unless the user paused it: "historial privado")
        if (!_prefs.value.pauseHistory) {
            FavoritesStore.addToHistory(song)
            _history.value = FavoritesStore.getHistory()
        }

        // Asynchronously fetch lyrics via LRCLIB
        _isLyricsLoading.value = true
        _currentLyrics.value = null
        lyricsExtrasJob?.cancel()
        _lyricsExtras.value = LyricsExtras()
        _lyricsSwitchingTo.value = null
        lyricsJob = scope.launch {
            val lyrics = LyricsService.fetchLyrics(lyricsRequestFor(song))
            if (requestId != playRequestId) return@launch
            _currentLyrics.value = lyrics
            _isLyricsLoading.value = false
            onLyricsLoaded(lyrics)
        }

        playJob = scope.launch {
            _statusMessage.value = "Cargando ${song.title}..."
            try {
                val isLocalFile = LocalMusicLibrary.isLocal(song.id)
                val localPath = LocalMusicLibrary.pathFor(song.id) ?: DownloadStore.getLocalFilePath(song.id)
                    // Video mode: the music video already downloaded (picture and sound in one file).
                    ?: if (_videoMode.value) echo.music.desktop.video.MusicVideoService.cachedFile(song)?.absolutePath else null
                if (isLocalFile && localPath == null) {
                    // A song from "En este PC" whose file was moved/deleted or whose folder was removed:
                    // it can't be found on YouTube by its local id.
                    onPlaybackFailed(song, "el archivo ya no está en tus carpetas de música")
                    return@launch
                }
                val url = if (localPath != null) {
                    File(localPath).toURI().toString()
                } else {
                    val streamResult = DesktopStreamResolver.resolveStreamUrl(
                        song.id,
                        lowQuality = _prefs.value.audioQuality == "LOW",
                        onRetry = { attempt, waitSeconds ->
                            if (requestId == playRequestId) {
                                _statusMessage.value = "YouTube está ocupado, reintentando ${song.title} en $waitSeconds s ($attempt de ${DesktopStreamResolver.RETRY_DELAYS_MS.size})..."
                            }
                        }
                    )
                    if (streamResult.isSuccess) streamResult.getOrThrow() else null
                }

                // A newer song was requested while this one was resolving: drop this result.
                if (requestId != playRequestId) return@launch

                if (url != null) {
                    val artistsStr = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" }
                    val durationMs = (song.duration ?: 0) * 1000L
                    applyVolumeGain(song.id)
                    loadSkipSegments(song)
                    playerEngine.play(
                        videoId = song.id,
                        url = url,
                        title = song.title,
                        artist = artistsStr,
                        artworkUrl = song.thumbnail,
                        durationMs = durationMs
                    )
                    if (resumeAtMs > 3_000L) {
                        // Restored session: jump back to where the song was when the app closed.
                        val ready = withTimeoutOrNull(20_000L) {
                            playbackState.first { it.videoId == song.id && it.isPlaying && !it.isBuffering }
                        }
                        if (ready != null && requestId == playRequestId) seekToPositionMs(resumeAtMs)
                    }
                    _statusMessage.value = when {
                        isLocalFile -> null
                        localPath != null -> "Reproduciendo sin conexión (${song.title})"
                        else -> null
                    }
                } else {
                    onPlaybackFailed(song, "no se encontró un audio reproducible")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == playRequestId) _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    private fun lyricsRequestFor(song: SongItem) = LyricsRequest(
        videoId = if (LocalMusicLibrary.isLocal(song.id)) "" else song.id,
        title = song.title,
        artist = song.artists.firstOrNull()?.name.orEmpty(),
        durationSec = song.duration ?: (playbackState.value.durationMs / 1000L).toInt(),
        album = song.album?.name
    )

    // --- Romanización y traducción de letras ---
    /** Decrypted API key for lyrics translation ("" when none). */
    private fun translationApiKey(): String =
        runCatching { echo.music.desktop.system.SecretProtector.unprotect(_prefs.value.translationApiKey) }.getOrDefault("")

    val hasTranslationKey: Boolean get() = translationApiKey().isNotBlank()

    fun setTranslationApiKey(plain: String) {
        updatePreferences { it.copy(translationApiKey = echo.music.desktop.system.SecretProtector.protect(plain.trim())) }
    }

    private fun translationSettings() = _prefs.value.let { p ->
        TranslationSettings(
            provider = TranslationProvider.from(p.translationProvider),
            apiKey = translationApiKey(),
            targetLanguage = p.translationLanguage,
            model = p.translationModel,
            baseUrl = p.translationBaseUrl
        )
    }

    /** Romanizes (and auto-translates, if enabled) lyrics that were just shown. */
    private fun onLyricsLoaded(lyrics: SongLyrics?) {
        lyricsExtrasJob?.cancel()
        _lyricsExtras.value = LyricsExtras()
        if (lyrics == null || lyrics.lines.isEmpty()) return
        val lines = lyrics.lines.map { it.text }
        lyricsExtrasJob = scope.launch {
            if (_prefs.value.lyricsRomanize && LyricsTransformService.needsRomanization(lines)) {
                val romanized = LyricsTransformService.romanize(lines)
                if (_currentLyrics.value === lyrics) _lyricsExtras.update { it.copy(romanized = romanized) }
            }
            if (_prefs.value.lyricsAutoTranslate && hasTranslationKey) translateLyrics(lyrics)
        }
    }

    private suspend fun translateLyrics(lyrics: SongLyrics) {
        _lyricsExtras.update { it.copy(isTranslating = true, translationError = null) }
        val result = LyricsTransformService.translate(lyrics.lines.map { it.text }, translationSettings())
        if (_currentLyrics.value !== lyrics) return
        _lyricsExtras.update {
            it.copy(
                translated = result.getOrNull(),
                isTranslating = false,
                translationError = result.exceptionOrNull()?.let { e -> "No se pudo traducir: ${e.message}" }
            )
        }
    }

    /** "Traducir" button in the lyrics panel. */
    fun translateCurrentLyrics() {
        val lyrics = _currentLyrics.value ?: return
        if (!hasTranslationKey) {
            _lyricsExtras.update {
                it.copy(translationError = "Para traducir, agrega tu clave de OpenRouter, DeepL o Mistral en Ajustes → Letras.")
            }
            return
        }
        scope.launch { translateLyrics(lyrics) }
    }

    fun hideLyricsTranslation() {
        _lyricsExtras.update { it.copy(translated = null, translationError = null) }
    }

    /** Loads the current song's lyrics from another provider chosen by the user. */
    fun switchLyricsProvider(provider: LyricsProviderId) {
        val song = _currentSong.value ?: return
        val requestId = playRequestId
        lyricsJob?.cancel()
        _isLyricsLoading.value = false
        _lyricsSwitchingTo.value = provider
        lyricsJob = scope.launch {
            val request = lyricsRequestFor(song)
            val lyrics = LyricsService.fetchFrom(provider, request)
            if (requestId != playRequestId) return@launch
            _lyricsSwitchingTo.value = null
            if (lyrics != null) {
                LyricsService.remember(request, lyrics)
                _currentLyrics.value = lyrics
                onLyricsLoaded(lyrics)
                _statusMessage.value = "Letra de ${provider.displayName}"
            } else {
                _statusMessage.value = "${provider.displayName} no tiene la letra de esta canción"
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        PreferencesStore.update { it.copy(repeatMode = _repeatMode.value.name) }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        PreferencesStore.update { it.copy(isShuffle = _isShuffle.value) }
        // Start a fresh shuffle pass from the song that is playing now.
        shufflePlayedIds.clear()
        shuffleHistory.clear()
        _currentSong.value?.let { shufflePlayedIds.add(it.id) }
    }

    /** Index of the song that should play after the current one, or null if the queue is finished. */
    private fun nextIndex(wrap: Boolean): Int? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val current = _currentIndex.value
        if (_isShuffle.value && q.size > 1) {
            var candidates = q.indices.filter { it != current && q[it].id !in shufflePlayedIds }
            if (candidates.isEmpty()) {
                if (!wrap) return null
                // Every song was played once: start a new shuffle pass.
                shufflePlayedIds.clear()
                q.getOrNull(current)?.let { shufflePlayedIds.add(it.id) }
                candidates = q.indices.filter { it != current }
            }
            return candidates.random()
        }
        return when {
            current + 1 < q.size -> current + 1
            wrap -> 0
            else -> null
        }
    }

    private fun handleTrackEnded() {
        // Record listening stats for Echo Wrapped (not while the history is paused)
        _currentSong.value?.takeIf { !_prefs.value.pauseHistory }?.let { current ->
            val durSec = current.duration ?: (playbackState.value.durationMs / 1000L).toInt()
            StatsStore.recordPlayback(current, durSec)
        }

        // Check if sleep timer was set to "Al terminar canción actual"
        if (pauseOnTrackEnd) {
            pauseOnTrackEnd = false
            _sleepTimerRemainingSeconds.value = null
            _statusMessage.value = "Temporizador de apagado completado"
            playerEngine.pause()
            return
        }

        // Music Flow with "Avance automático" off: stop at the end of each song.
        if (flowOwnsQueue() && !_prefs.value.flowAutoNext) return

        val q = _queue.value
        if (q.isEmpty()) return
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { playSong(it, q) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val next = nextIndex(wrap = false)
                if (next != null) {
                    advanceTo(next)
                } else if (_isAutomixEnabled.value && _currentSong.value != null) {
                    fetchAutomixNext(_currentSong.value!!)
                }
            }
        }
    }

    private fun fetchAutomixNext(song: SongItem) {
        scope.launch {
            _statusMessage.value = "Automix: buscando canciones recomendadas..."
            try {
                val res = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = song.id))
                }
                if (res.isSuccess) {
                    val nextSongs = res.getOrNull()?.items.orEmpty().applyContentFilters()
                    val existingIds = _queue.value.map { it.id }.toSet()
                    val newUnique = nextSongs.filterNot { it.id in existingIds }
                    if (newUnique.isNotEmpty()) {
                        val updatedQueue = _queue.value + newUnique
                        val nextIdx = _queue.value.size
                        _currentSong.value?.let { shuffleHistory.addLast(it.id) }
                        _queue.value = updatedQueue
                        playSongInternal(updatedQueue[nextIdx], updatedQueue, resetShuffle = false)
                        _statusMessage.value = "Automix: ${newUnique.size} canciones añadidas a la radio"
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) {
            q.removeAt(index)
            _queue.value = q
            if (index < _currentIndex.value) {
                _currentIndex.value--
            } else if (index == _currentIndex.value && q.isNotEmpty()) {
                val newIdx = index.coerceAtMost(q.size - 1)
                _currentIndex.value = newIdx
                playSong(q[newIdx], q)
            }
            savePlaybackSession()
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        savePlaybackSession()
    }

    fun playNext() {
        listenTogetherManager.broadcastSkipNext()
        nextIndex(wrap = true)?.let { advanceTo(it) }
    }

    private fun advanceTo(index: Int) {
        val q = _queue.value
        val target = q.getOrNull(index) ?: return
        _currentSong.value?.let { shuffleHistory.addLast(it.id) }
        while (shuffleHistory.size > 500) shuffleHistory.removeFirst()
        playSongInternal(target, q, resetShuffle = false)
    }

    /**
     * @param allowRestart when true (local button/shortcut) and the song is past 3 seconds,
     * "previous" restarts the current song like most players do. Remote Listen Together
     * commands pass false so every listener moves to the same track.
     */
    fun playPrevious(allowRestart: Boolean = true) {
        val q = _queue.value
        if (q.isEmpty()) return
        if (allowRestart && playbackState.value.currentPositionMs > 3_000L) {
            seekToPositionMs(0L)
            listenTogetherManager.broadcastSeek(0L)
            return
        }
        listenTogetherManager.broadcastSkipPrevious()
        // Go back to the song that actually played before (matters in shuffle mode).
        while (shuffleHistory.isNotEmpty()) {
            val prevId = shuffleHistory.removeLast()
            val prev = q.firstOrNull { it.id == prevId } ?: continue
            playSongInternal(prev, q, resetShuffle = false)
            return
        }
        val prevIndex = if (_currentIndex.value - 1 < 0) q.size - 1 else _currentIndex.value - 1
        playSongInternal(q[prevIndex], q, resetShuffle = false)
    }

    fun togglePlayPause() {
        // The user decided: a later reconnection of the headphones must not resume on its own.
        pausedForOutput = null
        // After a restart the restored song is shown but not loaded yet: start it at the saved position.
        val restored = _currentSong.value
        if (restored != null && playbackState.value.videoId != restored.id && playRequestId == 0L) {
            playSongInternal(restored, _queue.value, resetShuffle = false)
            return
        }
        val currentMs = playbackState.value.currentPositionMs
        if (playbackState.value.isPlaying) {
            playerEngine.pause()
            listenTogetherManager.broadcastPause(currentMs)
        } else {
            playerEngine.resume()
            listenTogetherManager.broadcastPlay(currentMs)
        }
    }

    fun seekTo(fraction: Float) {
        val totalMs = playbackState.value.durationMs
        if (totalMs > 0) {
            val target = (fraction * totalMs).toLong()
            playerEngine.seekTo(target)
            listenTogetherManager.broadcastSeek(target)
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        playerEngine.setVolume(clamped)
        if (clamped > 0.05f) {
            lastNonZeroVolume = clamped
        }
        PreferencesStore.update { it.copy(volume = clamped, isMuted = clamped <= 0f) }
    }

    fun adjustVolume(delta: Float) {
        val current = playbackState.value.volume
        val newVol = (current + delta).coerceIn(0f, 1f)
        setVolume(newVol)
        _statusMessage.value = "Volumen: ${(newVol * 100).roundToInt()}%"
    }

    fun toggleMute() {
        val current = playbackState.value.volume
        if (current > 0.01f) {
            lastNonZeroVolume = current
            setVolume(0f)
            _statusMessage.value = "Silenciado (Mute)"
        } else {
            val restoreVol = if (lastNonZeroVolume > 0.05f) lastNonZeroVolume else 0.8f
            setVolume(restoreVol)
            _statusMessage.value = "Volumen: ${(restoreVol * 100).roundToInt()}%"
        }
    }

    fun seekBySeconds(deltaSeconds: Long) {
        val totalMs = playbackState.value.durationMs
        val currentMs = playbackState.value.currentPositionMs
        if (totalMs > 0) {
            val target = (currentMs + deltaSeconds * 1000L).coerceIn(0L, totalMs)
            playerEngine.seekTo(target)
            listenTogetherManager.broadcastSeek(target)
            val min = target / 1000 / 60
            val sec = (target / 1000) % 60
            val sign = if (deltaSeconds > 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
            _statusMessage.value = "$sign • ${min}:${String.format("%02d", sec)}"
        }
    }

    fun closeActiveModal(): Boolean {
        if (_aiPrompt.value != null) {
            _aiPrompt.value = null
            return true
        }
        if (_aiTask.value?.running == false) {
            _aiTask.value = null
            return true
        }
        if (_addToPlaylistSongs.value != null) {
            _addToPlaylistSongs.value = null
            return true
        }
        if (_isCreatePlaylistOpen.value) {
            _isCreatePlaylistOpen.value = false
            return true
        }
        if (_isListenTogetherModalOpen.value) {
            _isListenTogetherModalOpen.value = false
            return true
        }
        if (_isExpandedPlayerOpen.value) {
            _isExpandedPlayerOpen.value = false
            return true
        }
        if (_isEqualizerModalOpen.value) {
            _isEqualizerModalOpen.value = false
            return true
        }
        if (_isStatsModalOpen.value) {
            _isStatsModalOpen.value = false
            return true
        }
        if (_isMusicFlowOpen.value) {
            _isMusicFlowOpen.value = false
            return true
        }
        if (_selectedPlaylistDetail.value != null) {
            _selectedPlaylistDetail.value = null
            return true
        }
        if (_selectedArtistDetail.value != null || _isArtistLoading.value) {
            _selectedArtistDetail.value = null
            _isArtistLoading.value = false
            return true
        }
        if (_selectedAlbumDetail.value != null || _isAlbumLoading.value) {
            _selectedAlbumDetail.value = null
            _isAlbumLoading.value = false
            return true
        }
        if (_isYouTubeLoginModalOpen.value) {
            _isYouTubeLoginModalOpen.value = false
            return true
        }
        if (_isSpotifyImportModalOpen.value) {
            _isSpotifyImportModalOpen.value = false
            return true
        }
        if (_echoFind.value != null) {
            closeEchoFind()
            return true
        }
        if (_selectedMood.value != null) {
            _selectedMood.value = null
            return true
        }
        return false
    }

    fun toggleFavorite(song: SongItem) {
        FavoritesStore.toggleFavorite(song)
        _favorites.value = FavoritesStore.getFavorites()
    }

    fun isFavorite(songId: String): Boolean {
        return _favorites.value.any { it.id == songId }
    }

    fun openYouTubeLoginModal() {
        _isYouTubeLoginModalOpen.value = true
    }

    fun closeYouTubeLoginModal() {
        _isYouTubeLoginModalOpen.value = false
    }

    fun openSpotifyImportModal() {
        _isSpotifyImportModalOpen.value = true
    }

    fun closeSpotifyImportModal() {
        _isSpotifyImportModalOpen.value = false
    }

    fun loginYouTube(cookie: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            _isSyncingYouTube.value = true
            _statusMessage.value = "Autenticando con YouTube Music..."
            try {
                val res = YouTubeSyncService.loginWithCookie(cookie)
                if (res.isSuccess) {
                    val account = res.getOrThrow()
                    _youtubeAccount.value = account
                    _statusMessage.value = "Conectado como ${account.name}"
                    onResult(true, null)
                    syncYouTubePlaylists()
                } else {
                    val errorMsg = res.exceptionOrNull()?.message ?: "Error al autenticar con YouTube Music"
                    _statusMessage.value = errorMsg
                    onResult(false, errorMsg)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                onResult(false, e.message)
            } finally {
                _isSyncingYouTube.value = false
            }
        }
    }

    fun logoutYouTube() {
        YouTube.cookie = ""
        AccountStore.clearYouTubeAccount()
        _libraryAlbums.value = emptyList()
        _libraryArtists.value = emptyList()
        accountLibraryLoaded = false
        _youtubeAccount.value = null
        _statusMessage.value = "Sesión de YouTube Music cerrada"
    }

    fun syncYouTubePlaylists(onComplete: ((Int) -> Unit)? = null) {
        scope.launch {
            _isSyncingYouTube.value = true
            _statusMessage.value = "Sincronizando playlists de YouTube Music..."
            try {
                val res = YouTubeSyncService.syncPlaylists { curr, total, name ->
                    _statusMessage.value = "Sincronizando ($curr/$total): $name"
                }
                if (res.isSuccess) {
                    val list = res.getOrThrow()
                    _youtubeAccount.value = AccountStore.getYouTubeAccount()
                    _userPlaylists.value = PlaylistStore.getPlaylists()
                    _favorites.value = FavoritesStore.getFavorites()
                    _statusMessage.value = "Sincronizadas ${list.size} playlists de YouTube Music"
                    onComplete?.invoke(list.size)
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Error al sincronizar"
                    _statusMessage.value = "Error: $err"
                    onComplete?.invoke(0)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                onComplete?.invoke(0)
            } finally {
                _isSyncingYouTube.value = false
            }
        }
    }

    fun refreshYouTubePlaylist(playlist: UserPlaylist, onComplete: ((Int) -> Unit)? = null) {
        if (playlist.sourceType != PlaylistSourceType.YOUTUBE_MUSIC) return
        val ytId = playlist.id.removePrefix("yt_")
        scope.launch {
            _statusMessage.value = "Actualizando pistas de ${playlist.title}..."
            try {
                val fullSongs = YouTubeSyncService.fetchPlaylistSongs(ytId)
                if (fullSongs.isNotEmpty()) {
                    val updated = UserPlaylist.fromSongs(
                        id = playlist.id,
                        title = playlist.title,
                        author = playlist.author,
                        thumbnailUrl = playlist.thumbnailUrl,
                        sourceType = playlist.sourceType,
                        songs = fullSongs
                    )
                    PlaylistStore.savePlaylist(updated)
                    _userPlaylists.value = PlaylistStore.getPlaylists()
                    if (_selectedPlaylistDetail.value?.id == playlist.id) {
                        _selectedPlaylistDetail.value = updated
                    }
                    _statusMessage.value = "Lista actualizada: ${fullSongs.size} canciones"
                    onComplete?.invoke(fullSongs.size)
                } else {
                    _statusMessage.value = "No se pudieron obtener canciones para ${playlist.title}"
                    onComplete?.invoke(playlist.savedSongs.size)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                onComplete?.invoke(0)
            }
        }
    }

    fun importSpotifyPlaylist(
        urlOrId: String,
        onProgress: (String) -> Unit = {},
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            _isImportingSpotify.value = true
            _importProgressText.value = "Consultando playlist de Spotify..."
            onProgress("Consultando playlist de Spotify...")
            try {
                val res = SpotifyImporter.importPlaylist(urlOrId) { curr, total, songName ->
                    val msg = "Importando ($curr/$total): $songName"
                    _importProgressText.value = msg
                    onProgress(msg)
                }
                if (res.isSuccess) {
                    val imported = res.getOrThrow()
                    _userPlaylists.value = PlaylistStore.getPlaylists()
                    _statusMessage.value = "Playlist \"${imported.title}\" importada con éxito"
                    _importProgressText.value = null
                    onComplete(true, null)
                } else {
                    val errMsg = res.exceptionOrNull()?.message ?: "Error al importar playlist"
                    _statusMessage.value = errMsg
                    _importProgressText.value = null
                    onComplete(false, errMsg)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Error desconocido"
                _statusMessage.value = errMsg
                _importProgressText.value = null
                onComplete(false, errMsg)
            } finally {
                _isImportingSpotify.value = false
            }
        }
    }

    fun openPlaylistDetail(playlist: UserPlaylist) {
        _selectedPlaylistDetail.value = playlist
    }

    fun closePlaylistDetail() {
        _selectedPlaylistDetail.value = null
    }

    fun deletePlaylist(playlistId: String) {
        PlaylistStore.deletePlaylist(playlistId)
        _userPlaylists.value = PlaylistStore.getPlaylists()
        if (_selectedPlaylistDetail.value?.id == playlistId) {
            _selectedPlaylistDetail.value = null
        }
        _statusMessage.value = "Playlist eliminada"
    }

    fun playPlaylist(playlist: UserPlaylist, startIndex: Int = 0) {
        if (playlist.songs.isEmpty()) return
        val targetIndex = startIndex.coerceIn(0, playlist.songs.size - 1)
        playSong(playlist.songs[targetIndex], playlist.songs)
    }

    // --- Agregar a playlist ---
    // Songs waiting for the user to pick a playlist in the "Agregar a playlist" dialog (null = closed).
    private val _addToPlaylistSongs = MutableStateFlow<List<SongItem>?>(null)
    val addToPlaylistSongs: StateFlow<List<SongItem>?> = _addToPlaylistSongs.asStateFlow()

    private val _isCreatePlaylistOpen = MutableStateFlow(false)
    val isCreatePlaylistOpen: StateFlow<Boolean> = _isCreatePlaylistOpen.asStateFlow()

    fun openAddToPlaylist(songs: List<SongItem>) {
        if (songs.isNotEmpty()) _addToPlaylistSongs.value = songs
    }

    fun closeAddToPlaylist() {
        _addToPlaylistSongs.value = null
    }

    fun openCreatePlaylist() {
        _isCreatePlaylistOpen.value = true
    }

    fun closeCreatePlaylist() {
        _isCreatePlaylistOpen.value = false
    }

    /** Playlists the user can add songs to (YouTube "Me gusta" is managed with the heart instead). */
    fun isPlaylistEditable(playlist: UserPlaylist): Boolean =
        playlist.id != "yt_LM" && playlist.id != "yt_SE" && playlist.sourceType != PlaylistSourceType.PODCAST

    /** Creates an empty local playlist (optionally with [songs]) and returns it. */
    fun createPlaylist(title: String, songs: List<SongItem> = emptyList()): UserPlaylist {
        val playlist = UserPlaylist.fromSongs(
            id = "local_${System.currentTimeMillis()}",
            title = title.trim().ifEmpty { "Nueva playlist" },
            author = _youtubeAccount.value?.name ?: "Echo Music",
            thumbnailUrl = songs.firstOrNull()?.thumbnail,
            sourceType = PlaylistSourceType.LOCAL,
            songs = songs.distinctBy { it.id }
        )
        PlaylistStore.savePlaylist(playlist)
        _userPlaylists.value = PlaylistStore.getPlaylists()
        _statusMessage.value = if (songs.isEmpty()) {
            "Playlist \"${playlist.title}\" creada"
        } else {
            "Playlist \"${playlist.title}\" creada con ${playlist.savedSongs.size} canción(es)"
        }
        return playlist
    }

    /**
     * Adds [songs] to the playlist [playlistId], skipping songs already in it.
     * For YouTube Music playlists the songs are also added to the account so the next sync keeps them.
     */
    fun addSongsToPlaylist(playlistId: String, songs: List<SongItem>, onDone: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val playlist = PlaylistStore.getPlaylist(playlistId)
            if (playlist == null || !isPlaylistEditable(playlist)) {
                onDone?.invoke(false)
                return@launch
            }
            val existingIds = playlist.savedSongs.map { it.id }.toSet()
            var toAdd = songs.distinctBy { it.id }.filterNot { it.id in existingIds }
            if (toAdd.isEmpty()) {
                _statusMessage.value = "Ya está en \"${playlist.title}\""
                onDone?.invoke(true)
                return@launch
            }

            if (playlist.sourceType == PlaylistSourceType.YOUTUBE_MUSIC) {
                if (_youtubeAccount.value == null) {
                    _statusMessage.value = "Inicia sesión en YouTube Music para editar \"${playlist.title}\""
                    onDone?.invoke(false)
                    return@launch
                }
                val ytId = playlist.id.removePrefix("yt_")
                val failed = mutableListOf<SongItem>()
                withContext(Dispatchers.IO) {
                    toAdd.forEach { song ->
                        if (YouTube.addToPlaylist(ytId, song.id).isFailure) failed += song
                    }
                }
                toAdd = toAdd - failed.toSet()
                if (toAdd.isEmpty()) {
                    _statusMessage.value = "YouTube Music no permitió editar \"${playlist.title}\" (¿es una playlist de otra persona?)"
                    onDone?.invoke(false)
                    return@launch
                }
            }

            val latest = PlaylistStore.getPlaylist(playlistId) ?: playlist
            val updated = latest.copy(
                savedSongs = latest.savedSongs + toAdd.map { SavedSong.fromSongItem(it) },
                thumbnailUrl = latest.thumbnailUrl ?: toAdd.first().thumbnail
            )
            PlaylistStore.savePlaylist(updated)
            _userPlaylists.value = PlaylistStore.getPlaylists()
            if (_selectedPlaylistDetail.value?.id == playlistId) _selectedPlaylistDetail.value = updated
            _statusMessage.value = if (toAdd.size == 1) {
                "\"${toAdd.first().title}\" agregada a \"${updated.title}\""
            } else {
                "${toAdd.size} canciones agregadas a \"${updated.title}\""
            }
            onDone?.invoke(true)
        }
    }

    /** Removes a song from a local (or imported Spotify) playlist. */
    // --- Privacidad ---
    fun clearListenHistory() {
        FavoritesStore.clearHistory()
        _history.value = emptyList()
        _statusMessage.value = "Historial de escucha borrado"
    }

    // --- Editar playlists: renombrar, reordenar, quitar canciones ---
    private fun isYouTubePlaylist(p: UserPlaylist) = p.sourceType == PlaylistSourceType.YOUTUBE_MUSIC

    /** Rename/reorder: local, imported Spotify, and your own YouTube Music playlists (not "Me gusta"). */
    fun canEditPlaylist(p: UserPlaylist): Boolean {
        if (!isPlaylistInLibrary(p.id) || p.sourceType == PlaylistSourceType.PODCAST) return false
        if (isYouTubePlaylist(p)) return _youtubeAccount.value != null && p.id != "yt_LM" && p.id != "yt_SE"
        return true
    }

    /** Remove songs: like [canEditPlaylist], plus "Me gusta" (removing = unliking). */
    fun canRemoveFromPlaylist(p: UserPlaylist): Boolean =
        canEditPlaylist(p) || (p.id == "yt_LM" && _youtubeAccount.value != null && isPlaylistInLibrary(p.id))

    private fun savePlaylistUpdate(updated: UserPlaylist) {
        PlaylistStore.savePlaylist(updated)
        _userPlaylists.value = PlaylistStore.getPlaylists()
        if (_selectedPlaylistDetail.value?.id == updated.id) _selectedPlaylistDetail.value = updated
    }

    /**
     * YouTube needs each song's setVideoId to move/remove it. Playlists synced before this was
     * stored don't have them, so they are fetched again once and merged in.
     */
    private suspend fun withSetVideoIds(p: UserPlaylist): UserPlaylist {
        if (!isYouTubePlaylist(p) || p.savedSongs.all { it.setVideoId != null }) return p
        val fresh = YouTubeSyncService.fetchPlaylistSongs(p.id.removePrefix("yt_"))
        if (fresh.isEmpty()) return p
        val remaining = fresh.groupBy { it.id }.mapValues { it.value.toMutableList() }
        val merged = p.copy(savedSongs = p.savedSongs.map { s ->
            if (s.setVideoId != null) s else s.copy(setVideoId = remaining[s.id]?.removeFirstOrNull()?.setVideoId)
        })
        savePlaylistUpdate(merged)
        return merged
    }

    fun renamePlaylist(playlistId: String, newTitle: String) {
        val title = newTitle.trim()
        val playlist = PlaylistStore.getPlaylist(playlistId) ?: return
        if (title.isEmpty() || title == playlist.title || !canEditPlaylist(playlist)) return
        scope.launch {
            if (isYouTubePlaylist(playlist)) {
                val ok = withContext(Dispatchers.IO) { YouTube.renamePlaylist(playlist.id.removePrefix("yt_"), title) }.isSuccess
                if (!ok) {
                    _statusMessage.value = "YouTube Music no permitió renombrar \"${playlist.title}\""
                    return@launch
                }
            }
            savePlaylistUpdate(playlist.copy(title = title))
            _statusMessage.value = "Playlist renombrada a \"$title\""
        }
    }

    /** Moves the song at [from] to position [to] (also on YouTube Music for your own playlists). */
    fun moveSongInPlaylist(playlistId: String, from: Int, to: Int) {
        val original = PlaylistStore.getPlaylist(playlistId) ?: return
        if (!canEditPlaylist(original) || from !in original.savedSongs.indices || to !in original.savedSongs.indices || from == to) return
        val reordered = original.savedSongs.toMutableList().apply { add(to, removeAt(from)) }
        // Optimistic: show the new order right away, undo if YouTube refuses.
        savePlaylistUpdate(original.copy(savedSongs = reordered))
        if (!isYouTubePlaylist(original)) return
        scope.launch {
            val withIds = withSetVideoIds(original)
            val songs = withIds.savedSongs.toMutableList().apply { add(to, removeAt(from)) }
            val moved = songs[to].setVideoId
            val successor = songs.getOrNull(to + 1)?.setVideoId
            val ok = moved != null && withContext(Dispatchers.IO) {
                YouTube.moveSongPlaylist(withIds.id.removePrefix("yt_"), moved, successor)
            }.isSuccess
            if (ok) {
                savePlaylistUpdate(withIds.copy(savedSongs = songs))
            } else {
                savePlaylistUpdate(withIds)
                _statusMessage.value = "No se pudo cambiar el orden en YouTube Music"
            }
        }
    }

    /** Removes a song from a playlist; for YouTube Music playlists also from your account. */
    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val playlist = PlaylistStore.getPlaylist(playlistId) ?: return
        if (!canRemoveFromPlaylist(playlist)) return
        val song = playlist.savedSongs.firstOrNull { it.id == songId } ?: return
        if (!isYouTubePlaylist(playlist)) {
            savePlaylistUpdate(playlist.copy(savedSongs = playlist.savedSongs.filterNot { it.id == songId }))
            _statusMessage.value = "Canción quitada de \"${playlist.title}\""
            return
        }
        scope.launch {
            val ok = if (playlist.id == "yt_LM") {
                // "Me gusta" is not a normal playlist: removing a song means un-liking it.
                withContext(Dispatchers.IO) { YouTube.likeVideo(songId, false) }.isSuccess
            } else {
                val withIds = withSetVideoIds(playlist)
                val setVideoId = withIds.savedSongs.firstOrNull { it.id == songId }?.setVideoId
                setVideoId != null && withContext(Dispatchers.IO) {
                    YouTube.removeFromPlaylist(playlist.id.removePrefix("yt_"), songId, setVideoId)
                }.isSuccess
            }
            if (ok) {
                val latest = PlaylistStore.getPlaylist(playlistId) ?: playlist
                savePlaylistUpdate(latest.copy(savedSongs = latest.savedSongs.filterNot { it.id == songId }))
                _statusMessage.value = "\"${song.title}\" quitada de \"${playlist.title}\""
            } else {
                _statusMessage.value = "YouTube Music no permitió quitar \"${song.title}\""
            }
        }
    }

    // --- Prioridad 2: Radio Infinita (Automix) ---
    fun toggleAutomix() {
        _isAutomixEnabled.value = !_isAutomixEnabled.value
    }

    fun startRadio(song: SongItem) {
        scope.launch {
            _statusMessage.value = "Iniciando radio de ${song.title}..."
            try {
                val res = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = song.id))
                }
                if (res.isSuccess) {
                    val radioSongs = (listOf(song) + res.getOrNull()?.items.orEmpty().applyContentFilters()).distinctBy { it.id }
                    playSong(song, radioSongs)
                    _statusMessage.value = "Radio iniciada (${radioSongs.size} canciones)"
                } else {
                    playSong(song)
                }
            } catch (e: Exception) {
                playSong(song)
            }
        }
    }

    // --- Prioridad 2: Ecualizador ---
    fun openEqualizerModal() {
        _isEqualizerModalOpen.value = true
    }

    fun closeEqualizerModal() {
        _isEqualizerModalOpen.value = false
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val updated = _equalizerSettings.value.copy(enabled = enabled)
        _equalizerSettings.value = updated
        EqualizerStore.saveSettings(updated)
        playerEngine.setEqualizerEnabled(enabled)
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        val updated = _equalizerSettings.value.copy(
            enabled = true,
            currentPresetName = preset.name,
            bandGains = preset.gains
        )
        _equalizerSettings.value = updated
        EqualizerStore.saveSettings(updated)
        playerEngine.setEqualizerEnabled(true)
        playerEngine.setEqualizerBands(preset.gains)
    }

    fun setEqualizerBandGain(bandIndex: Int, gain: Float) {
        val currentGains = _equalizerSettings.value.bandGains.toMutableList()
        if (bandIndex in currentGains.indices) {
            currentGains[bandIndex] = gain
            val updated = _equalizerSettings.value.copy(
                currentPresetName = "Personalizado",
                bandGains = currentGains
            )
            _equalizerSettings.value = updated
            EqualizerStore.saveSettings(updated)
            playerEngine.setEqualizerBands(currentGains)
        }
    }

    // --- Prioridad 2: Crossfade ---
    fun setCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 15)
        _crossfadeSeconds.value = clamped
        playerEngine.setCrossfadeDuration(clamped)
    }

    // --- Prioridad 2: Páginas de Artistas y Álbumes ---
    fun openArtist(browseId: String) {
        // In podcast episodes the "artist" link is the show itself.
        if (com.music.innertube.pages.PodcastPage.isShowBrowseId(browseId)) {
            openPodcast(browseId)
            return
        }
        scope.launch {
            _isArtistLoading.value = true
            _selectedArtistDetail.value = null
            try {
                val res = withContext(Dispatchers.IO) {
                    YouTube.artist(browseId)
                }
                if (res.isSuccess) {
                    _selectedArtistDetail.value = res.getOrNull()
                } else {
                    _statusMessage.value = "Error al cargar artista: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isArtistLoading.value = false
            }
        }
    }

    fun closeArtistModal() {
        _selectedArtistDetail.value = null
    }

    fun openAlbum(browseId: String) {
        scope.launch {
            _isAlbumLoading.value = true
            _selectedAlbumDetail.value = null
            try {
                val res = withContext(Dispatchers.IO) {
                    YouTube.album(browseId)
                }
                if (res.isSuccess) {
                    _selectedAlbumDetail.value = res.getOrNull()
                } else {
                    _statusMessage.value = "Error al cargar álbum: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isAlbumLoading.value = false
            }
        }
    }

    fun closeAlbumModal() {
        _selectedAlbumDetail.value = null
    }

    // --- Prioridad 3: Descargas / Modo Offline ---
    fun downloadSong(song: SongItem) {
        scope.launch {
            _statusMessage.value = "Descargando ${song.title}..."
            DesktopDownloadManager.downloadSong(
                song = song,
                onComplete = { ok, err ->
                    _downloadedSongs.value = DownloadStore.getDownloadedSongs()
                    if (ok) {
                        _statusMessage.value = "Descarga completada: ${song.title}"
                    } else {
                        _statusMessage.value = "Error al descargar: $err"
                    }
                }
            )
        }
    }

    fun deleteDownload(songId: String) {
        DesktopDownloadManager.deleteSong(songId)
        _downloadedSongs.value = DownloadStore.getDownloadedSongs()
        _statusMessage.value = "Canción eliminada del almacenamiento local"
    }

    fun isDownloaded(songId: String): Boolean {
        return DownloadStore.isDownloaded(songId)
    }

    fun downloadPlaylist(playlist: UserPlaylist) {
        if (playlist.songs.isEmpty()) return
        val playlistId = playlist.id

        if (_playlistDownloading.value[playlistId] == true) return

        scope.launch {
            _playlistDownloading.update { it + (playlistId to true) }
            val songsToDownload = playlist.songs.filter { !DownloadStore.isDownloaded(it.id) }
            val total = songsToDownload.size

            if (total == 0) {
                _statusMessage.value = "Todas las canciones de «${playlist.title}» ya están descargadas"
                _playlistDownloading.update { it - playlistId }
                return@launch
            }

            _statusMessage.value = "Descargando lista «${playlist.title}» ($total pendientes)..."

            var downloadedCount = 0
            var failedCount = 0

            for ((index, song) in songsToDownload.withIndex()) {
                if (_playlistDownloading.value[playlistId] != true) {
                    break
                }

                _statusMessage.value = "Descargando (${index + 1}/$total): ${song.title}"

                val success = suspendCancellableCoroutine<Boolean> { cont ->
                    DesktopDownloadManager.downloadSong(
                        song = song,
                        onComplete = { ok, _ ->
                            _downloadedSongs.value = DownloadStore.getDownloadedSongs()
                            if (cont.isActive) cont.resume(ok)
                        }
                    )
                }

                if (success) {
                    downloadedCount++
                } else {
                    failedCount++
                }
                delay(200)
            }

            _downloadedSongs.value = DownloadStore.getDownloadedSongs()
            _playlistDownloading.update { it - playlistId }

            if (failedCount == 0) {
                _statusMessage.value = "¡Lista «${playlist.title}» descargada con éxito! ($downloadedCount canciones)"
            } else {
                _statusMessage.value = "Descarga de «${playlist.title}»: $downloadedCount exitosas, $failedCount omitidas"
            }
        }
    }

    fun cancelPlaylistDownload(playlistId: String) {
        _playlistDownloading.update { it - playlistId }
        _statusMessage.value = "Descarga de lista cancelada"
    }

    fun setDownloadsDirectory(dir: File) {
        val success = DownloadStore.setDownloadsDirectory(dir)
        if (success) {
            _downloadsDirectory.value = dir
            _downloadedSongs.value = DownloadStore.getDownloadedSongs()
            _statusMessage.value = "Carpeta de descargas actualizada: ${dir.absolutePath}"
        }
    }

    fun resetDownloadsDirectory() {
        val defaultDir = File(System.getProperty("user.home"), ".echomusic/downloads")
        setDownloadsDirectory(defaultDir)
    }

    // --- Prioridad 3: Estadísticas / Echo Wrapped ---
    fun openStatsModal() {
        _isStatsModalOpen.value = true
    }

    fun closeStatsModal() {
        _isStatsModalOpen.value = false
    }

    // --- Prioridad 3: Resplandor Ambiental (Ambient Glow) ---
    fun toggleAmbientGlow() {
        _isAmbientGlowEnabled.value = !_isAmbientGlowEnabled.value
    }

    // --- Prioridad 3: Temporizador de Apagado (Sleep Timer) ---
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val totalSec = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSec
        pauseOnTrackEnd = false
        _statusMessage.value = "Temporizador configurado para $minutes minutos"
        sleepTimerJob = scope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining
            }
            playerEngine.pause()
            _sleepTimerRemainingSeconds.value = null
            _statusMessage.value = "Temporizador de apagado completado"
        }
    }

    fun setSleepTimerEndOfSong() {
        cancelSleepTimer()
        pauseOnTrackEnd = true
        _sleepTimerRemainingSeconds.value = -1L
        _statusMessage.value = "Apagado programado al terminar la canción actual"
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseOnTrackEnd = false
        _sleepTimerRemainingSeconds.value = null
        _statusMessage.value = "Temporizador de apagado cancelado"
    }

    // Runs after every property above is initialized (init blocks run in declaration order).
    // --- Spotify (tu biblioteca y sus Canvas) ---
    private val _spotifyProfile = MutableStateFlow(echo.music.desktop.spotify.SpotifyAccount.profile())
    val spotifyProfile: StateFlow<echo.music.desktop.spotify.SpotifyProfile?> = _spotifyProfile.asStateFlow()
    private val _spotifyPlaylists = MutableStateFlow<List<echo.music.iad1tya.spotify.models.SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<echo.music.iad1tya.spotify.models.SpotifyPlaylist>> = _spotifyPlaylists.asStateFlow()
    private val _spotifyLikedCount = MutableStateFlow(0)
    val spotifyLikedCount: StateFlow<Int> = _spotifyLikedCount.asStateFlow()
    private val _spotifyLoading = MutableStateFlow(false)
    val spotifyLoading: StateFlow<Boolean> = _spotifyLoading.asStateFlow()
    /** Login progress or the last error, shown in the Spotify tab. */
    private val _spotifyStatus = MutableStateFlow<String?>(null)
    val spotifyStatus: StateFlow<String?> = _spotifyStatus.asStateFlow()
    private val _spotifySigningIn = MutableStateFlow(false)
    val spotifySigningIn: StateFlow<Boolean> = _spotifySigningIn.asStateFlow()

    fun signInSpotify() {
        if (_spotifySigningIn.value) return
        _spotifySigningIn.value = true
        _spotifyStatus.value = "Abriendo Spotify en una ventana de Edge…"
        echo.music.desktop.spotify.SpotifyAccount.signIn(
            onStatus = { _spotifyStatus.value = it }
        ) { result ->
            _spotifySigningIn.value = false
            result.onSuccess { profile ->
                _spotifyProfile.value = profile
                _spotifyStatus.value = null
                _statusMessage.value = "Conectado a Spotify como ${profile.name}"
                loadSpotifyLibrary()
            }.onFailure { _spotifyStatus.value = it.message ?: "No se pudo iniciar sesión" }
        }
    }

    fun cancelSpotifySignIn() {
        echo.music.desktop.spotify.SpotifyAccount.cancelSignIn()
        _spotifySigningIn.value = false
        _spotifyStatus.value = null
    }

    fun signOutSpotify() {
        echo.music.desktop.spotify.SpotifyAccount.signOut()
        _spotifyProfile.value = null
        _spotifyPlaylists.value = emptyList()
        _spotifyLikedCount.value = 0
        _statusMessage.value = "Sesión de Spotify cerrada"
    }

    fun loadSpotifyLibrary() {
        if (_spotifyProfile.value == null || _spotifyLoading.value) return
        _spotifyLoading.value = true
        scope.launch {
            echo.music.desktop.spotify.SpotifyLibrary.playlists()
                .onSuccess { _spotifyPlaylists.value = it; _spotifyStatus.value = null }
                .onFailure {
                    _spotifyStatus.value = it.message ?: "No se pudo leer tu biblioteca de Spotify"
                    if (!echo.music.desktop.spotify.SpotifyAccount.isSignedIn) _spotifyProfile.value = null
                }
            _spotifyLikedCount.value = runCatching { echo.music.desktop.spotify.SpotifyLibrary.likedCount() }.getOrDefault(0)
            _spotifyLoading.value = false
        }
    }

    /** The Spotify playlist saved in this PC's library (after opening it once), if any. */
    fun importedSpotifyPlaylist(spotifyId: String): UserPlaylist? = PlaylistStore.getPlaylist("spotify_$spotifyId")

    /**
     * Opens a Spotify playlist (or "Tus me gusta"): reads its songs, finds each one on YouTube Music
     * and keeps it in the library as a playlist of this PC.
     */
    fun openSpotifyPlaylist(spotifyId: String, title: String, owner: String?, imageUrl: String?, refresh: Boolean = false) {
        if (!refresh) importedSpotifyPlaylist(spotifyId)?.let { openPlaylistDetail(it); return }
        runAi("Spotify · $title") { log ->
            val tracks = echo.music.desktop.spotify.SpotifyLibrary.tracks(spotifyId) { n, total -> setTaskLog("Leyendo canciones de Spotify: $n de $total") }
            if (tracks.isEmpty()) error("Esta lista no tiene canciones")
            log("Buscando ${tracks.size} canciones en YouTube Music…")
            val songs = echo.music.desktop.spotify.SpotifyLibrary.resolve(tracks, _prefs.value.hideExplicit) { done, total ->
                setTaskLog("Buscando en YouTube Music: $done de $total")
            }
            if (songs.isEmpty()) error("No se encontró ninguna de las canciones en YouTube Music")
            val playlist = UserPlaylist.fromSongs(
                id = "spotify_$spotifyId",
                title = title,
                author = "Spotify" + (owner?.let { " • $it" } ?: ""),
                thumbnailUrl = imageUrl ?: songs.firstOrNull()?.thumbnail,
                sourceType = PlaylistSourceType.SPOTIFY,
                songs = songs
            )
            PlaylistStore.savePlaylist(playlist)
            _userPlaylists.value = PlaylistStore.getPlaylists()
            openPlaylistDetail(playlist)
            "\"$title\": ${songs.size} de ${tracks.size} canciones encontradas en YouTube Music"
        }
    }

    private fun setTaskLog(line: String) {
        _aiTask.value = _aiTask.value?.copy(log = line)
    }

    // --- Modo video (videoclip oficial) ---
    private val _videoMode = MutableStateFlow(false)
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()
    private val _video = MutableStateFlow(VideoState())
    val video: StateFlow<VideoState> = _video.asStateFlow()
    private var videoJob: kotlinx.coroutines.Job? = null

    fun toggleVideoMode() {
        _videoMode.value = !_videoMode.value
        if (_videoMode.value) {
            prepareVideoForCurrent()
        } else {
            // The sound keeps going from where it is; only the picture goes away.
            videoJob?.cancel()
            _video.value = VideoState()
        }
    }

    private fun prepareVideoForCurrent() {
        val song = _currentSong.value ?: return
        if (LocalMusicLibrary.isLocal(song.id)) {
            _video.value = VideoState(song.id, message = "Las canciones de este PC no tienen video")
            return
        }
        videoJob?.cancel()
        _video.value = VideoState(song.id, loading = true)
        videoJob = scope.launch {
            val file = echo.music.desktop.video.MusicVideoService.prepare(song) { p -> _video.update { if (it.songId == song.id) it.copy(progress = p) else it } }
            if (!_videoMode.value || _currentSong.value?.id != song.id) return@launch
            if (file == null) {
                _video.value = VideoState(
                    song.id,
                    message = if (echo.music.desktop.video.MusicVideoService.isUnavailable(song)) "YouTube no deja cargar el video de esta canción"
                    else "Esta canción no tiene video oficial"
                )
                return@launch
            }
            // Play the video's own sound from the same file, so picture and sound always match.
            if (playingVideoFile != file) {
                playerEngine.switchSource(file.toURI().toString(), playbackState.value.currentPositionMs)
                playingVideoFile = file
            }
            _video.value = VideoState(song.id, file = file)
            prefetchNextVideo()
        }
    }

    /** The video file the audio engine is playing right now (its sound), if any. */
    private var playingVideoFile: java.io.File? = null

    /** The next song's video, ready before it starts. */
    private fun prefetchNextVideo() {
        val next = _queue.value.getOrNull(_currentIndex.value + 1) ?: return
        if (LocalMusicLibrary.isLocal(next.id)) return
        scope.launch(Dispatchers.IO) { echo.music.desktop.video.MusicVideoService.prepare(next) }
    }

    private fun startVideoFollower() = scope.launch {
        currentSong.collect { song ->
            if (!_videoMode.value || song == null) return@collect
            val cached = echo.music.desktop.video.MusicVideoService.cachedFile(song)
            playingVideoFile = cached
            if (cached != null) {
                // It started straight from the downloaded video.
                _video.value = VideoState(song.id, file = cached)
                prefetchNextVideo()
            } else {
                prepareVideoForCurrent()
            }
        }
    }

    // --- Music Flow (descubrir deslizando) ---
    private val flowSession = echo.music.desktop.recommend.FlowSession(
        echo.music.desktop.recommend.YouTubeFlowSource(
            echo.music.desktop.recommend.FlowInputs(
                history = { flowHistory() },
                liked = { flowLiked() },
                quickPicks = { _homeSongs.value },
                downloaded = { _downloadedSongs.value.map { it.toSongItem() } },
                hideRecent = { _prefs.value.flowHideRecent },
                contentFilter = { it.applyContentFilters() },
                neverRecommend = { ContentBlockStore.current.songs.map { echo.music.desktop.recommend.trackKeyOf(it.title, it.artist) }.toSet() }
            )
        )
    )
    val musicFlow: StateFlow<echo.music.desktop.recommend.FlowState> = flowSession.state

    /** What you listened to: the history, or Echo Wrapped's plays when it is paused or empty. */
    private fun flowHistory(): List<SongItem> = _history.value.ifEmpty { StatsStore.recentSongs() }

    /** What you like: favorites plus the songs of your library playlists (YouTube "Me gusta" first). */
    private fun flowLiked(): List<SongItem> {
        val playlists = _userPlaylists.value.sortedByDescending { it.id == "yt_LM" }
        return (_favorites.value + playlists.flatMap { it.songs }).distinctBy { it.id }
    }
    private val _isMusicFlowOpen = MutableStateFlow(false)
    val isMusicFlowOpen: StateFlow<Boolean> = _isMusicFlowOpen.asStateFlow()

    /** Song ids of the queue Music Flow put in place (it only extends its own queue). */
    private var flowQueueIds: List<String>? = null
    /** What was playing before Music Flow took over, to give it back. */
    private var queueBeforeFlow: Triple<List<SongItem>, Int, Long>? = null
    private val _canRestoreQueue = MutableStateFlow(false)
    val canRestoreQueue: StateFlow<Boolean> = _canRestoreQueue.asStateFlow()

    private fun flowOwnsQueue(): Boolean = flowQueueIds?.let { ids -> _queue.value.map { it.id } == ids } == true

    fun openMusicFlow() {
        _isMusicFlowOpen.value = true
        scope.launch {
            flowSession.start(_currentSong.value?.let { echo.music.desktop.recommend.FlowItem(it, echo.music.desktop.recommend.FlowSource.NOW_PLAYING) })
            if (flowOwnsQueue()) flowSession.indexOf(_currentSong.value?.id).takeIf { it >= 0 }?.let(flowSession::select)
        }
    }

    fun closeMusicFlow() {
        _isMusicFlowOpen.value = false
    }

    /** "Empezar un nuevo Flow". */
    fun restartMusicFlow() {
        scope.launch { flowSession.reset(_currentSong.value?.let { echo.music.desktop.recommend.FlowItem(it, echo.music.desktop.recommend.FlowSource.NOW_PLAYING) }) }
    }

    fun retryMusicFlow() = restartMusicFlow()

    /** A page of the Flow became the visible one. */
    fun selectFlowPage(index: Int, play: Boolean = _prefs.value.flowAutoplay) {
        flowSession.select(index)
        val items = flowSession.state.value.items
        val item = items.getOrNull(index) ?: return
        if (items.size - index <= echo.music.desktop.recommend.FlowSession.PREFETCH_THRESHOLD) extendFlow()
        if (!play || _currentSong.value?.id == item.songId) return
        if (flowOwnsQueue()) {
            _queue.value.firstOrNull { it.id == item.songId }?.let { playSong(it, _queue.value) }
            return
        }
        // Music Flow takes the queue over: keep what was there to give it back later.
        if (queueBeforeFlow == null && _queue.value.isNotEmpty()) {
            queueBeforeFlow = Triple(_queue.value, _currentIndex.value, playbackState.value.currentPositionMs)
        }
        val queue = items.map { it.song }
        flowQueueIds = queue.map { it.id }
        _canRestoreQueue.value = queueBeforeFlow != null
        playSong(item.song, queue)
    }

    /** Plays/pauses the visible song (starting it if another one is playing). */
    fun toggleFlowPlayback(index: Int) {
        val item = flowSession.state.value.items.getOrNull(index) ?: return
        if (_currentSong.value?.id == item.songId) togglePlayPause() else selectFlowPage(index, play = true)
    }

    private var flowExtendJob: kotlinx.coroutines.Job? = null

    /** Loads more songs and, if the Flow is playing, adds them to its queue. */
    private fun extendFlow() {
        if (flowExtendJob?.isActive == true) return
        flowExtendJob = scope.launch {
            val added = flowSession.loadMore()
            if (added.isNotEmpty() && flowOwnsQueue()) {
                val known = _queue.value.map { it.id }.toSet()
                _queue.value = _queue.value + added.map { it.song }.filterNot { it.id in known }
                flowQueueIds = _queue.value.map { it.id }
            }
        }
    }

    /** Keeps the Flow on the song that is playing, and its queue topped up. */
    private fun startFlowFollower() = scope.launch {
        currentSong.collect { song ->
            if (song == null || !flowOwnsQueue()) return@collect
            val index = flowSession.indexOf(song.id)
            if (index >= 0) flowSession.select(index)
            if (_queue.value.size - _currentIndex.value - 1 <= echo.music.desktop.recommend.FlowSession.PREFETCH_THRESHOLD) extendFlow()
        }
    }

    /** "Restaurar la cola anterior": what was playing before Music Flow, where it was left. */
    fun restoreQueueBeforeFlow() {
        val (queue, index, position) = queueBeforeFlow ?: return
        queueBeforeFlow = null
        flowQueueIds = null
        _canRestoreQueue.value = false
        val song = queue.getOrNull(index) ?: return
        pendingResume = song.id to position
        playSong(song, queue)
        _statusMessage.value = "Cola anterior restaurada"
    }

    /** "No me interesa": out of this Flow (and skipped if it is the one playing). */
    fun flowNotInterested(item: echo.music.desktop.recommend.FlowItem) {
        flowSession.hide(item.songId)
        skipFlowItemIfCurrent(item)
        _statusMessage.value = "Gracias, Flow se ajustará"
    }

    /** "Menos como esto": that artist goes to the back of what comes next. */
    fun flowLessLikeThis(item: echo.music.desktop.recommend.FlowItem) {
        flowSession.lessLikeThis(item)
        _statusMessage.value = "Gracias, Flow se ajustará"
    }

    /** "No recomendar nunca más": a "No me gusta" for good (also out of radios and Inicio). */
    fun flowNeverRecommend(item: echo.music.desktop.recommend.FlowItem) {
        flowSession.hide(item.songId)
        val wasCurrent = _currentSong.value?.id == item.songId
        dislikeSong(item.song) // already skips it if it is playing
        if (!wasCurrent) _statusMessage.value = "No volverás a ver \"${item.song.title}\""
    }

    private fun skipFlowItemIfCurrent(item: echo.music.desktop.recommend.FlowItem) {
        if (_currentSong.value?.id != item.songId) return
        val items = flowSession.state.value.items
        val next = (flowSession.indexOf(item.songId) + 1).takeIf { it in items.indices } ?: return
        selectFlowPage(next, play = true)
    }

    // --- Playlists con IA (con tu propia clave) ---
    /** Which AI dialog is open: "create", "taste" or "modify:<playlistId>". */
    private val _aiPrompt = MutableStateFlow<String?>(null)
    val aiPrompt: StateFlow<String?> = _aiPrompt.asStateFlow()
    private val _aiTask = MutableStateFlow<AiTask?>(null)
    val aiTask: StateFlow<AiTask?> = _aiTask.asStateFlow()

    private fun aiAccount() = _prefs.value.let { p ->
        echo.music.desktop.recommend.AiAccount(
            provider = echo.music.desktop.lyrics.TranslationProvider.from(p.translationProvider),
            apiKey = translationApiKey(),
            model = p.translationModel,
            baseUrl = p.translationBaseUrl
        )
    }

    /** Null when AI playlists can be made; otherwise what is missing. */
    fun aiUnavailableReason(): String? = echo.music.desktop.recommend.AiPlaylists.unsupportedReason(aiAccount())

    fun openAiCreate() { _aiPrompt.value = "create" }
    fun openAiModify(playlistId: String) { _aiPrompt.value = "modify:$playlistId" }
    fun closeAiPrompt() { _aiPrompt.value = null }
    fun dismissAiTask() { if (_aiTask.value?.running != true) _aiTask.value = null }

    private fun runAi(title: String, block: suspend (log: suspend (String) -> Unit) -> String) {
        if (_aiTask.value?.running == true) return
        _aiPrompt.value = null
        _aiTask.value = AiTask(title = title, log = "Preparando…", running = true)
        scope.launch {
            val log: suspend (String) -> Unit = { line -> _aiTask.value = _aiTask.value?.copy(log = line) }
            _aiTask.value = runCatching { block(log) }.fold(
                onSuccess = { AiTask(title = title, log = it, running = false) },
                onFailure = { AiTask(title = title, log = "", running = false, error = it.message ?: "Algo salió mal") }
            )
        }
    }

    /** "Crear playlist con IA". */
    fun createAiPlaylist(prompt: String, count: Int) = runAi("Playlist con IA") { log ->
        val result = echo.music.desktop.recommend.AiPlaylists.generate(aiAccount(), prompt, count, log).getOrThrow()
        val playlist = createPlaylist(result.name, result.songs)
        openPlaylistDetail(playlist)
        "Lista \"${playlist.title}\" creada con ${result.songs.size} canciones"
    }

    /** "De tus gustos": recommendations from what you listen to most and like. */
    fun createTastePlaylist() = runAi("De tus gustos") { log ->
        val taste = (StatsStore.getSummary().topSongs.map { it.song }.take(20) + flowLiked().shuffled().take(20))
            .distinctBy { it.id }
        val songs = echo.music.desktop.recommend.AiPlaylists.fromTaste(aiAccount(), taste, log).getOrThrow()
        val title = "De tus gustos · Echo Music"
        val existing = PlaylistStore.getPlaylists().firstOrNull { it.title == title && it.sourceType == PlaylistSourceType.LOCAL }
        val playlist = if (existing != null) {
            existing.copy(savedSongs = songs.map { SavedSong.fromSongItem(it) }, thumbnailUrl = songs.firstOrNull()?.thumbnail)
                .also { savePlaylistUpdate(it) }
        } else {
            createPlaylist(title, songs)
        }
        openPlaylistDetail(playlist)
        "${songs.size} canciones nuevas según tus gustos"
    }

    /** "Modificar con IA" (playlists of this PC). */
    fun modifyPlaylistWithAi(playlistId: String, instruction: String) = runAi("Modificar con IA") { log ->
        val playlist = PlaylistStore.getPlaylist(playlistId) ?: error("La playlist ya no existe")
        val songs = playlist.songs
        val change = echo.music.desktop.recommend.AiPlaylists.modify(aiAccount(), songs, instruction, log).getOrThrow()
        val kept = songs.filterIndexed { i, _ -> i !in change.removeIndexes }
        val updated = playlist.copy(savedSongs = (kept + change.additions).map { SavedSong.fromSongItem(it) })
        savePlaylistUpdate(updated)
        _selectedPlaylistDetail.value = PlaylistStore.getPlaylist(playlistId)
        "Quitadas ${change.removeIndexes.size}, añadidas ${change.additions.size}"
    }

    // --- Audífonos (Bluetooth o con cable) ---
    /** Output whose disconnection paused the music, and when. */
    private var pausedForOutput: String? = null
    private var pausedForOutputAt = 0L

    private fun startHeadphoneWatch() {
        if (!com.sun.jna.Platform.isWindows()) return
        val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        scope.launch(Dispatchers.IO) {
            echo.music.desktop.system.AudioDeviceNotifications.register { wake.trySend(Unit) }
            val tracker = echo.music.desktop.system.OutputChangeTracker { echo.music.desktop.system.AudioDevices.stateOf(it) }
            while (true) {
                val current = echo.music.desktop.system.AudioDevices.defaultOutput()
                val event = tracker.update(current)
                if (event != null) withContext(Dispatchers.Main) { onOutputEvent(event, current) }
                // Instant on Windows' notification, otherwise a check every few seconds.
                kotlinx.coroutines.withTimeoutOrNull(3000) { wake.receive() }
                delay(150) // let Windows finish switching the default output
            }
        }
    }

    private fun onOutputEvent(event: echo.music.desktop.system.OutputEvent, current: echo.music.desktop.system.AudioOutput?) {
        val prefs = _prefs.value
        if (event is echo.music.desktop.system.OutputEvent.PersonalDisconnected &&
            prefs.pauseOnHeadphonesDisconnect && playbackState.value.isPlaying
        ) {
            playerEngine.pause()
            pausedForOutput = event.device.id
            pausedForOutputAt = System.currentTimeMillis()
            _statusMessage.value = "Se desconectaron los audífonos (${event.device.name}): música en pausa"
            return
        }
        val waitingFor = pausedForOutput ?: return
        if (current?.id == waitingFor && current.isActive) {
            pausedForOutput = null
            val recent = System.currentTimeMillis() - pausedForOutputAt < 30 * 60 * 1000L
            if (prefs.resumeOnHeadphonesReconnect && recent && !playbackState.value.isPlaying) {
                playerEngine.resume()
                _statusMessage.value = "Audífonos conectados (${current.name}): continuando"
            }
        }
    }

    // --- Actualizaciones ---
    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private val _updateBanner = MutableStateFlow<echo.music.desktop.system.UpdateInfo?>(null)
    /** New version announced in the main window (until dismissed). */
    val updateBanner: StateFlow<echo.music.desktop.system.UpdateInfo?> = _updateBanner.asStateFlow()

    /** Checks GitHub for a newer release. [manual] also announces versions the user skipped. */
    fun checkForUpdates(manual: Boolean = true) {
        if (_update.value.checking) return
        _update.value = _update.value.copy(checking = true, message = null)
        scope.launch {
            val repo = echo.music.desktop.system.UpdateChecker.DEFAULT_REPO
            val result = withContext(Dispatchers.IO) { echo.music.desktop.system.UpdateChecker.check(repo) }
            updatePreferences { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            val lastFailure = echo.music.desktop.system.UpdateInstaller.lastFailure()
            _update.value = when (result) {
                is echo.music.desktop.system.UpdateResult.Available ->
                    UpdateState(available = result.info, message = "Echo Music ${result.info.version} está disponible.", lastFailure = lastFailure)
                echo.music.desktop.system.UpdateResult.UpToDate ->
                    UpdateState(message = "Tienes la última versión (${echo.music.desktop.system.AppInfo.version}).", lastFailure = lastFailure)
                is echo.music.desktop.system.UpdateResult.Failed -> UpdateState(message = result.message, lastFailure = lastFailure)
            }
            if (result is echo.music.desktop.system.UpdateResult.Available &&
                (manual || result.info.version != _prefs.value.skippedUpdateVersion)
            ) {
                _updateBanner.value = result.info
                if (_prefs.value.autoInstallUpdates) downloadUpdate(result.info, manual)
            }
        }
    }

    /** Main window hidden in the tray (or started minimized): a ready update may install then. */
    @Volatile private var windowHiddenSince: Long? = null

    fun setWindowHidden(hidden: Boolean) {
        windowHiddenSince = if (hidden) windowHiddenSince ?: System.currentTimeMillis() else null
    }

    /**
     * The installed app looks for updates shortly after it opens and then every few hours (it
     * usually stays open in the tray for days). A downloaded update installs when Echo Music
     * closes, or by itself once the window has been in the tray for a while with nothing playing.
     * Copies started from Gradle never check, so they do not touch the installed app's state.
     */
    private fun startUpdateWatch() {
        if (echo.music.desktop.system.AppInfo.launcherPath == null) return
        scope.launch {
            delay(20_000)
            while (true) {
                if (_prefs.value.checkUpdates && _update.value.readyVersion == null) checkForUpdates(manual = false)
                delay(3 * 60 * 60 * 1000L)
            }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                val hiddenSince = windowHiddenSince ?: continue
                if (_update.value.readyVersion == null || !_prefs.value.autoInstallUpdates) continue
                if (playbackState.value.isPlaying || System.currentTimeMillis() - hiddenSince < 5 * 60_000L) continue
                restartToUpdate()
            }
        }
    }

    /** True when this copy can install updates by itself (installed for this user, without admin). */
    val canAutoInstallUpdates: Boolean get() = echo.music.desktop.system.UpdateInstaller.canAutoInstall

    private fun downloadUpdate(info: echo.music.desktop.system.UpdateInfo, manual: Boolean) {
        val pkg = info.installer ?: return
        if (!echo.music.desktop.system.UpdateInstaller.canAutoInstall || _update.value.downloading != null) return
        // A version whose silent install already failed is only tried again when asked ("Buscar ahora"):
        // otherwise it would be downloaded and retried over and over while Echo Music sits in the tray.
        val failure = echo.music.desktop.system.UpdateInstaller.lastFailure()
        if (!manual && failure?.version == info.version) {
            _update.value = _update.value.copy(
                message = "Echo Music ${info.version} no se pudo instalar sola. Pulsa \"Buscar ahora\" para reintentar o descárgala."
            )
            return
        }
        if (echo.music.desktop.system.UpdateInstaller.pendingVersion() == info.version && echo.music.desktop.system.UpdateInstaller.pendingInstaller() != null) {
            _update.value = _update.value.copy(readyVersion = info.version, message = "Echo Music ${info.version} se instalará al cerrar.")
            return
        }
        _update.value = _update.value.copy(downloading = 0f)
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    echo.music.desktop.system.UpdateInstaller.download(info.version, pkg) { p -> _update.value = _update.value.copy(downloading = p) }
                }.getOrNull()
            }
            _update.value = if (file != null) {
                _update.value.copy(downloading = null, readyVersion = info.version, message = "Echo Music ${info.version} se instalará al cerrar.")
            } else {
                _update.value.copy(downloading = null, message = "No se pudo descargar o verificar la versión ${info.version}.")
            }
        }
    }

    /** Asks the window to close; the update installs and Echo Music opens again by itself. */
    private val _exitRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _exitRequests

    fun restartToUpdate() {
        if (echo.music.desktop.system.UpdateInstaller.pendingInstaller() == null) return
        _exitRequests.tryEmit(Unit)
    }

    /** Opens the folder with the update's install.log and history.log. */
    fun openUpdateFolder() {
        runCatching { java.awt.Desktop.getDesktop().open(echo.music.desktop.system.UpdateInstaller.folder.apply { mkdirs() }) }
    }

    fun openUpdatePage() {
        val url = (_updateBanner.value ?: _update.value.available)?.url ?: return
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }

    /** Hides the announcement; with [skip] that version is not announced again automatically. */
    fun dismissUpdate(skip: Boolean) {
        val info = _updateBanner.value
        if (skip && info != null) updatePreferences { it.copy(skippedUpdateVersion = info.version) }
        _updateBanner.value = null
    }

    // --- Iniciar con Windows ---
    private val _startWithWindows = MutableStateFlow(echo.music.desktop.system.WindowsStartup.isEnabled())
    val startWithWindows: StateFlow<Boolean> = _startWithWindows.asStateFlow()

    fun setStartWithWindows(enabled: Boolean) {
        if (!echo.music.desktop.system.WindowsStartup.setEnabled(enabled, _prefs.value.startMinimized)) {
            _statusMessage.value = "No se pudo cambiar el inicio con Windows"
        }
        _startWithWindows.value = echo.music.desktop.system.WindowsStartup.isEnabled()
    }

    fun setStartMinimized(minimized: Boolean) {
        updatePreferences { it.copy(startMinimized = minimized) }
        echo.music.desktop.system.WindowsStartup.refresh(minimized)
    }

    init {
        applyContentLocale()
        playerEngine.setPlaybackRate(_prefs.value.playbackSpeed)
        echo.music.desktop.system.WindowsStartup.refresh(_prefs.value.startMinimized)
        echo.music.desktop.system.UpdateInstaller.takeLastOutcome()?.let { (version, code) ->
            _statusMessage.value = when (code) {
                0 -> "Echo Music se actualizó a la versión $version"
                3010 -> "La versión $version termina de instalarse al reiniciar Windows"
                else -> "No se pudo instalar la versión $version: ${echo.music.desktop.system.UpdateInstaller.describe(code)}"
            }
        }
        _update.value = _update.value.copy(lastFailure = echo.music.desktop.system.UpdateInstaller.lastFailure())
        startUpdateWatch()
        if (_prefs.value.localMusicFolders.isNotEmpty()) rescanLocalMusic()
        startScrobbleTracking()
        startHeadphoneWatch()
        startFlowFollower()
        startVideoFollower()
        echo.music.desktop.canvas.CanvasService.spotifyCanvas = { title, artist -> echo.music.desktop.spotify.SpotifyLibrary.canvasUrl(title, artist) }
        if (_spotifyProfile.value != null) loadSpotifyLibrary()

        // SponsorBlock: jump over marked parts (each one once per playback).
        scope.launch {
            playbackState.collect { state ->
                val segments = skipSegments
                if (!state.isPlaying || segments.isEmpty() || state.videoId != skipSegmentsVideoId) return@collect
                val segment = segments.firstOrNull {
                    state.currentPositionMs in it.startMs until it.endMs && it.uuid !in skippedSegments
                } ?: return@collect
                skippedSegments += segment.uuid
                // Never jump past the end of the song; let it finish instead.
                if (state.durationMs > 0 && segment.endMs >= state.durationMs - 500) return@collect
                seekToPositionMs(segment.endMs)
                _statusMessage.value = "SponsorBlock: se saltó ${echo.music.desktop.player.SponsorBlockService.label(segment.category)}"
            }
        }

        // Player errors (e.g. a stream that stops working mid-song) -> skip if enabled.
        scope.launch {
            var lastError: Pair<String, String>? = null
            playbackState.collect { state ->
                if (state.isPlaying) consecutiveFailures = 0
                val error = state.errorMessage
                if (error != null && state.videoId.isNotBlank()) {
                    val key = state.videoId to error
                    if (key != lastError) {
                        lastError = key
                        _currentSong.value?.takeIf { it.id == state.videoId }?.let { onPlaybackFailed(it, error) }
                    }
                } else if (error == null) {
                    lastError = null
                }
            }
        }

        // Continue where the user left off (queue + position), without auto-playing.
        restorePlaybackSession()
        echoConnect.start()
        scope.launch {
            while (true) {
                delay(5_000)
                if (playbackState.value.isPlaying) savePlaybackSession()
            }
        }
    }

    fun release() {
        // Synchronous: the app is closing.
        echoConnect.shutdown()
        PlaybackSessionStore.save(_queue.value, _currentIndex.value, currentPositionForSession())
        cancelSleepTimer()
        playerEngine.release()
        // A downloaded update installs now, while Echo Music is closed, and opens it again.
        if (_prefs.value.autoInstallUpdates && echo.music.desktop.system.UpdateInstaller.canAutoInstall) {
            echo.music.desktop.system.UpdateInstaller.pendingInstaller()?.let {
                echo.music.desktop.system.UpdateInstaller.installAfterExit(it, reopenMinimized = windowHiddenSince != null)
            }
        }
    }
}

enum class SearchCategory(val title: String, val filter: YouTube.SearchFilter, val isSongList: Boolean) {
    SONGS("Canciones", YouTube.SearchFilter.FILTER_SONG, true),
    VIDEOS("Videos", YouTube.SearchFilter.FILTER_VIDEO, true),
    ARTISTS("Artistas", YouTube.SearchFilter.FILTER_ARTIST, false),
    ALBUMS("Álbumes", YouTube.SearchFilter.FILTER_ALBUM, false),
    PLAYLISTS("Playlists", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST, false),
    COMMUNITY("Playlists de la comunidad", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST, false),
    PODCASTS("Podcasts", YouTube.SearchFilter.FILTER_PODCAST, false),
    EPISODES("Episodios", YouTube.SearchFilter.FILTER_EPISODE, true)
}

/** Extra lines shown with the lyrics: romanization and translation (null = not shown). */
data class LyricsExtras(
    val romanized: List<String?>? = null,
    val translated: List<String?>? = null,
    val isTranslating: Boolean = false,
    val translationError: String? = null
)

data class SongComment(
    val id: String,
    val author: String,
    val avatar: String?,
    val time: String,
    val text: String,
    val likes: String,
    val replyCount: Int = 0,
    /** Token to load the replies (null when the comment has none). */
    val replyToken: String? = null
)

/** Replies of one comment that the user opened. */
data class ReplyThread(
    val items: List<SongComment> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false
)

data class CommentsState(
    val videoId: String = "",
    val comments: List<SongComment> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    /** Shown when there are no comments (error, local song...). */
    val message: String? = null,
    /** Opened reply threads, by comment id. */
    val replies: Map<String, ReplyThread> = emptyMap()
)

sealed class EchoFindState {
    data object Ready : EchoFindState()
    data class Listening(val progress: Float) : EchoFindState()
    data object Processing : EchoFindState()
    data class Found(val result: com.music.shazamkit.models.RecognitionResult) : EchoFindState()
    data object NoMatch : EchoFindState()
    data class Failed(val message: String) : EchoFindState()
}

data class UpdateState(
    val checking: Boolean = false,
    val available: echo.music.desktop.system.UpdateInfo? = null,
    /** Result of the last check, shown in Settings. */
    val message: String? = null,
    /** Download progress (0..1) of the new version, while it downloads. */
    val downloading: Float? = null,
    /** Version downloaded and verified, waiting for Echo Music to close. */
    val readyVersion: String? = null,
    /** Last silent install that did not work (shown in Settings until one works). */
    val lastFailure: echo.music.desktop.system.UpdateInstaller.Failure? = null
)

/** A running or finished AI playlist job (shown in a small dialog). */
data class AiTask(
    val title: String,
    val log: String,
    val running: Boolean,
    val error: String? = null
)

/** Video mode for the current song: downloading, ready (file) or why there is none. */
data class VideoState(
    val songId: String? = null,
    val loading: Boolean = false,
    val progress: Float? = null,
    val file: java.io.File? = null,
    val message: String? = null
)
