package echo.music.desktop.player

import com.music.innertube.NewPipeExtractor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test

/** Manual network probe (-DlyricsProbe=true): can NewPipe's video URLs be downloaded whole? */
class VideoFormatsProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val t0 = System.currentTimeMillis()
        val streams = NewPipeExtractor.newPipePlayer("kJQP7kiw5Fk")
        println("PROBE newpipe ${System.currentTimeMillis() - t0} ms itags=${streams.map { it.first }}")
        for (itag in listOf(18, 135, 134)) {
            val url = streams.firstOrNull { it.first == itag }?.second ?: continue
            val host = URI(url).host
            val c = Regex("[?&]c=([A-Z_0-9]+)").find(url)?.groupValues?.get(1)
            var offset = 0L
            var ok = true
            val t1 = System.currentTimeMillis()
            while (ok) {
                val req = HttpRequest.newBuilder(URI(url)).header("Range", "bytes=$offset-${offset + 4 * 1048576L - 1}")
                PlayerClient.mediaHeadersFor(url).forEach { (k, v) -> runCatching { req.header(k, v) } }
                val r = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())
                if (r.statusCode() != 206 && r.statusCode() != 200) {
                    println("PROBE   itag $itag stopped at ${offset / 1048576} MB with ${r.statusCode()}")
                    ok = false
                } else {
                    offset += r.body().size
                    val total = r.headers().firstValue("Content-Range").orElse("").substringAfter('/').toLongOrNull() ?: 0
                    if (r.body().isEmpty() || offset >= total) break
                }
            }
            println("PROBE itag $itag client=$c host=$host got ${offset / 1048576} MB in ${System.currentTimeMillis() - t1} ms complete=$ok")
        }
    }
}
