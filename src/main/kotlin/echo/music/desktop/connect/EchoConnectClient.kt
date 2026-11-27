package echo.music.desktop.connect

import kotlinx.serialization.KSerializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Calls to the phone's Echo Connect API (blocking; use from an IO thread). */
class EchoConnectClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    fun state(peer: ConnectPeer): PeerStateDto =
        get(peer, "/v1/state", PeerStateDto.serializer())

    fun control(peer: ConnectPeer, command: ControlCommand): PeerStateDto =
        post(peer, "/v1/control", command, ControlCommand.serializer(), PeerStateDto.serializer())

    fun handoff(peer: ConnectPeer, payload: HandoffPayload): PeerStateDto =
        post(peer, "/v1/handoff", payload, HandoffPayload.serializer(), PeerStateDto.serializer(), timeoutSec = 8)

    fun pairStart(peer: ConnectPeer, payload: PairStartPayload) {
        post(peer, "/v1/pair/start", payload, PairStartPayload.serializer(), OkResponse.serializer())
    }

    fun pairVerify(peer: ConnectPeer, request: PairVerifyRequest): PairVerifyResponse =
        post(peer, "/v1/pair/verify", request, PairVerifyRequest.serializer(), PairVerifyResponse.serializer())

    fun unpair(peer: ConnectPeer, deviceId: String) {
        post(peer, "/v1/pair/unpair", UnpairPayload(deviceId), UnpairPayload.serializer(), OkResponse.serializer())
    }

    fun sync(peer: ConnectPeer, bundle: SyncBundle): SyncBundle =
        post(peer, "/v1/sync", bundle, SyncBundle.serializer(), SyncBundle.serializer(), timeoutSec = 30)

    private fun <T> get(peer: ConnectPeer, path: String, out: KSerializer<T>): T {
        val request = HttpRequest.newBuilder(URI(peer.baseUrl + path))
            .timeout(Duration.ofSeconds(4))
            .GET().build()
        return send(request, out)
    }

    private fun <I, T> post(
        peer: ConnectPeer, path: String, body: I, input: KSerializer<I>, out: KSerializer<T>, timeoutSec: Long = 5
    ): T {
        val request = HttpRequest.newBuilder(URI(peer.baseUrl + path))
            .timeout(Duration.ofSeconds(timeoutSec))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(connectJson.encodeToString(input, body)))
            .build()
        return send(request, out)
    }

    private fun <T> send(request: HttpRequest, out: KSerializer<T>): T {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) throw IllegalStateException("HTTP ${response.statusCode()}")
        return connectJson.decodeFromString(out, response.body().ifBlank { "{}" })
    }
}
