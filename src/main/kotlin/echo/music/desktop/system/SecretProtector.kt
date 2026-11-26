package echo.music.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64

/**
 * Encrypts secrets at rest with Windows DPAPI (the same mechanism Chrome/Edge use for cookies):
 * only the same Windows user on the same PC can decrypt them. On other systems it is a no-op
 * marker so data stays readable.
 */
object SecretProtector {
    private const val DPAPI_PREFIX = "dpapi:"
    private val entropy = "EchoMusicDesktop/v1".toByteArray(Charsets.UTF_8)

    val isAvailable: Boolean get() = Platform.isWindows()

    /** Returns an encoded, encrypted form of [plain] (prefixed so it can be recognized later). */
    fun protect(plain: String): String {
        if (plain.isEmpty() || !isAvailable) return plain
        val encrypted = Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8), entropy, 0, "", null)
        return DPAPI_PREFIX + Base64.getEncoder().encodeToString(encrypted)
    }

    /** Reverses [protect]. Values that were never protected are returned unchanged. */
    fun unprotect(stored: String): String {
        if (!isProtected(stored)) return stored
        val blob = Base64.getDecoder().decode(stored.removePrefix(DPAPI_PREFIX))
        return String(Crypt32Util.cryptUnprotectData(blob, entropy, 0, null), Charsets.UTF_8)
    }

    fun isProtected(value: String): Boolean = value.startsWith(DPAPI_PREFIX)
}
