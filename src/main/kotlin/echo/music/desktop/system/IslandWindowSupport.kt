package echo.music.desktop.system

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

/** Win32 bits for the dynamic island: an overlay that never takes focus and hides in full-screen apps. */
object IslandWindowSupport {
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000
    private const val WS_EX_NOACTIVATE = 0x08000000
    private const val SW_HIDE = 0
    private const val SW_SHOWNOACTIVATE = 4
    private const val MONITOR_DEFAULTTONEAREST = 2

    /** Desktop and taskbar windows: never "full screen" even though they cover the monitor. */
    private val shellClasses = setOf("Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd")

    private val ownPid = ProcessHandle.current().pid()

    /** Keeps the island out of the taskbar and Alt+Tab, and clicking it never steals focus from other apps. */
    fun makeOverlay(hwnd: Long) {
        if (!Platform.isWindows() || hwnd == 0L) return
        runCatching {
            val h = WinDef.HWND(Pointer(hwnd))
            val user32 = User32.INSTANCE
            val style = user32.GetWindowLongPtr(h, WinUser.GWL_EXSTYLE).toLong()
            val newStyle = (style or WS_EX_TOOLWINDOW.toLong() or WS_EX_NOACTIVATE.toLong()) and WS_EX_APPWINDOW.toLong().inv()
            if (newStyle != style) {
                // The taskbar only drops the button when the window is shown again.
                user32.ShowWindow(h, SW_HIDE)
                user32.SetWindowLongPtr(h, WinUser.GWL_EXSTYLE, Pointer(newStyle))
                user32.ShowWindow(h, SW_SHOWNOACTIVATE)
            }
            keepOnTop(hwnd)
        }
    }

    /** Puts the island back above other always-on-top windows (and restores it if it lost the flag). */
    fun keepOnTop(hwnd: Long) {
        if (!Platform.isWindows() || hwnd == 0L) return
        runCatching {
            User32.INSTANCE.SetWindowPos(
                WinDef.HWND(Pointer(hwnd)), WinDef.HWND(Pointer.createConstant(-1L)), // HWND_TOPMOST
                0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE
            )
        }
    }

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOACTIVATE = 0x0010

    /**
     * True while the window in front covers its whole monitor, taskbar included (games, videos and
     * presentations). Maximized windows leave the taskbar visible, so they do not count.
     * (Windows' SHQueryUserNotificationState also reports "busy" for ordinary maximized windows.)
     */
    fun isFullScreenAppRunning(): Boolean {
        if (!Platform.isWindows()) return false
        return runCatching {
            val user32 = User32.INSTANCE
            val fg = user32.GetForegroundWindow() ?: return false
            val pid = IntByReference()
            user32.GetWindowThreadProcessId(fg, pid)
            if (pid.value.toLong() == ownPid) return false
            val className = CharArray(64)
            val len = user32.GetClassName(fg, className, className.size)
            if (String(className, 0, len) in shellClasses) return false

            val rect = WinDef.RECT()
            if (!user32.GetWindowRect(fg, rect)) return false
            val monitor = user32.MonitorFromWindow(fg, MONITOR_DEFAULTTONEAREST)
            val info = WinUser.MONITORINFO()
            if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false
            val m = info.rcMonitor
            rect.left <= m.left && rect.top <= m.top && rect.right >= m.right && rect.bottom >= m.bottom
        }.getOrDefault(false)
    }
}
