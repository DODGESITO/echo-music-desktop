package echo.music.desktop.connect

import com.music.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What Echo Connect needs from the player and the library. Called on the main thread. */
interface EchoConnectApp {
    data class Playback(
        val queue: List<SongItem>,
        val index: Int,
        val current: SongItem?,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val volume: Float,
        val shuffle: Boolean,
        /** off, all, one */
        val repeat: String
    )

    fun playback(): Playback
    fun play()
    fun pause()
    fun togglePlay()
    fun next()
    fun previous()
    fun seek(positionMs: Long)
    fun setVolume(volume: Float)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: String)
    fun playIndex(index: Int)
    fun addToQueue(song: SongItem)
    /** Plays [songs] from [index] at [positionMs] (or only loads them, paused). */
    fun playQueue(songs: List<SongItem>, index: Int, positionMs: Long, play: Boolean)

    /** This PC's YouTube Music session, if signed in. */
    fun account(): AccountSyncDto?
    suspend fun signIn(cookie: String): Result<String>
    fun localPlaylists(): List<PlaylistSyncDto>
    fun mergePlaylists(incoming: List<PlaylistSyncDto>, from: String): Int
    fun bringToFront()
    fun notify(message: String)
}

/**
 * Echo Connect for this PC: found by Echo Music for Android on the same Wi-Fi, paired with a
 * 6-digit code, then it can take over or hand over what is playing, be controlled from the phone
 * (or control it), and sync playlists and, if allowed, the YouTube Music account.
 */
