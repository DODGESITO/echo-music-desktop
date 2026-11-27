package echo.music.desktop.system

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * Silent updates for the per-user install (%LOCALAPPDATA%\EchoMusic, no administrator prompt):
 * download the release's .msi, check its SHA-256, and once Echo Music has closed run
 * `msiexec /qn` and open the new version.
 */
object UpdateInstaller {
    @Serializable
    private data class Pending(val version: String, val file: String, val sha256: String, val attempts: Int = 0)

    private val json = Json { ignoreUnknownKeys = true }
    /** Tests point this somewhere else. */
    internal var baseDir: File? = null
    private val dir: File
        get() = baseDir ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "EchoMusic-updates")
    private val pendingFile get() = File(dir, "pending.json")
    private val outcomeFile get() = File(dir, "result.json")

    /** Only the installed per-user app can update itself without asking for permissions. */
    val canAutoInstall: Boolean
        get() {
            if (!Platform.isWindows()) return false
            val exe = AppInfo.launcherPath ?: return false
            val local = System.getenv("LOCALAPPDATA") ?: return false
            return exe.startsWith(local, ignoreCase = true)
        }

    // ------------------------------------------------------------------ download

    /** Downloads and verifies [pkg]; returns the file, or null if it does not match its checksum. */
    fun download(version: String, pkg: UpdatePackage, onProgress: (Float) -> Unit): File? {
        dir.mkdirs()
        val target = File(dir, "EchoMusic-$version.msi")
        val part = File(dir, "EchoMusic-$version.msi.part")
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI(pkg.url))
            .header("User-Agent", "EchoMusicDesktop")
            .header("Accept", "application/octet-stream")
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) return null
        val total = pkg.size.takeIf { it > 0 } ?: response.headers().firstValueAsLong("Content-Length").orElse(-1)
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        response.body().use { input ->
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(pkg.sha256, ignoreCase = true)) {
            part.delete()
            return null
        }
        target.delete()
        if (!part.renameTo(target)) return null
        pendingFile.writeText(json.encodeToString(Pending.serializer(), Pending(version, target.absolutePath, sha)))
        return target
    }

    /**
     * A verified installer waiting to be installed (newer than the running version). Never one that
     * was already tried: a failed install is reported once instead of being retried on every start.
     */
    fun pendingInstaller(): File? = runCatching {
        if (!pendingFile.exists() || outcomeFile.exists()) return null
        val p = json.decodeFromString(Pending.serializer(), pendingFile.readText())
        val file = File(p.file)
        if (p.attempts > 0 || !UpdateChecker.isNewer(p.version, AppInfo.version) || !file.exists() || sha256(file) != p.sha256) {
            clear()
            return null
        }
        file
    }.getOrNull()

    private fun markAttempt() = runCatching {
        val p = json.decodeFromString(Pending.serializer(), pendingFile.readText())
        pendingFile.writeText(json.encodeToString(Pending.serializer(), p.copy(attempts = p.attempts + 1)))
    }

    fun pendingVersion(): String? = runCatching {
        json.decodeFromString(Pending.serializer(), pendingFile.readText()).version
    }.getOrNull()

    /** Result of the last silent install ("Actualizado a 1.2.0" / error), reported once. */
    fun takeLastOutcome(): Pair<String, Int>? = runCatching {
        if (!outcomeFile.exists()) return null
        val text = outcomeFile.readText().trim()
        outcomeFile.delete()
        val version = pendingVersion()
        clear()
        // 3010: installed, Windows asks for a restart to finish (some file was in use).
        (version ?: AppInfo.version) to (text.toIntOrNull() ?: -1)
    }.getOrNull()

    fun clear() {
        dir.listFiles()?.filter { it.name != "install.log" }?.forEach { it.delete() }
    }

    // ------------------------------------------------------------------ install

    /**
     * Starts a hidden helper that waits until Echo Music has closed, installs [msi] silently and
     * opens Echo Music again. Call it right before exiting.
     */
    fun installAfterExit(msi: File, reopenMinimized: Boolean = false): Boolean {
        val exe = AppInfo.launcherPath ?: return false
        dir.mkdirs()
        outcomeFile.delete()
        markAttempt()
        val self = ProcessHandle.current()
        // The installed launcher runs the app as a child process: wait for both before replacing files.
        val pids = listOfNotNull(self.pid(), self.parent().orElse(null)
            ?.takeIf { p -> p.info().command().orElse("").endsWith("EchoMusic.exe", ignoreCase = true) }
            ?.pid())
        SingleInstance.release()
        val script = File(dir, "install-update.ps1")
        script.writeText(
            buildString {
                appendLine("\$ErrorActionPreference = 'Continue'")
                appendLine("foreach (\$id in @(${pids.joinToString(",")})) { try { Wait-Process -Id \$id -Timeout 120 -ErrorAction Stop } catch {} }")
                // Any other copy of Echo Music still open (e.g. hidden in the tray) keeps the files in
                // use and Windows could only replace them after a reboot: close it first.
                appendLine("\$exe = ${ps(exe)}")
                appendLine("Get-Process -Name EchoMusic -ErrorAction SilentlyContinue | Where-Object { \$_.Path -eq \$exe } | Stop-Process -Force -ErrorAction SilentlyContinue")
                appendLine("Start-Sleep -Seconds 2")
                appendLine("\$log = ${ps(File(dir, "install.log").absolutePath)}")
                // 1618: another installation is running; wait for it and try again.
                appendLine("\$code = 1618")
                appendLine("for (\$i = 0; \$i -lt 8 -and \$code -eq 1618; \$i++) {")
                appendLine("  if (\$i -gt 0) { Start-Sleep -Seconds 15 }")
                appendLine("  \$p = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', '\"' + ${ps(msi.absolutePath)} + '\"', '/qn', '/norestart', '/l*v', '\"' + \$log + '\"') -Wait -PassThru")
                appendLine("  \$code = \$p.ExitCode")
                appendLine("}")
                appendLine("Set-Content -Path ${ps(outcomeFile.absolutePath)} -Value \$code")
                val reopenArgs = if (reopenMinimized) " -ArgumentList '${WindowsStartup.MINIMIZED_ARG}'" else ""
                appendLine("if (Test-Path ${ps(exe)}) { Start-Process -FilePath ${ps(exe)}$reopenArgs }")
            },
            Charsets.UTF_8
        )
        val command = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File \"${script.absolutePath}\""
        return startDetached(command)
    }

    /** PowerShell single-quoted string. */
    private fun ps(value: String) = "'" + value.replace("'", "''") + "'"

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------------ detached process

    @Suppress("FunctionName")
    private interface JobApi : StdCallLibrary {
        fun IsProcessInJob(process: com.sun.jna.platform.win32.WinNT.HANDLE?, job: com.sun.jna.platform.win32.WinNT.HANDLE?, result: IntByReference): Boolean
    }

    private const val CREATE_NO_WINDOW = 0x08000000
    private const val CREATE_BREAKAWAY_FROM_JOB = 0x01000000

    /**
     * Starts [command] without a window and outside the launcher's job object, so it survives
     * when Echo Music exits (a process in the same job would be closed together with the app).
     */
    private fun startDetached(command: String): Boolean {
        val inJob = runCatching {
            val result = IntByReference()
            Native.load("kernel32", JobApi::class.java).IsProcessInJob(Kernel32.INSTANCE.GetCurrentProcess(), null, result)
            result.value != 0
        }.getOrDefault(false)

        if (!inJob) {
            return runCatching { ProcessBuilder("cmd.exe", "/c", command).start(); true }.getOrDefault(false)
        }
        if (createProcess(command, CREATE_NO_WINDOW or CREATE_BREAKAWAY_FROM_JOB)) return true
        // The job does not allow breaking away: let Windows (WMI) start it, outside any job of ours.
        val wmi = "\$si = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{ ShowWindow = [uint16]0 }; " +
            "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = ${ps(command)}; ProcessStartupInformation = \$si } | Out-Null"
        return runCatching {
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", wmi).start().waitFor() == 0
        }.getOrDefault(false)
    }

    private fun createProcess(command: String, flags: Int): Boolean = runCatching {
        val startup = WinBase.STARTUPINFO()
        val info = WinBase.PROCESS_INFORMATION()
        val api = Native.load("kernel32", CreateProcessApi::class.java, W32APIOptions.UNICODE_OPTIONS)
        val ok = api.CreateProcess(null, command, null, null, false, WinDef.DWORD(flags.toLong()), null, null, startup, info)
        if (ok) {
            Kernel32.INSTANCE.CloseHandle(info.hProcess)
            Kernel32.INSTANCE.CloseHandle(info.hThread)
        }
        ok
    }.getOrDefault(false)

    @Suppress("FunctionName")
    private interface CreateProcessApi : StdCallLibrary {
        fun CreateProcess(
            applicationName: String?, commandLine: String, processAttributes: Pointer?, threadAttributes: Pointer?,
            inheritHandles: Boolean, creationFlags: WinDef.DWORD, environment: Pointer?, currentDirectory: String?,
            startupInfo: WinBase.STARTUPINFO, processInformation: WinBase.PROCESS_INFORMATION
        ): Boolean
    }
}
