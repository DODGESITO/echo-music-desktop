package echo.music.desktop.state

import echo.music.desktop.system.SecretProtector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserAccountData(
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val avatarUrl: String? = null,
    /** YouTube Music session cookie. Kept in memory in plain text; encrypted on disk. */
    val cookie: String = ""
)

object AccountStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val accountFile: File by lazy {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".echomusic")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "account.json")
    }

    /** Browser profile used by the "Iniciar sesión con Edge/Chrome" flow (holds Google cookies). */
    val browserAuthProfileDir: File by lazy {
        File(System.getProperty("user.home"), ".echomusic/browser_auth_profile")
    }

    fun getYouTubeAccount(): UserAccountData? {
        return try {
            if (!accountFile.exists()) return null
            val text = accountFile.readText()
            if (text.isBlank()) return null
            val stored = json.decodeFromString<UserAccountData>(text)
            val account = stored.copy(cookie = SecretProtector.unprotect(stored.cookie))
            // Files written by older versions kept the cookie in plain text: encrypt it now.
            if (account.cookie.isNotEmpty() && !SecretProtector.isProtected(stored.cookie) && SecretProtector.isAvailable) {
                saveYouTubeAccount(account)
            }
            account
        } catch (e: Exception) {
            // Undecryptable (e.g. copied from another PC/user): behave as logged out.
            System.err.println("[AccountStore] Error reading account: ${e.message}")
            null
        }
    }

    fun saveYouTubeAccount(account: UserAccountData) {
        try {
            val onDisk = account.copy(cookie = SecretProtector.protect(account.cookie))
            val text = json.encodeToString(UserAccountData.serializer(), onDisk)
            val tmp = File(accountFile.parentFile, accountFile.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(accountFile)) {
                accountFile.delete()
                tmp.renameTo(accountFile)
            }
        } catch (e: Exception) {
            System.err.println("[AccountStore] Error saving account: ${e.message}")
        }
    }

    /** Logs out: deletes the saved account and the browser login profile (Google cookies). */
    fun clearYouTubeAccount() {
        try {
            if (accountFile.exists()) accountFile.delete()
        } catch (_: Exception) {
        }
        try {
            if (browserAuthProfileDir.exists()) browserAuthProfileDir.deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
