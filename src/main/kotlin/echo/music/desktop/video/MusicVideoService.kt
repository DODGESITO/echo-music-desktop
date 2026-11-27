package echo.music.desktop.video

import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.desktop.player.PlayerClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Video mode: the official music video of a song, as one MP4 with picture and sound together
 * (YouTube's 360p "itag 18"), downloaded to a small cache so it can be played, seeked and kept in
 * sync. Without special tokens YouTube only serves whole files this way.
 */
object MusicVideoService {
    private const val MUXED_ITAG = 18
    private const val CHUNK = 4L * 1024 * 1024 // YouTube refuses bigger ranges (403)
    private const val KEEP_FILES = 6
    private const val NO_VIDEO = ""

    private val dir: File by lazy { File(System.getProperty("java.io.tmpdir"), "echomusic-video").apply { mkdirs() } }
    private val videoIds = ConcurrentHashMap<String, String>() // song id → video id ("" = none)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<File?>>()
    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build() }

    /** Already downloaded video of [song] (null if not ready yet). */
    fun cachedFile(song: SongItem): File? {
        val videoId = videoIds[song.id]?.takeIf { it.isNotEmpty() } ?: song.id.takeIf { song.isVideoSong } ?: return null
        return fileFor(videoId).takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Finds and downloads the video of [song]; null when it has none. When a candidate cannot be
     * downloaded (YouTube refuses some), the next best official video is tried.
     */
    suspend fun prepare(song: SongItem, onProgress: (Float) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        videoIds[song.id]?.let { known -> return@withContext if (known.isEmpty()) null else prepareVideo(known, onProgress) }
        val candidates = if (song.isVideoSong) listOf(song.id) else findOfficialVideoIds(song.title, song.artists.joinToString(", ") { it.name })
        for (candidate in candidates.take(3)) {
            val file = prepareVideo(candidate, onProgress)
            if (file != null) {
                videoIds[song.id] = candidate
                return@withContext file
            }
        }
        videoIds[song.id] = NO_VIDEO
        if (candidates.isNotEmpty()) unavailable.add(song.id)
        null
    }

    /** Songs whose official video exists but YouTube would not let us download. */
    private val unavailable = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun isUnavailable(song: SongItem): Boolean = song.id in unavailable

    private suspend fun prepareVideo(videoId: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val file = fileFor(videoId)
        if (file.exists() && file.length() > 0) return@withContext file
        // One download per video even if the current song and the prefetch ask at the same time.
        val mine = CompletableDeferred<File?>()
        val running = inflight.putIfAbsent(videoId, mine)
        if (running != null) return@withContext running.await()
        try {
            val result = runCatching { download(videoId, file, onProgress) }
                .onFailure { System.err.println("MusicVideoService: ${it.message}") }
                .getOrNull()
            mine.complete(result)
            result
        } finally {
            inflight.remove(videoId)
        }
    }

    /** Best official video candidate of [song] (for tests and diagnostics). */
    suspend fun videoIdFor(song: SongItem): String? =
        if (song.isVideoSong) song.id else findOfficialVideoIds(song.title, song.artists.joinToString(", ") { it.name }).firstOrNull()

    private fun fileFor(videoId: String) = File(dir, "$videoId.mp4")

    private fun download(videoId: String, target: File, onProgress: (Float) -> Unit): File? {
        val url = runCatching { NewPipeExtractor.newPipePlayer(videoId) }.getOrNull()
            ?.firstOrNull { it.first == MUXED_ITAG }?.second ?: return null
        val part = File(dir, "$videoId.part")
        // First piece tells the size; the rest come 3 at a time.
        val (first, total) = fetchRange(url, 0) ?: return null
        java.io.RandomAccessFile(part, "rw").use { out ->
            out.setLength(total)
            out.write(first)
            var done = first.size.toLong()
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
            val offsets = generateSequence(first.size.toLong()) { it + CHUNK }.takeWhile { it < total }.toList()
            val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
            try {
                val jobs = offsets.map { offset -> pool.submit<Pair<Long, ByteArray>> { offset to (fetchRange(url, offset)?.first ?: error("parte fallida")) } }
                for (job in jobs) {
                    val (offset, bytes) = job.get()
                    synchronized(out) {
                        out.seek(offset)
                        out.write(bytes)
                    }
                    done += bytes.size
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            } finally {
                pool.shutdownNow()
            }
        }
        target.delete()
        if (!part.renameTo(target)) error("no se pudo guardar el video")
        trimCache()
        return target
    }

    /** One piece (≤ 4 MB) of the file and the file's total size. */
    private fun fetchRange(url: String, offset: Long): Pair<ByteArray, Long>? {
        val req = HttpRequest.newBuilder(URI(url))
            .header("Range", "bytes=$offset-${offset + CHUNK - 1}")
            .timeout(Duration.ofSeconds(30))
        PlayerClient.mediaHeadersFor(url).forEach { (k, v) -> runCatching { req.header(k, v) } }
        val response = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 206 && response.statusCode() != 200) return null
        val total = response.headers().firstValue("Content-Range").orElse("").substringAfter('/').toLongOrNull()
            ?: response.body().size.toLong()
        return response.body() to total
    }

    private fun trimCache() {
        dir.listFiles { f -> f.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_FILES)
            ?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------- official video search
    // Same rules as the phone app (YouTubeVideoCanvasProvider.findOfficialVideoId).

    private val negativeKeywords = listOf(
        "mashup", "mash up", "mash-up", "remix", "dj", "chillout", "slowed", "reverb", "speed up", "sped up",
        "nightcore", "bass boosted", "lofi", "lo-fi", "8d audio", "8d", "status", "short", "shorts", "tiktok",
        "reels", "cover", "reaction", "review", "bts", "behind the scenes", "making of", "teaser", "trailer",
        "promo", "karaoke", "instrumental", "lyrics", "lyric video", "lyrics video", "full audio", "audio only",
        "jukebox", "live performance", "concert", "unplugged", "acoustic", "dance cover", "choreography",
        "fan edit", "edit", "whatsapp status", "scene", "scenes", "dialogue", "movie scene", "episode",
        "audio", "visualizer", "visualiser", "visual", "official art track", "topic", "static", "testo",
        "letra", "paroles", "en vivo", "karaoke"
    )

    internal fun cleanTitle(title: String) = title
        .replace(Regex("""\s*[(\[].*?[)\]]\s*"""), " ")
        .replace(Regex("""\s*[-|/].*"""), " ")
        .trim()

    private fun keywords(text: String) = text.lowercase(Locale.ROOT).split(' ')
        .map { w -> w.trim().filter { it.isLetterOrDigit() } }
        .filter { it.length > 2 }

    /** Official video candidates, best first (empty when none passes the rules). */
    private suspend fun findOfficialVideoIds(title: String, artist: String): List<String> {
        val clean = cleanTitle(title)
        val cleanArtist = artist.split(',', '&', '/', ';').firstOrNull()
            ?.replace(Regex("""\s*[(\[].*?[)\]]\s*"""), " ")?.trim() ?: artist.trim()
        val titleWords = keywords(clean)
        val artistWords = keywords(cleanArtist)
        val lowerTitle = clean.lowercase(Locale.ROOT)
        val queries = listOf(
            "\"$clean\" $cleanArtist official music video",
            "\"$clean\" $cleanArtist official video",
            "$clean $cleanArtist video oficial",
            "$clean $cleanArtist"
        )
        for (query in queries) {
            val items = runCatching { YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items }.getOrNull().orEmpty()
            val ranked = items.mapNotNull { item ->
                val itemTitle = item.title.lowercase(Locale.ROOT)
                val itemArtists = (item as? SongItem)?.artists.orEmpty().map { it.name.lowercase(Locale.ROOT) }
                if (negativeKeywords.any { neg -> itemTitle.contains(neg) && !lowerTitle.contains(neg) }) return@mapNotNull null
                val exact = itemTitle.contains(lowerTitle)
                val matched = titleWords.count { itemTitle.contains(it) }
                val titleOk = when {
                    exact || titleWords.isEmpty() -> true
                    titleWords.size <= 2 -> matched == titleWords.size
                    else -> matched.toFloat() / titleWords.size >= 0.75f
                }
                if (!titleOk) return@mapNotNull null
                // Only the artist's own video: YouTube Music marks official videos (OMV); anything
                // else must at least come from the artist's channel. Re-uploads and fan videos with
                // the right title ("Official Video 4K" by someone else) are left out.
                val official = (item as? SongItem)?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"
                val byArtist = artistWords.isEmpty() || itemArtists.any { a -> artistWords.any { a.contains(it) } }
                if (!official && !byArtist) return@mapNotNull null
                var score = if (exact) 50 else matched * 15
                if (official) score += 100
                if (byArtist) score += 40
                score += when {
                    listOf("official music video", "official video", "official mv", "video oficial", "videoclip oficial").any { itemTitle.contains(it) } -> 40
                    itemTitle.contains("music video") || itemTitle.contains(" mv") || itemTitle.contains("videoclip") -> 20
                    else -> 0
                }
                if (score >= 80 && item.id.isNotBlank()) item.id to score else null
            }.sortedByDescending { it.second }.map { it.first }.distinct()
            if (ranked.isNotEmpty()) return ranked
        }
        return emptyList()
    }
}
