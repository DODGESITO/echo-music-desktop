package echo.music.desktop.listentogether

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageCodecTest {

    @Test
    fun testEncodeDecodeProtobuf() {
        val codec = MessageCodec(format = MessageFormat.PROTOBUF, compressionEnabled = false)

        // 1. CreateRoomPayload
        val createPayload = CreateRoomPayload("TestUser")
        val encodedCreate = codec.encode(MessageTypes.CREATE_ROOM, createPayload)
        assertTrue(encodedCreate.isNotEmpty())

        val (msgType, payloadBytes) = codec.decode(encodedCreate)
        assertEquals(MessageTypes.CREATE_ROOM, msgType)

        val decodedCreate = codec.decodePayload(msgType, payloadBytes, MessageFormat.PROTOBUF) as? CreateRoomPayload
        assertNotNull(decodedCreate)
        assertEquals("TestUser", decodedCreate.username)

        // 2. PlaybackActionPayload
        val track = TrackInfo(
            id = "test-track-123",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 180000L
        )
        val actionPayload = PlaybackActionPayload(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = "test-track-123",
            position = 45000L,
            trackInfo = track,
            volume = 0.9f
        )
        val encodedAction = codec.encode(MessageTypes.PLAYBACK_ACTION, actionPayload)
        val (actionType, actionBytes) = codec.decode(encodedAction)
        assertEquals(MessageTypes.PLAYBACK_ACTION, actionType)

        val decodedAction = codec.decodePayload(actionType, actionBytes, MessageFormat.PROTOBUF) as? PlaybackActionPayload
        assertNotNull(decodedAction)
        assertEquals(PlaybackActions.CHANGE_TRACK, decodedAction.action)
        assertEquals("test-track-123", decodedAction.trackId)
        assertEquals(45000L, decodedAction.position)
        assertNotNull(decodedAction.trackInfo)
        assertEquals("Test Song", decodedAction.trackInfo?.title)
        assertEquals("Test Artist", decodedAction.trackInfo?.artist)
    }

    @Test
    fun testCompression() {
        val codec = MessageCodec(format = MessageFormat.PROTOBUF, compressionEnabled = true)

        // Large queue to exceed compression threshold
        val queue = (1..50).map { i ->
            TrackInfo(
                id = "track-$i",
                title = "Very Long Track Title Number $i with extra metadata",
                artist = "Very Famous Artist with a Long Name $i",
                album = "Album Name Number $i Deluxe Remastered",
                duration = 240000L
            )
        }
        val action = PlaybackActionPayload(
            action = PlaybackActions.PLAY,
            queue = queue
        )

        val encoded = codec.encode(MessageTypes.PLAYBACK_ACTION, action)
        val (type, bytes) = codec.decode(encoded)
        assertEquals(MessageTypes.PLAYBACK_ACTION, type)

        val decoded = codec.decodePayload(type, bytes, MessageFormat.PROTOBUF) as? PlaybackActionPayload
        assertNotNull(decoded)
        assertEquals(50, decoded.queue?.size)
        assertEquals("track-1", decoded.queue?.first()?.id)
    }

    @Test
    fun testLiveCreateRoomOnMetroserver() {
        runBlocking {
            val client = DesktopListenTogetherClient()
            try {
                client.connect("wss://metroserverx.meowery.eu/ws")
                client.createRoom("EchoDesktopTest")

                val event = withTimeout(10000) {
                    client.events.first { it is ListenTogetherEvent.RoomCreated }
                } as ListenTogetherEvent.RoomCreated

                println("Live Room created successfully! Room code: ${event.roomCode}, User ID: ${event.userId}")
                assertTrue(event.roomCode.isNotBlank())
                assertTrue(event.userId.isNotBlank())
                assertEquals(RoomRole.HOST, client.role.value)
                assertEquals(event.roomCode, client.currentRoomCode)
                assertNotNull(client.sessionToken)
                assertNotNull(client.roomState.value)
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun testLiveCreateAndJoinFlow() {
        runBlocking {
            val host = DesktopListenTogetherClient()
            val guest = DesktopListenTogetherClient()
            try {
                host.connect("wss://metroserverx.meowery.eu/ws")
                host.createRoom("HostTester")

                val hostRoomEvent = withTimeout(10000) {
                    host.events.first { it is ListenTogetherEvent.RoomCreated }
                } as ListenTogetherEvent.RoomCreated

                val roomCode = hostRoomEvent.roomCode
                println("Host created room: $roomCode")

                guest.connect("wss://metroserverx.meowery.eu/ws")
                guest.joinRoom(roomCode, "GuestTester")

                val guestJoinedEvent = withTimeout(10000) {
                    guest.events.first { it is ListenTogetherEvent.JoinApproved }
                } as ListenTogetherEvent.JoinApproved

                println("Guest joined room: ${guestJoinedEvent.roomCode}, User ID: ${guestJoinedEvent.userId}")
                assertEquals(roomCode, guestJoinedEvent.roomCode)
                assertEquals(RoomRole.GUEST, guest.role.value)
                assertNotNull(guest.sessionToken)
            } finally {
                guest.disconnect()
                host.disconnect()
            }
        }
    }
}
