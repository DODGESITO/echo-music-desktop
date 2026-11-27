package echo.music.desktop.video

import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import kotlin.test.Test

/**
 * Manual probe (-DlyricsProbe=true, after VideoPlaybackProbe filled the cache): the off-screen
 * video gives real frames and follows a clock that jumps, pauses and resumes.
 */
class MusicVideoViewProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        val file = File(System.getProperty("java.io.tmpdir"), "echomusic-video").listFiles { f -> f.extension == "mp4" }
            ?.maxByOrNull { it.lastModified() } ?: return println("PROBE no cached video")
        var clock = VideoClock(positionMs = 30_000, isPlaying = true, rate = 1.0)
        val playback = VideoPlayback(file)
        playback.start { clock }
        Thread.sleep(3000)
        val frame = playback.latest.get()
        if (frame == null) {
            println("PROBE no frame")
        } else {
            val bmp = frame.asSkiaBitmap()
            println("PROBE bitmap ${bmp.width}x${bmp.height} null=${bmp.isNull} closed=${bmp.isClosed} info=${bmp.imageInfo}")
            var lit = 0
            var total = 0
            for (y in 0 until frame.height step 20) for (x in 0 until frame.width step 20) {
                val c = bmp.getColor(x, y)
                total++
                if ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF) > 40) lit++
            }
            println("PROBE sampled $total points")
            val png = org.jetbrains.skia.Image.makeFromBitmap(bmp).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
            File(System.getProperty("java.io.tmpdir"), "echo-video-frame.png").writeBytes(png)
            println("PROBE frame ${frame.width}x${frame.height}, non-black samples=$lit")
        }
        println("PROBE playing: clock=${clock.now()} video=${playback.videoPositionMs}")
        clock = VideoClock(positionMs = 120_000, isPlaying = true, rate = 1.0) // the audio jumped
        Thread.sleep(1500)
        println("PROBE after jump: clock=${clock.now()} video=${playback.videoPositionMs}")
        clock = VideoClock(positionMs = clock.now(), isPlaying = false, rate = 1.0) // paused
        Thread.sleep(1200)
        val paused = playback.videoPositionMs
        Thread.sleep(1000)
        println("PROBE paused: stays ${paused == playback.videoPositionMs} at ${playback.videoPositionMs}")
        clock = VideoClock(positionMs = clock.positionMs, isPlaying = true, rate = 1.0)
        Thread.sleep(1500)
        println("PROBE resumed: clock=${clock.now()} video=${playback.videoPositionMs}")
        playback.dispose()
        Thread.sleep(300)
    }
}
