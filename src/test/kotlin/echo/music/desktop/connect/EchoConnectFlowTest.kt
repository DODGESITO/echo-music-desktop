package echo.music.desktop.connect

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.KSerializer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole Echo Connect flow against a simulated Android phone (same endpoints and JSON as
 * LanSyncManager), on 127.0.0.1 and without mDNS.
 */
class EchoConnectFlowTest {
    private val dir: File = Files.createTempDirectory("echo-connect-test").toFile()
    private val http = HttpClient.newHttpClient()

    private class FakeApp : EchoConnectApp {
        val songs = listOf(song("a", "Uno"), song("b", "Dos"), song("c", "Tres"))
        var queue = songs
        var index = 1
        var playing = true
        val calls = CopyOnWriteArrayList<String>()
        var played: Triple<List<String>, Int, Long>? = null
        var signedInWith: String? = null
        val merged = CopyOnWriteArrayList<PlaylistSyncDto>()

        override fun playback() = EchoConnectApp.Playback(queue, index, queue.getOrNull(index), playing, 42_000, 180_000, 0.7f, false, "off")
        override fun play() { calls += "play"; playing = true }
        override fun pause() { calls += "pause"; playing = false }
        override fun togglePlay() { calls += "toggle" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seek(positionMs: Long) { calls += "seek:$positionMs" }
        override fun setVolume(volume: Float) { calls += "volume:$volume" }
        override fun setShuffle(enabled: Boolean) { calls += "shuffle:$enabled" }
        override fun setRepeat(mode: String) { calls += "repeat:$mode" }
        override fun playIndex(index: Int) { calls += "index:$index" }
        override fun addToQueue(song: SongItem) { calls += "add:${song.id}" }
        override fun playQueue(songs: List<SongItem>, index: Int, positionMs: Long, play: Boolean) {
            played = Triple(songs.map { it.id }, index, positionMs)
            playing = play
        }
        override fun account(): AccountSyncDto? = null
        override suspend fun signIn(cookie: String): Result<String> {
            signedInWith = cookie
            return Result.success("Ana")
        }
        override fun localPlaylists() = listOf(PlaylistSyncDto(syncId = "desktop:1", title = "De la PC", tracks = listOf(TrackDto("a", "Uno"))))
        override fun mergePlaylists(incoming: List<PlaylistSyncDto>, from: String): Int {
            merged += incoming
            return incoming.size
        }
        override fun bringToFront() {}
        override fun notify(message: String) {}

        companion object {
            fun song(id: String, title: String) = SongItem(id = id, title = title, artists = listOf(Artist("Banda", null)), thumbnail = "", duration = 180)
        }
    }

    /** Simulated phone: records what the PC sends, answers like Android does. */
    private class FakePhone {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        val received = CopyOnWriteArrayList<Pair<String, String>>()
        val peer get() = ConnectPeer("phone-1", "Pixel", "android", "127.0.0.1", server.address.port)
        var state = PeerStateDto(
            deviceId = "phone-1", deviceName = "Pixel", platform = "android", playing = true, positionMs = 90_000,
            current = TrackDto("x", "Del celular", "Artista"), queue = listOf(TrackDto("w", "Antes"), TrackDto("x", "Del celular")), index = 1
        )

        init {
            server.createContext("/v1/") { ex ->
                val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                received += ex.requestURI.path to body
                val out = when (ex.requestURI.path) {
                    "/v1/state", "/v1/control", "/v1/handoff" -> connectJson.encodeToString(PeerStateDto.serializer(), state)
                    "/v1/sync" -> connectJson.encodeToString(
                        SyncBundle.serializer(),
                        SyncBundle(
                            senderId = "phone-1", senderName = "Pixel",
                            googleAccount = AccountSyncDto(cookie = "SAPISID=secret", name = "Ana"),
                            localPlaylists = listOf(PlaylistSyncDto(syncId = "local:9", title = "Del celular", tracks = listOf(TrackDto("x", "Del celular"))))
                        )
                    )
                    else -> """{"ok":true}"""
                }
                val bytes = out.toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun bodiesFor(path: String) = received.filter { it.first == path }.map { it.second }
    }

    private val app = FakeApp()
    private val phone = FakePhone()
    private val manager: EchoConnectManager

    init {
        EchoConnectStore.fileOverride = File(dir, "echo_connect.json")
        manager = EchoConnectManager(app, useDiscovery = false)
        manager.setEnabled(true)
    }

    @AfterTest
    fun cleanUp() {
        manager.shutdown()
        phone.server.stop(0)
        EchoConnectStore.fileOverride = null
        dir.deleteRecursively()
    }

    private fun <T> callPc(path: String, body: String?, out: KSerializer<T>?): Pair<Int, T?> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${manager.port}$path")).header("Content-Type", "application/json")
        val request = if (body == null) builder.GET().build() else builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val parsed = if (response.statusCode() == 200 && out != null) connectJson.decodeFromString(out, response.body()) else null
        return response.statusCode() to parsed
    }

    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(50)
        }
    }

    private fun pairFromPc() {
        manager.startPairing(phone.peer)
        waitFor("pair/start on the phone") { phone.bodiesFor("/v1/pair/start").isNotEmpty() }
        val code = assertNotNull(manager.hostPairing.value).code
        val wrong = if (code == "123456") "654321" else "123456"
        val (_, bad) = callPc("/v1/pair/verify", """{"deviceId":"phone-1","deviceName":"Pixel","code":"$wrong"}""", PairVerifyResponse.serializer())
        assertFalse(bad!!.ok)
        val (_, good) = callPc("/v1/pair/verify", """{"deviceId":"phone-1","deviceName":"Pixel","code":"$code"}""", PairVerifyResponse.serializer())
        assertTrue(good!!.ok)
        assertTrue(manager.settings.value.isPaired)
    }

