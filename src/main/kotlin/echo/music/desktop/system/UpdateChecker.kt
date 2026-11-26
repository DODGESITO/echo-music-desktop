package echo.music.desktop.system

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The installer of a release, with the SHA-256 it must match before it is run. */
data class UpdatePackage(val name: String, val url: String, val size: Long, val sha256: String)

data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String,
    /** Null when the release has no .msi with a known checksum: then it can only be installed by hand. */
    val installer: UpdatePackage? = null
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** Looks for a newer release on GitHub (the latest non-draft, non-prerelease one). */
object UpdateChecker {
    const val DEFAULT_REPO = "DODGESITO/echo-music-desktop"

    @Serializable
    internal data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
        /** "sha256:<hex>", filled in by GitHub for uploaded files. */
        val digest: String? = null
    )

    @Serializable
    internal data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val assets: List<Asset> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build() }

    fun check(repo: String, currentVersion: String = AppInfo.version): UpdateResult = try {
        val response = client.send(request("https://api.github.com/repos/${repo.trim()}/releases/latest"), HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            200 -> {
                val release = json.decodeFromString(Release.serializer(), response.body())
                val remote = release.tagName.trim().removePrefix("v").removePrefix("V")
                if (isNewer(remote, currentVersion)) {
                    UpdateResult.Available(UpdateInfo(remote, release.htmlUrl, release.body.orEmpty().trim(), installerOf(release)))
                } else UpdateResult.UpToDate
            }
            404 -> UpdateResult.Failed("No hay versiones publicadas en $repo (o el repositorio es privado).")
            403, 429 -> UpdateResult.Failed("GitHub limitó las consultas; prueba más tarde.")
            else -> UpdateResult.Failed("GitHub respondió ${response.statusCode()}.")
        }
    } catch (e: Exception) {
        UpdateResult.Failed("No se pudo conectar con GitHub.")
    }

    /** The .msi of the release and its checksum (GitHub's digest, or a "<name>.sha256" file next to it). */
    internal fun installerOf(release: Release, fetchText: (String) -> String? = ::fetchText): UpdatePackage? {
        val msi = release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) } ?: return null
        val fromDigest = msi.digest?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':')
        val sha = fromDigest ?: release.assets
            .firstOrNull { it.name.equals(msi.name + ".sha256", ignoreCase = true) }
            ?.let { fetchText(it.downloadUrl) }
            ?.let { Regex("""\b[0-9a-fA-F]{64}\b""").find(it)?.value }
        return sha?.let { UpdatePackage(msi.name, msi.downloadUrl, msi.size, it.lowercase()) }
    }

    private fun fetchText(url: String): String? = runCatching {
        client.send(request(url), HttpResponse.BodyHandlers.ofString()).takeIf { it.statusCode() == 200 }?.body()
    }.getOrNull()

    private fun request(url: String) = HttpRequest.newBuilder(URI(url))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "EchoMusicDesktop")
        .timeout(Duration.ofSeconds(15))
        .GET().build()

    /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); anything after '-' or '+' is ignored. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').substringBefore('+').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        if (current == "dev") return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
