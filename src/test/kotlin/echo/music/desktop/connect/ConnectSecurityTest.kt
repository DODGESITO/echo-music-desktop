package echo.music.desktop.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectSecurityTest {
    private val phone = ConnectPeer("phone-1", "Pixel", "android", "192.168.1.20", 45870)
    private var now = 1_000_000L
    private val pairing = HostPairingCode(clock = { now })

    @Test
    fun rightCodeFromTheChosenPhonePairs() {
        val code = pairing.start(phone)
        val result = pairing.verify("phone-1", code.code)
        assertIs<HostPairingCode.Result.Paired>(result)
        assertEquals(32, result.token.length)
        assertNull(pairing.current, "a code works only once")
    }

    @Test
    fun anotherDeviceCannotUseTheCode() {
        val code = pairing.start(phone)
        assertIs<HostPairingCode.Result.Rejected>(pairing.verify("intruder", code.code))
    }

    @Test
    fun expiredCodeIsRejected() {
        val code = pairing.start(phone)
        now += HostPairingCode.LIFETIME_MS + 1
        assertIs<HostPairingCode.Result.Rejected>(pairing.verify("phone-1", code.code))
    }

    @Test
    fun guessingIsStoppedAfterFiveWrongCodes() {
        val code = pairing.start(phone)
        val wrong = if (code.code == "000000") "111111" else "000000"
        repeat(HostPairingCode.MAX_WRONG) { pairing.verify("phone-1", wrong) }
        assertNull(pairing.current)
        assertIs<HostPairingCode.Result.Rejected>(pairing.verify("phone-1", code.code), "the right code no longer works either")
    }

    @Test
    fun codesAreSixDigits() {
        repeat(50) { assertTrue(Regex("\\d{6}").matches(pairing.start(phone).code)) }
    }

    @Test
    fun onlyThePairedAddressIsTrusted() {
        assertTrue(ConnectAccess.isFromPairedPeer("192.168.1.20", "192.168.1.20"))
        assertTrue(ConnectAccess.isFromPairedPeer("/192.168.1.20", "192.168.1.20"))
        assertTrue(ConnectAccess.isFromPairedPeer("::ffff:192.168.1.20", "192.168.1.20"))
        assertFalse(ConnectAccess.isFromPairedPeer("192.168.1.21", "192.168.1.20"))
        assertFalse(ConnectAccess.isFromPairedPeer("192.168.1.20", null))
        assertFalse(ConnectAccess.isFromPairedPeer(null, "192.168.1.20"))
    }
}
