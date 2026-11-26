package echo.music.desktop.system

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class MediaControlButton { PLAY, PAUSE, STOP, NEXT, PREVIOUS }

/**
 * Windows media panel (System Media Transport Controls): the card with title, artist, cover and
 * ⏮ ⏯ ⏭ that Windows shows next to the volume indicator, in Quick Settings and on the lock screen.
 *
 * Plain WinRT through JNA: ISystemMediaTransportControlsInterop::GetForWindow on the main window, then
 * the interfaces of Windows.Media (vtable slots checked against C:\Windows\System32\WinMetadata).
 * Every WinRT call runs on one MTA worker thread; button presses arrive on Windows' thread pool.
 */
object WindowsMediaControls {
    private const val S_OK = 0
    private const val E_NOINTERFACE = 0x80004002.toInt()
    private const val RO_INIT_MULTITHREADED = 1
    private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()

    // Windows.Media enums
    private const val STATUS_STOPPED = 2
    private const val STATUS_PLAYING = 3
    private const val STATUS_PAUSED = 4
    private const val TYPE_MUSIC = 1

    private val IID_INTEROP = Guid.GUID.fromString("{DDB0472D-C911-4A1F-86D9-DC3D71A95F5A}")
    private const val ISMTC = "{99fa3ff4-1742-42a6-902e-087d41f965ec}"
    private val IID_SMTC = Guid.GUID.fromString(ISMTC)
    private val IID_SMTC2 = Guid.GUID.fromString("{ea98d2f6-7f3c-4af2-a586-72889808efb1}")
    private val IID_TIMELINE = Guid.GUID.fromString("{5125316a-c3a2-475b-8507-93534dc88f15}")
    private val IID_STREAM_REF_STATICS = Guid.GUID.fromString("{857309dc-3fbf-4e7d-986f-ef3b1a07a964}")
    private val IID_URI_FACTORY = Guid.GUID.fromString("{44a9796f-723e-4fdf-a218-033e75b0c084}")
    private val IID_IUNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-C000-000000000046}")
    private val IID_IAGILE = Guid.GUID.fromString("{94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90}")

    /** TypedEventHandler<SystemMediaTransportControls, ...EventArgs> interface ids (WinRT parameterized ids). */
    internal val IID_BUTTON_HANDLER: Guid.GUID = typedEventHandlerIid(
        "rc(Windows.Media.SystemMediaTransportControlsButtonPressedEventArgs;{b7f47116-a56f-4dc8-9e11-92031f4a87c2})"
    )
    internal val IID_SEEK_HANDLER: Guid.GUID = typedEventHandlerIid(
        "rc(Windows.Media.PlaybackPositionChangeRequestedEventArgs;{b4493f88-eb28-4961-9c14-335e44f3e125})"
    )

    @Suppress("FunctionName")
    private interface Combase : StdCallLibrary {
        fun RoInitialize(initType: Int): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Guid.GUID, factory: PointerByReference): Int
        fun RoActivateInstance(classId: Pointer, instance: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
    }

    private val combase: Combase? by lazy {
        if (Platform.isWindows()) runCatching { Native.load("combase", Combase::class.java) }.getOrNull() else null
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "windows-media-controls").apply { isDaemon = true } }

    @Volatile private var smtc: Pointer? = null
    @Volatile private var smtc2: Pointer? = null
    private var updater: Pointer? = null
    private var buttonToken = 0L
    private var seekToken = 0L
    private var onButton: (MediaControlButton) -> Unit = {}
    private var onSeek: (Long) -> Unit = {}

    // Last state sent to Windows (only changes are sent again).
    private var shownSongId: String? = null
    private var shownStatus = -1
    private var timelineSentAt = 0L
    private var timelinePositionMs = 0L
    private var timelineDurationMs = 0L

    val isActive: Boolean get() = smtc != null

    /** Connects the media panel to the window [hwnd]. Returns false when Windows does not offer it. */
    fun start(hwnd: Long, onButton: (MediaControlButton) -> Unit, onSeek: (Long) -> Unit): Boolean {
        if (hwnd == 0L || combase == null) return false
        this.onButton = onButton
        this.onSeek = onSeek
        return runCatching {
            worker.submit<Boolean> { if (smtc != null) true else connect(hwnd) }.get(10, TimeUnit.SECONDS)
        }.getOrElse { e ->
            System.err.println("WindowsMediaControls: ${e.message}")
            false
        }
    }

    fun stop() {
        worker.execute {
            val s = smtc ?: return@execute
            runCatching {
                if (buttonToken != 0L) call(s, 33, buttonToken)
                smtc2?.let { if (seekToken != 0L) call(it, 14, seekToken) }
                call(s, 7, STATUS_STOPPED)
                updater?.let { call(it, 16); call(it, 17) } // ClearAll, Update
                call(s, 11, 0) // IsEnabled = false
            }
            updater?.let(::release)
            smtc2?.let(::release)
            release(s)
            updater = null
            smtc2 = null
            smtc = null
            shownSongId = null
            shownStatus = -1
        }
    }

    /** Title, artist and cover of the current song ([songId] avoids resending the same song). */
    fun updateSong(songId: String?, title: String, artist: String, thumbnailUrl: String?) {
        worker.execute {
            val s = smtc ?: return@execute
            val u = updater ?: return@execute
            if (songId == shownSongId) return@execute
            shownSongId = songId
            runCatching {
                if (songId == null) {
                    call(u, 16) // ClearAll
                    call(u, 17)
                    return@execute
                }
                call(u, 7, TYPE_MUSIC)
                val music = PointerByReference()
                check(call(u, 12, music), "MusicProperties")
                music.value?.let { m ->
                    withHString(title) { call(m, 7, it) }
                    withHString(artist) { call(m, 11, it) }
                    release(m)
                }
                val thumb = thumbnailUrl?.takeIf { it.startsWith("http") }?.let(::highResThumbnail)
                if (thumb != null) streamReferenceFromUri(thumb)?.let { ref ->
                    call(u, 11, ref)
                    release(ref)
                } else call(u, 11, Pointer.NULL)
                call(u, 17) // Update
                call(s, 11, 1) // IsEnabled
            }.onFailure { System.err.println("WindowsMediaControls.updateSong: ${it.message}") }
        }
    }

    /** Playing/paused state and the progress bar of the panel. */
    fun updatePlayback(hasSong: Boolean, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        worker.execute {
            val s = smtc ?: return@execute
            val status = when {
                !hasSong -> STATUS_STOPPED
                isPlaying -> STATUS_PLAYING
                else -> STATUS_PAUSED
            }
            runCatching {
                val statusChanged = status != shownStatus
                if (statusChanged) {
                    call(s, 7, status)
                    shownStatus = status
                }
                val s2 = smtc2 ?: return@runCatching
                val now = System.currentTimeMillis()
                // Where the panel thinks we are (it does not advance the bar by itself while playing).
                val expected = timelinePositionMs + if (shownStatus == STATUS_PLAYING) now - timelineSentAt else 0
                val drift = kotlin.math.abs(expected - positionMs)
                if (statusChanged || durationMs != timelineDurationMs || drift > 1500 || now - timelineSentAt > 5000) {
                    sendTimeline(s2, positionMs, durationMs)
                    timelineSentAt = now
                    timelinePositionMs = positionMs
                    timelineDurationMs = durationMs
                }
            }
        }
    }

    // ---------------------------------------------------------------- setup

    private fun connect(hwnd: Long): Boolean {
        val api = combase ?: return false
        val init = api.RoInitialize(RO_INIT_MULTITHREADED)
        if (init < 0 && init != RPC_E_CHANGED_MODE) return false

        val interop = activationFactory("Windows.Media.SystemMediaTransportControls", IID_INTEROP) ?: return false
        val out = PointerByReference()
        // ISystemMediaTransportControlsInterop::GetForWindow(HWND, REFIID, void**)
        val hr = call(interop, 6, Pointer(hwnd), IID_SMTC, out)
        release(interop)
        val s = out.value
        if (hr != S_OK || s == null) {
            System.err.println("WindowsMediaControls: GetForWindow 0x%08X".format(hr))
            return false
        }

        call(s, 11, 1) // IsEnabled
        call(s, 13, 1) // IsPlayEnabled
        call(s, 17, 1) // IsPauseEnabled
        call(s, 15, 1) // IsStopEnabled
        call(s, 25, 1) // IsPreviousEnabled
        call(s, 27, 1) // IsNextEnabled
        call(s, 7, STATUS_STOPPED)

        val du = PointerByReference()
        if (call(s, 8, du) == S_OK) updater = du.value

        val token = Memory(8)
        if (call(s, 32, buttonHandler.self, token) == S_OK) buttonToken = token.getLong(0)

        val q = PointerByReference()
        if (call(s, 0, IID_SMTC2, q) == S_OK && q.value != null) {
            smtc2 = q.value
            val t2 = Memory(8)
            if (call(q.value, 13, seekHandler.self, t2) == S_OK) seekToken = t2.getLong(0)
        }
        smtc = s
        return true
    }

    private fun sendTimeline(s2: Pointer, positionMs: Long, durationMs: Long) {
        val instance = activateInstance("Windows.Media.SystemMediaTransportControlsTimelineProperties") ?: return
        val q = PointerByReference()
        if (call(instance, 0, IID_TIMELINE, q) == S_OK && q.value != null) {
            val t = q.value
            val end = durationMs.coerceAtLeast(0) * 10_000 // TimeSpan = 100 ns units
            call(t, 7, 0L)                    // StartTime
            call(t, 9, end)                   // EndTime
            call(t, 11, 0L)                   // MinSeekTime
            call(t, 13, end)                  // MaxSeekTime
            call(t, 15, positionMs.coerceIn(0, durationMs.coerceAtLeast(0)) * 10_000) // Position
            call(s2, 12, t)                   // UpdateTimelineProperties
            release(t)
        }
        release(instance)
    }

    private fun streamReferenceFromUri(url: String): Pointer? {
        val uriFactory = activationFactory("Windows.Foundation.Uri", IID_URI_FACTORY) ?: return null
        val uri = PointerByReference()
        val ok = withHString(url) { call(uriFactory, 6, it, uri) } == S_OK
        release(uriFactory)
        if (!ok || uri.value == null) return null
        val statics = activationFactory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REF_STATICS)
        val ref = PointerByReference()
        val hr = statics?.let { call(it, 7, uri.value, ref) } ?: -1
        statics?.let(::release)
        release(uri.value)
        return if (hr == S_OK) ref.value else null
    }

    /** YouTube Music covers come small (w60-h60); the panel shows them big. */
    internal fun highResThumbnail(url: String): String =
        url.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")

    // ---------------------------------------------------------------- event handlers (COM objects)

    interface QueryInterfaceFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    interface RefCountFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    interface InvokeFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    private class Handler(val iid: Guid.GUID, val invoke: (Pointer?) -> Unit) {
        // Callbacks must stay strongly referenced for as long as Windows may call them (forever here).
        private val qi = object : QueryInterfaceFn {
            override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
                val asked = Guid.GUID(riid)
                return if (asked == iid || asked == IID_IUNKNOWN || asked == IID_IAGILE) {
                    out.setPointer(0, self)
                    S_OK
                } else {
                    out.setPointer(0, Pointer.NULL)
                    E_NOINTERFACE
                }
            }
        }
        private val ref = object : RefCountFn {
            override fun invoke(self: Pointer): Int = 1
        }
        private val call = object : InvokeFn {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                runCatching { invoke(args) }
                return S_OK
            }
        }
        private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
            setPointer(0, com.sun.jna.CallbackReference.getFunctionPointer(qi))
            setPointer(1L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(ref)) // AddRef
            setPointer(2L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(ref)) // Release
            setPointer(3L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(call))
        }
        val self: Memory = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
    }

    private val buttonHandler = Handler(IID_BUTTON_HANDLER) { args ->
        if (args == null) return@Handler
        val button = IntByReference()
        if (call(args, 6, button) != S_OK) return@Handler // get_Button
        val mapped = when (button.value) {
            0 -> MediaControlButton.PLAY
            1 -> MediaControlButton.PAUSE
            2 -> MediaControlButton.STOP
            6 -> MediaControlButton.NEXT
            7 -> MediaControlButton.PREVIOUS
            else -> null
        }
        mapped?.let { onButton(it) }
    }

    private val seekHandler = Handler(IID_SEEK_HANDLER) { args ->
        if (args == null) return@Handler
        val position = Memory(8)
        if (call(args, 6, position) == S_OK) onSeek(position.getLong(0) / 10_000) // RequestedPlaybackPosition
    }

    // ---------------------------------------------------------------- WinRT helpers

    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val fn = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun release(obj: Pointer?) {
        if (obj != null) call(obj, 2)
    }

    private fun check(hr: Int, what: String) {
        if (hr != S_OK) error("$what 0x%08X".format(hr))
    }

    private fun <T> withHString(text: String, block: (Pointer?) -> T): T {
        val api = combase!!
        val h = PointerByReference()
        api.WindowsCreateString(WString(text), text.length, h)
        try {
            return block(h.value)
        } finally {
            api.WindowsDeleteString(h.value)
        }
    }

    private fun activationFactory(className: String, iid: Guid.GUID): Pointer? {
        val out = PointerByReference()
        val hr = withHString(className) { combase!!.RoGetActivationFactory(it!!, iid, out) }
        return if (hr == S_OK) out.value else null
    }

    private fun activateInstance(className: String): Pointer? {
        val out = PointerByReference()
        val hr = withHString(className) { combase!!.RoActivateInstance(it!!, out) }
        return if (hr == S_OK) out.value else null
    }

    /** WinRT parameterized interface id: UUID v5 (SHA-1) of the type signature in the WinRT namespace. */
    internal fun typedEventHandlerIid(argsSignature: String): Guid.GUID {
        val signature = "pinterface({9de1c534-6ae1-11e0-84e1-18a905bcc53f};" +
            "rc(Windows.Media.SystemMediaTransportControls;$ISMTC);$argsSignature)"
        return pinterfaceIid(signature)
    }

    internal fun pinterfaceIid(signature: String): Guid.GUID {
        val namespace = byteArrayOf(
            0x11, 0xf4.toByte(), 0x7a, 0xd5.toByte(), 0x7b, 0x73, 0x42, 0xc0.toByte(),
            0xab.toByte(), 0xae.toByte(), 0x87.toByte(), 0x8b.toByte(), 0x1e, 0x16, 0xad.toByte(), 0xee.toByte()
        )
        val hash = MessageDigest.getInstance("SHA-1").apply {
            update(namespace)
            update(signature.toByteArray(Charsets.UTF_8))
        }.digest()
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = hash.take(16).joinToString("") { "%02x".format(it) }
        return Guid.GUID.fromString(
            "{${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}}"
        )
    }
}
