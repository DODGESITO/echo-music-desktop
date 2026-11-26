package echo.music.desktop.listentogether

import echo.music.desktop.state.PreferencesStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

sealed class PendingAction {
    data class CreateRoom(val username: String) : PendingAction()
    data class JoinRoom(val roomCode: String, val username: String) : PendingAction()
}

class DesktopListenTogetherClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val PING_INTERVAL_MS = 25000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val ALLOWED_USER_AGENT = "com.nevar.nzik"
    }

    private val codec = MessageCodec(MessageFormat.PROTOBUF, compressionEnabled = true)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    private var pingJob: Job? = null
    private var pingSentTime: Long = 0L
    private var reconnectAttempts = 0

    @Volatile
    var sessionToken: String? = null
        private set

    @Volatile
    var currentUsername: String = PreferencesStore.current.listenTogetherUsername.ifBlank {
        System.getProperty("user.name") ?: "Desktop User"
    }
        private set

    @Volatile
    var currentRoomCode: String? = null
        private set

    @Volatile
    private var pendingAction: PendingAction? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _role = MutableStateFlow(RoomRole.NONE)
    val role: StateFlow<RoomRole> = _role.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessagePayload>> = _chatMessages.asStateFlow()

    private val _rtt = MutableStateFlow(0L)
    val rtt: StateFlow<Long> = _rtt.asStateFlow()

    private val _events = MutableSharedFlow<ListenTogetherEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val isInRoom: Boolean get() = _roomState.value != null
    val isHost: Boolean get() = _role.value == RoomRole.HOST

    private fun log(message: String) {
        println("[ListenTogether] $message")
        _logs.value = (_logs.value + message).takeLast(100)
    }

    @Volatile
    private var currentUrl: String = ""

    fun connect(url: String = PreferencesStore.current.listenTogetherServerUrl, force: Boolean = false) {
        if (!force && currentUrl == url && (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING)) {
            log("Ya conectado o conectando a $url")
            executePendingAction()
            return
        }

        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            disconnect()
        }

        currentUrl = url
        _connectionState.value = ConnectionState.CONNECTING
        log("Conectando al servidor: $url (User-Agent: $ALLOWED_USER_AGENT)...")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ALLOWED_USER_AGENT)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@DesktopListenTogetherClient.webSocket = webSocket
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                startPingJob()
                log("Conexión WebSocket establecida con éxito.")

                val token = sessionToken
                if (token != null) {
                    log("Reanudando sesión previa con token...")
                    sendMessage(MessageTypes.RECONNECT, ReconnectPayload(token))
                } else {
                    executePendingAction()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text.toByteArray(Charsets.UTF_8))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("Servidor cerrando conexión ($code: $reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("Conexión WebSocket cerrada ($code: $reason)")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Error en WebSocket: ${t.message}")
                handleFailure(t)
            }
        })
    }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        if (_connectionState.value != ConnectionState.CONNECTED) return
        pendingAction = null

        when (action) {
            is PendingAction.CreateRoom -> {
                log("Ejecutando acción pendiente: Crear sala (${action.username})")
                sendMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(action.username))
            }
            is PendingAction.JoinRoom -> {
                log("Ejecutando acción pendiente: Unirse a sala ${action.roomCode} (${action.username})")
                sendMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(action.roomCode, action.username))
            }
        }
    }

    fun disconnect() {
        log("Desconectando cliente...")
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED

        sessionToken = null
        currentRoomCode = null
        pendingAction = null
        _roomState.value = null
        _role.value = RoomRole.NONE
        _userId.value = null
        _pendingJoinRequests.value = emptyList()
        _chatMessages.value = emptyList()
        reconnectAttempts = 0

        scope.launch { _events.emit(ListenTogetherEvent.Disconnected) }
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(PING_INTERVAL_MS)
                pingSentTime = System.currentTimeMillis()
                sendMessageNoPayload(MessageTypes.PING)
            }
        }
    }

    private fun handleDisconnect() {
        pingJob?.cancel()
        pingJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _pendingJoinRequests.value = emptyList()

        if (sessionToken != null && _roomState.value != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            scheduleReconnect()
        } else {
            scope.launch { _events.emit(ListenTogetherEvent.Disconnected) }
        }
    }

    private fun handleFailure(t: Throwable) {
        pingJob?.cancel()
        pingJob = null
        if (sessionToken != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            scheduleReconnect()
        } else {
            _connectionState.value = ConnectionState.ERROR
            scope.launch {
                _events.emit(ListenTogetherEvent.ConnectionError(t.message ?: "Error desconocido"))
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectAttempts++
        _connectionState.value = ConnectionState.RECONNECTING
        val delayMs = 1500L * reconnectAttempts
        log("Reconectando intento $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS en ${delayMs}ms...")
        scope.launch {
            _events.emit(ListenTogetherEvent.Reconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS))
            delay(delayMs)
            connect()
        }
    }

    private fun handleIncomingMessage(data: ByteArray) {
        try {
            val detectedFormat = MessageCodec.detectMessageFormat(data)
            if (detectedFormat == MessageFormat.PROTOBUF && codec.format == MessageFormat.JSON) {
                codec.format = MessageFormat.PROTOBUF
            }

            val (msgType, payloadBytes) = codec.decode(data)
            val payload = codec.decodePayload(msgType, payloadBytes, detectedFormat)

            when (msgType) {
                MessageTypes.ROOM_CREATED -> {
                    val p = payload as? RoomCreatedPayload ?: return
                    _userId.value = p.userId
                    _role.value = RoomRole.HOST
                    sessionToken = p.sessionToken
                    currentRoomCode = p.roomCode
                    _roomState.value = RoomState(
                        roomCode = p.roomCode,
                        hostId = p.userId,
                        users = listOf(UserInfo(p.userId, currentUsername, true)),
                        isPlaying = false,
                        position = 0L,
                        lastUpdate = System.currentTimeMillis()
                    )
                    log("Sala creada con éxito. Código: ${p.roomCode}")
                    scope.launch { _events.emit(ListenTogetherEvent.RoomCreated(p.roomCode, p.userId)) }
                }

                MessageTypes.JOIN_REQUEST -> {
                    val p = payload as? JoinRequestPayload ?: return
                    log("Solicitud de unión de: ${p.username}")
                    val autoApprove = PreferencesStore.current.listenTogetherAutoApprove
                    if (_role.value == RoomRole.HOST) {
                        if (autoApprove) {
                            log("Aprobando automáticamente a ${p.username}...")
                            approveJoin(p.userId)
                        } else {
                            _pendingJoinRequests.value += p
                        }
                    }
                    scope.launch { _events.emit(ListenTogetherEvent.JoinRequestReceived(p.userId, p.username)) }
                }

                MessageTypes.JOIN_APPROVED -> {
                    val p = payload as? JoinApprovedPayload ?: return
                    _userId.value = p.userId
                    _role.value = RoomRole.GUEST
                    sessionToken = p.sessionToken
                    currentRoomCode = p.roomCode
                    _roomState.value = p.state
                    log("Unido a la sala ${p.roomCode} como Invitado.")
                    scope.launch { _events.emit(ListenTogetherEvent.JoinApproved(p.roomCode, p.userId, p.state)) }
                }

                MessageTypes.JOIN_REJECTED -> {
                    val p = payload as? JoinRejectedPayload
                    val reason = p?.reason ?: "Solicitud rechazada por el anfitrión"
                    log("Unión rechazada: $reason")
                    scope.launch { _events.emit(ListenTogetherEvent.JoinRejected(reason)) }
                }

                MessageTypes.USER_JOINED -> {
                    val p = payload as? UserJoinedPayload ?: return
                    _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != p.userId }
                    _roomState.value = _roomState.value?.copy(
                        users = (_roomState.value?.users.orEmpty().filter { it.userId != p.userId }) + UserInfo(p.userId, p.username, false)
                    )
                    log("${p.username} se unió a la sala.")
                    scope.launch { _events.emit(ListenTogetherEvent.UserJoined(p.userId, p.username)) }
                }

                MessageTypes.USER_LEFT -> {
                    val p = payload as? UserLeftPayload ?: return
                    _roomState.value = _roomState.value?.copy(
                        users = _roomState.value?.users.orEmpty().filter { it.userId != p.userId }
                    )
                    log("${p.username} salió de la sala.")
                    scope.launch { _events.emit(ListenTogetherEvent.UserLeft(p.userId, p.username)) }
                }

                MessageTypes.HOST_CHANGED -> {
                    val p = payload as? HostChangedPayload ?: return
                    val isMeNow = p.newHostId == _userId.value
                    _role.value = if (isMeNow) RoomRole.HOST else RoomRole.GUEST
                    _roomState.value = _roomState.value?.copy(
                        hostId = p.newHostId,
                        users = _roomState.value?.users.orEmpty().map { it.copy(isHost = it.userId == p.newHostId) }
                    )
                    log("El nuevo anfitrión es ${p.newHostName}")
                    scope.launch { _events.emit(ListenTogetherEvent.HostChanged(p.newHostId, p.newHostName)) }
                }

                MessageTypes.KICKED -> {
                    val p = payload as? KickedPayload
                    val reason = p?.reason ?: "Has sido expulsado de la sala"
                    log("Expulsado de la sala: $reason")
                    sessionToken = null
                    currentRoomCode = null
                    _roomState.value = null
                    _role.value = RoomRole.NONE
                    scope.launch { _events.emit(ListenTogetherEvent.Kicked(reason)) }
                }

                MessageTypes.SYNC_PLAYBACK -> {
                    val p = payload as? PlaybackActionPayload ?: return
                    log("Acción de reproducción sincronizada: ${p.action}")
                    when (p.action) {
                        PlaybackActions.PLAY -> {
                            _roomState.value = _roomState.value?.copy(
                                isPlaying = true,
                                position = p.position ?: _roomState.value?.position ?: 0L
                            )
                        }
                        PlaybackActions.PAUSE -> {
                            _roomState.value = _roomState.value?.copy(
                                isPlaying = false,
                                position = p.position ?: _roomState.value?.position ?: 0L
                            )
                        }
                        PlaybackActions.SEEK -> {
                            _roomState.value = _roomState.value?.copy(
                                position = p.position ?: _roomState.value?.position ?: 0L
                            )
                        }
                        PlaybackActions.CHANGE_TRACK -> {
                            _roomState.value = _roomState.value?.copy(
                                currentTrack = p.trackInfo,
                                isPlaying = false,
                                position = 0L
                            )
                        }
                    }
                    scope.launch { _events.emit(ListenTogetherEvent.PlaybackSync(p)) }
                }

                MessageTypes.SYNC_STATE -> {
                    val p = payload as? SyncStatePayload ?: return
                    log("Estado sincronizado: isPlaying=${p.isPlaying}, pos=${p.position}")
                    _roomState.value = _roomState.value?.copy(
                        currentTrack = p.currentTrack ?: _roomState.value?.currentTrack,
                        isPlaying = p.isPlaying,
                        position = p.position,
                        queue = p.queue ?: _roomState.value?.queue.orEmpty(),
                        volume = p.volume ?: _roomState.value?.volume ?: 1f
                    )
                    scope.launch { _events.emit(ListenTogetherEvent.SyncStateReceived(p)) }
                }

                MessageTypes.CHAT -> {
                    val p = payload as? ChatMessagePayload ?: return
                    _chatMessages.value = (_chatMessages.value + p).takeLast(200)
                    log("Mensaje de chat de ${p.username}: ${p.message}")
                    scope.launch { _events.emit(ListenTogetherEvent.ChatMessageReceived(p)) }
                }

                MessageTypes.ROOM_SETTINGS_CHANGED -> {
                    val p = payload as? UpdateRoomSettingsPayload ?: return
                    _roomState.value = _roomState.value?.copy(
                        allowParticipantControl = p.allowParticipantControl
                    )
                    scope.launch { _events.emit(ListenTogetherEvent.RoomSettingsChanged(p.allowParticipantControl)) }
                }

                MessageTypes.RECONNECTED -> {
                    val p = payload as? ReconnectedPayload ?: return
                    _userId.value = p.userId
                    _role.value = if (p.isHost) RoomRole.HOST else RoomRole.GUEST
                    _roomState.value = p.state
                    currentRoomCode = p.roomCode
                    log("Reconexión completada a sala ${p.roomCode}")
                    scope.launch { _events.emit(ListenTogetherEvent.Reconnected(p.roomCode, p.userId, p.state, p.isHost)) }
                }

                MessageTypes.PONG -> {
                    if (pingSentTime > 0) {
                        _rtt.value = System.currentTimeMillis() - pingSentTime
                        pingSentTime = 0
                    }
                }

                MessageTypes.ERROR -> {
                    val p = payload as? ErrorPayload
                    val code = p?.code ?: "error"
                    val message = p?.message ?: "Error devuelto por el servidor"
                    log("Error de servidor ($code): $message")
                    scope.launch { _events.emit(ListenTogetherEvent.ServerError(code, message)) }
                }
            }
        } catch (e: Exception) {
            log("Error decodificando mensaje: ${e.message}")
        }
    }

    private fun sendMessage(type: String, payload: Any?) {
        try {
            val data = codec.encode(type, payload)
            val sent = webSocket?.send(ByteString.of(*data)) ?: false
            if (!sent) {
                log("Fallo al enviar mensaje: $type")
            }
        } catch (e: Exception) {
            log("Error enviando mensaje $type: ${e.message}")
        }
    }

    private fun sendMessageNoPayload(type: String) {
        sendMessage(type, null)
    }

    fun createRoom(username: String) {
        currentUsername = username.trim()
        PreferencesStore.update { it.copy(listenTogetherUsername = currentUsername) }
        if (_connectionState.value != ConnectionState.CONNECTED) {
            pendingAction = PendingAction.CreateRoom(currentUsername)
            connect()
        } else {
            sendMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(currentUsername))
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        val code = roomCode.trim().uppercase()
        currentUsername = username.trim()
        PreferencesStore.update { it.copy(listenTogetherUsername = currentUsername) }
        if (_connectionState.value != ConnectionState.CONNECTED) {
            pendingAction = PendingAction.JoinRoom(code, currentUsername)
            connect()
        } else {
            sendMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(code, currentUsername))
        }
    }

    fun leaveRoom() {
        sendMessageNoPayload(MessageTypes.LEAVE_ROOM)
        sessionToken = null
        currentRoomCode = null
        pendingAction = null
        _roomState.value = null
        _role.value = RoomRole.NONE
        _pendingJoinRequests.value = emptyList()
        _chatMessages.value = emptyList()
    }

    fun approveJoin(userId: String) {
        if (!isHost) return
        sendMessage(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }
    }

    fun rejectJoin(userId: String, reason: String? = null) {
        if (!isHost) return
        sendMessage(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId, reason))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }
    }

    fun kickUser(userId: String, reason: String? = null) {
        if (!isHost) return
        sendMessage(MessageTypes.KICK_USER, KickUserPayload(userId, reason))
    }

    fun transferHost(newHostId: String) {
        if (!isHost) return
        sendMessage(MessageTypes.TRANSFER_HOST, TransferHostPayload(newHostId))
    }

    fun updateRoomSettings(allowParticipantControl: Boolean) {
        if (!isHost) return
        _roomState.value = _roomState.value?.copy(allowParticipantControl = allowParticipantControl)
    }

    fun sendPlaybackAction(
        action: String,
        trackId: String? = null,
        position: Long? = null,
        trackInfo: TrackInfo? = null,
        insertNext: Boolean? = null,
        queue: List<TrackInfo>? = null,
        queueTitle: String? = null,
        volume: Float? = null
    ) {
        val canControl = isHost || (_role.value == RoomRole.GUEST && _roomState.value?.allowParticipantControl == true)
        if (!canControl) return

        sendMessage(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(
                action = action,
                trackId = trackId,
                position = position,
                trackInfo = trackInfo,
                insertNext = insertNext,
                queue = queue,
                queueTitle = queueTitle,
                volume = volume,
                serverTime = System.currentTimeMillis()
            )
        )
    }

    fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
        if (!isInRoom || message.isBlank()) return
        sendMessage(
            MessageTypes.CHAT,
            ChatPayload(message = message.trim(), replyTo = replyTo)
        )
    }

    fun requestSync() {
        if (!isInRoom) return
        sendMessageNoPayload(MessageTypes.REQUEST_SYNC)
    }
}
