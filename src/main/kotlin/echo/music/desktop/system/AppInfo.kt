package echo.music.desktop.system

/** Version and launcher of the running app. */
object AppInfo {
    /** Set by the jpackage launcher of the installed app; under Gradle it comes from the generated resource. */
    val version: String by lazy {
        System.getProperty("jpackage.app-version")
            ?: AppInfo::class.java.getResource("/echo-version.txt")?.readText()?.trim()
            ?: "dev"
    }

    /** Path of EchoMusic.exe when running the installed app; null when started from Gradle. */
    val launcherPath: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
}
