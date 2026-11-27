package echo.music.desktop.spotify

import echo.music.desktop.account.CookieLogin
import echo.music.desktop.account.EdgeAuthService
import echo.music.desktop.system.SecretProtector
import echo.music.iad1tya.spotify.Spotify
import echo.music.iad1tya.spotify.SpotifyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Who is signed in to Spotify (the cookies stay encrypted on disk, never in backups). */
data class SpotifyProfile(val name: String, val avatarUrl: String?)

/**
 * The user's Spotify session, like the phone app's SpotifySessionManager: the `sp_dc` cookie from
 * the web login, exchanged for short-lived access tokens (refreshed automatically).
 */
object SpotifyAccount {
    @Serializable
    private data class Stored(
        val spDc: String,
        val spKey: String = "",
        val name: String? = null,
        val avatarUrl: String? = null
    )

    private const val GRACE_MS = 60_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var tokenExpiresAt = 0L

    private val dir get() = File(System.getProperty("user.home"), ".echomusic")
    private val file get() = File(dir, "spotify.json")
    val browserProfileDir: File get() = File(dir, "browser_spotify_profile")

    private fun load(): Stored? = runCatching {
        val s = json.decodeFromString(Stored.serializer(), file.readText())
        s.copy(spDc = SecretProtector.unprotect(s.spDc), spKey = SecretProtector.unprotect(s.spKey))
    }.getOrNull()

    private fun save(s: Stored) {
        dir.mkdirs()
        val encrypted = s.copy(spDc = SecretProtector.protect(s.spDc), spKey = SecretProtector.protect(s.spKey))
        val tmp = File(dir, "spotify.json.tmp")
        tmp.writeText(json.encodeToString(Stored.serializer(), encrypted))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    val isSignedIn: Boolean get() = file.exists() && load() != null

    fun profile(): SpotifyProfile? = load()?.let { SpotifyProfile(it.name ?: "Spotify", it.avatarUrl) }

    /** Makes sure [Spotify] has a valid token; false when not signed in or the session expired. */
    suspend fun ensureToken(): Boolean = mutex.withLock {
        if (Spotify.accessToken != null && tokenExpiresAt > System.currentTimeMillis() + GRACE_MS) return@withLock true
        val stored = withContext(Dispatchers.IO) { load() } ?: return@withLock false
        val token = withContext(Dispatchers.IO) { SpotifyAuth.fetchAccessToken(stored.spDc, stored.spKey) }
        token.onSuccess {
            Spotify.accessToken = it.accessToken
            tokenExpiresAt = it.accessTokenExpirationTimestampMs
        }.onFailure { e ->
            Spotify.accessToken = null
            // The cookie no longer works (signed out on the web, expired): forget it.
            if ((e as? Spotify.SpotifyException)?.statusCode == 401) signOut()
        }
        token.isSuccess
    }

    /** Opens the Spotify login in an Edge window; [onDone] gets the profile or an error. */
    fun signIn(onStatus: (String) -> Unit, onDone: (Result<SpotifyProfile>) -> Unit) {
        EdgeAuthService.launchCookieLogin(
            CookieLogin(
                loginUrl = SpotifyAuth.LOGIN_URL,
                isSiteHost = { h -> h == "spotify.com" || h.endsWith(".spotify.com") },
                cookieUrls = listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com"),
                loggedInCookies = setOf("sp_dc"),
                profileDir = browserProfileDir,
                siteName = "Spotify"
            ),
            onSuccess = { cookies ->
                val spDc = cookies["sp_dc"].orEmpty()
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                  val result = runCatching {
                    save(Stored(spDc = spDc, spKey = cookies["sp_key"].orEmpty()))
                    Spotify.accessToken = null
                    tokenExpiresAt = 0
                    if (!ensureToken()) error("Spotify no aceptó la sesión. Intenta de nuevo.")
                    val me = Spotify.me().getOrNull()
                    val profile = SpotifyProfile(me?.displayName ?: me?.id ?: "Spotify", me?.images?.firstOrNull()?.url)
                    load()?.let { save(it.copy(name = profile.name, avatarUrl = profile.avatarUrl)) }
                    profile
                  }
                  withContext(Dispatchers.Main) { onDone(result) }
                }
            },
            onError = { onDone(Result.failure(IllegalStateException(it))) },
            onStatus = onStatus
        )
    }

    fun cancelSignIn() = EdgeAuthService.cancel()

    /** Forgets the session here and in the login browser profile. */
    fun signOut() {
        file.delete()
        Spotify.accessToken = null
        tokenExpiresAt = 0
        browserProfileDir.deleteRecursively()
    }

}
