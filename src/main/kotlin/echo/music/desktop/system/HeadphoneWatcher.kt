package echo.music.desktop.system

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.win32.StdCallLibrary

/** What changed in the default output between two checks. */
sealed interface OutputEvent {
    /** Headphones (Bluetooth or wired) that were playing went away. */
    data class PersonalDisconnected(val device: AudioOutput, val now: AudioOutput?) : OutputEvent

    /** The default output changed for another reason (connected a device, picked another one...). */
    data class Changed(val previous: AudioOutput?, val now: AudioOutput?) : OutputEvent
}

/** Turns successive "default output" readings into events. */
class OutputChangeTracker(private val stateOf: (String) -> Int) {
    var last: AudioOutput? = null
        private set
    private var initialized = false

    fun update(current: AudioOutput?): OutputEvent? {
        if (!initialized) {
            initialized = true
            last = current
            return null
        }
        val previous = last
        if (previous?.id == current?.id) return null
        last = current
        // Only a real disconnection counts: choosing the speakers by hand leaves the headphones active.
        return if (previous != null && previous.isPersonal && stateOf(previous.id) != 1) {
            OutputEvent.PersonalDisconnected(previous, current)
        } else {
            OutputEvent.Changed(previous, current)
        }
    }
}

/**
 * Calls [onChange] as soon as Windows reports an audio device change (IMMNotificationClient),
 * so the music can pause before it reaches the speakers.
 */
object AudioDeviceNotifications {
    private const val S_OK = 0
    private const val E_NOINTERFACE = 0x80004002.toInt()
    private val IID_IUNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-C000-000000000046}")
    private val IID_CLIENT = Guid.GUID.fromString("{7991EEC9-7E89-4D85-8390-6C703CEC60C0}")

    interface QueryInterfaceFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    interface RefFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    interface IdStateFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, id: Pointer?, state: Int): Int
    }

    interface IdFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, id: Pointer?): Int
    }

    interface DefaultFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, flow: Int, role: Int, id: Pointer?): Int
    }

    interface PropertyFn : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, id: Pointer?, key: Pointer?): Int
    }

    @Volatile private var listener: () -> Unit = {}

    // Strong references: Windows calls these for as long as the app runs.
    private val qi = object : QueryInterfaceFn {
        override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
            val asked = Guid.GUID(riid)
            return if (asked == IID_CLIENT || asked == IID_IUNKNOWN) {
                out.setPointer(0, self); S_OK
            } else {
                out.setPointer(0, Pointer.NULL); E_NOINTERFACE
            }
        }
    }
    private val ref = object : RefFn {
        override fun invoke(self: Pointer): Int = 1
    }
    private val stateChanged = object : IdStateFn {
        override fun invoke(self: Pointer, id: Pointer?, state: Int): Int { fire(); return S_OK }
    }
    private val added = object : IdFn {
        override fun invoke(self: Pointer, id: Pointer?): Int { fire(); return S_OK }
    }
    private val removed = object : IdFn {
        override fun invoke(self: Pointer, id: Pointer?): Int { fire(); return S_OK }
    }
    private val defaultChanged = object : DefaultFn {
        override fun invoke(self: Pointer, flow: Int, role: Int, id: Pointer?): Int {
            if (flow == 0) fire() // eRender
            return S_OK
        }
    }
    private val propertyChanged = object : PropertyFn {
        override fun invoke(self: Pointer, id: Pointer?, key: Pointer?): Int = S_OK
    }

    private val client: Memory by lazy {
        val fns = listOf(qi, ref, ref, stateChanged, added, removed, defaultChanged, propertyChanged)
        val vtable = Memory(fns.size.toLong() * Native.POINTER_SIZE)
        fns.forEachIndexed { i, f -> vtable.setPointer(i.toLong() * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(f)) }
        Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }.also { keepVtable = vtable }
    }
    private var keepVtable: Memory? = null

    private fun fire() {
        // Called on Windows' audio thread: never block it.
        runCatching { listener() }
    }

    /** Registers [onChange]; returns false when Windows notifications are not available. */
    fun register(onChange: () -> Unit): Boolean {
        listener = onChange
        return AudioDevices.registerNotifications(client)
    }
}
