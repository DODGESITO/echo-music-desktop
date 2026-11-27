package echo.music.desktop.system

import echo.music.desktop.player.DesktopStreamResolver
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorLogTest {
    @Test
    fun findsNetworkErrorsDeepInTheCauseChain() {
        var error: Throwable = SocketException("Software caused connection abort")
        repeat(15) { error = RuntimeException("wrapper $it", error) }
        assertTrue(ErrorLog.isNetworkError(error))
        assertTrue(ErrorLog.isNetworkError(IllegalStateException(UnknownHostException("music.youtube.com"))))
    }

    @Test
    fun otherErrorsAreNotNetworkErrorsAndCyclesEnd() {
        assertFalse(ErrorLog.isNetworkError(IllegalStateException("bug")))
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertFalse(ErrorLog.isNetworkError(a))
    }

    @Test
    fun onlyBusyStatusesAreRetried() {
        assertTrue(DesktopStreamResolver.isTemporaryStatus(429))
        assertTrue(DesktopStreamResolver.isTemporaryStatus(503))
        assertFalse(DesktopStreamResolver.isTemporaryStatus(403))
        assertFalse(DesktopStreamResolver.isTemporaryStatus(404))
    }
}
