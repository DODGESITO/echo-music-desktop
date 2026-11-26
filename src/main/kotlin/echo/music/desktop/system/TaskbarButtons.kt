package echo.music.desktop.system

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.Executors

/**
 * ⏮ ⏯ ⏭ under the window preview that Windows shows when the mouse is over the taskbar icon
 * (ITaskbarList3 thumbnail toolbar). Clicks arrive as WM_COMMAND, so the window procedure is wrapped.
 */
object TaskbarButtons {
    private const val ID_PREVIOUS = 1
    private const val ID_PLAY_PAUSE = 2
    private const val ID_NEXT = 3
    private const val WM_COMMAND = 0x0111
    private const val THBN_CLICKED = 0x1800
    private const val GWLP_WNDPROC = -4

    // THUMBBUTTON (64-bit layout): mask, id, bitmap, [pad], hIcon, szTip[260], flags
    private const val THUMB_SIZE = 552
    private const val THB_ICON = 0x2
    private const val THB_TOOLTIP = 0x4
    private const val THB_FLAGS = 0x8

    private val CLSID_TASKBAR = Guid.CLSID.fromString("{56FDF344-FD6D-11d0-958A-006097C9A090}")
    private val IID_TASKBAR3 = Guid.GUID.fromString("{ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf}")

    // All ITaskbarList calls on one COM (STA) thread.
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "taskbar-buttons").apply { isDaemon = true } }
    private var taskbar: Pointer? = null
    private var hwnd: WinDef.HWND? = null
    private var added = false
    private var playing = false
    private var onButton: (MediaControlButton) -> Unit = {}

    // Kept strongly referenced: Windows calls them for as long as the window lives.
    private var oldProc: Pointer? = null
    private var proc: WinUser.WindowProc? = null
    private var taskbarCreatedMessage = 0

    private val icons by lazy {
        val size = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSMICON).coerceIn(16, 48)
        mapOf(
            "prev" to icon(size) { g, s -> drawSkip(g, s, forward = false) },
            "play" to icon(size) { g, s -> drawPlay(g, s) },
            "pause" to icon(size) { g, s -> drawPause(g, s) },
            "next" to icon(size) { g, s -> drawSkip(g, s, forward = true) }
        )
    }

    /** Adds the buttons to the window [handle] (once it has a taskbar button). */
    fun install(handle: Long, onButton: (MediaControlButton) -> Unit) {
        if (!Platform.isWindows() || handle == 0L || !Platform.is64Bit()) return
        this.onButton = onButton
        val h = WinDef.HWND(Pointer(handle))
        worker.execute {
            runCatching {
                if (hwnd == null) {
                    hwnd = h
                    subclass(h)
                }
                if (taskbar == null) {
                    Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
                    val out = PointerByReference()
                    val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_TASKBAR, null, 1 /* CLSCTX_INPROC_SERVER */, IID_TASKBAR3, out)
                    if (hr.toInt() != 0 || out.value == null) return@runCatching
                    taskbar = out.value
                    call(out.value, 3) // HrInit
                }
                addButtons()
            }.onFailure { System.err.println("TaskbarButtons: ${it.message}") }
        }
    }

    /** Shows ⏸ while playing and ▶ while paused. */
    fun setPlaying(isPlaying: Boolean) {
        worker.execute {
            if (playing == isPlaying && added) return@execute
            playing = isPlaying
            val tb = taskbar ?: return@execute
            val h = hwnd ?: return@execute
            if (!added) return@execute
            val buttons = buttons()
            call(tb, 16, h.pointer, 3, buttons) // ThumbBarUpdateButtons
        }
    }

    private fun addButtons() {
        val tb = taskbar ?: return
        val h = hwnd ?: return
        val hr = call(tb, 15, h.pointer, 3, buttons()) // ThumbBarAddButtons
        added = hr == 0
    }

    private fun buttons(): Memory {
        val mem = Memory(THUMB_SIZE.toLong() * 3)
        mem.clear()
        fun put(index: Int, id: Int, icon: WinDef.HICON, tip: String) {
            val base = index.toLong() * THUMB_SIZE
            mem.setInt(base, THB_ICON or THB_TOOLTIP or THB_FLAGS)
            mem.setInt(base + 4, id)
            mem.setPointer(base + 16, icon.pointer)
            mem.setWideString(base + 24, tip.take(259))
            mem.setInt(base + 544, 0) // THBF_ENABLED
        }
        put(0, ID_PREVIOUS, icons.getValue("prev"), "Anterior")
        put(1, ID_PLAY_PAUSE, icons.getValue(if (playing) "pause" else "play"), if (playing) "Pausar" else "Reproducir")
        put(2, ID_NEXT, icons.getValue("next"), "Siguiente")
        return mem
    }

    private fun subclass(h: WinDef.HWND) {
        val user32 = User32.INSTANCE
        taskbarCreatedMessage = user32.RegisterWindowMessage("TaskbarButtonCreated")
        val previous = user32.GetWindowLongPtr(h, GWLP_WNDPROC).toPointer()
        val wrapper = WinUser.WindowProc { window, msg, wParam, lParam ->
            if (msg == WM_COMMAND && (wParam.toLong() shr 16 and 0xFFFF).toInt() == THBN_CLICKED) {
                when ((wParam.toLong() and 0xFFFF).toInt()) {
                    ID_PREVIOUS -> onButton(MediaControlButton.PREVIOUS)
                    ID_PLAY_PAUSE -> onButton(if (playing) MediaControlButton.PAUSE else MediaControlButton.PLAY)
                    ID_NEXT -> onButton(MediaControlButton.NEXT)
                }
                return@WindowProc WinDef.LRESULT(0)
            }
            if (msg == taskbarCreatedMessage && taskbarCreatedMessage != 0) {
                // Explorer restarted, or the button appeared after we tried: add them (again).
                worker.execute { added = false; addButtons() }
            }
            user32.CallWindowProc(previous, window, msg, wParam, lParam)
        }
        proc = wrapper
        oldProc = previous
        user32.SetWindowLongPtr(h, GWLP_WNDPROC, com.sun.jna.CallbackReference.getFunctionPointer(wrapper))
    }

    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val fn = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(obj, *args))
    }

    // ---------------------------------------------------------------- icons

    private fun icon(size: Int, draw: (java.awt.Graphics2D, Float) -> Unit): WinDef.HICON {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        draw(g, size.toFloat())
        g.dispose()
        return toHicon(image)
    }

    private fun drawPlay(g: java.awt.Graphics2D, s: Float) {
        val p = Path2D.Float()
        p.moveTo(s * 0.30, s * 0.18); p.lineTo(s * 0.82, s * 0.50); p.lineTo(s * 0.30, s * 0.82); p.closePath()
        g.fill(p)
    }

    private fun drawPause(g: java.awt.Graphics2D, s: Float) {
        g.fill(RoundRectangle2D.Float(s * 0.24f, s * 0.18f, s * 0.18f, s * 0.64f, s * 0.08f, s * 0.08f))
        g.fill(RoundRectangle2D.Float(s * 0.58f, s * 0.18f, s * 0.18f, s * 0.64f, s * 0.08f, s * 0.08f))
    }

    private fun drawSkip(g: java.awt.Graphics2D, s: Float, forward: Boolean) {
        val p = Path2D.Float()
        if (forward) {
            p.moveTo(s * 0.18, s * 0.20); p.lineTo(s * 0.66, s * 0.50); p.lineTo(s * 0.18, s * 0.80); p.closePath()
            g.fill(p)
            g.stroke = BasicStroke(s * 0.12f)
            g.drawLine((s * 0.76f).toInt(), (s * 0.22f).toInt(), (s * 0.76f).toInt(), (s * 0.78f).toInt())
        } else {
            p.moveTo(s * 0.82, s * 0.20); p.lineTo(s * 0.34, s * 0.50); p.lineTo(s * 0.82, s * 0.80); p.closePath()
            g.fill(p)
            g.stroke = BasicStroke(s * 0.12f)
            g.drawLine((s * 0.24f).toInt(), (s * 0.22f).toInt(), (s * 0.24f).toInt(), (s * 0.78f).toInt())
        }
    }

    /** 32-bit ARGB BufferedImage → HICON (alpha-blended icon). */
    private fun toHicon(image: BufferedImage): WinDef.HICON {
        val w = image.width
        val h = image.height
        val bmi = WinGDI.BITMAPINFO()
        bmi.bmiHeader.biWidth = w
        bmi.bmiHeader.biHeight = -h // top-down
        bmi.bmiHeader.biPlanes = 1
        bmi.bmiHeader.biBitCount = 32
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB
        val bits = PointerByReference()
        val color = GDI32.INSTANCE.CreateDIBSection(null, bmi, WinGDI.DIB_RGB_COLORS, bits, null, 0)
        val pixels = image.getRGB(0, 0, w, h, null, 0, w)
        // DIB wants premultiplied BGRA.
        val premultiplied = IntArray(pixels.size) { i ->
            val a = pixels[i] ushr 24
            val r = (pixels[i] shr 16 and 0xFF) * a / 255
            val gr = (pixels[i] shr 8 and 0xFF) * a / 255
            val b = (pixels[i] and 0xFF) * a / 255
            (a shl 24) or (r shl 16) or (gr shl 8) or b
        }
        bits.value.write(0, premultiplied, 0, premultiplied.size)
        val mask = IconApi.gdi.CreateBitmap(w, h, 1, 1, null)
        val info = WinGDI.ICONINFO()
        info.fIcon = true
        info.hbmColor = color
        info.hbmMask = mask
        val icon = IconApi.user.CreateIconIndirect(info)
        GDI32.INSTANCE.DeleteObject(color)
        GDI32.INSTANCE.DeleteObject(mask)
        return icon
    }
}

/** Win32 functions JNA's platform classes do not declare. */
@Suppress("FunctionName")
private object IconApi {
    interface Gdi : com.sun.jna.win32.StdCallLibrary {
        fun CreateBitmap(width: Int, height: Int, planes: Int, bitsPerPixel: Int, bits: Pointer?): WinDef.HBITMAP
    }

    interface User : com.sun.jna.win32.StdCallLibrary {
        fun CreateIconIndirect(info: WinGDI.ICONINFO): WinDef.HICON
    }

    val gdi: Gdi = Native.load("gdi32", Gdi::class.java)
    val user: User = Native.load("user32", User::class.java)
}
