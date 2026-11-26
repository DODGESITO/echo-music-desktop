package echo.music.desktop.recognition

import echo.music.iad1tya.recognition.VibraSignature
import kotlinx.coroutines.runBlocking
import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EchoFindTest {
    /** 16-bit PCM tone (a few notes) at [rate] Hz with [channels] channels. */
    private fun tone(rate: Int, channels: Int, seconds: Int): ByteArray {
        val frames = rate * seconds
        val out = ByteArray(frames * channels * 2)
        for (i in 0 until frames) {
            val freq = listOf(440.0, 554.0, 659.0, 880.0)[(i / (rate / 2)) % 4]
            val v = (sin(2 * PI * freq * i / rate) * 8000).toInt().toShort()
            for (c in 0 until channels) {
                val idx = (i * channels + c) * 2
                out[idx] = (v.toInt() and 0xFF).toByte(); out[idx + 1] = (v.toInt() shr 8).toByte()
            }
        }
        return out
    }

    @Test
    fun convertsStereo48kTo16kMono() {
        val pcm = tone(48_000, 2, 1)
        val mono = EchoFindService.toMono16k(pcm, AudioFormat(48_000f, 16, 2, true, false))
        assertEquals(16_000 * 2, mono.size) // 1 s of 16 kHz mono 16-bit
        assertTrue(EchoFindService.rms(mono) > 1000, "tone keeps its level")
        assertTrue(EchoFindService.rms(ByteArray(3200)) < 1, "silence detected")
    }

    @Test
    fun shazamSignatureIsGenerated() {
        val sig = VibraSignature.fromI16(tone(16_000, 1, 10))
        println("SIGNATURE ${sig.take(60)}... (${sig.length} chars)")
        assertTrue(sig.startsWith("data:audio/vnd.shazam.sig;base64,"), sig.take(40))
    }

    /** Manual network check (-DlyricsProbe=true): a synthetic tone should come back as "no match". */
    @Test
    fun liveShazamProbe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val r = EchoFindService.recognize(tone(16_000, 1, 10))
            println("PROBE shazam ok=${r.isSuccess} value=${r.getOrNull()?.title} err=${r.exceptionOrNull()?.message}")
        }
    }
}
