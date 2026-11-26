package echo.music.desktop.recognition

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A song identified with Echo Find. */
@Serializable
data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: Long = System.currentTimeMillis()
)

@Serializable
private data class RecognitionHistory(val songs: List<RecognizedSong> = emptyList())

/** Echo Find history (~/.echomusic/recognition_history.json), newest first, up to 200 songs. */
object RecognitionHistoryStore {
    private const val MAX = 200
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file get() = File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }.let { File(it, "recognition_history.json") }

    @Synchronized
    fun load(): List<RecognizedSong> = try {
        if (file.exists()) json.decodeFromString(RecognitionHistory.serializer(), file.readText()).songs else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Adds a song (moving it to the top if it was already there) and returns the new list. */
    @Synchronized
    fun add(song: RecognizedSong): List<RecognizedSong> {
        val list = (listOf(song) + load().filterNot { it.title.equals(song.title, true) && it.artist.equals(song.artist, true) }).take(MAX)
        save(list)
        return list
    }

    @Synchronized
    fun remove(song: RecognizedSong): List<RecognizedSong> {
        val list = load().filterNot { it.recognizedAt == song.recognizedAt && it.title == song.title }
        save(list)
        return list
    }

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<RecognizedSong>) {
        try {
            file.writeText(json.encodeToString(RecognitionHistory.serializer(), RecognitionHistory(list)))
        } catch (_: Exception) {
        }
    }
}
