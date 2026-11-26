package echo.music.desktop.player

import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object DesktopStreamResolver {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Loudness reported by YouTube for each resolved song (dB; >0 means louder than reference). */
    private val loudnessByVideo = java.util.concurrent.ConcurrentHashMap<String, Double>()

    fun loudnessDbFor(videoId: String): Double? = loudnessByVideo[videoId]

    /**
     * Validates that the stream URL actually responds with HTTP 200/206
     * before passing it to MediaPlayer, preventing 403 Forbidden playback errors.
     */
    private fun validateStream(url: String): Boolean {
        return try {
            val headers = PlayerClient.mediaHeadersFor(url)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1024")

            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            probeClient.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val ok = code in 200..299
                if (!ok) {
                    println("[StreamResolver] Stream probe rejected with HTTP $code: ${url.take(60)}...")
                }
                ok
            }
        } catch (e: Exception) {
            println("[StreamResolver] Stream probe failed (${e.message}): ${url.take(60)}...")
            false
        }
    }

    /**
     * @param lowQuality pick the smallest AAC stream (data saver) instead of the best one.
     * The desktop player (JavaFX) only plays AAC/MP4 audio, so Opus/WebM is a last resort.
     */
    suspend fun resolveStreamUrl(videoId: String, lowQuality: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        // Ensure visitorData session is active
        if (YouTube.visitorData.isNullOrBlank()) {
            try {
                YouTube.refreshVisitorData()
            } catch (t: Throwable) {
                println("[StreamResolver] visitorData initialization notice: ${t.message}")
            }
        }

        // Method 1: Instant InnerTube API via VISIONOS (<300ms)
        try {
            val playerResult = YouTube.player(videoId, client = YouTubeClient.VISIONOS)
            if (playerResult.isSuccess) {
                val response = playerResult.getOrNull()
                val loudness = response?.playerConfig?.audioConfig?.loudnessDb
                    ?: response?.playerConfig?.audioConfig?.perceptualLoudnessDb
                if (loudness != null) loudnessByVideo[videoId] = loudness

                val formats = response?.streamingData?.adaptiveFormats.orEmpty()
                val aacFormats = formats
                    .filter { it.isAudio && !it.url.isNullOrBlank() && it.mimeType.contains("audio/mp4") }
                val aacFormat = if (lowQuality) aacFormats.minByOrNull { it.bitrate } else aacFormats.maxByOrNull { it.bitrate }

                val candidateUrl = aacFormat?.url
                    ?: formats.filter { it.isAudio && !it.url.isNullOrBlank() }.maxByOrNull { it.bitrate }?.url

                if (!candidateUrl.isNullOrBlank()) {
                    if (validateStream(candidateUrl)) {
                        println("[StreamResolver] Valid direct stream verified via VISIONOS")
                        return@withContext Result.success(LocalAudioProxy.getProxyUrl(candidateUrl))
                    }
                }
            }
        } catch (t: Throwable) {
            println("[StreamResolver] VISIONOS direct resolution skipped: ${t.message}")
        }

        // Method 2: System yt-dlp extractor (100% resilient on Windows desktop)
        val ytdlpUrl = resolveViaYtDlp(videoId, lowQuality)
        if (!ytdlpUrl.isNullOrBlank() && validateStream(ytdlpUrl)) {
            println("[StreamResolver] Stream verified and resolved via yt-dlp")
            return@withContext Result.success(LocalAudioProxy.getProxyUrl(ytdlpUrl))
        }

        // Method 3: NewPipeExtractor fallback
        try {
            val streams = NewPipeExtractor.newPipePlayer(videoId)
            val preferredAac = if (lowQuality) listOf(139, 140) else listOf(140, 139)
            val streamUrl = preferredAac.firstNotNullOfOrNull { itag -> streams.firstOrNull { it.first == itag }?.second }
                ?: streams.firstOrNull { it.first in listOf(251, 250, 249) }?.second
                ?: streams.firstOrNull()?.second

            if (!streamUrl.isNullOrBlank() && validateStream(streamUrl)) {
                println("[StreamResolver] Stream verified via NewPipe fallback")
                return@withContext Result.success(LocalAudioProxy.getProxyUrl(streamUrl))
            }
        } catch (t: Throwable) {
            println("[StreamResolver] NewPipe fallback warning: ${t.message}")
        }

        // Method 4: Piped API Fallback (Public instances)
        val pipedUrl = resolveViaPiped(videoId)
        if (!pipedUrl.isNullOrBlank() && validateStream(pipedUrl)) {
            println("[StreamResolver] Stream verified via Piped API fallback")
            return@withContext Result.success(LocalAudioProxy.getProxyUrl(pipedUrl))
        }

        // Refresh visitor data for subsequent searches if all failed
        try { YouTube.refreshVisitorData() } catch (ignored: Throwable) {}

        Result.failure(RuntimeException("No se pudo obtener un stream reproducible y autorizado para $videoId"))
    }

    private fun resolveViaYtDlp(videoId: String, lowQuality: Boolean): String? {
        return try {
            val process = ProcessBuilder(
                "yt-dlp",
                "-g",
                "-f", if (lowQuality) "wa[ext=m4a]/ba[ext=m4a]/wa" else "ba[ext=m4a]/ba[ext=webm]/ba",
                "https://www.youtube.com/watch?v=$videoId"
            ).redirectErrorStream(true).start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val finished = process.waitFor(12, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                output.lines().firstOrNull { it.startsWith("http") }?.trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaPiped(videoId: String): String? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net"
        )
        for (instance in instances) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                probeClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val audioStreams = root["audioStreams"]?.jsonArray
                        if (audioStreams != null && audioStreams.isNotEmpty()) {
                            var bestUrl: String? = null
                            var bestBitrate = 0
                            for (element in audioStreams) {
                                val item = element.jsonObject
                                val url = item["url"]?.jsonPrimitive?.content
                                val bitrate = item["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                                if (!url.isNullOrBlank() && bitrate > bestBitrate) {
                                    bestBitrate = bitrate
                                    bestUrl = url
                                }
                            }
                            if (!bestUrl.isNullOrBlank()) return bestUrl
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }
        return null
    }
}
