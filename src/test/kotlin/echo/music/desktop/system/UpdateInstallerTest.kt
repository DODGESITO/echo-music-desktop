package echo.music.desktop.system

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateInstallerTest {
    private lateinit var server: HttpServer
    private val payload = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    @BeforeTest
    fun start() {
        UpdateInstaller.baseDir = Files.createTempDirectory("echo-update-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/EchoMusic-9.9.9.msi") { ex ->
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        UpdateInstaller.baseDir = null
    }

    private fun url() = "http://127.0.0.1:${server.address.port}/EchoMusic-9.9.9.msi"

    @Test
    fun downloadKeepsOnlyAMatchingFile() {
        var lastProgress = 0f
        val ok = UpdateInstaller.download("9.9.9", UpdatePackage("EchoMusic-9.9.9.msi", url(), payload.size.toLong(), sha)) { lastProgress = it }
        assertNotNull(ok)
        assertEquals(1f, lastProgress)
        assertTrue(ok.readBytes().contentEquals(payload))
        assertEquals("9.9.9", UpdateInstaller.pendingVersion())
        assertNotNull(UpdateInstaller.pendingInstaller(), "newer than the running version and still intact")

        // Tampered or corrupted download: rejected, nothing left to install.
        UpdateInstaller.clear()
        val bad = UpdateInstaller.download("9.9.9", UpdatePackage("EchoMusic-9.9.9.msi", url(), payload.size.toLong(), "0".repeat(64))) {}
        assertNull(bad)
        assertNull(UpdateInstaller.pendingInstaller())
    }

    @Test
    fun aTriedInstallIsNeverRetried() {
        assertNotNull(UpdateInstaller.download("9.9.9", UpdatePackage("EchoMusic-9.9.9.msi", url(), payload.size.toLong(), sha)) {})
        // What the helper leaves behind after msiexec failed (the app reports it once and starts normally).
        java.io.File(UpdateInstaller.baseDir, "result.json").writeText("1603")
        assertNull(UpdateInstaller.pendingInstaller())
        assertEquals("9.9.9" to 1603, UpdateInstaller.takeLastOutcome())
        assertNull(UpdateInstaller.pendingInstaller())
    }

    @Test
    fun pendingFileThatChangedIsDiscarded() {
        val file = UpdateInstaller.download("9.9.9", UpdatePackage("EchoMusic-9.9.9.msi", url(), payload.size.toLong(), sha)) {}
        assertNotNull(file)
        file.appendBytes(byteArrayOf(1))
        assertNull(UpdateInstaller.pendingInstaller())
    }

    @Test
    fun installerChecksumFromDigestOrShaFile() {
        val withDigest = UpdateChecker.Release(
            "v1.3.0", "https://example/release", assets = listOf(
                UpdateChecker.Asset("EchoMusic-1.3.0.msi", "https://example/a.msi", 10, "sha256:" + "AB".repeat(32)),
                UpdateChecker.Asset("EchoMusic-1.3.0.exe", "https://example/a.exe", 10)
            )
        )
        assertEquals("ab".repeat(32), UpdateChecker.installerOf(withDigest) { null }?.sha256)

        val withShaFile = UpdateChecker.Release(
            "v1.3.0", "https://example/release", assets = listOf(
                UpdateChecker.Asset("EchoMusic-1.3.0.msi", "https://example/a.msi", 10),
                UpdateChecker.Asset("EchoMusic-1.3.0.msi.sha256", "https://example/a.sha", 70)
            )
        )
        assertEquals("cd".repeat(32), UpdateChecker.installerOf(withShaFile) { "${"CD".repeat(32)}  EchoMusic-1.3.0.msi" }?.sha256)

        val noChecksum = UpdateChecker.Release("v1.3.0", "x", assets = listOf(UpdateChecker.Asset("EchoMusic-1.3.0.msi", "u", 1)))
        assertNull(UpdateChecker.installerOf(noChecksum) { null }, "never install what cannot be verified")
    }
}
