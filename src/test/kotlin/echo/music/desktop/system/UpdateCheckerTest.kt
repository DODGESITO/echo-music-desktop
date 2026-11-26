package echo.music.desktop.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(UpdateChecker.isNewer("1.1.1", "1.1"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("9.9.9", "dev"), "development runs never announce updates")
    }

    @Test
    fun startupCommandQuotesThePath() {
        assertEquals("\"C:\\Program Files\\EchoMusic\\EchoMusic.exe\" --minimized",
            WindowsStartup.command("C:\\Program Files\\EchoMusic\\EchoMusic.exe", minimized = true))
        assertEquals("\"C:\\EchoMusic.exe\"", WindowsStartup.command("C:\\EchoMusic.exe", minimized = false))
    }

    @Test
    fun versionComesFromTheBuild() {
        assertTrue(AppInfo.version.matches(Regex("""\d+\.\d+\.\d+""")), AppInfo.version)
    }
}
