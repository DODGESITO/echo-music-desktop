package echo.music.desktop.connect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class EchoConnectSettings(
    /** Off until the user turns it on: it opens a port on the local network. */
    val enabled: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val pairedPeerId: String? = null,
    val pairedPeerName: String? = null,
    /** Last known address of the paired phone (only requests from it are trusted). */
    val pairedHost: String? = null,
    val pairedPort: Int = 0,
    val pairToken: String? = null,
    /** Send this PC's YouTube Music session to the paired phone (off: it is a password-like secret). */
    val shareAccount: Boolean = false,
    val lastSyncAtMs: Long = 0
) {
    val isPaired: Boolean get() = !pairedPeerId.isNullOrBlank()
}

/** ~/.echomusic/echo_connect.json */
object EchoConnectStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    internal var fileOverride: File? = null
    private val file: File
        get() = fileOverride ?: File(File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }, "echo_connect.json")

    @Synchronized
    fun load(): EchoConnectSettings {
        val loaded = runCatching { json.decodeFromString(EchoConnectSettings.serializer(), file.readText()) }.getOrNull()
            ?: EchoConnectSettings()
        if (loaded.deviceId.isNotBlank()) return loaded
        val withId = loaded.copy(deviceId = UUID.randomUUID().toString().replace("-", "").take(16))
        save(withId)
        return withId
    }

    @Synchronized
    fun save(settings: EchoConnectSettings) {
        runCatching { file.writeText(json.encodeToString(EchoConnectSettings.serializer(), settings)) }
    }

    fun defaultDeviceName(): String =
        (System.getenv("COMPUTERNAME") ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull())
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> name.lowercase().replaceFirstChar { it.uppercase() } }
            ?: "Echo Music PC"
}
