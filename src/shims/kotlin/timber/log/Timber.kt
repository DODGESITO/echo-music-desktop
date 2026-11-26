package timber.log

/**
 * Desktop stand-in for the Timber logger used by shared modules.
 * Debug logs are dropped; warnings and errors go to stderr when ECHO_DEBUG=1.
 */
object Timber {
    private val enabled = System.getenv("ECHO_DEBUG") == "1"

    fun tag(@Suppress("UNUSED_PARAMETER") tag: String): Timber = this

    fun d(message: String?, vararg args: Any?) = Unit

    fun i(message: String?, vararg args: Any?) = Unit

    fun w(message: String?, vararg args: Any?) {
        if (enabled) System.err.println("[W] $message")
    }

    fun w(t: Throwable?, message: String?, vararg args: Any?) {
        if (enabled) System.err.println("[W] $message: ${t?.message}")
    }

    fun e(message: String?, vararg args: Any?) {
        if (enabled) System.err.println("[E] $message")
    }

    fun e(t: Throwable?, message: String?, vararg args: Any?) {
        if (enabled) System.err.println("[E] $message: ${t?.message}")
    }

    fun e(t: Throwable?) {
        if (enabled) System.err.println("[E] ${t?.message}")
    }
}
