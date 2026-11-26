package echo.music.desktop.discord

import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DiscordRpcClient {
    private const val CLIENT_ID = "1518210534070292541"
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pipe: RandomAccessFile? = null
    private var isConnected = false
    private var nonceCounter = 1L
    private var currentPid = 0L

    init {
        try {
            currentPid = ProcessHandle.current().pid()
        } catch (ignored: Throwable) {}
    }

    fun start() {
        scope.launch {
            tryConnect()
        }
    }

    private fun tryConnect(): Boolean {
        if (isConnected && pipe != null) return true
        close()

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows) {
            for (i in 0..9) {
                try {
                    val p = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw")
                    pipe = p
                    if (sendHandshake(p)) {
                        isConnected = true
                        println("[DiscordRPC] Connected to Discord IPC pipe #$i")
                        return true
                    } else {
                        p.close()
                    }
                } catch (ignored: Throwable) {}
            }
        }
        return false
    }

    private fun sendHandshake(p: RandomAccessFile): Boolean {
        return try {
            val json = """{"v":1,"client_id":"$CLIENT_ID"}"""
            writeFrame(p, OP_HANDSHAKE, json)
            // Read handshake response
            val header = ByteArray(8)
            p.readFully(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val op = buf.int
            val len = buf.int
            if (len > 0) {
                val data = ByteArray(len)
                p.readFully(data)
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun writeFrame(p: RandomAccessFile, op: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(op)
        header.putInt(bytes.size)
        p.write(header.array())
        p.write(bytes)
    }

    fun updateActivity(
        title: String,
        artist: String,
        isPlaying: Boolean,
        currentPosMs: Long,
        durationMs: Long,
        artworkUrl: String?
    ) {
        scope.launch {
            if (!isConnected && !tryConnect()) return@launch

            val p = pipe ?: return@launch
            try {
                val nonce = (nonceCounter++).toString()
                val safeTitle = escapeJson(title)
                val safeArtist = escapeJson(artist)
                val stateText = if (isPlaying) "Reproduciendo" else "En pausa"
                val largeImage = artworkUrl ?: "logo"

                val nowSec = System.currentTimeMillis() / 1000L
                val startSec = if (isPlaying) (nowSec - (currentPosMs / 1000L)).coerceAtLeast(0L) else 0L
                val endSec = if (isPlaying && durationMs > 0L) startSec + (durationMs / 1000L) else 0L

                val timestampsJson = if (isPlaying && endSec > startSec) {
                    ""","timestamps":{"start":$startSec,"end":$endSec}"""
                } else if (isPlaying) {
                    ""","timestamps":{"start":$startSec}"""
                } else {
                    ""
                }

                val json = """
                {
                    "cmd": "SET_ACTIVITY",
                    "args": {
                        "pid": $currentPid,
                        "activity": {
                            "details": "$safeTitle",
                            "state": "$safeArtist • $stateText",
                            "assets": {
                                "large_image": "$largeImage",
                                "large_text": "$safeTitle",
                                "small_image": "${if (isPlaying) "play" else "pause"}",
                                "small_text": "$stateText"
                            }$timestampsJson
                        }
                    },
                    "nonce": "$nonce"
                }
                """.trimIndent()

                writeFrame(p, OP_FRAME, json)
            } catch (e: Throwable) {
                isConnected = false
                close()
            }
        }
    }

    fun clearActivity() {
        scope.launch {
            val p = pipe ?: return@launch
            try {
                val nonce = (nonceCounter++).toString()
                val json = """
                {
                    "cmd": "SET_ACTIVITY",
                    "args": {
                        "pid": $currentPid,
                        "activity": null
                    },
                    "nonce": "$nonce"
                }
                """.trimIndent()
                writeFrame(p, OP_FRAME, json)
            } catch (ignored: Throwable) {
                close()
            }
        }
    }

    private fun close() {
        try {
            pipe?.close()
        } catch (ignored: Throwable) {}
        pipe = null
        isConnected = false
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
