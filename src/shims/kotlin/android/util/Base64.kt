package android.util

/** Desktop stand-in for android.util.Base64 (used by the shared Shazam signature generator). */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        // java.util.Base64 never wraps lines, which is what NO_WRAP asks for.
        return encoder.encodeToString(input)
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray =
        if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder().decode(str) else java.util.Base64.getDecoder().decode(str)
}
