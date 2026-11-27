package echo.music.desktop.system

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap
import javax.net.ssl.SSLException

/**
 * Writes unexpected errors to ~/.echomusic/errors.log. The installed app has no console,
 * so without it there would be no trace of why it closed or what failed in the background.
 */
object ErrorLog {
    private val file = File(File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }, "errors.log")
    private const val MAX_BYTES = 512 * 1024L
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Errors on background threads: written down (a dropped connection in one line). */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write("background thread '${thread.name}'", error)
        }
    }

    /** Errors in a window: written down, then handled as Compose does by default (the app closes). */
    @OptIn(ExperimentalComposeUiApi::class)
    val windowExceptionHandlerFactory = WindowExceptionHandlerFactory { window ->
        val default = DefaultWindowExceptionHandlerFactory.exceptionHandler(window)
        WindowExceptionHandler { error ->
            write("window", error)
            default.onException(error)
        }
    }

    /** True when [error], or any error that caused it, is a dropped or failed connection. */
    fun isNetworkError(error: Throwable): Boolean {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is SocketException || current is SocketTimeoutException ||
                current is UnknownHostException || current is SSLException ||
                current is HttpTimeoutException
            ) return true
            current = current.cause
        }
        return false
    }

    @Synchronized
    private fun write(where: String, error: Throwable) {
        System.err.println("[ErrorLog] Uncaught error in $where: $error")
        runCatching {
            if (file.length() > MAX_BYTES) file.writeText("")
            val text = buildString {
                append(LocalDateTime.now().format(timeFormat)).append("  ").append(where).append(": ")
                if (isNetworkError(error)) {
                    append("connection error, ").appendLine(error.toString())
                } else {
                    appendLine(error.stackTraceToString().trimEnd())
                }
            }
            file.appendText(text)
        }
    }
}
