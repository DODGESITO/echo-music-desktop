package echo.music.desktop.player

import com.music.innertube.models.SongItem
import echo.music.desktop.state.DownloadStore
import echo.music.desktop.state.DownloadedSongRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DesktopDownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun isDownloading(songId: String): Boolean {
        return activeJobs.containsKey(songId)
    }

    fun downloadSong(
        song: SongItem,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (DownloadStore.isDownloaded(song.id)) {
            onComplete?.invoke(true, null)
            return
        }

        if (activeJobs.containsKey(song.id)) {
            return // Already downloading
        }

        val job = scope.launch {
            _downloadProgress.update { it + (song.id to 0f) }
            val targetFile = File(DownloadStore.downloadsDir, "${song.id}.m4a")
            val tempFile = File(DownloadStore.downloadsDir, "${song.id}.tmp")

            try {
                // 1. Resolve raw audio stream URL
                val streamResult = DesktopStreamResolver.resolveStreamUrl(song.id)
                if (streamResult.isFailure) {
                    throw IllegalStateException("No se pudo obtener el flujo de audio para la descarga")
                }
                val streamUrl = streamResult.getOrThrow()

                // 2. Fetch using OkHttpClient with custom Range / VisionOS headers
                val headers = PlayerClient.mediaHeadersFor(streamUrl)
                val requestBuilder = Request.Builder().url(streamUrl)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Servidor respondió con código HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Cuerpo de audio vacío")
                    val contentLength = body.contentLength().coerceAtLeast(1L)

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesCopied = 0L

                            while (isActive) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                val progress = (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                _downloadProgress.update { it + (song.id to progress) }
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }

                // 3. Move temp file to final target file
                if (tempFile.exists() && tempFile.length() > 1024) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)

                    val record = DownloadedSongRecord.fromSongItem(
                        song = song,
                        filePath = targetFile.absolutePath,
                        fileSizeBytes = targetFile.length()
                    )
                    DownloadStore.registerDownload(record)
                    onComplete?.invoke(true, null)
                } else {
                    throw IllegalStateException("El archivo descargado está dañado o incompleto")
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                val err = e.message ?: "Error al descargar"
                println("[DownloadManager] Error downloading ${song.title}: $err")
                onComplete?.invoke(false, err)
            } finally {
                _downloadProgress.update { it - song.id }
                activeJobs.remove(song.id)
            }
        }

        activeJobs[song.id] = job
    }

    fun cancelDownload(songId: String) {
        activeJobs.remove(songId)?.cancel()
        _downloadProgress.update { it - songId }
        val temp = File(DownloadStore.downloadsDir, "$songId.tmp")
        if (temp.exists()) temp.delete()
    }

    fun deleteSong(songId: String) {
        cancelDownload(songId)
        DownloadStore.removeDownload(songId)
    }
}
