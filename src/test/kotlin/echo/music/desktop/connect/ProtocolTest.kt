package echo.music.desktop.connect

import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.SavedSong
import echo.music.desktop.state.UserPlaylist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTest {
    /** What Android's stateToJson sends (field names from LanSyncManager). */
    @Test
    fun readsAndroidState() {
        val raw = """
            {"deviceId":"a1","deviceName":"Pixel","platform":"android","playing":true,"volume":0.8,
             "positionMs":61000,"durationMs":200000,"shuffle":false,"repeat":"all",
             "current":{"videoId":"abc","title":"Song","artist":"Band","durationSec":200,"thumbnail":"https://t"},
             "queue":[{"videoId":"abc","title":"Song","artist":"Band"},{"videoId":"def","title":"Two","artist":"Band"}],
             "index":0,"episodes":[],"extraFieldFromTheFuture":1}
        """.trimIndent()
        val state = connectJson.decodeFromString(PeerStateDto.serializer(), raw)
        assertEquals("Pixel", state.deviceName)
        assertEquals(61_000, state.positionMs)
        assertEquals("all", state.repeat)
        assertEquals(listOf("abc", "def"), state.queue.map { it.videoId })
    }

    @Test
    fun readsSparseControlAndHandoff() {
        val pause = connectJson.decodeFromString(ControlCommand.serializer(), """{"action":"pause","controllerPeerId":"a1"}""")
        assertEquals("pause", pause.action)
        assertEquals("a1", pause.controllerPeerId)
        val handoff = connectJson.decodeFromString(HandoffPayload.serializer(), """{"items":[{"videoId":"x"}],"positionMs":5000}""")
        assertEquals(5000, handoff.positionMs)
        assertTrue(handoff.playImmediately)
    }

    @Test
    fun writesTheFieldNamesAndroidReads() {
        val state = PeerStateDto(deviceId = "pc", deviceName = "PC", current = TrackDto(videoId = "abc", title = "T", artist = "A"))
        val obj = Json.parseToJsonElement(connectJson.encodeToString(PeerStateDto.serializer(), state)).jsonObject
        for (key in listOf("deviceId", "deviceName", "platform", "playing", "volume", "positionMs", "durationMs", "shuffle", "repeat", "current", "queue", "index", "updatedAt")) {
            assertTrue(key in obj, "missing $key")
        }
        val bundle = connectJson.encodeToString(SyncBundle.serializer(), SyncBundle(senderId = "pc"))
        assertTrue("\"googleAccount\"" !in bundle, "no account unless shared")
    }

    @Test
    fun playlistsMergeByTitleWithoutDuplicates() {
        val mine = UserPlaylist(
            id = "local_1",
            title = "Gym",
            sourceType = PlaylistSourceType.LOCAL,
            savedSongs = listOf(SavedSong("abc", "Song", "Band", ""))
        )
        val youtube = UserPlaylist(id = "yt_PL1", title = "Chill", sourceType = PlaylistSourceType.YOUTUBE_MUSIC)
        val incoming = listOf(
            PlaylistSyncDto(syncId = "local:7", title = " gym ", tracks = listOf(TrackDto("abc", "Song"), TrackDto("def", "New"), TrackDto("def", "New"))),
            PlaylistSyncDto(syncId = "local:8", title = "Chill", tracks = listOf(TrackDto("ghi", "Calm"), TrackDto("", "No id"))),
            PlaylistSyncDto(syncId = "spotify:1", title = "Spotify one", isSpotify = true, tracks = listOf(TrackDto("", "x")))
        )
        val changed = PlaylistSync.merge(listOf(mine, youtube), incoming, "Pixel", now = 42)
        assertEquals(2, changed.size)
        val gym = changed.first { it.id == "local_1" }
        assertEquals(listOf("abc", "def"), gym.savedSongs.map { it.id })
        // A YouTube playlist with the same title is not touched: a new local one is made.
        val chill = changed.first { it.title == "Chill" }
        assertEquals(PlaylistSourceType.LOCAL, chill.sourceType)
        assertEquals(listOf("ghi"), chill.savedSongs.map { it.id })
        assertEquals("Pixel", chill.author)
        // Nothing new the second time.
        val again = PlaylistSync.merge(listOf(mine.copy(savedSongs = gym.savedSongs), youtube, chill), incoming, "Pixel", now = 43)
        assertTrue(again.isEmpty())
    }

    @Test
    fun onlyEchoMusicPlaylistsAreSent() {
        val out = PlaylistSync.outgoing(
            listOf(
                UserPlaylist(id = "local_1", title = "Mine", sourceType = PlaylistSourceType.LOCAL, savedSongs = listOf(SavedSong("abc", "S", "A", "t"))),
                UserPlaylist(id = "yt_1", title = "YT", sourceType = PlaylistSourceType.YOUTUBE_MUSIC)
            ),
            now = 1
        )
        assertEquals(listOf("Mine"), out.map { it.title })
        assertEquals("abc", out.single().tracks.single().videoId)
    }
}
