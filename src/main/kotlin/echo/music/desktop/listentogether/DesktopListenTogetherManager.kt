package echo.music.desktop.listentogether

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import echo.music.desktop.state.DesktopAppModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

fun TrackInfo.toSongItem(): SongItem {
    return SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist, id = null)),
        album = album?.let { Album(name = it, id = "") },
        duration = duration.toInt(),
        thumbnail = thumbnail ?: ""
    )
}

fun SongItem.toTrackInfo(): TrackInfo {
    return TrackInfo(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
        album = album?.name,
        duration = (duration ?: 0).toLong(),
        thumbnail = thumbnail
    )
}

class DesktopListenTogetherManager(
    val client: DesktopListenTogetherClient = DesktopListenTogetherClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var appModel: DesktopAppModel? = null

    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val roomState: StateFlow<RoomState?> = client.roomState
    val role: StateFlow<RoomRole> = client.role
    val userId: StateFlow<String?> = client.userId
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = client.pendingJoinRequests
    val chatMessages: StateFlow<List<ChatMessagePayload>> = client.chatMessages
    val rtt: StateFlow<Long> = client.rtt
    val logs: StateFlow<List<String>> = client.logs

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost
    val allowParticipantControl: Boolean get() = roomState.value?.allowParticipantControl == true
    val canControlMusic: Boolean get() = !isInRoom || isHost || allowParticipantControl

    @Volatile
    var isApplyingRemoteSync: Boolean = false
        private set

    private var eventListenerJob: Job? = null

    fun attachAppModel(model: DesktopAppModel) {
        this.appModel = model
        startEventListener()
    }

    private fun startEventListener() {
        eventListenerJob?.cancel()
        eventListenerJob = scope.launch {
            client.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        val model = appModel ?: return
        when (event) {
            is ListenTogetherEvent.RoomCreated -> {
                val currentSong = model.currentSong.value
                if (currentSong != null) {
                    val trackInfo = currentSong.toTrackInfo()
                    client.sendPlaybackAction(
                        PlaybackActions.CHANGE_TRACK,
                        trackId = currentSong.id,
                        trackInfo = trackInfo
                    )
                    if (model.playbackState.value.isPlaying) {
                        val pos = model.playbackState.value.currentPositionMs
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                    }
                }
            }

            is ListenTogetherEvent.UserJoined -> {
                if (isHost) {
                    val currentSong = model.currentSong.value
                    if (currentSong != null) {
                        val trackInfo = currentSong.toTrackInfo()
                        val pos = model.playbackState.value.currentPositionMs
                        client.sendPlaybackAction(
                            PlaybackActions.CHANGE_TRACK,
                            trackId = currentSong.id,
                            trackInfo = trackInfo,
                            position = pos
                        )
                        if (model.playbackState.value.isPlaying) {
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                        }
                    }
                }
            }

            is ListenTogetherEvent.JoinApproved -> {
                applyRoomState(event.state)
            }

            is ListenTogetherEvent.PlaybackSync -> {
                applyPlaybackAction(event.action)
            }

            is ListenTogetherEvent.SyncStateReceived -> {
                applySyncState(event.state)
            }

            is ListenTogetherEvent.Reconnected -> {
                if (!event.isHost) {
                    applyRoomState(event.state)
                }
            }

            else -> {}
        }
    }

    private fun applyRoomState(state: RoomState) {
        val model = appModel ?: return
        val track = state.currentTrack ?: return
        val current = model.currentSong.value

        isApplyingRemoteSync = true
        scope.launch {
            try {
                if (current?.id != track.id) {
                    val songItem = track.toSongItem()
                    model.playSong(songItem)
                    delay(800)
                }

                if (state.position > 0) {
                    model.seekToPositionMs(state.position)
                }

                if (state.isPlaying && !model.playbackState.value.isPlaying) {
                    model.togglePlayPause()
                } else if (!state.isPlaying && model.playbackState.value.isPlaying) {
                    model.togglePlayPause()
                }
            } finally {
                delay(300)
                isApplyingRemoteSync = false
            }
        }
    }

    private fun applySyncState(state: SyncStatePayload) {
        val model = appModel ?: return
        val track = state.currentTrack ?: return
        val current = model.currentSong.value

        isApplyingRemoteSync = true
        scope.launch {
            try {
                if (current?.id != track.id) {
                    model.playSong(track.toSongItem())
                    delay(800)
                }
                if (abs(model.playbackState.value.currentPositionMs - state.position) > 2000L) {
                    model.seekToPositionMs(state.position)
                }
                if (state.isPlaying && !model.playbackState.value.isPlaying) {
                    model.togglePlayPause()
                } else if (!state.isPlaying && model.playbackState.value.isPlaying) {
                    model.togglePlayPause()
                }
            } finally {
                delay(300)
                isApplyingRemoteSync = false
            }
        }
    }

    private fun applyPlaybackAction(action: PlaybackActionPayload) {
        val model = appModel ?: return
        isApplyingRemoteSync = true
        scope.launch {
            try {
                when (action.action) {
                    PlaybackActions.CHANGE_TRACK -> {
                        val track = action.trackInfo
                        if (track != null) {
                            val current = model.currentSong.value
                            if (current?.id != track.id) {
                                model.playSong(track.toSongItem())
                            }
                        }
                    }

                    PlaybackActions.PLAY -> {
                        val pos = action.position ?: 0L
                        val currentMs = model.playbackState.value.currentPositionMs
                        if (abs(currentMs - pos) > 2500L) {
                            model.seekToPositionMs(pos)
                        }
                        if (!model.playbackState.value.isPlaying) {
                            model.togglePlayPause()
                        }
                    }

                    PlaybackActions.PAUSE -> {
                        val pos = action.position ?: 0L
                        if (model.playbackState.value.isPlaying) {
                            model.togglePlayPause()
                        }
                        val currentMs = model.playbackState.value.currentPositionMs
                        if (abs(currentMs - pos) > 2000L) {
                            model.seekToPositionMs(pos)
                        }
                    }

                    PlaybackActions.SEEK -> {
                        val pos = action.position ?: 0L
                        val currentMs = model.playbackState.value.currentPositionMs
                        if (abs(currentMs - pos) > 1500L) {
                            model.seekToPositionMs(pos)
                        }
                    }

                    PlaybackActions.SKIP_NEXT -> {
                        model.playNext()
                    }

                    PlaybackActions.SKIP_PREV -> {
                        model.playPrevious(allowRestart = false)
                    }
                }
            } finally {
                delay(400)
                isApplyingRemoteSync = false
            }
        }
    }

    // Host Outgoing Event Dispatchers
    fun broadcastTrackChange(song: SongItem) {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        val trackInfo = song.toTrackInfo()
        client.sendPlaybackAction(
            PlaybackActions.CHANGE_TRACK,
            trackId = song.id,
            trackInfo = trackInfo
        )
    }

    fun broadcastPlay(positionMs: Long) {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        client.sendPlaybackAction(PlaybackActions.PLAY, position = positionMs)
    }

    fun broadcastPause(positionMs: Long) {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        client.sendPlaybackAction(PlaybackActions.PAUSE, position = positionMs)
    }

    fun broadcastSeek(positionMs: Long) {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        client.sendPlaybackAction(PlaybackActions.SEEK, position = positionMs)
    }

    fun broadcastSkipNext() {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        client.sendPlaybackAction(PlaybackActions.SKIP_NEXT)
    }

    fun broadcastSkipPrevious() {
        if (!isInRoom || !canControlMusic || isApplyingRemoteSync) return
        client.sendPlaybackAction(PlaybackActions.SKIP_PREV)
    }
}
