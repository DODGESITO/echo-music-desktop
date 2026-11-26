package echo.music.desktop.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import echo.music.desktop.player.JavaFxPlayerEngine
import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Plays a canvas video muted and looping with JavaFX *off screen* and draws its frames as a
 * normal Compose image. Unlike embedding a Swing/JavaFX panel, this keeps menus and dialogs on
 * top of the video and allows rounded corners. Fades in once the first frame is ready.
 */
@Composable
fun CanvasVideoView(file: File, modifier: Modifier = Modifier, side: Int = 480) {
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    val holder = remember(file) { CanvasPlayback(file, side) }

    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.dispose() }
    }
    LaunchedEffect(holder) {
        while (isActive) {
            delay(41) // ~24 fps, the canvas frame rate
            holder.latest.getAndSet(null)?.let { frame = it }
        }
    }

    val alpha by animateFloatAsState(if (frame != null) 1f else 0f, tween(400))
    frame?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.alpha(alpha))
    }
}

/** JavaFX side: off-screen MediaView whose frames are copied (BGRA) into Skia bitmaps. */
private class CanvasPlayback(private val file: File, private val side: Int) {
    private companion object { const val MAX_ATTEMPTS = 4 }

    val latest = AtomicReference<ImageBitmap?>(null)
    @Volatile private var disposed = false
    private var player: MediaPlayer? = null
    private var scene: Scene? = null
    private var image: WritableImage? = null
    private var pump: Thread? = null
    // Three reused native bitmaps (triple buffering) instead of a new one per frame.
    private val pool = Array(3) {
        Bitmap().apply { allocPixels(ImageInfo(side, side, ColorType.BGRA_8888, ColorAlphaType.PREMUL)) }
    }
    private var next = 0

    /** Platform.runLater that never throws into Compose (e.g. if JavaFX could not start). */
    private fun fx(action: () -> Unit) {
        try {
            Platform.runLater(action)
        } catch (_: IllegalStateException) {
            disposed = true
        }
    }

    fun start() {
        JavaFxPlayerEngine.ensureFxInitialized()
        fx { open(attempt = 1) }
        // Ask the FX thread for a snapshot ~24 times per second.
        pump = Thread({
            val bytes = ByteArray(side * side * 4)
            while (!disposed) {
                Thread.sleep(41)
                fx { capture(bytes) }
            }
        }, "canvas-frames").apply { isDaemon = true; start() }
    }

    /**
     * Opens the video on the FX thread. JavaFX's GStreamer sometimes rejects a valid file at open
     * time (ERROR_MEDIA_INVALID) and accepts it on the next try, so a few retries are made; if all
     * fail the static cover simply stays.
     */
    private fun open(attempt: Int) {
        if (disposed) return
        try {
            val p = MediaPlayer(Media(file.toURI().toString())).apply {
                isMute = true
                cycleCount = MediaPlayer.INDEFINITE
            }
            val view = MediaView(p).apply {
                fitWidth = side.toDouble()
                fitHeight = side.toDouble()
                isPreserveRatio = false
            }
            scene = Scene(Group(view), side.toDouble(), side.toDouble())
            if (image == null) image = WritableImage(side, side)
            player = p
            p.setOnReady { if (!disposed) p.play() }
            p.setOnError {
                p.dispose()
                if (player === p) player = null
                if (attempt < MAX_ATTEMPTS && !disposed) {
                    Thread({
                        Thread.sleep(300)
                        fx { open(attempt + 1) }
                    }, "canvas-retry").apply { isDaemon = true; start() }
                } else {
                    dispose()
                }
            }
        } catch (_: Throwable) {
            dispose()
        }
    }

    private fun capture(bytes: ByteArray) {
        val p = player ?: return
        val s = scene ?: return
        if (disposed || p.status != MediaPlayer.Status.PLAYING) return
        val img = s.snapshot(image)
        img.pixelReader.getPixels(0, 0, side, side, PixelFormat.getByteBgraPreInstance(), bytes, 0, side * 4)
        val bitmap = pool[next]
        next = (next + 1) % pool.size
        bitmap.installPixels(bytes)
        latest.set(bitmap.asComposeImageBitmap())
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        fx {
            player?.stop()
            player?.dispose()
            player = null
            scene = null
            pool.forEach { it.close() }
        }
    }
}
