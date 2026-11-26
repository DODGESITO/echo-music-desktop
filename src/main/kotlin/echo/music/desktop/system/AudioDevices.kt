package echo.music.desktop.system

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One audio output of Windows (speakers, headphones, Bluetooth, HDMI...). */
data class AudioOutput(
    val id: String,
    val name: String,
    /** DEVICE_STATE_*: 1 active, 2 disabled, 4 not present, 8 unplugged. */
    val state: Int,
    /** Bus that provides it: "BTHENUM"/"BTHHFENUM"/"BTHLEDEVICE" for Bluetooth, "USB", "HDAUDIO"... */
    val enumerator: String,
    /** PKEY_AudioEndpoint_FormFactor: 1 speakers, 3 headphones, 5 headset... */
    val formFactor: Int
) {
    val isActive: Boolean get() = state == 1
    val isBluetooth: Boolean get() = enumerator.uppercase().startsWith("BTH")

    /** Audio that goes only to the user's ears: when it disappears, music should not jump to the speakers. */
    val isPersonal: Boolean get() = isBluetooth || formFactor == 3 || formFactor == 5
}

/**
 * Windows Core Audio (MMDevice API) through JNA: the default output and the list of outputs.
 * Calls run on one MTA worker thread.
 */
object AudioDevices {
    private val CLSID_ENUMERATOR = Guid.CLSID.fromString("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    private val IID_ENUMERATOR = Guid.GUID.fromString("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    private const val E_RENDER = 0
    private const val E_MULTIMEDIA = 1
    private const val STATE_MASK_ALL = 0xF

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "audio-devices").apply { isDaemon = true } }
    private var enumerator: Pointer? = null

    /** The output Windows sends media to right now (null when there is none). */
    fun defaultOutput(): AudioOutput? = onWorker {
        val e = enumerator() ?: return@onWorker null
        val device = PointerByReference()
        if (call(e, 4, E_RENDER, E_MULTIMEDIA, device) != 0 || device.value == null) return@onWorker null
        try {
            describe(device.value)
        } finally {
            release(device.value)
        }
    }

    /** Every output Windows knows, connected or not. */
    fun allOutputs(): List<AudioOutput> = onWorker {
        val e = enumerator() ?: return@onWorker emptyList()
        val collection = PointerByReference()
        if (call(e, 3, E_RENDER, STATE_MASK_ALL, collection) != 0 || collection.value == null) return@onWorker emptyList()
        val c = collection.value
        try {
            val count = IntByReference()
            call(c, 3, count) // GetCount
            (0 until count.value).mapNotNull { i ->
                val device = PointerByReference()
                if (call(c, 4, i, device) != 0 || device.value == null) return@mapNotNull null // Item
                try {
                    describe(device.value)
                } finally {
                    release(device.value)
                }
            }
        } finally {
            release(c)
        }
    } ?: emptyList()

    /** State of the output [id] (0 when Windows no longer knows it). */
    fun stateOf(id: String): Int = onWorker {
        val e = enumerator() ?: return@onWorker 0
        val device = PointerByReference()
        val hr = call(e, 5, com.sun.jna.WString(id), device) // GetDevice
        if (hr != 0 || device.value == null) return@onWorker 0
        try {
            val state = IntByReference()
            if (call(device.value, 6, state) == 0) state.value else 0 // GetState
        } finally {
            release(device.value)
        }
    } ?: 0

    /** IMMDeviceEnumerator::RegisterEndpointNotificationCallback with a COM object built by the caller. */
    internal fun registerNotifications(client: Pointer): Boolean = onWorker {
        val e = enumerator() ?: return@onWorker false
        call(e, 6, client) == 0
    } ?: false

    // ------------------------------------------------------------------ internals

    private fun <T> onWorker(block: () -> T): T? {
        if (!Platform.isWindows()) return null
        return runCatching { worker.submit<T> { block() }.get(5, TimeUnit.SECONDS) }
            .onFailure { System.err.println("AudioDevices: ${it.message}") }
            .getOrNull()
    }

    private fun enumerator(): Pointer? {
        enumerator?.let { return it }
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_ENUMERATOR, null, 1 /* CLSCTX_INPROC_SERVER */, IID_ENUMERATOR, out)
        if (hr.toInt() != 0) return null
        enumerator = out.value
        return out.value
    }

    private fun describe(device: Pointer): AudioOutput? {
        val idOut = PointerByReference()
        if (call(device, 5, idOut) != 0 || idOut.value == null) return null // GetId
        val id = idOut.value.getWideString(0)
        Ole32.INSTANCE.CoTaskMemFree(idOut.value)
        val state = IntByReference()
        call(device, 6, state) // GetState
        val store = PointerByReference()
        var name = id
        var enumeratorName = ""
        var formFactor = 0
        if (call(device, 4, 0 /* STGM_READ */, store) == 0 && store.value != null) {
            val s = store.value
            try {
                readString(s, "{a45c254e-df1c-4efd-8020-67d146a850e0}", 14)?.let { name = it } // Device_FriendlyName
                readString(s, "{a45c254e-df1c-4efd-8020-67d146a850e0}", 24)?.let { enumeratorName = it } // Device_EnumeratorName
                formFactor = readUInt(s, "{1da5d803-d492-4edd-8c23-e0c0ffee7f0e}", 0) ?: 0 // AudioEndpoint_FormFactor
            } finally {
                release(s)
            }
        }
        return AudioOutput(id, name, state.value, enumeratorName, formFactor)
    }

    /** PROPVARIANT (24 bytes on 64-bit): vt at 0, value at 8. */
    private fun readValue(store: Pointer, fmtid: String, pid: Int, read: (Memory) -> Any?): Any? {
        val key = Memory(20)
        val guid = Guid.GUID.fromString(fmtid)
        guid.write()
        key.write(0, guid.pointer.getByteArray(0, 16), 0, 16)
        key.setInt(16, pid)
        val value = Memory(24)
        value.clear()
        if (call(store, 5, key, value) != 0) return null // IPropertyStore::GetValue
        return try {
            read(value)
        } finally {
            PropVariantApi.lib.PropVariantClear(value)
        }
    }

    private fun readString(store: Pointer, fmtid: String, pid: Int): String? =
        readValue(store, fmtid, pid) { v ->
            if (v.getShort(0).toInt() == 31 /* VT_LPWSTR */) v.getPointer(8)?.getWideString(0) else null
        } as String?

    private fun readUInt(store: Pointer, fmtid: String, pid: Int): Int? =
        readValue(store, fmtid, pid) { v ->
            if (v.getShort(0).toInt() == 19 /* VT_UI4 */) v.getInt(8) else null
        } as Int?

    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val fn = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun release(obj: Pointer?) {
        if (obj != null) call(obj, 2)
    }
}

@Suppress("FunctionName")
private object PropVariantApi {
    interface Lib : com.sun.jna.win32.StdCallLibrary {
        fun PropVariantClear(value: Pointer): Int
    }

    val lib: Lib = Native.load("ole32", Lib::class.java)
}
