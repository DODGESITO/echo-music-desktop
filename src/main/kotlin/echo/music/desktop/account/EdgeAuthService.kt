package echo.music.desktop.account

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

object EdgeAuthService {
    private var activeProcess: Process? = null
    private var job: Job? = null

    fun isBrowserAvailable(): Boolean = findBrowserExecutable() != null

    fun findBrowserExecutable(): String? {
        val candidates = listOf(
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
        )
        return candidates.firstOrNull { File(it).exists() }
    }

    fun launch(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        cancel()
        val browserPath = findBrowserExecutable()
        if (browserPath == null) {
            onError("No se encontró Microsoft Edge ni Google Chrome en el sistema.")
            return
        }

        val profileDir = echo.music.desktop.state.AccountStore.browserAuthProfileDir
        if (!profileDir.exists()) profileDir.mkdirs()
        // Port 0 = the browser picks a free port and writes it to this file (no fixed, guessable
        // port and no clash with other tools such as the Node.js debugger on 9229).
        val activePortFile = File(profileDir, "DevToolsActivePort")
        activePortFile.delete()

        val process = try {
            ProcessBuilder(
                browserPath,
                "--remote-debugging-port=0",
                "--remote-debugging-address=127.0.0.1",
                "--user-data-dir=${profileDir.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                "--app=https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
            ).start()
        } catch (e: Exception) {
            onError("No se pudo iniciar el navegador: ${e.message}")
            return
        }
        activeProcess = process

        job = CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                onStatus("Ventana de Google abierta. Inicia sesión en la ventana de Edge...")
            }
            val httpClient = HttpClient.newHttpClient()
            var detectedCookie: String? = null
            var attempts = 0
            val maxAttempts = 180 // 3 minutes timeout

            while (isActive && attempts < maxAttempts && detectedCookie == null) {
                delay(1200)
                attempts++

                if (!process.isAlive) {
                    break
                }

                val debugPort = readDebugPort(activePortFile) ?: continue

                try {
                    val req = HttpRequest.newBuilder()
                        .uri(URI("http://127.0.0.1:$debugPort/json"))
                        .GET()
                        .build()
                    val res = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                    if (res.statusCode() == 200) {
                        val pagesJson = Json.parseToJsonElement(res.body()).jsonArray
                        // Only talk to the Google/YouTube login page we opened.
                        val page = pagesJson.firstOrNull { elem ->
                            val type = elem.jsonObject["type"]?.jsonPrimitive?.content
                            val host = runCatching {
                                URI(elem.jsonObject["url"]?.jsonPrimitive?.content.orEmpty()).host.orEmpty()
                            }.getOrDefault("")
                            type == "page" && isGoogleHost(host)
                        }

                        val wsUrl = page?.jsonObject?.get("webSocketDebuggerUrl")?.jsonPrimitive?.content
                        // The DevTools socket must be the local browser we started.
                        if (!wsUrl.isNullOrBlank() && wsUrl.startsWith("ws://127.0.0.1:$debugPort/")) {
                            val cookies = queryCookiesViaWebSocket(httpClient, wsUrl)
                            if (cookies.isNotEmpty()) {
                                val hasLogin = cookies.any { it.first == "LOGIN_INFO" || it.first == "SAPISID" || it.first == "SID" }
                                if (hasLogin) {
                                    val cookieStr = cookies.joinToString("; ") { "${it.first}=${it.second}" }
                                    detectedCookie = cookieStr
                                }
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            try {
                process.destroy()
            } catch (ignored: Exception) {}
            activeProcess = null

            if (detectedCookie != null) {
                withContext(Dispatchers.Main) {
                    onSuccess(detectedCookie)
                }
            } else if (!isActive) {
                // Cancelled
            } else if (!process.isAlive) {
                withContext(Dispatchers.Main) {
                    onError("Se cerró la ventana del navegador antes de completar el inicio de sesión.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("Tiempo de espera agotado. Por favor, intenta de nuevo.")
                }
            }
        }
    }

    /** First line of DevToolsActivePort is the port the browser chose. */
    private fun readDebugPort(file: File): Int? = try {
        file.takeIf { it.exists() }?.readLines()?.firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
    } catch (_: Exception) {
        null
    }

    private fun isGoogleHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "google.com" || h.endsWith(".google.com") ||
            h == "youtube.com" || h.endsWith(".youtube.com")
    }

    private fun queryCookiesViaWebSocket(client: HttpClient, wsUrl: String): List<Pair<String, String>> {
        val resultFuture = CompletableFuture<String>()
        val sb = StringBuilder()

        val listener = object : WebSocket.Listener {
            override fun onText(webSocket: WebSocket?, data: CharSequence?, last: Boolean): CompletionStage<*>? {
                if (data != null) sb.append(data)
                if (last) {
                    resultFuture.complete(sb.toString())
                }
                return null
            }

            override fun onError(webSocket: WebSocket?, error: Throwable?) {
                resultFuture.completeExceptionally(error ?: Exception("WebSocket error"))
            }
        }

        try {
            val ws = client.newWebSocketBuilder()
                .buildAsync(URI(wsUrl), listener)
                .join()

            val msg = """{"id":1,"method":"Network.getCookies","params":{"urls":["https://music.youtube.com","https://youtube.com"]}}"""
            ws.sendText(msg, true)

            val responseJson = try {
                resultFuture.get(2, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Done")
            } catch (ignored: Exception) {}

            if (responseJson != null) {
                val root = Json.parseToJsonElement(responseJson).jsonObject
                val cookiesArray = root["result"]?.jsonObject?.get("cookies")?.jsonArray
                if (cookiesArray != null) {
                    return cookiesArray.mapNotNull { item ->
                        val obj = item.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content
                        val value = obj["value"]?.jsonPrimitive?.content
                        if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                            name to value
                        } else null
                    }
                }
            }
        } catch (ignored: Exception) {}
        return emptyList()
    }

    fun cancel() {
        job?.cancel()
        job = null
        try {
            activeProcess?.destroy()
            activeProcess = null
        } catch (ignored: Exception) {}
    }
}