class EchoConnectManager(
    private val app: EchoConnectApp,
    /** Tests turn mDNS off so they do not announce themselves on the real network. */
    private val useDiscovery: Boolean = true
) : EchoConnectHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = EchoConnectClient()
    private val hostCode = HostPairingCode()

    private val _settings = MutableStateFlow(EchoConnectStore.load())
    val settings: StateFlow<EchoConnectSettings> = _settings.asStateFlow()

    private var server: EchoConnectServer? = null
    /** Port of the local API (0 while off). */
    val port: Int get() = server?.port ?: 0
    private var discovery: EchoConnectDiscovery? = null
    private var jobs = mutableListOf<Job>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _peers = MutableStateFlow<List<ConnectPeer>>(emptyList())
    /** Phones found on the network (Echo Music for Android); other PCs are left out. */
    val peers: StateFlow<List<ConnectPeer>> = _peers.asStateFlow()

    /** Code this PC shows while pairing (the phone types it). */
    private val _hostPairing = MutableStateFlow<HostPairingCode.Code?>(null)
    val hostPairing: StateFlow<HostPairingCode.Code?> = _hostPairing.asStateFlow()

    data class IncomingPair(val peer: ConnectPeer, val expiresAtMs: Long, val busy: Boolean = false, val error: String? = null)
    /** The phone started pairing: it shows a code that has to be typed here. */
    private val _incomingPair = MutableStateFlow<IncomingPair?>(null)
    val incomingPair: StateFlow<IncomingPair?> = _incomingPair.asStateFlow()

    data class RemoteSession(val peer: ConnectPeer, val state: PeerStateDto?)
    /** Music playing on the phone, controlled from here. */
    private val _remote = MutableStateFlow<RemoteSession?>(null)
    val remote: StateFlow<RemoteSession?> = _remote.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val deviceName: String get() = _settings.value.deviceName.ifBlank { EchoConnectStore.defaultDeviceName() }

    // ------------------------------------------------------------------ on / off

    fun start() {
        if (_settings.value.enabled) launchServices()
    }

    fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
        if (enabled) launchServices() else stopServices()
    }

    fun setShareAccount(share: Boolean) = updateSettings { it.copy(shareAccount = share) }

    @Synchronized
    private fun launchServices() {
        if (_running.value) return
        try {
            val srv = EchoConnectServer(this)
            val port = srv.start()
            server = srv
            if (useDiscovery) {
                val disc = EchoConnectDiscovery(_settings.value.deviceId, deviceName, port)
                discovery = disc
                scope.launch {
                    runCatching { disc.start() }.onFailure { addLog("No se pudo anunciar esta PC en la red: ${it.message}") }
                    disc.peers.collect { found -> onPeersFound(found) }
                }
            }
            _running.value = true
            addLog("Echo Connect activado (puerto $port)")
            jobs += scope.launch { syncLoop() }
            jobs += scope.launch { remoteLoop() }
        } catch (e: Exception) {
            addLog("No se pudo activar Echo Connect: ${e.message}")
            stopServices()
        }
    }

    @Synchronized
    private fun stopServices() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        discovery?.let { d -> scope.launch { d.stop() } }
        discovery = null
        server?.stop()
        server = null
        _running.value = false
        _peers.value = emptyList()
        _remote.value = null
        cancelPairing()
        _incomingPair.value = null
    }

    fun shutdown() {
        stopServices()
    }

    /** This PC's address on the network, as the phone sees it. */
    fun localAddress(): String? = discovery?.localAddress?.hostAddress

    fun refreshDevices() {
        discovery?.refresh()
    }

    private fun onPeersFound(found: List<ConnectPeer>) {
        _peers.value = found.filter { it.isPhone }
        // The paired phone got a new address (DHCP): follow it.
        val s = _settings.value
        val paired = found.firstOrNull { it.id == s.pairedPeerId } ?: return
        if (paired.host != s.pairedHost || paired.port != s.pairedPort) {
            updateSettings { it.copy(pairedHost = paired.host, pairedPort = paired.port) }
        }
    }

    /** The paired phone: as found on the network now, or where it was last seen. */
    fun pairedPeer(): ConnectPeer? {
        val s = _settings.value
        val id = s.pairedPeerId ?: return null
        _peers.value.firstOrNull { it.id == id }?.let { return it }
        val host = s.pairedHost ?: return null
        return ConnectPeer(id, s.pairedPeerName ?: "Celular", "android", host, s.pairedPort.takeIf { it > 0 } ?: EchoConnectServer.PHONE_PORT)
    }

    // ------------------------------------------------------------------ pairing (code shown here)

    private var hostPairingJob: Job? = null

    fun startPairing(peer: ConnectPeer) {
        hostPairingJob?.cancel()
        hostPairingJob = scope.launch {
            while (isActive) {
                val code = hostCode.start(peer)
                _hostPairing.value = code
                val local = discovery?.localAddress?.hostAddress
                runCatching {
                    client.pairStart(
                        peer,
                        PairStartPayload(
                            deviceId = _settings.value.deviceId,
                            deviceName = deviceName,
                            platform = "desktop",
                            host = local,
                            port = server?.port ?: 0,
                            expiresAtMs = code.expiresAtMs
                        )
                    )
                }.onFailure { addLog("No se pudo contactar a ${peer.name}: ${it.message}") }
                // A new code every minute until the phone answers or the dialog is closed.
                while (isActive && hostCode.current == code && System.currentTimeMillis() < code.expiresAtMs) delay(500)
                if (hostCode.current == null) break
            }
        }
    }

    fun cancelPairing() {
        hostPairingJob?.cancel()
        hostPairingJob = null
        hostCode.cancel()
        _hostPairing.value = null
    }

    override fun pairVerify(request: PairVerifyRequest, remoteAddress: String): PairVerifyResponse {
        val session = hostCode.current
        return when (val result = hostCode.verify(request.deviceId, request.code)) {
            is HostPairingCode.Result.Rejected -> {
                if (hostCode.current == null) {
                    hostPairingJob?.cancel()
                    _hostPairing.value = null
                    addLog("Emparejamiento cancelado: demasiados códigos incorrectos")
                }
                PairVerifyResponse(ok = false, error = result.error)
            }
            is HostPairingCode.Result.Paired -> {
                hostPairingJob?.cancel()
                _hostPairing.value = null
                val peer = session!!.peer
                savePairing(
                    peer.copy(
                        name = request.deviceName.ifBlank { peer.name },
                        host = ConnectAccess.normalize(remoteAddress).ifBlank { peer.host }
                    ),
                    result.token
                )
                PairVerifyResponse(ok = true, pairToken = result.token)
            }
        }
    }

    // ------------------------------------------------------------------ pairing (code shown on the phone)

    override fun pairStart(request: PairStartPayload, remoteAddress: String) {
        if (!_settings.value.enabled) return
        val address = ConnectAccess.normalize(remoteAddress)
        val known = _peers.value.firstOrNull { it.id == request.deviceId }
        val peer = known ?: ConnectPeer(
            id = request.deviceId.ifBlank { address },
            name = request.deviceName.ifBlank { "Celular" },
            platform = request.platform.ifBlank { "android" },
            // Where the request really came from, not an address written in it.
            host = address.ifBlank { request.host.orEmpty() },
            port = request.port.takeIf { it > 0 } ?: EchoConnectServer.PHONE_PORT
        )
        val expires = request.expiresAtMs.takeIf { it > System.currentTimeMillis() } ?: (System.currentTimeMillis() + 60_000L)
        _incomingPair.value = IncomingPair(peer, expires)
        scope.launch { onMain { app.bringToFront() } }
    }

    fun submitIncomingCode(code: String) {
        val request = _incomingPair.value ?: return
        val trimmed = code.filter(Char::isDigit)
        if (trimmed.length != 6) {
            _incomingPair.value = request.copy(error = "Escribe los 6 números que muestra el celular.")
            return
        }
        _incomingPair.value = request.copy(busy = true, error = null)
        scope.launch {
            val response = runCatching {
                client.pairVerify(request.peer, PairVerifyRequest(_settings.value.deviceId, deviceName, trimmed))
            }
            val ok = response.getOrNull()
            if (ok?.ok == true) {
                _incomingPair.value = null
                savePairing(request.peer, ok.pairToken)
            } else {
                val error = ok?.error?.let(::translatePhoneError)
                    ?: response.exceptionOrNull()?.let { "No se pudo contactar al celular (${it.message})." }
                    ?: "Código incorrecto o vencido."
                _incomingPair.update { it?.copy(busy = false, error = error) }
            }
        }
    }

    fun dismissIncomingPair() {
        _incomingPair.value = null
    }

    private fun savePairing(peer: ConnectPeer, token: String?) {
        updateSettings {
            it.copy(
                pairedPeerId = peer.id,
                pairedPeerName = peer.name,
                pairedHost = peer.host,
                pairedPort = peer.port,
                pairToken = token
            )
        }
        addLog("Emparejado con ${peer.name}")
        scope.launch { onMain { app.notify("Echo Connect: emparejado con ${peer.name}") } }
        scope.launch {
            delay(800)
            syncNow()
        }
    }

    fun unpair() {
        val peer = pairedPeer()
        clearPairing("Desemparejado de ${peer?.name ?: "el celular"}")
        if (peer != null) scope.launch { runCatching { client.unpair(peer, _settings.value.deviceId) } }
    }

    override fun unpair(deviceId: String) {
        if (deviceId.isBlank() || deviceId == _settings.value.pairedPeerId) {
            clearPairing("El celular deshizo el emparejamiento")
        }
    }

    private fun clearPairing(message: String) {
        updateSettings { it.copy(pairedPeerId = null, pairedPeerName = null, pairedHost = null, pairedPort = 0, pairToken = null) }
        _remote.value = null
        addLog(message)
    }

    override fun pairedHost(): String? = _settings.value.pairedHost

    // ------------------------------------------------------------------ this PC as the player

    override fun state(publicOnly: Boolean): PeerStateDto {
        val base = PeerStateDto(
            deviceId = _settings.value.deviceId,
            deviceName = deviceName,
            platform = "desktop",
            updatedAt = System.currentTimeMillis()
        )
        if (publicOnly) return base
        val p = onMain { app.playback() }
        return base.copy(
            playing = p.playing,
            volume = p.volume.toDouble(),
            positionMs = p.positionMs,
            durationMs = p.durationMs.takeIf { it > 0 } ?: ((p.current?.duration ?: 0) * 1000L),
            shuffle = p.shuffle,
            repeat = p.repeat,
            current = p.current?.toTrackDto(),
            queue = p.queue.map { it.toTrackDto() },
            index = p.index.coerceAtLeast(0)
        )
    }

    override fun control(command: ControlCommand): PeerStateDto {
        onMain {
            when (command.action) {
                "play" -> app.play()
                "pause" -> app.pause()
                "toggle" -> app.togglePlay()
                "next" -> app.next()
                "previous" -> app.previous()
                "seek" -> app.seek(command.positionMs.coerceAtLeast(0))
                "volume" -> {
                    val v = command.volume.toFloat()
                    app.setVolume(if (v > 1f) v / 100f else v)
                }
                "shuffle" -> command.shuffle?.let(app::setShuffle)
                "repeat" -> app.setRepeat(command.repeat ?: "off")
                "play_index" -> app.playIndex(command.index)
                "queue_add" -> command.item?.takeIf { it.videoId.isNotBlank() }?.let { app.addToQueue(it.toSongItem()) }
            }
        }
        // "Move to phone": the phone paused this PC and plays now; show it here as a remote.
        val controller = command.controllerPeerId
        if (command.action == "pause" && !controller.isNullOrBlank() && controller == _settings.value.pairedPeerId) {
            pairedPeer()?.let { peer ->
                _remote.value = RemoteSession(peer, null)
                addLog("La música siguió en ${peer.name}")
            }
        }
        return state(publicOnly = false)
    }

    override fun handoff(payload: HandoffPayload): PeerStateDto {
        val songs = payload.items.filter { it.videoId.isNotBlank() }.map { it.toSongItem() }
        if (songs.isNotEmpty()) {
            _remote.value = null
            val index = payload.index.takeIf { it in songs.indices } ?: 0
            onMain { app.playQueue(songs, index, payload.positionMs.coerceAtLeast(0), payload.playImmediately) }
            addLog("Recibido del celular: ${songs[index].title}")
        }
        return state(publicOnly = false)
    }

    // ------------------------------------------------------------------ the phone as the player

    /** Sends what is playing here to the phone, which carries on at the same second. */
    fun playOnPhone() {
        val peer = pairedPeer() ?: return
        scope.launch {
            val p = onMain { app.playback() }
            val songs = p.queue.ifEmpty { listOfNotNull(p.current) }
            if (songs.isEmpty()) {
                onMain { app.notify("No hay nada para enviar a ${peer.name}") }
                return@launch
            }
            val index = if (p.queue.isEmpty()) 0 else p.index.coerceIn(0, songs.size - 1)
            val payload = HandoffPayload(
                items = songs.map { it.toTrackDto() },
                index = index,
                positionMs = p.positionMs,
                source = "Echo Music PC",
                playImmediately = true
            )
            runCatching { client.handoff(peer, payload) }
                .onSuccess { st ->
                    onMain { app.pause() }
                    _remote.value = RemoteSession(peer, st)
                    addLog("Reproduciendo en ${peer.name}")
                }
                .onFailure {
                    addLog("No se pudo enviar a ${peer.name}: ${it.message}")
                    onMain { app.notify("No se pudo enviar la música a ${peer.name}") }
                }
        }
    }

    /** Brings what the phone is playing back to this PC. */
    fun playHere() {
        val session = _remote.value
        val peer = session?.peer ?: pairedPeer() ?: return
        scope.launch {
            val st = runCatching { client.state(peer) }.getOrElse {
                addLog("No se pudo leer ${peer.name}: ${it.message}")
                _remote.value = null
                return@launch
            }
            val items = st.queue.ifEmpty { listOfNotNull(st.current) }.filter { it.videoId.isNotBlank() }
            _remote.value = null
            if (items.isEmpty()) {
                onMain { app.notify("${peer.name} no está reproduciendo nada") }
                return@launch
            }
            // Pause the phone and tell it this PC plays now (it becomes the remote).
            runCatching { client.control(peer, ControlCommand(action = "pause", controllerPeerId = _settings.value.deviceId)) }
            val index = if (st.queue.isEmpty()) 0 else st.index.coerceIn(0, items.size - 1)
            onMain { app.playQueue(items.map { it.toSongItem() }, index, st.positionMs, true) }
            addLog("Continuando aquí lo que sonaba en ${peer.name}")
        }
    }

    /** Stops showing the phone here without touching what it plays. */
    fun disconnectRemote() {
        _remote.value = null
    }

    fun remoteControl(command: ControlCommand) {
        val session = _remote.value ?: return
        scope.launch {
            runCatching { client.control(session.peer, command) }
                .onSuccess { st -> _remote.update { it?.copy(state = st) } }
                .onFailure { addLog("${session.peer.name} no respondió: ${it.message}") }
        }
    }

    private suspend fun remoteLoop() {
        var failures = 0
        while (scope.isActive) {
            val session = _remote.value
            if (session == null) {
                failures = 0
                delay(1_000)
                continue
            }
            runCatching { client.state(session.peer) }
                .onSuccess { st ->
                    failures = 0
                    _remote.update { if (it?.peer?.id == session.peer.id) it.copy(state = st) else it }
                }
                .onFailure {
                    if (++failures >= 6) {
                        _remote.value = null
                        addLog("Se perdió la conexión con ${session.peer.name}")
                    }
                }
            delay(1_000)
        }
    }

    // ------------------------------------------------------------------ sync

    private suspend fun syncLoop() {
        delay(15_000)
        while (scope.isActive) {
            if (_settings.value.isPaired) syncNow()
            delay(5 * 60_000L)
        }
    }

    fun syncNow() {
        val peer = pairedPeer() ?: return
        if (_syncing.value) return
        _syncing.value = true
        scope.launch {
            try {
                val reply = client.sync(peer, buildBundle())
                applyBundle(reply, peer.name)
            } catch (e: Exception) {
                addLog("No se pudo sincronizar con ${peer.name}: ${e.message}")
            } finally {
                _syncing.value = false
            }
        }
    }

    override fun sync(bundle: SyncBundle): SyncBundle {
        val from = bundle.senderName.ifBlank { _settings.value.pairedPeerName ?: "el celular" }
        runBlocking { applyBundle(bundle, from) }
        return buildBundle()
    }

    private fun buildBundle(): SyncBundle {
        val s = _settings.value
        return SyncBundle(
            senderId = s.deviceId,
            senderName = deviceName,
            sentAtMs = System.currentTimeMillis(),
            googleAccount = if (s.shareAccount) onMain { app.account() } else null,
            localPlaylists = onMain { app.localPlaylists() }
        )
    }

    private suspend fun applyBundle(bundle: SyncBundle, from: String) {
        val parts = mutableListOf<String>()
        bundle.googleAccount?.cookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            val mine = onMain { app.account() }
            when {
                mine == null -> {
                    val result = app.signIn(cookie)
                    parts += result.fold({ "sesión de YouTube Music iniciada ($it)" }, { "no se pudo usar la cuenta de $from" })
                }
                !bundle.googleAccount.email.isNullOrBlank() && !mine.email.isNullOrBlank() &&
                    !bundle.googleAccount.email.equals(mine.email, ignoreCase = true) ->
                    parts += "la cuenta de $from es otra; se mantiene la de esta PC"
            }
        }
        val merged = onMain { app.mergePlaylists(bundle.localPlaylists, from) }
        if (merged > 0) parts += "$merged playlist(s) actualizadas"
        updateSettings { it.copy(lastSyncAtMs = System.currentTimeMillis()) }
        addLog("Sincronizado con $from" + if (parts.isEmpty()) "" else ": " + parts.joinToString(", "))
    }

    // ------------------------------------------------------------------ helpers

    private fun updateSettings(change: (EchoConnectSettings) -> EchoConnectSettings) {
        val updated = _settings.updateAndGetCompat(change)
        EchoConnectStore.save(updated)
    }

    private fun MutableStateFlow<EchoConnectSettings>.updateAndGetCompat(change: (EchoConnectSettings) -> EchoConnectSettings): EchoConnectSettings {
        while (true) {
            val prev = value
            val next = change(prev)
            if (compareAndSet(prev, next)) return next
        }
    }

    private fun addLog(message: String) {
        val line = "${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}  $message"
        _log.update { (listOf(line) + it).take(30) }
        println("[EchoConnect] $message")
    }

    private fun <T> onMain(block: () -> T): T =
        if (javax.swing.SwingUtilities.isEventDispatchThread()) block() else runBlocking(Dispatchers.Main) { block() }

    private fun translatePhoneError(error: String): String = when {
        error.contains("expired", ignoreCase = true) -> "El código venció. Mira el código nuevo en el celular."
        error.contains("incorrect", ignoreCase = true) -> "Código incorrecto."
        else -> error
    }
}