    @Test
    fun strangersOnlySeeWhoThisPcIs() {
        val (status, state) = callPc("/v1/state", null, PeerStateDto.serializer())
        assertEquals(200, status)
        assertTrue(state!!.queue.isEmpty() && state.current == null, "no queue for unpaired devices")
        assertEquals(403, callPc<Unit>("/v1/sync", "{}", null).first, "no sync before pairing")
        assertEquals(403, callPc<Unit>("/v1/control", """{"action":"next"}""", null).first)
        assertEquals(403, callPc<Unit>("/v1/handoff", """{"items":[{"videoId":"z"}]}""", null).first)
        assertTrue(app.calls.isEmpty())
    }

    @Test
    fun pairingThenSyncSignsInAndMergesPlaylists() {
        pairFromPc()
        assertEquals("127.0.0.1", manager.settings.value.pairedHost)
        waitFor("first sync") { app.merged.isNotEmpty() }
        val sent = connectJson.decodeFromString(SyncBundle.serializer(), phone.bodiesFor("/v1/sync").first())
        assertNull(sent.googleAccount, "this PC's session is not shared unless enabled")
        assertEquals(listOf("De la PC"), sent.localPlaylists.map { it.title })
        assertEquals("SAPISID=secret", app.signedInWith)
        assertEquals("Del celular", app.merged.first().title)
    }

    @Test
    fun phoneControlsAndHandsOffToThePc() {
        pairFromPc()
        val (_, state) = callPc("/v1/state", null, PeerStateDto.serializer())
        assertEquals(listOf("a", "b", "c"), state!!.queue.map { it.videoId })
        assertEquals("b", state.current?.videoId)

        assertEquals(200, callPc("/v1/control", """{"action":"next"}""", PeerStateDto.serializer()).first)
        callPc("/v1/control", """{"action":"volume","volume":50}""", PeerStateDto.serializer())
        callPc("/v1/control", """{"action":"seek","positionMs":20000}""", PeerStateDto.serializer())
        assertEquals(listOf("next", "volume:0.5", "seek:20000"), app.calls.toList())

        callPc("/v1/handoff", """{"items":[{"videoId":"x","title":"T"},{"videoId":"y"}],"index":1,"positionMs":33000,"playImmediately":true}""", PeerStateDto.serializer())
        assertEquals(Triple(listOf("x", "y"), 1, 33_000L), app.played)

        // "Move to phone": the phone pauses the PC and plays; the PC shows the phone as remote.
        callPc("/v1/control", """{"action":"pause","controllerPeerId":"phone-1"}""", PeerStateDto.serializer())
        assertEquals("phone-1", manager.remote.value?.peer?.id)
    }

    @Test
    fun pcHandsOffToThePhoneAndTakesItBack() {
        pairFromPc()
        manager.playOnPhone()
        waitFor("handoff on the phone") { phone.bodiesFor("/v1/handoff").isNotEmpty() }
        val handoff = connectJson.decodeFromString(HandoffPayload.serializer(), phone.bodiesFor("/v1/handoff").first())
        assertEquals(listOf("a", "b", "c"), handoff.items.map { it.videoId })
        assertEquals(1, handoff.index)
        assertEquals(42_000, handoff.positionMs)
        waitFor("remote session") { manager.remote.value != null }
        assertTrue("pause" in app.calls)

        manager.playHere()
        waitFor("playback back on the PC") { app.played != null }
        assertEquals(Triple(listOf("w", "x"), 1, 90_000L), app.played)
        val pause = phone.bodiesFor("/v1/control").map { connectJson.decodeFromString(ControlCommand.serializer(), it) }.last()
        assertEquals("pause", pause.action)
        assertEquals(manager.settings.value.deviceId, pause.controllerPeerId)
        assertNull(manager.remote.value)
    }

    @Test
    fun phoneCanUnpair() {
        pairFromPc()
        callPc<Unit>("/v1/pair/unpair", """{"deviceId":"phone-1"}""", null)
        assertFalse(manager.settings.value.isPaired)
        assertEquals(403, callPc<Unit>("/v1/control", """{"action":"next"}""", null).first)
    }

    @Test
    fun phoneStartsPairingAndThePcTypesTheCode() {
        callPc<Unit>(
            "/v1/pair/start",
            """{"deviceId":"phone-1","deviceName":"Pixel","platform":"android","host":"10.9.9.9","port":${phone.server.address.port},"expiresAtMs":${System.currentTimeMillis() + 60_000}}""",
            null
        )
        val request = assertNotNull(manager.incomingPair.value)
        assertEquals("127.0.0.1", request.peer.host, "the real sender address, not the one written in the request")
        manager.submitIncomingCode("12a3456")
        waitFor("verify sent to the phone") { phone.bodiesFor("/v1/pair/verify").isNotEmpty() }
        assertEquals("123456", connectJson.decodeFromString(PairVerifyRequest.serializer(), phone.bodiesFor("/v1/pair/verify").first()).code)
        waitFor("paired") { manager.settings.value.isPaired }
    }
}
