package echo.music.desktop.state

import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class PlaylistSourceType {
    YOUTUBE_MUSIC,
    SPOTIFY,
    LOCAL,
    PODCAST
}

@Serializable
data class UserPlaylist(
    val id: String,
    val title: String,
    val author: String = "Echo Music",
    val thumbnailUrl: String? = null,
    val sourceType: PlaylistSourceType = PlaylistSourceType.LOCAL,
    val savedSongs: List<SavedSong> = emptyList()
) {
    val songs: List<SongItem> get() = savedSongs.map { it.toSongItem() }

    companion object {
        fun fromSongs(
            id: String,
            title: String,
            author: String = "Echo Music",
            thumbnailUrl: String? = null,
            sourceType: PlaylistSourceType = PlaylistSourceType.LOCAL,
            songs: List<SongItem> = emptyList()
        ): UserPlaylist = UserPlaylist(
            id = id,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            sourceType = sourceType,
            savedSongs = songs.map { SavedSong.fromSongItem(it) }
        )
    }
}

object PlaylistStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val playlistsFile: File by lazy {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".echomusic")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "playlists.json")
    }

    fun getPlaylists(): List<UserPlaylist> {
        return try {
            if (!playlistsFile.exists()) return emptyList()
            val text = playlistsFile.readText()
            if (text.isBlank()) return emptyList()
            json.decodeFromString<List<UserPlaylist>>(text)
        } catch (e: Exception) {
            println("[PlaylistStore] Error loading playlists: ${e.message}")
            emptyList()
        }
    }

    fun getPlaylist(playlistId: String): UserPlaylist? {
        return getPlaylists().find { it.id == playlistId }
    }

    fun savePlaylist(playlist: UserPlaylist) {
        val list = getPlaylists().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == playlist.id }
        if (existingIndex >= 0) {
            list[existingIndex] = playlist
        } else {
            list.add(0, playlist)
        }
        saveAll(list)
    }

    fun deletePlaylist(playlistId: String) {
        val list = getPlaylists().filterNot { it.id == playlistId }
        saveAll(list)
    }

    fun updatePlaylists(playlists: List<UserPlaylist>) {
        saveAll(playlists)
    }

    private fun saveAll(playlists: List<UserPlaylist>) {
        try {
            val text = json.encodeToString(playlists)
            playlistsFile.writeText(text)
        } catch (e: Exception) {
            println("[PlaylistStore] Error writing playlists: ${e.message}")
        }
    }
}
