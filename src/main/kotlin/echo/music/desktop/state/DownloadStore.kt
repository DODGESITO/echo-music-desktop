package echo.music.desktop.state

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DownloadedSongRecord(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: Int? = null,
    val filePath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSongItem(): SongItem {
        return SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artist, id = null)),
            thumbnail = thumbnail,
            duration = duration
        )
    }

    companion object {
        fun fromSongItem(
            song: SongItem,
            filePath: String,
            fileSizeBytes: Long
        ): DownloadedSongRecord {
            return DownloadedSongRecord(
                id = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                thumbnail = song.thumbnail,
                duration = song.duration,
                filePath = filePath,
                fileSizeBytes = fileSizeBytes
            )
        }
    }
}

@Serializable
data class DownloadsIndex(
    val songs: List<DownloadedSongRecord> = emptyList()
)

object DownloadStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile: File by lazy {
        File(System.getProperty("user.home"), ".echomusic/storage_config.json")
    }

    var downloadsDir: File = loadDownloadsDir()
        private set

    private fun loadDownloadsDir(): File {
        return try {
            val userHome = System.getProperty("user.home")
            val defaultDir = File(userHome, ".echomusic/downloads").apply { mkdirs() }
            val cfg = File(userHome, ".echomusic/storage_config.json")
            if (cfg.exists()) {
                val path = cfg.readText().trim()
                if (path.isNotBlank()) {
                    val dir = File(path)
                    if (!dir.exists()) dir.mkdirs()
                    return dir
                }
            }
            defaultDir
        } catch (e: Exception) {
            File(System.getProperty("user.home"), ".echomusic/downloads").apply { mkdirs() }
        }
    }

    fun setDownloadsDirectory(newDir: File): Boolean {
        return try {
            if (!newDir.exists()) newDir.mkdirs()
            downloadsDir = newDir
            configFile.writeText(newDir.absolutePath)
            currentIndex = load()
            true
        } catch (e: Exception) {
            println("[DownloadStore] Failed to update downloads directory: ${e.message}")
            false
        }
    }

    private val indexFile: File = File(System.getProperty("user.home"), ".echomusic/downloads.json")

    private var currentIndex: DownloadsIndex = load()

    @Synchronized
    private fun load(): DownloadsIndex {
        return try {
            if (indexFile.exists()) {
                val content = indexFile.readText()
                json.decodeFromString<DownloadsIndex>(content)
            } else {
                DownloadsIndex()
            }
        } catch (e: Exception) {
            println("[DownloadStore] Failed to load index: ${e.message}")
            DownloadsIndex()
        }
    }

    @Synchronized
    private fun save(data: DownloadsIndex) {
        currentIndex = data
        try {
            indexFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            println("[DownloadStore] Failed to save index: ${e.message}")
        }
    }

    @Synchronized
    fun getDownloadedSongs(): List<DownloadedSongRecord> {
        // Validate that local files still exist either in recorded path or current downloadsDir
        val validSongs = currentIndex.songs.filter {
            File(it.filePath).exists() || File(downloadsDir, "${it.id}.m4a").exists()
        }
        if (validSongs.size != currentIndex.songs.size) {
            save(DownloadsIndex(validSongs))
        }
        return validSongs
    }

    @Synchronized
    fun isDownloaded(songId: String): Boolean {
        if (File(downloadsDir, "$songId.m4a").exists()) return true
        val record = currentIndex.songs.firstOrNull { it.id == songId } ?: return false
        return File(record.filePath).exists()
    }

    @Synchronized
    fun getLocalFilePath(songId: String): String? {
        val inCurrent = File(downloadsDir, "$songId.m4a")
        if (inCurrent.exists()) return inCurrent.absolutePath
        val record = currentIndex.songs.firstOrNull { it.id == songId } ?: return null
        val file = File(record.filePath)
        return if (file.exists()) file.absolutePath else null
    }

    @Synchronized
    fun registerDownload(record: DownloadedSongRecord) {
        val filtered = currentIndex.songs.filterNot { it.id == record.id }
        save(DownloadsIndex(listOf(record) + filtered))
    }

    @Synchronized
    fun removeDownload(songId: String) {
        val record = currentIndex.songs.firstOrNull { it.id == songId }
        if (record != null) {
            try {
                val f = File(record.filePath)
                if (f.exists()) f.delete()
            } catch (ignored: Exception) {}
        }
        val remaining = currentIndex.songs.filterNot { it.id == songId }
        save(DownloadsIndex(remaining))
    }

    @Synchronized
    fun getTotalSizeBytes(): Long {
        return getDownloadedSongs().sumOf { it.fileSizeBytes }
    }

    @Synchronized
    fun getFormattedDiskSpace(): String {
        val bytes = getTotalSizeBytes()
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }
}
