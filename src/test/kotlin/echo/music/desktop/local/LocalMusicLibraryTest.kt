package echo.music.desktop.local

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMusicLibraryTest {
    companion object {
        // Temp home so the index never touches the real ~/.echomusic.
        private val home: File = Files.createTempDirectory("echo-local-test").toFile()
            .also { System.setProperty("user.home", it.absolutePath) }
    }

    /** Minimal valid 16-bit mono PCM WAV of [seconds] of silence. */
    private fun writeWav(file: File, seconds: Int) {
        val rate = 8000
        val data = ByteArray(rate * 2 * seconds)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        file.writeBytes(header.array() + data)
    }

    @Test
    fun scansPlayableFilesAndReadsNames() = runBlocking {
        val music = File(home, "Music/Album").apply { mkdirs() }
        writeWav(File(music, "Silencio.wav"), 1)
        File(music, "Juanes - La Camisa Negra.mp3").writeBytes(ByteArray(64)) // not real audio: name fallback
        File(music, "lossless.flac").writeBytes(ByteArray(64)) // not playable on desktop: skipped
        File(home, "Music/.hidden").mkdirs()
        writeWav(File(home, "Music/.hidden/oculto.wav"), 1) // hidden folders are skipped

        val appDownloads = File(home, "Music/EchoDownloads").apply { mkdirs() }
        writeWav(File(appDownloads, "_EOuntUHw6M.wav"), 1) // app downloads are excluded (shown in Descargas)

        val tracks = LocalMusicLibrary.scan(listOf(File(home, "Music").absolutePath), excluded = listOf(appDownloads))
        assertEquals(2, tracks.size, tracks.toString())

        val wav = tracks.first { it.path.endsWith("Silencio.wav") }
        assertEquals("Silencio", wav.title)
        assertEquals(1, wav.durationSec)

        val mp3 = tracks.first { it.path.endsWith(".mp3") }
        assertEquals("La Camisa Negra", mp3.title)
        assertEquals("Juanes", mp3.artist)

        // Ids map back to files, and songs carry a playable file thumbnail/id.
        assertEquals(wav.path, LocalMusicLibrary.pathFor(wav.id))
        assertTrue(LocalMusicLibrary.isLocal(wav.toSongItem().id))
        assertEquals("Juanes", mp3.toSongItem().artists.first().name)
    }

    @Test
    fun removedFolderEmptiesLibraryAndDeletedFilesDisappear() = runBlocking {
        val dir = File(home, "Other").apply { mkdirs() }
        val f = File(dir, "uno.wav").also { writeWav(it, 1) }
        val first = LocalMusicLibrary.scan(listOf(dir.absolutePath))
        val id = first.single().id
        f.delete()
        assertNull(LocalMusicLibrary.pathFor(id), "a deleted file is not playable")
        assertTrue(LocalMusicLibrary.scan(listOf(dir.absolutePath)).isEmpty())
        assertTrue(LocalMusicLibrary.scan(emptyList()).isEmpty())
    }
}
