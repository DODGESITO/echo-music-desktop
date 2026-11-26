package echo.music.desktop.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopPreferences(
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val previousVolume: Float = 0.8f,
    val isShuffle: Boolean = false,
    val repeatMode: String = "ALL",
    val windowWidth: Int = 1220,
    val windowHeight: Int = 800,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val isMaximized: Boolean = false,
    val minimizeToTray: Boolean = true,
    /** Apariencia: accent color (AccentTheme) and background style (BackgroundTheme). */
    val themeAccent: String = "RED",
    val themeBackground: String = "DARK",
    /** Show the animated cover (Canvas) in the expanded player when the song has one. */
    val showCanvas: Boolean = true,
    /** Media keys (play/pause, next, previous) work even when the window is not focused. */
    val globalMediaKeys: Boolean = true,
    // --- Reproducción ---
    /** Skip to the next song when one cannot be loaded or played. */
    val autoSkipOnError: Boolean = true,
    /** Even out loudness between songs using YouTube's loudness data. */
    val normalizeAudio: Boolean = true,
    /** QUIET, NORMAL, LOUD, AGGRESSIVE (same presets as the phone app). */
    val loudnessPreset: String = "NORMAL",
    val playbackSpeed: Float = 1.0f,
    /** HIGH = best AAC stream, LOW = data saver. */
    val audioQuality: String = "HIGH",
    /** Skip intros, outros, sponsors and non-music parts marked on SponsorBlock. */
    val sponsorBlock: Boolean = false,
    // --- Contenido ---
    val hideExplicit: Boolean = false,
    val hideVideoSongs: Boolean = false,
    val hideShorts: Boolean = false,
    /** Content language/country for YouTube Music ("" = same as Windows). */
    val contentLanguage: String = "",
    val contentCountry: String = "",
    // --- Letras: romanización y traducción ---
    /** Show a Latin-letters line under Japanese/Korean/Chinese/Cyrillic/Hindi/Punjabi lyrics. */
    /** Privacy: while true, played songs are not added to the history nor to Echo Wrapped stats. */
    val pauseHistory: Boolean = false,
    val lyricsRomanize: Boolean = true,
    /** Folders scanned for local music (Biblioteca → En este PC). */
    val localMusicFolders: List<String> = emptyList(),
    /** Translate lyrics automatically when they load (needs an API key). */
    val lyricsAutoTranslate: Boolean = false,
    /** OPENROUTER, DEEPL or MISTRAL (same AI services as the phone app). */
    val translationProvider: String = "OPENROUTER",
    /** Target language code for translations. */
    val translationLanguage: String = "es",
    val translationModel: String = "",
    val translationBaseUrl: String = "",
    /** API key for the translation service, encrypted with Windows DPAPI (SecretProtector). */
    val translationApiKey: String = "",
    // --- Registro de escuchas (scrobbling) ---
    /** ListenBrainz user token (encrypted). */
    val listenBrainzToken: String = "",
    /** Last.fm API account of the user (the secret is encrypted). */
    val lastFmApiKey: String = "",
    val lastFmSecret: String = "",
    /** Last.fm session after authorizing Echo Music (encrypted) and the account name. */
    val lastFmSessionKey: String = "",
    val lastFmUsername: String = "",
    val listenTogetherUsername: String = "",
    val listenTogetherServerUrl: String = "wss://metroserverx.meowery.eu/ws",
    val listenTogetherAutoApprove: Boolean = true,
    // --- Ventana y sistema ---
    /** When Echo Music starts with Windows, open it in the tray instead of showing the window. */
    val startMinimized: Boolean = true,
    /** Last position of the mini player (dp, screen coordinates). */
    val miniPlayerX: Int? = null,
    val miniPlayerY: Int? = null,
    // --- Actualizaciones ---
    val checkUpdates: Boolean = true,
    /** GitHub repository ("owner/name") whose releases are checked. */
    val updateRepo: String = echo.music.desktop.system.UpdateChecker.DEFAULT_REPO,
    val lastUpdateCheck: Long = 0L,
    /** Version the user chose to skip; it is not announced again. */
    val skippedUpdateVersion: String = ""
)

object PreferencesStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir = File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }
    private val prefsFile = File(storageDir, "preferences.json")

    var current: DesktopPreferences = load()
        private set

    /** Re-reads preferences.json (after restoring a backup). */
    @Synchronized
    fun reload() {
        current = load()
    }

    @Synchronized
    private fun load(): DesktopPreferences {
        return try {
            if (prefsFile.exists()) {
                val content = prefsFile.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString<DesktopPreferences>(content)
                } else {
                    DesktopPreferences()
                }
            } else {
                DesktopPreferences()
            }
        } catch (e: Exception) {
            println("[PreferencesStore] Failed to load preferences: ${e.message}")
            DesktopPreferences()
        }
    }

    @Synchronized
    fun update(transform: (DesktopPreferences) -> DesktopPreferences) {
        current = transform(current)
        save()
    }

    @Synchronized
    private fun save() {
        try {
            prefsFile.writeText(json.encodeToString(current))
        } catch (e: Exception) {
            println("[PreferencesStore] Failed to save preferences: ${e.message}")
        }
    }
}
