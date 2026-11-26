package echo.music.desktop.state

import echo.music.desktop.system.SecretProtector
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountStoreTest {
    companion object {
        // One temp home for the whole class (JUnit makes a new instance per test, and AccountStore
        // resolves its files once). Never touches the real ~/.echomusic.
        private val home: File = Files.createTempDirectory("echo-account-test").toFile()
            .also { System.setProperty("user.home", it.absolutePath) }
    }

    private val cookie = "SID=abc123; SAPISID=xyz/789; LOGIN_INFO=secret-value"

    private fun accountFile() = File(home, ".echomusic/account.json")

    @Test
    fun protectRoundTrip() {
        val protected = SecretProtector.protect(cookie)
        if (SecretProtector.isAvailable) {
            assertTrue(SecretProtector.isProtected(protected))
            assertFalse(protected.contains("secret-value"))
        }
        assertEquals(cookie, SecretProtector.unprotect(protected))
        assertEquals("plain", SecretProtector.unprotect("plain"))
    }

    @Test
    fun cookieIsEncryptedOnDiskAndReadBack() {
        AccountStore.saveYouTubeAccount(UserAccountData(name = "Test", cookie = cookie))
        val onDisk = accountFile().readText()
        if (SecretProtector.isAvailable) {
            assertFalse(onDisk.contains("secret-value"), "cookie must not be stored in plain text")
        }
        assertEquals(cookie, AccountStore.getYouTubeAccount()?.cookie)
    }

    @Test
    fun oldPlainTextFileIsMigrated() {
        accountFile().parentFile.mkdirs()
        accountFile().writeText("""{"name":"Old","cookie":"$cookie"}""")
        assertEquals(cookie, AccountStore.getYouTubeAccount()?.cookie)
        if (SecretProtector.isAvailable) {
            assertFalse(accountFile().readText().contains("secret-value"))
        }
        assertEquals(cookie, AccountStore.getYouTubeAccount()?.cookie)
    }

    @Test
    fun logoutRemovesAccountAndBrowserProfile() {
        AccountStore.saveYouTubeAccount(UserAccountData(name = "Test", cookie = cookie))
        File(AccountStore.browserAuthProfileDir, "Default").mkdirs()
        AccountStore.clearYouTubeAccount()
        assertNull(AccountStore.getYouTubeAccount())
        assertFalse(AccountStore.browserAuthProfileDir.exists())
    }
}
