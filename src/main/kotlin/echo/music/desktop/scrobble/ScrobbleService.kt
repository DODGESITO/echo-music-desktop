package echo.music.desktop.scrobble

import android.content.Context
import echo.music.iad1tya.ui.screens.settings.ListenBrainzManager
import echo.music.iad1tya.utils.lastfm.LastFM
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScrobbleTrack(
    val title: String,
    /** Main artist (what Last.fm matches best). */
    val artist: String,
    /** Full credit, e.g. "Bad Bunny, Bomba Estéreo" (ListenBrainz). */
    val allArtists: String,
    val album: String?,
    val durationSec: Int
)

/** Decrypted credentials for the scrobbling services (blank = not configured). */
data class ScrobbleConfig(
    val listenBrainzToken: String = "",
    val lastFmApiKey: String = "",
    val lastFmSecret: String = "",
    val lastFmSessionKey: String = ""
) {
    val listenBrainzOn get() = listenBrainzToken.isNotBlank()
    val lastFmOn get() = lastFmApiKey.isNotBlank() && lastFmSecret.isNotBlank() && lastFmSessionKey.isNotBlank()
}

/**
 * Scrobbling to Last.fm and ListenBrainz with the phone app's own clients
 * (LastFM.kt and ListenBrainzManager.kt), plus the same rules for when a listen counts.
 */
object ScrobbleService {
    private val context = Context()

    /** A listen counts after half the song or 4 minutes, for songs of at least 30 s (Last.fm rules). */
    fun scrobbleThresholdMs(durationSec: Int): Long? {
        if (durationSec < LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION) return null
        val half = (durationSec * 1000L * LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT).toLong()
        return minOf(half, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS * 1000L)
    }

    private fun useLastFm(cfg: ScrobbleConfig) {
        LastFM.initialize(cfg.lastFmApiKey, cfg.lastFmSecret)
        LastFM.sessionKey = cfg.lastFmSessionKey
    }

    suspend fun nowPlaying(track: ScrobbleTrack, cfg: ScrobbleConfig) = withContext(Dispatchers.IO) {
        if (cfg.lastFmOn) {
            useLastFm(cfg)
            LastFM.updateNowPlaying(track.artist, track.title, track.album, duration = track.durationSec.takeIf { it > 0 })
        }
        if (cfg.listenBrainzOn) {
            ListenBrainzManager.submitPlayingNow(
                context, cfg.listenBrainzToken, track.title, track.allArtists, track.album.orEmpty(),
                track.durationSec * 1000L, 0L
            )
        }
    }

    /** Records a finished listen. Returns the names of the services that accepted it. */
    suspend fun scrobble(track: ScrobbleTrack, startedAtMs: Long, cfg: ScrobbleConfig): List<String> =
        withContext(Dispatchers.IO) {
            val ok = mutableListOf<String>()
            if (cfg.lastFmOn) {
                useLastFm(cfg)
                val response = LastFM.scrobble(
                    track.artist, track.title, startedAtMs / 1000L, track.album,
                    duration = track.durationSec.takeIf { it > 0 }
                ).getOrNull()
                if (response != null && response.status.value in 200..299 && "\"error\"" !in response.bodyAsText()) ok += "Last.fm"
            }
            if (cfg.listenBrainzOn) {
                val accepted = ListenBrainzManager.submitFinished(
                    context, cfg.listenBrainzToken, track.title, track.allArtists, track.album.orEmpty(),
                    track.durationSec * 1000L, startedAtMs, System.currentTimeMillis()
                )
                if (accepted) ok += "ListenBrainz"
            }
            ok
        }

    /** Step 1 of Last.fm login: gets a token and the page where the user approves Echo Music. */
    suspend fun lastFmStartAuth(apiKey: String, secret: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        LastFM.initialize(apiKey.trim(), secret.trim())
        LastFM.getToken().map { it.token to LastFM.getAuthUrl(it.token) }
    }

    /** Step 2: after approving in the browser, exchanges the token for a session (username, session key). */
    suspend fun lastFmFinishAuth(apiKey: String, secret: String, token: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            LastFM.initialize(apiKey.trim(), secret.trim())
            LastFM.getSession(token).map { it.session.name to it.session.key }
        }
}
