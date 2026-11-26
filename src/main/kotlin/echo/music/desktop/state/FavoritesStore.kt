package echo.music.desktop.state

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Position id of the song inside a YouTube playlist (to move or remove it there). */
    val setVideoId: String? = null
) {
    fun toSongItem(): SongItem {
        return SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artist, id = null)),
            thumbnail = thumbnail,
            duration = duration,
            setVideoId = setVideoId
        )
    }

    companion object {
        fun fromSongItem(song: SongItem): SavedSong {
            return SavedSong(
                id = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                thumbnail = song.thumbnail,
                duration = song.duration,
                setVideoId = song.setVideoId
            )
        }
    }
}

@Serializable
data class LibraryData(
    val favorites: List<SavedSong> = emptyList(),
    val history: List<SavedSong> = emptyList()
)

object FavoritesStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir = File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }
    private val storageFile = File(storageDir, "library.json")

    private var currentData: LibraryData = load()

    /** Re-reads library.json (after restoring a backup). */
    @Synchronized
    fun reload() {
        currentData = load()
    }

    @Synchronized
    private fun load(): LibraryData {
        return try {
            if (storageFile.exists()) {
                val text = storageFile.readText()
                json.decodeFromString<LibraryData>(text)
            } else {
                LibraryData()
            }
        } catch (e: Exception) {
            println("[FavoritesStore] Error loading library: ${e.message}")
            LibraryData()
        }
    }

    @Synchronized
    private fun save() {
        try {
            val text = json.encodeToString(currentData)
            storageFile.writeText(text)
        } catch (e: Exception) {
            println("[FavoritesStore] Error saving library: ${e.message}")
        }
    }

    @Synchronized
    fun getFavorites(): List<SongItem> = currentData.favorites.map { it.toSongItem() }

    @Synchronized
    fun getHistory(): List<SongItem> = currentData.history.map { it.toSongItem() }

    @Synchronized
    fun isFavorite(songId: String): Boolean = currentData.favorites.any { it.id == songId }

    @Synchronized
    fun toggleFavorite(song: SongItem): Boolean {
        val exists = isFavorite(song.id)
        currentData = if (exists) {
            currentData.copy(favorites = currentData.favorites.filterNot { it.id == song.id })
        } else {
            val saved = SavedSong.fromSongItem(song)
            currentData.copy(favorites = listOf(saved) + currentData.favorites.filterNot { it.id == song.id })
        }
        save()
        return !exists
    }

    @Synchronized
    fun addFavorite(song: SongItem) {
        if (!isFavorite(song.id)) {
            val saved = SavedSong.fromSongItem(song)
            currentData = currentData.copy(favorites = listOf(saved) + currentData.favorites)
            save()
        }
    }

    @Synchronized
    fun addToHistory(song: SongItem) {
        val saved = SavedSong.fromSongItem(song)
        val filtered = currentData.history.filterNot { it.id == song.id }.take(49)
        currentData = currentData.copy(history = listOf(saved) + filtered)
        save()
    }

    @Synchronized
    fun clearHistory() {
        currentData = currentData.copy(history = emptyList())
        save()
    }
}
