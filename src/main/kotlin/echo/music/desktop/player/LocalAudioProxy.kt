package echo.music.desktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LocalAudioProxy {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var server: HttpServer? = null
    var port: Int = 0
        private set

    init {
        try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val handler = StreamHandler()
            s.createContext("/stream", handler)
            s.createContext("/stream.m4a", handler)
            s.createContext("/stream.mp3", handler)
            s.createContext("/stream.mp4", handler)
            s.executor = Executors.newCachedThreadPool { r ->
                Thread(r, "AudioProxyThread").apply { isDaemon = true }
            }
            s.start()
            server = s
            port = s.address.port
            println("LocalAudioProxy running on http://127.0.0.1:$port")
        } catch (e: Exception) {
            println("Failed to start LocalAudioProxy: ${e.message}")
        }
    }

    fun getProxyUrl(directUrl: String, extension: String = "m4a"): String {
        return if (port > 0) {
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(directUrl.toByteArray(StandardCharsets.UTF_8))
            "http://127.0.0.1:$port/stream.$extension?u=$b64"
        } else {
            directUrl
        }
    }

    private class StreamHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val method = exchange.requestMethod.uppercase()
            try {
                val query = exchange.requestURI.rawQuery ?: ""
                val rawToken = query.substringAfter("u=", "")
                if (rawToken.isBlank()) {
                    exchange.sendResponseHeaders(400, -1)
                    exchange.close()
                    return
                }

                val decodedUrl = String(Base64.getUrlDecoder().decode(rawToken), StandardCharsets.UTF_8)
                val requestBuilder = Request.Builder().url(decodedUrl)

                // Inject client headers required by YouTube (User-Agent, Origin, Referer)
                val headers = PlayerClient.mediaHeadersFor(decodedUrl)
                headers.forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }

                // Handle HEAD request specially for JavaFX MediaPlayer
                if (method == "HEAD") {
                    println("[Proxy] HEAD request received from MediaPlayer")
                    val responseHeaders = exchange.responseHeaders
                    responseHeaders.set("Content-Type", "audio/mp4")
                    responseHeaders.set("Accept-Ranges", "bytes")
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                    return
                }

                // GET request: YouTube googlevideo STRICTLY requires a Range header.
                // If MediaPlayer did not pass a Range header, default to bytes=0-
                val rangeHeader = exchange.requestHeaders.getFirst("Range")
                val effectiveRange = if (!rangeHeader.isNullOrBlank()) rangeHeader else "bytes=0-"
                requestBuilder.header("Range", effectiveRange)

                val upstreamResponse = httpClient.newCall(requestBuilder.build()).execute()
                val responseCode = upstreamResponse.code
                val responseHeaders = exchange.responseHeaders

                val rawContentType = upstreamResponse.header("Content-Type")?.substringBefore(";")?.trim() ?: "audio/mp4"
                responseHeaders.set("Content-Type", rawContentType)
                upstreamResponse.header("Content-Range")?.let { responseHeaders.set("Content-Range", it) }
                upstreamResponse.header("Accept-Ranges")?.let { responseHeaders.set("Accept-Ranges", it) }

                val contentLength = upstreamResponse.body?.contentLength() ?: -1L
                if (contentLength > 0) {
                    responseHeaders.set("Content-Length", contentLength.toString())
                }

                // In Java HttpServer: passing contentLength > 0 sends Content-Length, passing 0 sends chunked
                val sendLength = if (contentLength > 0) contentLength else 0
                exchange.sendResponseHeaders(responseCode, sendLength)

                println("[Proxy] GET response code=$responseCode, len=$contentLength, range=$effectiveRange")

                val input: InputStream? = upstreamResponse.body?.byteStream()
                val output: OutputStream = exchange.responseBody
                if (input != null) {
                    try {
                        input.use { inStream ->
                            output.use { outStream ->
                                inStream.copyTo(outStream, bufferSize = 32 * 1024)
                            }
                        }
                    } catch (ignored: java.io.IOException) {
                        // Normal client socket disconnect when MediaPlayer buffers enough or seeks
                    }
                } else {
                    output.close()
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (!msg.contains("reset", ignoreCase = true) &&
                    !msg.contains("anulado", ignoreCase = true) &&
                    !msg.contains("Broken pipe", ignoreCase = true)) {
                    println("[Proxy] Error handling $method: $msg")
                }
                try {
                    exchange.sendResponseHeaders(500, -1)
                    exchange.close()
                } catch (ignored: Exception) {}
            }
        }
    }
}
