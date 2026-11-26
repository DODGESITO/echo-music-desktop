package echo.music.desktop.player

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import kotlin.test.Test

/** Manual network probe (-DlyricsProbe=true): is the YouTube audio stream fragmented MP4? */
class StreamFormatProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val url = DesktopStreamResolver.resolveStreamUrl("kJQP7kiw5Fk").getOrThrow()
            println("PROBE url host=${url.substringAfter("//").substringBefore('/')} clen=${Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)} itag=${Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1)}")
            val req = Request.Builder().url(url).header("Range", "bytes=0-65535")
            PlayerClient.mediaHeadersFor(url).forEach { (k, v) -> req.header(k, v) }
            val bytes = OkHttpClient().newCall(req.build()).execute().use { it.body!!.bytes() }
            val buf = ByteBuffer.wrap(bytes)
            var pos = 0
            val boxes = mutableListOf<String>()
            while (pos + 8 <= bytes.size && boxes.size < 12) {
                val size = buf.getInt(pos).toLong() and 0xffffffffL
                val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                boxes += "$type($size)"
                if (size < 8) break
                pos += size.toInt()
            }
            println("PROBE top-level boxes: $boxes")

            val t0 = System.currentTimeMillis()
            val full = Request.Builder().url(url).header("Range", "bytes=0-")
            PlayerClient.mediaHeadersFor(url).forEach { (k, v) -> full.header(k, v) }
            val all = OkHttpClient().newCall(full.build()).execute().use { it.body!!.bytes() }
            val t1 = System.currentTimeMillis()
            val remuxed = echo.music.desktop.canvas.Fmp4Remuxer.remux(all)
            val t2 = System.currentTimeMillis()
            val out = ByteBuffer.wrap(remuxed)
            val outBoxes = mutableListOf<String>()
            var p = 0
            while (p + 8 <= remuxed.size && outBoxes.size < 12) {
                val size = out.getInt(p)
                outBoxes += "${String(remuxed, p + 4, 4, Charsets.US_ASCII)}($size)"
                if (size < 8) break
                p += size
            }
            println("PROBE download ${all.size} bytes in ${t1 - t0} ms, remux ${t2 - t1} ms -> ${remuxed.size} bytes $outBoxes")
            java.io.File(System.getProperty("java.io.tmpdir"), "echo-remux-probe.m4a").writeBytes(remuxed)
        }
    }
}
