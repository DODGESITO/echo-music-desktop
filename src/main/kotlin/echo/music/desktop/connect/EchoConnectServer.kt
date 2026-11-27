package echo.music.desktop.connect

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.KSerializer
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** What the server needs from the app. Calls arrive on server threads. */
interface EchoConnectHandler {
    /** Full state (queue included) for the paired phone; [publicOnly] = only who this PC is. */
    fun state(publicOnly: Boolean): PeerStateDto
    fun control(command: ControlCommand): PeerStateDto
    fun handoff(payload: HandoffPayload): PeerStateDto
    fun pairStart(request: PairStartPayload, remoteAddress: String)
    fun pairVerify(request: PairVerifyRequest, remoteAddress: String): PairVerifyResponse
    fun unpair(deviceId: String)
    fun sync(bundle: SyncBundle): SyncBundle
    /** The paired phone's address, the only one trusted with playback, playlists and account. */
    fun pairedHost(): String?
}

/**
 * The local HTTP/1.1 JSON API that Echo Music for Android talks to (port 45871, like the official
 * desktop client). Only pairing and a public state are open to the whole network; everything else
 * answers only to the paired phone.
 */
class EchoConnectServer(private val handler: EchoConnectHandler) {
    private var server: HttpServer? = null
    val port: Int get() = server?.address?.port ?: 0

    @Synchronized
    fun start(): Int {
        server?.let { return it.address.port }
        val created = try {
            HttpServer.create(InetSocketAddress(PREFERRED_PORT), 16)
        } catch (e: BindException) {
            // The official Echo Music Desktop (or another copy) already has 45871.
            HttpServer.create(InetSocketAddress(0), 16)
        }
        created.executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "echo-connect-http").apply { isDaemon = true } }
        created.createContext("/v1/") { exchange -> runCatching { route(exchange) }.onFailure { runCatching { reply(exchange, 500, """{"error":"internal"}""") } } }
        created.start()
        server = created
        return created.address.port
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun route(exchange: HttpExchange) {
        val method = exchange.requestMethod.uppercase()
        val path = exchange.requestURI.path.trimEnd('/')
        val remote = exchange.remoteAddress?.address?.hostAddress
        val trusted = ConnectAccess.isFromPairedPeer(remote, handler.pairedHost())
        fun denied() = reply(exchange, 403, """{"ok":false,"error":"Not paired with this PC"}""")

        when {
            method == "GET" && path == "/v1/state" ->
                reply(exchange, 200, connectJson.encodeToString(PeerStateDto.serializer(), handler.state(publicOnly = !trusted)))
            method != "POST" -> reply(exchange, 405, """{"error":"method not allowed"}""")
            path == "/v1/pair/start" -> {
                val body = read(exchange, PairStartPayload.serializer()) ?: return
                handler.pairStart(body, remote.orEmpty())
                reply(exchange, 200, """{"ok":true}""")
            }
            path == "/v1/pair/verify" -> {
                val body = read(exchange, PairVerifyRequest.serializer()) ?: return
                reply(exchange, 200, connectJson.encodeToString(PairVerifyResponse.serializer(), handler.pairVerify(body, remote.orEmpty())))
            }
            !trusted -> denied()
            path == "/v1/control" -> {
                val body = read(exchange, ControlCommand.serializer()) ?: return
                reply(exchange, 200, connectJson.encodeToString(PeerStateDto.serializer(), handler.control(body)))
            }
            path == "/v1/handoff" -> {
                val body = read(exchange, HandoffPayload.serializer()) ?: return
                reply(exchange, 200, connectJson.encodeToString(PeerStateDto.serializer(), handler.handoff(body)))
            }
            path == "/v1/sync" -> {
                val body = read(exchange, SyncBundle.serializer()) ?: return
                reply(exchange, 200, connectJson.encodeToString(SyncBundle.serializer(), handler.sync(body)))
            }
            // This PC keeps no podcast progress: nothing to merge, nothing to send back.
            path == "/v1/episodes/sync" -> reply(exchange, 200, """{"episodes":[]}""")
            path == "/v1/pair/unpair" -> {
                val body = read(exchange, UnpairPayload.serializer()) ?: return
                handler.unpair(body.deviceId)
                reply(exchange, 200, """{"ok":true}""")
            }
            else -> reply(exchange, 404, """{"error":"not found"}""")
        }
    }

    private fun <T> read(exchange: HttpExchange, serializer: KSerializer<T>): T? {
        val bytes = exchange.requestBody.use { it.readNBytes(MAX_BODY + 1) }
        if (bytes.size > MAX_BODY) {
            reply(exchange, 413, """{"error":"too large"}""")
            return null
        }
        return runCatching { connectJson.decodeFromString(serializer, bytes.toString(Charsets.UTF_8).ifBlank { "{}" }) }
            .getOrElse {
                reply(exchange, 400, """{"error":"bad json"}""")
                null
            }
    }

    private fun reply(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        const val PREFERRED_PORT = 45871
        const val PHONE_PORT = 45870
        private const val MAX_BODY = 4 * 1024 * 1024
    }
}
