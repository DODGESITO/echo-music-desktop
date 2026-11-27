package echo.music.desktop.system

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateInstallerScriptTest {
    private val dir = Files.createTempDirectory("echo-update-test").toFile()

    init {
        UpdateInstaller.baseDir = dir
    }

    @AfterTest
    fun cleanUp() {
        UpdateInstaller.baseDir = null
        dir.deleteRecursively()
    }

    /**
     * Runs the real helper script against an installer that does not exist: msiexec must get as far
     * as opening it (1619) and write its log. 1622 means the command line is broken again.
     */
    @Test
    fun scriptRunsMsiexecWithAWorkingCommandLine() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        // Spaces and an apostrophe, like a user folder can have.
        val folder = File(dir, "Mi carpeta d'Echo").apply { mkdirs() }
        val script = File(folder, "install-update.ps1")
        script.writeText(
            UpdateInstaller.buildScript(
                msi = File(folder, "EchoMusic-9.9.9.msi"),
                exe = File(folder, "EchoMusic.exe").absolutePath, // does not exist: nothing is closed or opened
                pids = emptyList(),
                reopenMinimized = false
            )
        )
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "script did not finish: $output")

        assertEquals("1619", File(dir, "result.json").readText().trim(), "msiexec exit code (script output: $output)")
        assertTrue(File(dir, "install.log").exists(), "msiexec did not write its log")
        assertTrue(File(dir, "history.log").readText().contains("msiexec 1619"))
    }

    @Test
    fun failedInstallIsRememberedAndNotRetriedAutomatically() {
        File(dir, "pending.json").writeText("""{"version":"99.0.0","file":"x.msi","sha256":"00","attempts":1}""")
        File(dir, "result.json").writeText("1622")

        assertEquals("99.0.0" to 1622, UpdateInstaller.takeLastOutcome())
        val failure = UpdateInstaller.lastFailure()
        assertEquals("99.0.0", failure?.version)
        assertEquals(1622, failure?.code)
        // The record survives the cleanup that removes the downloaded installer.
        assertTrue(File(dir, "last-failure.json").exists())
        assertNull(UpdateInstaller.pendingVersion())
    }

    @Test
    fun successfulInstallForgetsTheFailure() {
        File(dir, "last-failure.json").writeText("""{"version":"99.0.0","code":1622,"at":0}""")
        File(dir, "pending.json").writeText("""{"version":"99.0.0","file":"x.msi","sha256":"00","attempts":1}""")
        File(dir, "result.json").writeText("0")

        UpdateInstaller.takeLastOutcome()
        assertNull(UpdateInstaller.lastFailure())
    }

    @Test
    fun failureOfAnOlderVersionIsDropped() {
        File(dir, "last-failure.json").writeText("""{"version":"0.0.1","code":1622,"at":0}""")
        assertNull(UpdateInstaller.lastFailure())
    }
}
