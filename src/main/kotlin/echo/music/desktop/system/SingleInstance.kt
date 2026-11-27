package echo.music.desktop.system

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Only one Echo Music at a time. A second copy (opened again while the first sits in the tray, or
 * started with Windows twice) would keep the program files in use, and then an update can't
 * replace them. The second copy asks the first one to show its window and quits.
 */
object SingleInstance {
    // The installed app and copies started from Gradle use different ports, so both can run.
    private val port get() = if (AppInfo.launcherPath != null) 47_391 else 47_392
    private var server: ServerSocket? = null

    private val _showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Another launch asked this copy to show its window. */
    val showRequests: SharedFlow<Unit> = _showRequests

    /** Brings the window forward from inside the app (e.g. a phone asking to pair). */
    fun requestShow() {
        _showRequests.tryEmit(Unit)
    }

    /** True when this is the only copy; false when another one is running (it was told to [show]). */
    fun acquire(show: Boolean): Boolean {
        val socket = try {
            ServerSocket().apply { bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port)) }
        } catch (e: BindException) {
            return !notifyRunning(show)
        } catch (e: Exception) {
            return true
        }
        server = socket
        Thread({
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        client.soTimeout = 2_000
                        val line = client.getInputStream().bufferedReader().readLine()
                        client.getOutputStream().write("echo-music\n".toByteArray())
                        if (line == "show") _showRequests.tryEmit(Unit)
                    }
                }
            }
        }, "single-instance").apply { isDaemon = true }.start()
        return true
    }

    /** Tells the running copy to come forward; false if what holds the port is not Echo Music. */
    private fun notifyRunning(show: Boolean): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
            s.soTimeout = 2_000
            s.getOutputStream().write((if (show) "show\n" else "ping\n").toByteArray())
            s.getOutputStream().flush()
            s.getInputStream().bufferedReader().readLine() == "echo-music"
        }
    }.getOrDefault(false)

    /** Frees the port before an update relaunches the app. */
    fun release() {
        runCatching { server?.close() }
        server = null
    }
}
