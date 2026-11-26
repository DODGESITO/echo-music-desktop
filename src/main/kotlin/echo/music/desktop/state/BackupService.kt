package echo.music.desktop.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore of the user's data (playlists, favorites & history, stats, settings,
 * equalizer, blocked artists) as a .zip, like the phone app's Backup & Restore.
 *
 * Never included: the YouTube account/cookie, downloads (paths of this PC) and the
 * translation API key (encrypted for this Windows user, useless elsewhere).
 */
object BackupService {
    /** Files that go into a backup (all inside ~/.echomusic). */
    val BACKUP_FILES = listOf(
        "playlists.json",
        "library.json",
        "stats.json",
        "preferences.json",
        "equalizer.json",
        "blocked.json"
    )

    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024

    /** Preferences encrypted for this Windows user: never exported, kept from this PC on restore. */
    private val SECRET_KEYS = listOf("translationApiKey", "listenBrainzToken", "lastFmSecret", "lastFmSessionKey", "lastFmUsername")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val dataDir: File get() = File(System.getProperty("user.home"), ".echomusic")

    data class Summary(val files: List<String>)

    fun export(target: File): Result<Summary> = runCatching {
        val included = mutableListOf<String>()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (name in BACKUP_FILES) {
                val f = File(dataDir, name)
                if (!f.isFile) continue
                val bytes = if (name == "preferences.json") stripSecrets(f.readText()).toByteArray() else f.readBytes()
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
                included += name
            }
        }
        check(included.isNotEmpty()) { "No hay datos para respaldar" }
        Summary(included)
    }

    /**
     * Restores a backup made by [export]. Only known file names are accepted (anything else in
     * the zip is ignored), each must be valid JSON, and nothing is written unless all are valid.
     */
    fun import(source: File): Result<Summary> = runCatching {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory || name !in BACKUP_FILES) continue
                val bytes = zip.readNBytes((MAX_ENTRY_BYTES + 1).toInt())
                require(bytes.size <= MAX_ENTRY_BYTES) { "$name es demasiado grande" }
                Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) // must be valid JSON
                entries[name] = bytes
            }
        }
        require(entries.isNotEmpty()) { "El archivo no es una copia de seguridad de Echo Music" }

        dataDir.mkdirs()
        for ((name, bytes) in entries) {
            val target = File(dataDir, name)
            val finalBytes = if (name == "preferences.json") {
                keepLocalSecrets(bytes.toString(Charsets.UTF_8), target).toByteArray()
            } else bytes
            val tmp = File(dataDir, "$name.restore")
            tmp.writeBytes(finalBytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
        Summary(entries.keys.sorted())
    }

    /** Removes encrypted secrets (translation key, scrobbling tokens) from exported preferences. */
    private fun stripSecrets(prefsJson: String): String = try {
        val obj = Json.parseToJsonElement(prefsJson).jsonObject
        json.encodeToString(JsonObject.serializer(), JsonObject(obj - SECRET_KEYS.toSet()))
    } catch (_: Exception) {
        prefsJson
    }

    /** Keeps this PC's secrets when restoring preferences from a backup. */
    private fun keepLocalSecrets(restoredJson: String, currentFile: File): String = try {
        val restored = Json.parseToJsonElement(restoredJson).jsonObject
        val local = if (currentFile.isFile) Json.parseToJsonElement(currentFile.readText()).jsonObject else JsonObject(emptyMap())
        val merged = (restored - SECRET_KEYS.toSet()).toMutableMap()
        for (key in SECRET_KEYS) {
            val value = local[key]
            if (value != null && value is JsonPrimitive) merged[key] = value
        }
        json.encodeToString(JsonObject.serializer(), JsonObject(merged))
    } catch (_: Exception) {
        restoredJson
    }
}
