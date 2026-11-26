package echo.music.desktop.state

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A song as stored in the saved session (keeps artist ids so artist links still work). */
@Serializable
data class SessionSong(
    val id: String,
    val title: String,
    val artists: List<SessionArtist> = emptyList(),
    val albumName: String? = null,
    val albumId: String? = null,
    val thumbnail: String = "",
    val duration: Int? = null,
    val explicit: Boolean = false
) {
    fun toSongItem() = SongItem(
        id = id,
        title = title,
        artists = artists.map { Artist(name = it.name, id = it.id) },
        album = if (albumName != null && albumId != null) Album(name = albumName, id = albumId) else null,
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit
    )

    companion object {
        fun from(song: SongItem) = SessionSong(
            id = song.id,
            title = song.title,
            artists = song.artists.map { SessionArtist(it.name, it.id) },
            albumName = song.album?.name,
            albumId = song.album?.id,
            thumbnail = song.thumbnail,
            duration = song.duration,
            explicit = song.explicit
        )
    }
}

@Serializable
data class SessionArtist(val name: String, val id: String? = null)

/** What was playing when the app closed. */
@Serializable
data class PlaybackSession(
    val queue: List<SessionSong> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val savedAt: Long = System.currentTimeMillis()
)

/** Saves/loads the queue and position so playback can continue after restarting the app. */
object PlaybackSessionStore {
    private const val MAX_QUEUE = 500

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File by lazy {
        File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }.let { File(it, "playback_session.json") }
    }

    fun load(): PlaybackSession? = try {
        if (!file.exists()) null
        else json.decodeFromString(PlaybackSession.serializer(), file.readText())
            .takeIf { it.queue.isNotEmpty() && it.currentIndex in it.queue.indices }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun save(queue: List<SongItem>, currentIndex: Int, positionMs: Long) {
        try {
            if (queue.isEmpty() || currentIndex !in queue.indices) {
                file.delete()
                return
            }
            // Keep a window around the current song so huge radio queues stay small on disk.
            val start = (currentIndex - MAX_QUEUE / 2).coerceAtLeast(0)
            val end = (start + MAX_QUEUE).coerceAtMost(queue.size)
            val session = PlaybackSession(
                queue = queue.subList(start, end).map(SessionSong::from),
                currentIndex = currentIndex - start,
                positionMs = positionMs.coerceAtLeast(0L)
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(PlaybackSession.serializer(), session))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }
}
