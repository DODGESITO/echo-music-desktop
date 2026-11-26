package echo.music.desktop.canvas

import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasTest {
    @Test
    fun artistMatchRule() {
        assertTrue(CanvasService.artistMatches("Bad Bunny, Bomba Estéreo", "Bad Bunny"))
        assertTrue(CanvasService.artistMatches("Bad Bunny", "Bad Bunny & Bomba Estéreo"))
        assertFalse(CanvasService.artistMatches("Juanes", "Bad Bunny"))
        assertTrue(CanvasService.artistMatches("Juanes", null))
    }

    /** Manual network check (-DlyricsProbe=true): lookup → download → remux → JavaFX frames advance. */
    @Test
    fun liveCanvasProbe() {
        if (System.getProperty("lyricsProbe") != "true") return
        System.setProperty("user.home", Files.createTempDirectory("echo-canvas").toString())
        try { Platform.startup {} } catch (_: IllegalStateException) {}
        for ((title, artist, album) in listOf(
            Triple("Ojitos Lindos", "Bad Bunny, Bomba Estéreo", "Un Verano Sin Ti"),
            Triple("Flowers", "Miley Cyrus", "Endless Summer Vacation"),
            Triple("La Camisa Negra", "Juanes", "Mi Sangre")
        )) {
            val t0 = System.currentTimeMillis()
            val file = runBlocking { CanvasService.canvasFor("id-$title", title, artist, album) }
            println("PROBE '$title' canvas=${file?.length()?.div(1024)}KB in ${System.currentTimeMillis() - t0}ms")
            if (file == null) continue
            var status = "timeout"
            var pos = 0.0
            for (attempt in 1..3) {
                val ready = CountDownLatch(1)
                var player: MediaPlayer? = null
                var scene: Scene? = null
                Platform.runLater {
                    val p = MediaPlayer(Media(file.toURI().toString())).apply { isMute = true }
                    scene = Scene(Group(MediaView(p).apply { fitWidth = 240.0; fitHeight = 240.0 }), 240.0, 240.0)
                    player = p
                    p.setOnReady { status = "ready (try $attempt) ${p.media.width}x${p.media.height}"; p.play(); ready.countDown() }
                    p.setOnError { status = "ERROR (try $attempt) ${p.error?.type}"; ready.countDown() }
                }
                ready.await(20, TimeUnit.SECONDS)
                if (status.startsWith("ready")) {
                    Thread.sleep(1200)
                    val t = CountDownLatch(1)
                    Platform.runLater { pos = player?.currentTime?.toSeconds() ?: 0.0; player?.dispose(); t.countDown() }
                    t.await(5, TimeUnit.SECONDS)
                    break
                }
                Platform.runLater { player?.dispose() }
                Thread.sleep(300)
            }
            println("PROBE   javafx $status, advanced to ${"%.2f".format(pos)}s")
        }
    }
}
