package echo.music.desktop.connect

import java.security.SecureRandom

/**
 * The 6-digit code this PC shows when it starts pairing. The phone types it in and sends it to
 * POST /v1/pair/verify. Unlike Android and the official desktop, wrong guesses are limited: after
 * [MAX_WRONG] wrong codes the code is thrown away, so the million combinations cannot be tried
 * within its minute of life.
 */
class HostPairingCode(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom()
) {
    data class Code(val peer: ConnectPeer, val code: String, val expiresAtMs: Long)

    sealed interface Result {
        data class Paired(val token: String) : Result
        data class Rejected(val error: String) : Result
    }

    @Volatile var current: Code? = null
        private set
    private var wrong = 0

    @Synchronized
    fun start(peer: ConnectPeer): Code {
        val code = Code(peer, "%06d".format(100_000 + random.nextInt(900_000)), clock() + LIFETIME_MS)
        current = code
        wrong = 0
        return code
    }

    @Synchronized
    fun cancel() {
        current = null
        wrong = 0
    }

    @Synchronized
    fun verify(deviceId: String, code: String): Result {
        val session = current
        if (session == null || clock() > session.expiresAtMs) {
            return Result.Rejected("Code expired. Check Echo Music on the PC for the new code.")
        }
        // Only the device this PC chose to pair with may answer.
        if (deviceId.isNotBlank() && deviceId != session.peer.id) {
            return Result.Rejected("This PC is pairing with another device.")
        }
        if (code.trim() != session.code) {
            wrong++
            if (wrong >= MAX_WRONG) {
                current = null
                return Result.Rejected("Too many wrong codes. Start pairing again on the PC.")
            }
            return Result.Rejected("Incorrect 6-digit code.")
        }
        current = null
        wrong = 0
        return Result.Paired(newToken())
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LIFETIME_MS = 60_000L
        const val MAX_WRONG = 5
    }
}

/**
 * Who may use the endpoints that read or change this PC (playback, queue, playlists, account).
 *
 * Android's Echo Connect sends no token with its requests, so the only thing that tells the paired
 * phone apart is where the request comes from: its address on the local network. Requests from
 * any other address only get the public parts (pairing and a state without the queue).
 */
object ConnectAccess {
    fun isFromPairedPeer(remoteAddress: String?, pairedHost: String?): Boolean {
        if (remoteAddress.isNullOrBlank() || pairedHost.isNullOrBlank()) return false
        return normalize(remoteAddress) == normalize(pairedHost)
    }

    /** "/192.168.1.5", "::ffff:192.168.1.5" and "192.168.1.5" are the same address. */
    internal fun normalize(address: String): String =
        address.trim().removePrefix("/").substringBefore('%').lowercase().removePrefix("::ffff:")
}
