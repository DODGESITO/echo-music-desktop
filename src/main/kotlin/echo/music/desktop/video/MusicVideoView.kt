package echo.music.desktop.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
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
import javafx.util.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Where the audio is: the video follows it. */
data class VideoClock(val positionMs: Long, val isPlaying: Boolean, val rate: Double, val sampledAt: Long = System.currentTimeMillis()) {
    fun now(): Long = if (isPlaying) positionMs + ((System.currentTimeMillis() - sampledAt) * rate).toLong() else positionMs
}

/**
 * The music video, muted (the sound comes from the audio player, playing the same file), drawn as
 * a Compose image from an off-screen JavaFX player, like the Canvas covers.
 */
@Composable
fun MusicVideoView(file: File, clock: VideoClock, modifier: Modifier = Modifier) {
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    val holder = remember(file) { VideoPlayback(file) }
    val latestClock = rememberUpdatedState(clock)

    DisposableEffect(holder) {
        holder.start { latestClock.value }
        onDispose { holder.dispose() }
    }
    LaunchedEffect(holder) {
        while (isActive) {
            delay(33) // ~30 fps
            holder.latest.getAndSet(null)?.let { frame = it }
        }
    }
    val alpha by animateFloatAsState(if (frame != null) 1f else 0f, tween(300))
    frame?.let { Image(bitmap = it, contentDescription = "Video", contentScale = ContentScale.Fit, modifier = modifier.alpha(alpha)) }
}

internal class VideoPlayback(private val file: File) {
    val latest = AtomicReference<ImageBitmap?>(null)
    /** Where the picture is (ms), for diagnostics. */
    @Volatile var videoPositionMs = 0L
        private set
    @Volatile private var disposed = false
    private var player: MediaPlayer? = null
    private var scene: Scene? = null
    private var image: WritableImage? = null
    private var width = 640
    private var height = 360
    private var pool: Array<Bitmap>? = null
    private var next = 0
    private var lastSync = 0L

    private fun fx(action: () -> Unit) {
        try {
            Platform.runLater(action)
        } catch (_: IllegalStateException) {
            disposed = true
        }
    }

    fun start(clock: () -> VideoClock) {
        JavaFxPlayerEngine.ensureFxInitialized()
        fx { open(attempt = 1, clock = clock) }
        Thread({
            var bytes = ByteArray(0)
            while (!disposed) {
                Thread.sleep(33)
                fx {
                    val size = width * height * 4
                    if (bytes.size != size) bytes = ByteArray(size)
                    tick(bytes, clock())
                }
            }
        }, "music-video-frames").apply { isDaemon = true; start() }
    }

    private fun open(attempt: Int, clock: () -> VideoClock) {
        if (disposed) return
        try {
            val p = MediaPlayer(Media(file.toURI().toString())).apply { isMute = true }
            player = p
            p.setOnReady {
                if (disposed) return@setOnReady
                width = p.media.width.takeIf { it > 0 } ?: 640
                height = p.media.height.takeIf { it > 0 } ?: 360
                val view = MediaView(p).apply {
                    fitWidth = width.toDouble()
                    fitHeight = height.toDouble()
                    isPreserveRatio = false
                }
                scene = Scene(Group(view), width.toDouble(), height.toDouble())
                image = WritableImage(width, height)
                // (inside apply{} "width" would be the empty bitmap's own width)
                val w = width
                val h = height
                pool = Array(3) { Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)) } }
                val c = clock()
                p.seek(Duration.millis(c.now().toDouble()))
                if (c.isPlaying) p.play() else p.pause()
            }
            p.setOnError {
                p.dispose()
                if (player === p) player = null
                if (attempt < 4 && !disposed) {
                    Thread({ Thread.sleep(300); fx { open(attempt + 1, clock) } }, "music-video-retry").apply { isDaemon = true; start() }
                }
            }
        } catch (_: Throwable) {
            dispose()
        }
    }

    /** Keeps the picture on the audio's time, then copies the current frame. */
    private fun tick(bytes: ByteArray, clock: VideoClock) {
        val p = player ?: return
        val s = scene ?: return
        if (disposed) return
        val target = clock.now()
        videoPositionMs = p.currentTime.toMillis().toLong()
        if (clock.isPlaying && p.status != MediaPlayer.Status.PLAYING) p.play()
        if (!clock.isPlaying && p.status == MediaPlayer.Status.PLAYING) p.pause()
        if (p.rate != clock.rate) p.rate = clock.rate
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSync > 400) {
            lastSync = nowMs
            val drift = p.currentTime.toMillis() - target
            if (kotlin.math.abs(drift) > 300) p.seek(Duration.millis(target.toDouble()))
        }
        if (p.status != MediaPlayer.Status.PLAYING && latest.get() != null) return
        val img = s.snapshot(image)
        img.pixelReader.getPixels(0, 0, width, height, PixelFormat.getByteBgraPreInstance(), bytes, 0, width * 4)
        val bitmaps = pool ?: return
        val bitmap = bitmaps[next]
        next = (next + 1) % bitmaps.size
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
            pool?.forEach { it.close() }
        }
    }
}
