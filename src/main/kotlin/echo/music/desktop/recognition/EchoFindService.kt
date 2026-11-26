package echo.music.desktop.recognition

import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionResult
import echo.music.iad1tya.recognition.VibraSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Echo Find (song recognition) for the desktop: records the microphone with javax.sound and
 * reuses the phone app's Shazam signature generator (VibraSignature) and Shazam client.
 */
object EchoFindService {
    const val RECORD_MS = 10_000L
    private const val RATE = VibraSignature.REQUIRED_SAMPLE_RATE // 16 kHz, mono, 16-bit little endian

    class MicrophoneUnavailable(message: String) : Exception(message)

    /**
     * Records [durationMs] of audio from the default microphone as 16 kHz mono 16-bit PCM.
     * If the device can't capture at 16 kHz it records at 44.1/48 kHz and downsamples.
     */
    suspend fun record(durationMs: Long = RECORD_MS, onProgress: (Float) -> Unit = {}): ByteArray =
        withContext(Dispatchers.IO) {
            val (line, format) = openLine()
            val bytesWanted = (format.sampleRate * format.frameSize * durationMs / 1000).toLong()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(format.frameSize * 1024)
            try {
                line.start()
                while (out.size() < bytesWanted) {
                    coroutineContext.ensureActive()
                    val n = line.read(buffer, 0, buffer.size)
                    if (n > 0) out.write(buffer, 0, n)
                    onProgress((out.size().toFloat() / bytesWanted).coerceAtMost(1f))
                }
            } finally {
                line.stop()
                line.close()
            }
            toMono16k(out.toByteArray(), format)
        }

    private fun openLine(): Pair<TargetDataLine, AudioFormat> {
        val candidates = listOf(RATE.toFloat(), 44_100f, 48_000f).flatMap { rate ->
            listOf(AudioFormat(rate, 16, 1, true, false), AudioFormat(rate, 16, 2, true, false))
        }
        for (format in candidates) {
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) continue
            try {
                val line = AudioSystem.getLine(info) as TargetDataLine
                line.open(format)
                return line to format
            } catch (_: Exception) {
            }
        }
        throw MicrophoneUnavailable("No se encontró un micrófono disponible.")
    }

    /** Converts 16-bit PCM of any rate/channels to 16 kHz mono (channel average + linear resampling). */
    fun toMono16k(pcm: ByteArray, format: AudioFormat): ByteArray {
        val channels = format.channels
        val frames = pcm.size / (2 * channels)
        val mono = ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) {
                val i = (f * channels + c) * 2
                sum += ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            }
            (sum / channels).toShort()
        }
        val ratio = format.sampleRate / RATE
        val outFrames = if (ratio == 1f) frames else (frames / ratio).toInt()
        val result = ByteArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val src = i * ratio
            val a = src.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val t = src - a
            val v = (mono[a] * (1 - t) + mono[b] * t).toInt().toShort()
            result[i * 2] = (v.toInt() and 0xFF).toByte()
            result[i * 2 + 1] = (v.toInt() shr 8).toByte()
        }
        return result
    }

    /** Loudness of the recording (0..32767). Near zero means the microphone captured silence. */
    fun rms(pcm16k: ByteArray): Double {
        val n = pcm16k.size / 2
        if (n == 0) return 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val v = ((pcm16k[i * 2 + 1].toInt() shl 8) or (pcm16k[i * 2].toInt() and 0xFF)).toShort().toDouble()
            sum += v * v
        }
        return sqrt(sum / n)
    }

    /** Fingerprints the 16 kHz mono recording and asks Shazam what it is. */
    suspend fun recognize(pcm16k: ByteArray): Result<RecognitionResult> = withContext(Dispatchers.Default) {
        runCatching { VibraSignature.fromI16(pcm16k) }.fold(
            onSuccess = { signature ->
                val durationMs = (pcm16k.size / 2) * 1000L / RATE
                Shazam.recognize(signature, durationMs)
            },
            onFailure = { Result.failure(it) }
        )
    }
}
