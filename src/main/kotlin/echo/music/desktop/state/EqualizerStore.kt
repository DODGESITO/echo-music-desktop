package echo.music.desktop.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EqualizerPreset(
    val name: String,
    val gains: List<Float> // 10 bands
)

@Serializable
data class EqualizerSettings(
    val enabled: Boolean = false,
    val currentPresetName: String = "Plano",
    val bandGains: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
)

object EqualizerStore {
    val BANDS_HZ = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val PRESETS = listOf(
        EqualizerPreset("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("Bass Boost", listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 7f, 8f)),
        EqualizerPreset("Electrónica", listOf(6f, 5f, 2f, 0f, -2f, 2f, 1f, 3f, 5f, 6f)),
        EqualizerPreset("Rock", listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 5f)),
        EqualizerPreset("Pop", listOf(-1f, 2f, 4f, 4f, 2f, -1f, -1f, 1f, 3f, 4f)),
        EqualizerPreset("Vocal", listOf(-2f, -3f, -1f, 2f, 5f, 5f, 4f, 2f, 0f, -1f)),
        EqualizerPreset("Nothing 5.0", listOf(5f, 4f, 1f, 0f, 2f, 1f, 3f, 4f, 5f, 4f))
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val eqFile: File by lazy {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".echomusic")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "equalizer.json")
    }

    fun getSettings(): EqualizerSettings {
        return try {
            if (!eqFile.exists()) return EqualizerSettings()
            val text = eqFile.readText()
            if (text.isBlank()) return EqualizerSettings()
            json.decodeFromString<EqualizerSettings>(text)
        } catch (e: Exception) {
            EqualizerSettings()
        }
    }

    fun saveSettings(settings: EqualizerSettings) {
        try {
            val text = json.encodeToString(settings)
            eqFile.writeText(text)
        } catch (ignored: Exception) {}
    }
}
