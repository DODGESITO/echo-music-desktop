package echo.music.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities

enum class MediaKey { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

/**
 * Listens to the keyboard media keys (⏯ ⏭ ⏮ ⏹) system-wide on Windows, even when Echo Music is
 * minimized or another app has focus. Uses a low-level keyboard hook that only observes keys:
 * they are passed on to Windows unchanged, so other apps keep receiving them.
 */
object GlobalMediaKeys {
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    @Volatile private var thread: Thread? = null
    @Volatile private var threadId: Int = 0
    @Volatile private var hook: WinUser.HHOOK? = null
    // Strong reference: if the callback were garbage collected, Windows would call freed memory.
    private var proc: WinUser.LowLevelKeyboardProc? = null

    val isRunning: Boolean get() = hook != null

    /**
     * Starts listening; [onKey] runs on the UI thread. Returns false when not on Windows or when
     * the hook could not be installed (the in-window shortcuts still work then).
     */
    @Synchronized
    fun start(onKey: (MediaKey) -> Unit): Boolean {
        if (!Platform.isWindows()) return false
        if (thread != null) return isRunning

        val ready = java.util.concurrent.CountDownLatch(1)
        val t = Thread({
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val callback = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
                if (nCode >= 0 && (wParam.toInt() == WinUser.WM_KEYDOWN || wParam.toInt() == WinUser.WM_SYSKEYDOWN)) {
                    val key = when (info.vkCode) {
                        VK_MEDIA_PLAY_PAUSE -> MediaKey.PLAY_PAUSE
                        VK_MEDIA_NEXT_TRACK -> MediaKey.NEXT
                        VK_MEDIA_PREV_TRACK -> MediaKey.PREVIOUS
                        VK_MEDIA_STOP -> MediaKey.STOP
                        else -> null
                    }
                    // Hooks must return fast: hand the work to the UI thread.
                    if (key != null) SwingUtilities.invokeLater { onKey(key) }
                }
                User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)))
            }
            proc = callback
            hook = User32.INSTANCE.SetWindowsHookEx(
                WinUser.WH_KEYBOARD_LL,
                callback,
                Kernel32.INSTANCE.GetModuleHandle(null),
                0
            )
            ready.countDown()
            if (hook == null) return@Thread

            // Message loop required for low-level hooks; ends on WM_QUIT from stop().
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }
            hook?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
            hook = null
            proc = null
        }, "echo-media-keys")
        t.isDaemon = true
        thread = t
        t.start()
        ready.await(2, java.util.concurrent.TimeUnit.SECONDS)
        if (hook == null) {
            thread = null
            return false
        }
        return true
    }

    @Synchronized
    fun stop() {
        val t = thread ?: return
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }
        t.join(1_000)
        thread = null
        threadId = 0
    }
}
