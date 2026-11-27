package echo.music.desktop.spotify

import com.music.echo.spotify.SpotifyPlaybackResolver
import com.music.innertube.models.SongItem
import echo.music.iad1tya.spotify.Spotify
import echo.music.iad1tya.spotify.models.SpotifyPlaylist
import echo.music.iad1tya.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** The user's Spotify library and how its songs are found on YouTube Music (phone app's resolver). */
object SpotifyLibrary {
    const val LIKED_ID = "liked"
    private const val MAX_TRACKS = 1000
    private val resolver = SpotifyPlaybackResolver()

    /** Playlists of the signed-in account (own and followed). */
    suspend fun playlists(): Result<List<SpotifyPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            check(SpotifyAccount.ensureToken()) { "Tu sesión de Spotify venció. Vuelve a iniciar sesión." }
            val all = mutableListOf<SpotifyPlaylist>()
            var offset = 0
            while (offset < 500) {
                val page = Spotify.myPlaylists(limit = 50, offset = offset).getOrThrow()
                all += page.items
                if (page.items.size < 50 || all.size >= page.total) break
                offset += 50
            }
            all.distinctBy { it.id }
        }
    }

    /** Number of "Tus me gusta". */
    suspend fun likedCount(): Int = withContext(Dispatchers.IO) {
        if (!SpotifyAccount.ensureToken()) return@withContext 0
        Spotify.likedSongs(limit = 1, offset = 0).getOrNull()?.total ?: 0
    }

    /** All tracks of a playlist (or of "Tus me gusta" with [LIKED_ID]). */
    suspend fun tracks(playlistId: String, onPage: (Int, Int) -> Unit = { _, _ -> }): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        check(SpotifyAccount.ensureToken()) { "Tu sesión de Spotify venció. Vuelve a iniciar sesión." }
        val out = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (out.size < MAX_TRACKS) {
            val (items, total) = if (playlistId == LIKED_ID) {
                val page = Spotify.likedSongs(limit = 50, offset = offset).getOrThrow()
                page.items.map { it.track } to page.total
            } else {
                val page = Spotify.playlistTracks(playlistId, limit = 100, offset = offset).getOrThrow()
                page.items.mapNotNull { it.track } to page.total
            }
            out += items.filter { !it.isLocal && it.name.isNotBlank() }
            onPage(minOf(offset + items.size, total), total)
            if (items.isEmpty() || offset + items.size >= total) break
            offset += items.size
        }
        out
    }

    /** Each Spotify track → its song on YouTube Music (6 at a time); tracks with no match are left out. */
    suspend fun resolve(tracks: List<SpotifyTrack>, hideExplicit: Boolean, onProgress: (Int, Int) -> Unit): List<SongItem> = coroutineScope {
        val done = AtomicInteger(0)
        tracks.map { track ->
            async(Dispatchers.IO) {
                resolver.resolveTrack(track, hideExplicit).also { onProgress(done.incrementAndGet(), tracks.size) }
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    /**
     * Spotify Canvas (the short looping video) of a song: the best search match whose artist is
     * one of the song's artists, so a canvas never lands on the wrong song.
     */
    suspend fun canvasUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        if (!SpotifyAccount.isSignedIn || !SpotifyAccount.ensureToken()) return@withContext null
        val wanted = artist.lowercase().split(",", "&", " feat", " ft.", " x ").map { it.trim() }.filter { it.isNotBlank() }
        val track = Spotify.search("$title $artist", types = listOf("track"), limit = 5).getOrNull()
            ?.tracks?.items.orEmpty()
            .firstOrNull { t -> t.artists.any { a -> a.name.lowercase().let { n -> wanted.any { w -> n.contains(w) || w.contains(n) } } } }
            ?: return@withContext null
        Spotify.getCanvas(track.id)
    }
}
