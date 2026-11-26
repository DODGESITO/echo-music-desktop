package echo.music.desktop.state

import com.music.innertube.YouTube
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BlockedArtist(val id: String?, val name: String)

@Serializable
data class DislikedSong(val id: String, val title: String, val artist: String = "")

@Serializable
data class ContentBlocks(
    val artists: List<BlockedArtist> = emptyList(),
    val songs: List<DislikedSong> = emptyList()
)

/**
 * Blocked artists and disliked songs ("No me gusta"). They are pushed into
 * `YouTube.blockedArtists` / `YouTube.dislikedSongs`, which the shared innertube filters
 * (home feed, `filterBlockedArtists`, `filterDislikedSongs`) already use, in the
 * same "id||name" format as the phone app.
 */
object ContentBlockStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val file: File by lazy {
        File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }.let { File(it, "blocked.json") }
    }

    var current: ContentBlocks = ContentBlocks()
        private set

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        current = try {
            if (file.exists()) json.decodeFromString(ContentBlocks.serializer(), file.readText()) else ContentBlocks()
        } catch (_: Exception) {
            ContentBlocks()
        }
        publish()
    }

    @Synchronized
    fun update(transform: (ContentBlocks) -> ContentBlocks) {
        current = transform(current)
        publish()
        try {
            file.writeText(json.encodeToString(ContentBlocks.serializer(), current))
        } catch (_: Exception) {
        }
    }

    private fun publish() {
        YouTube.blockedArtists = current.artists.map { "${it.id.orEmpty()}||${it.name}" }.toSet()
        YouTube.dislikedSongs = current.songs.map { "${it.id}||${it.title}" }.toSet()
    }
}
