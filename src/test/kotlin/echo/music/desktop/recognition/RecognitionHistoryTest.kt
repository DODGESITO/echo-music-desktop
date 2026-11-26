package echo.music.desktop.recognition

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import echo.music.desktop.state.SavedSong
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecognitionHistoryTest {
    companion object {
        // Temp home: never touches the real ~/.echomusic.
        private val home: File = Files.createTempDirectory("echo-recog-test").toFile()
            .also { System.setProperty("user.home", it.absolutePath) }
    }

    @Test
    fun addMovesDuplicatesToTopRemoveAndClear() {
        RecognitionHistoryStore.clear()
        val a = RecognizedSong("Flowers", "Miley Cyrus", recognizedAt = 1)
        val b = RecognizedSong("Ojitos Lindos", "Bad Bunny", recognizedAt = 2)
        RecognitionHistoryStore.add(a)
        RecognitionHistoryStore.add(b)
        val again = RecognitionHistoryStore.add(a.copy(recognizedAt = 3))
        assertEquals(listOf("Flowers", "Ojitos Lindos"), again.map { it.title }, "no duplicates, newest first")
        assertEquals(listOf("Ojitos Lindos"), RecognitionHistoryStore.remove(again.first()).map { it.title })
        RecognitionHistoryStore.clear()
        assertTrue(RecognitionHistoryStore.load().isEmpty())
    }

    @Test
    fun savedSongKeepsSetVideoIdAndReadsOldFiles() {
        val song = SongItem(id = "v1", title = "T", artists = listOf(Artist("A", null)), thumbnail = "", setVideoId = "SVID")
        val saved = SavedSong.fromSongItem(song)
        assertEquals("SVID", saved.toSongItem().setVideoId)
        // Playlists saved before setVideoId existed still load (field missing -> null).
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(SavedSong.serializer(), """{"id":"v1","title":"T","artist":"A","thumbnail":""}""")
        assertNull(old.setVideoId)
    }
}
