package echo.music.desktop.canvas

import echo.music.iad1tya.applecanvas.AppleMusicCanvasProvider
import echo.music.iad1tya.canvas.CanvasArtwork
import echo.music.iad1tya.canvas.TidalCanvasProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Canvas (animated cover) for a song, like the phone app: looks it up on Apple Music (by album,
 * then by song) and Tidal, checks the artist matches, and prepares a file JavaFX can play.
 *
 * Apple serves canvases as HLS with fragmented MP4, which JavaFX can't play; the H.264 variant
 * is downloaded whole and converted to a regular MP4 with [Fmp4Remuxer]. Results are cached in
 * ~/.echomusic/canvas_cache (max ~150 MB, oldest removed first).
 */
object CanvasService {
    /** Spotify Canvas lookup (set by the app; returns null when not signed in or no match). */
    @Volatile var spotifyCanvas: (suspend (title: String, artist: String) -> String?)? = null

    private const val MAX_SIDE = 768
    private const val CACHE_LIMIT_BYTES = 150L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val cacheDir get() = File(System.getProperty("user.home"), ".echomusic/canvas_cache").apply { mkdirs() }
    /** Songs known to have no canvas during this session (avoids repeated lookups). */
    private val missing = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Returns a playable canvas video for the song, or null when it has none. */
    suspend fun canvasFor(songId: String, title: String, artist: String, album: String?): File? = withContext(Dispatchers.IO) {
        val key = hash(songId)
        val cached = File(cacheDir, "$key.mp4")
        if (cached.isFile && cached.length() > 0) {
            cached.setLastModified(System.currentTimeMillis())
            return@withContext cached
        }
        if (songId in missing) return@withContext null
        // Spotify's own Canvas first (its lookup already checks the artist), then Apple Music / Tidal.
        val spotifyUrl = runCatching { spotifyCanvas?.invoke(title, artist) }.getOrNull()?.takeIf { it.isNotBlank() }
        val artwork = if (spotifyUrl == null) runCatching { lookup(title, artist, album) }.getOrNull() else null
        val url = spotifyUrl ?: artwork?.preferredAnimationUrl
        if (url.isNullOrBlank() || (spotifyUrl == null && !artistMatches(artist, artwork?.artist))) {
            missing += songId
            return@withContext null
        }
        val bytes = runCatching { download(url) }.getOrNull()
        if (bytes == null) {
            missing += songId
            return@withContext null
        }
        val tmp = File(cacheDir, "$key.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(cached)) { cached.delete(); tmp.renameTo(cached) }
        trimCache()
        cached
    }

    private suspend fun lookup(title: String, artist: String, album: String?): CanvasArtwork? {
        val song = title.substringBefore(" (").substringBefore(" [").substringBefore(" - ").trim().ifBlank { title }
        val mainArtist = artist.split(",", "&", " feat", " ft.").first().trim().ifBlank { artist }
        if (!album.isNullOrBlank()) {
            AppleMusicCanvasProvider.getByAlbumArtist(album = album, artist = mainArtist)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }?.let { return it }
        }
        return AppleMusicCanvasProvider.getBySongArtist(song = song, artist = mainArtist, album = album)
            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            ?: TidalCanvasProvider.getBySongArtist(song = song, artist = mainArtist, album = album)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
    }

    /** Same idea as the phone: only accept a canvas whose artist is one of the song's artists. */
    fun artistMatches(songArtists: String, canvasArtist: String?): Boolean {
        if (canvasArtist.isNullOrBlank()) return true
        fun split(s: String) = s.lowercase().split(",", "&", " feat.", " feat", " ft.", " x ", " y ")
            .map { it.trim() }.filter { it.isNotEmpty() }
        val mine = split(songArtists)
        val theirs = split(canvasArtist)
        return mine.any { m -> theirs.any { t -> t == m || t.contains(m) || m.contains(t) } }
    }

    /** Downloads the canvas as a regular MP4 (HLS → best H.264 variant ≤ 768 px → remuxed). */
    private fun download(url: String): ByteArray? {
        if (!url.substringBefore('?').endsWith(".m3u8")) {
            val raw = get(url) ?: return null
            return if (isFragmented(raw)) Fmp4Remuxer.remux(raw) else raw
        }
        val master = getText(url) ?: return null
        val lines = master.lines()
        var best: String? = null
        var bestSide = 0
        lines.forEachIndexed { i, line ->
            if (line.startsWith("#EXT-X-STREAM-INF") && "avc1" in line) {
                val side = Regex("RESOLUTION=([0-9]+)x").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (side in (bestSide + 1)..MAX_SIDE) {
                    bestSide = side
                    best = lines.getOrNull(i + 1)?.trim()
                }
            }
        }
        val variantUrl = URI(url).resolve(best ?: return null).toString()
        val media = getText(variantUrl) ?: return null
        // All segments are byte ranges of one fragmented MP4 named in EXT-X-MAP.
        val mapName = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(media)?.groupValues?.get(1) ?: return null
        val raw = get(URI(variantUrl).resolve(mapName).toString()) ?: return null
        return Fmp4Remuxer.remux(raw)
    }

    private fun isFragmented(data: ByteArray): Boolean {
        val s = String(data, 0, minOf(data.size, 4096), Charsets.ISO_8859_1)
        return "mvex" in s || "moof" in s
    }

    private fun get(url: String): ByteArray? =
        client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }

    private fun getText(url: String): String? = get(url)?.toString(Charsets.UTF_8)

    private fun trimCache() {
        val files = cacheDir.listFiles { f -> f.extension == "mp4" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= CACHE_LIMIT_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)
}
