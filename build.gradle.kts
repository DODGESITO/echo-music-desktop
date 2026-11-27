plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.google.protobuf") version "0.9.6"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Base64 is stable from Kotlin 2.2; shared modules (KuGou) already use it.
        optIn.add("kotlin.io.encoding.ExperimentalEncodingApi")
    }
}

// Code reused from Echo Music (the Android app) lives in shared/ — see README.md:
//  * shared/<module>: the app's pure-Kotlin modules (YouTube Music, lyrics providers, Shazam, Canvas)
//  * shared/echo-app: single files from the app (AI translation, Last.fm, ListenBrainz, Shazam signature)
//  * shared/echo-app-romanization/LyricsUtils.kt: romanizers, filtered below

// Lyrics romanization (Japanese, Korean, Chinese, Cyrillic, Hindi, Punjabi) from the app's
// LyricsUtils.kt. That file also holds an Android/accompanist lyrics parser the desktop does not
// need (it has its own), so the copy drops that block and those imports. Fails loudly if the
// file changes shape after an update, instead of silently compiling something different.
val sharedRomanization = tasks.register("syncSharedRomanization") {
    val source = file("shared/echo-app-romanization/LyricsUtils.kt")
    val target = layout.buildDirectory.file("generated/sharedRomanization/echo/music/iad1tya/lyrics/LyricsUtils.kt")
    inputs.file(source)
    outputs.file(target)
    doLast {
        val text = source.readText()
        val start = text.indexOf("  private val HEX_ENTITY_REGEX")
        val end = text.indexOf("  fun katakanaToRomaji")
        check(start in 0 until end) { "LyricsUtils.kt changed: parser block markers not found" }
        val dropImports = listOf("import android.", "import com.mocharealm.")
        val result = (text.substring(0, start) + text.substring(end))
            .lines()
            .filterNot { line -> dropImports.any { line.startsWith(it) } }
            .joinToString("\n")
        check("AutoParser" !in result && "DateUtils" !in result) { "LyricsUtils.kt changed: parser code still present" }
        target.get().asFile.apply { parentFile.mkdirs() }.writeText(result)
    }
}

/** App version: used by the installers and shown/compared by the update checker. */
// -PappVersion=x.y.z builds another version (used to test updates).
val appVersion = (findProperty("appVersion") as String?) ?: "1.3.3"

val versionResource = tasks.register("generateVersionResource") {
    val target = layout.buildDirectory.file("generated/versionResource/echo-version.txt")
    inputs.property("version", appVersion)
    outputs.file(target)
    doLast { target.get().asFile.apply { parentFile.mkdirs() }.writeText(appVersion) }
}

sourceSets {
    main {
        resources.srcDir(versionResource.map { layout.buildDirectory.dir("generated/versionResource").get() })
        kotlin {
            srcDir(sharedRomanization.map { layout.buildDirectory.dir("generated/sharedRomanization").get() })
            srcDirs(
                "src/main/kotlin",
                // Minimal desktop stand-ins for android.content.Context, android.util.Base64 and Timber
                "src/shims/kotlin",
                // YouTube Music client and metadata cleaning
                "shared/innertube",
                "shared/metadata",
                // Lyrics providers
                "shared/lrclib",
                "shared/kugou",
                "shared/betterlyrics",
                "shared/simpmusic",
                "shared/youlyplus",
                "shared/paxsenixlyrics",
                // Echo Find (Shazam) and Canvas (Apple Music / Tidal animated covers)
                "shared/shazamkit",
                "shared/canvas",
                "shared/applecanvas",
                // Spotify client (library, Canvas, matching songs on YouTube Music)
                "shared/spotify",
                // Single files from the app: translation, Last.fm, ListenBrainz, Shazam signature
                "shared/echo-app"
            )
        }
    }
}

val osName = System.getProperty("os.name").lowercase()
val jfxClassifier = when {
    osName.contains("win") -> "win"
    osName.contains("mac") -> if (System.getProperty("os.arch").contains("aarch64")) "mac-aarch64" else "mac"
    else -> "linux"
}

configurations.all {
    resolutionStrategy {
        force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
    }
    exclude(group = "com.grack", module = "nanojson")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor Client (for InnerTube, LRCLIB & Network)
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")

    // YouTube Extractors & HTML parsing
    implementation("org.brotli:dec:0.1.2")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
    implementation("com.github.maxrave-dev:PipePipeExtractor:f8982ca9e7")
    implementation("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
    implementation("org.jsoup:jsoup:1.18.3")

    // JavaFX Media (GStreamer-lite embedded) for Out-of-the-box Windows/Mac/Linux Audio
    implementation("org.openjfx:javafx-base:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-graphics:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-media:21.0.2:$jfxClassifier")

    // Image loading (Coil 3 for Compose Desktop)
    val coilVersion = "3.1.0"
    implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
    implementation("io.coil-kt.coil3:coil-network-okhttp:$coilVersion")

    // org.json is built into Android; the shared AI translation services use it
    implementation("org.json:json:20240303")

    // Lyrics romanization (same libraries as the phone app): Japanese and Chinese
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    implementation("com.github.promeG:tinypinyin:2.0.3")

    // Local music: read tags (title, artist, album, cover) from MP3/M4A/WAV/AIFF files
    implementation("net.jthink:jaudiotagger:3.0.1")

    // JNA: global media keys (Windows low-level keyboard hook)
    // @Inject/@Singleton annotations in the shared Spotify client
    implementation("javax.inject:javax.inject:1")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    // OkHttp (for Listen Together WebSocket & Network)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Protobuf
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("com.google.protobuf:protobuf-kotlin-lite:4.35.0")

    testImplementation(kotlin("test"))
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.35.0" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") { option("lite") }
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "echo.music.desktop.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Dprism.order=sw",
            "-Dprism.enabled=false",
            "-Dquantum.multithreaded=false"
        )
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "EchoMusic"
            // From suggestRuntimeModules, plus what is only reached by reflection/locale lookups:
            // TLS elliptic curves, Spanish dates, extra charsets, DNS (okhttp), SQL types (jaudiotagger).
            modules(
                "java.instrument", "java.management", "java.net.http", "jdk.dynalink", "jdk.httpserver",
                "jdk.jfr", "jdk.unsupported", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets",
                "java.naming", "java.sql", "java.logging", "jdk.zipfs", "java.prefs"
            )
            packageVersion = appVersion
            description = "Echo Music Desktop Player - Nothing OS 5.0"
            vendor = "Echo Music"
            copyright = "© 2026 Echo Music"

            windows {
                menu = true
                shortcut = true
                // Per-user install (%LOCALAPPDATA%): no administrator prompt, so updates install silently.
                dirChooser = false
                perUserInstall = true
                menuGroup = "Echo Music"
                upgradeUuid = "a4c28f32-8419-4b2a-bf34-7fa454f73891"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}

tasks.test {
    // Forward -DlyricsProbe=true to the manual lyrics network probe.
    System.getProperty("lyricsProbe")?.let { systemProperty("lyricsProbe", it) }
    System.getProperty("canvasWindow")?.let { systemProperty("canvasWindow", it) }
    testLogging { showStandardStreams = true }
}
