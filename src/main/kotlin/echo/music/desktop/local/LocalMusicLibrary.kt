package echo.music.desktop.local

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.coroutineContext

/** A music file found on this PC. */
@Serializable
data class LocalTrack(
    val path: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val trackNumber: Int? = null,
    val artworkPath: String? = null,
    val lastModified: Long = 0L,
    val size: Long = 0L
) {
    val id: String get() = LocalMusicLibrary.idFor(path)

    fun toSongItem() = SongItem(
        id = id,
        title = title,
        artists = artist.split(", ").filter { it.isNotBlank() }.map { Artist(name = it, id = null) }
            .ifEmpty { listOf(Artist(name = "Artista desconocido", id = null)) },
        album = album.takeIf { it.isNotBlank() }?.let { Album(name = it, id = "") },
        duration = durationSec.takeIf { it > 0 },
        thumbnail = artworkPath?.let { File(it).toURI().toString() } ?: ""
    )
}

@Serializable
private data class LocalIndex(val tracks: List<LocalTrack> = emptyList())

/**
 * Local music ("Música local" in the phone app): scans folders for audio files the desktop
 * player can play, reads their tags (title, artist, album, cover) and keeps an index in
 * ~/.echomusic/local_library.json so later scans only re-read changed files.
 */
object LocalMusicLibrary {
    const val ID_PREFIX = "local:"

    /** Formats JavaFX Media can play. FLAC/OGG/OPUS are not supported by the desktop player. */
    val PLAYABLE_EXTENSIONS = setOf("mp3", "m4a", "aac", "mp4", "wav", "aif", "aiff")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dataDir get() = File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }
    private val indexFile get() = File(dataDir, "local_library.json")
    private val artworkDir get() = File(dataDir, "local_artwork").apply { mkdirs() }

    @Volatile private var byId: Map<String, LocalTrack> = emptyMap()

    // jaudiotagger logs every tag detail; keep the console clean. The reference must be kept:
    // java.util.logging only holds loggers weakly, so an unreferenced level setting is lost.
    private val tagLogger: Logger = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF }

    init {
        byId = loadIndex().associateBy { it.id }
    }

    val tracks: List<LocalTrack> get() = byId.values.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber ?: 0 }, { it.title.lowercase() }))

    fun idFor(path: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(File(path).absolutePath.lowercase().toByteArray())
        return ID_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    fun isLocal(songId: String) = songId.startsWith(ID_PREFIX)

    /** File path for a local song id, if the file still exists. */
    fun pathFor(songId: String): String? = byId[songId]?.path?.takeIf { File(it).isFile }

    /**
     * Scans [folders] recursively. Unchanged files keep their previous tags; new or modified
     * files are read again. Files in folders no longer listed disappear from the library.
     */
    suspend fun scan(
        folders: List<String>,
        excluded: List<File> = emptyList(),
        onProgress: (found: Int) -> Unit = {}
    ): List<LocalTrack> =
        withContext(Dispatchers.IO) {
            val excludedPaths = excluded.map { it.absoluteFile.normalize() }
            val previous = byId.values.associateBy { File(it.path).absolutePath }
            val result = mutableListOf<LocalTrack>()
            for (folder in folders.map(::File).filter { it.isDirectory }) {
                folder.walkTopDown()
                    .onEnter { dir ->
                        // Hidden folders and the app's own download folder (those songs are in Descargas).
                        !dir.name.startsWith(".") && excludedPaths.none { dir.absoluteFile.normalize() == it }
                    }
                    .filter { it.isFile && it.extension.lowercase() in PLAYABLE_EXTENSIONS }
                    .forEach { file ->
                        coroutineContext.ensureActive()
                        val known = previous[file.absolutePath]
                        val track = if (known != null && known.lastModified == file.lastModified() && known.size == file.length()) {
                            known
                        } else {
                            readTrack(file)
                        }
                        result += track
                        if (result.size % 25 == 0) onProgress(result.size)
                    }
            }
            val unique = result.distinctBy { it.id }
            byId = unique.associateBy { it.id }
            saveIndex(unique)
            onProgress(unique.size)
            tracks
        }

    /** Reads tags; falls back to "Artist - Title" from the file name when tags are missing. */
    fun readTrack(file: File): LocalTrack {
        val fromName = file.nameWithoutExtension
        val nameArtist = fromName.substringBefore(" - ", "").trim()
        val nameTitle = if (fromName.contains(" - ")) fromName.substringAfter(" - ").trim() else fromName
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            fun field(key: FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull()?.trim().orEmpty()
            val artwork = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.let { saveArtwork(file, it) }
            LocalTrack(
                path = file.absolutePath,
                title = field(FieldKey.TITLE).ifBlank { nameTitle },
                artist = field(FieldKey.ARTIST).ifBlank { field(FieldKey.ALBUM_ARTIST) }.ifBlank { nameArtist },
                album = field(FieldKey.ALBUM),
                durationSec = audio.audioHeader?.trackLength ?: 0,
                trackNumber = field(FieldKey.TRACK).substringBefore("/").toIntOrNull(),
                artworkPath = artwork,
                lastModified = file.lastModified(),
                size = file.length()
            )
        } catch (_: Exception) {
            LocalTrack(
                path = file.absolutePath,
                title = nameTitle,
                artist = nameArtist,
                lastModified = file.lastModified(),
                size = file.length()
            )
        }
    }

    private fun saveArtwork(file: File, bytes: ByteArray): String? = try {
        val name = idFor(file.absolutePath).removePrefix(ID_PREFIX) + ".img"
        val target = File(artworkDir, name)
        if (!target.exists() || target.length() != bytes.size.toLong()) target.writeBytes(bytes)
        target.absolutePath
    } catch (_: Exception) {
        null
    }

    private fun loadIndex(): List<LocalTrack> = try {
        if (indexFile.exists()) json.decodeFromString(LocalIndex.serializer(), indexFile.readText()).tracks else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveIndex(tracks: List<LocalTrack>) {
        try {
            indexFile.writeText(json.encodeToString(LocalIndex.serializer(), LocalIndex(tracks)))
        } catch (_: Exception) {
        }
    }
}
