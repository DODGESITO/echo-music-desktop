package echo.music.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** "Start with Windows": a value under HKCU\...\Run pointing at the installed EchoMusic.exe (per user, no admin). */
object WindowsStartup {
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "EchoMusic"
    const val MINIMIZED_ARG = "--minimized"

    /** Only the installed app has a stable .exe to register. */
    val isSupported: Boolean get() = Platform.isWindows() && AppInfo.launcherPath != null

    fun isEnabled(): Boolean = Platform.isWindows() && runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE)
    }.getOrDefault(false)

    fun command(exe: String, minimized: Boolean) = "\"$exe\"" + if (minimized) " $MINIMIZED_ARG" else ""

    /** Returns false when it could not be changed. */
    fun setEnabled(enabled: Boolean, minimized: Boolean): Boolean = runCatching {
        if (enabled) {
            val exe = AppInfo.launcherPath ?: return false
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE, command(exe, minimized))
        } else if (isEnabled()) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE)
        }
        true
    }.getOrDefault(false)

    /** Keeps the registered command in sync if the app was reinstalled elsewhere or the option changed. */
    fun refresh(minimized: Boolean) {
        val exe = AppInfo.launcherPath ?: return
        if (!isEnabled()) return
        val current = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE) }.getOrNull()
        if (current != command(exe, minimized)) setEnabled(true, minimized)
    }
}
