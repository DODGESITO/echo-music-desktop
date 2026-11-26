package echo.music.desktop.state

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import echo.music.desktop.system.GlobalMediaKeys
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSessionStoreTest {

    private fun song(i: Int) = SongItem(
        id = "id$i",
        title = "Song $i",
        artists = listOf(Artist(name = "Artist $i", id = "UC$i")),
        album = Album(name = "Album $i", id = "MPRE$i"),
        duration = 200 + i,
        thumbnail = "https://img/$i.jpg"
    )

    init {
        // PlaybackSessionStore resolves its file lazily from user.home: point it at a temp dir.
        System.setProperty("user.home", Files.createTempDirectory("echo-session-test").toString())
    }

    @Test
    fun savesAndRestoresQueueSongAndPosition() {
        val queue = (1..5).map(::song)
        PlaybackSessionStore.save(queue, currentIndex = 2, positionMs = 61_500L)

        val session = PlaybackSessionStore.load()!!
        assertEquals(2, session.currentIndex)
        assertEquals(61_500L, session.positionMs)
        val restored = session.queue.map { it.toSongItem() }
        assertEquals(queue.map { it.id }, restored.map { it.id })
        // Artist ids and album survive, so artist/album links keep working after a restart.
        assertEquals("UC3", restored[2].artists.first().id)
        assertEquals("Album 3", restored[2].album?.name)
        assertEquals(203, restored[2].duration)
    }

    @Test
    fun hugeQueueKeepsWindowAroundCurrentSong() {
        val queue = (0 until 1200).map(::song)
        PlaybackSessionStore.save(queue, currentIndex = 1000, positionMs = 0L)
        val session = PlaybackSessionStore.load()!!
        assertTrue(session.queue.size <= 500)
        assertEquals("id1000", session.queue[session.currentIndex].id)
    }

    @Test
    fun emptyQueueClearsSession() {
        PlaybackSessionStore.save(listOf(song(1)), 0, 0L)
        PlaybackSessionStore.save(emptyList(), -1, 0L)
        assertNull(PlaybackSessionStore.load())
    }

    @Test
    fun mediaKeysHookInstallsAndStops() {
        if (!com.sun.jna.Platform.isWindows()) return
        assertTrue(GlobalMediaKeys.start { })
        assertTrue(GlobalMediaKeys.isRunning)
        GlobalMediaKeys.stop()
        assertTrue(!GlobalMediaKeys.isRunning)
        // Can be started again (e.g. toggled off and on in Settings).
        assertTrue(GlobalMediaKeys.start { })
        GlobalMediaKeys.stop()
    }
}
