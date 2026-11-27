package echo.music.desktop.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.desktop.video.MusicVideoService
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/** Manual probe (-DlyricsProbe=true): song → official video → download → JavaFX play and seek. */
class VideoPlaybackProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        JavaFxPlayerEngine.ensureFxInitialized()
        runBlocking {
            for (query in listOf("Despacito Luis Fonsi", "Historia de Taxi Ricardo Arjona")) {
                val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>().first()
                val t0 = System.currentTimeMillis()
                val videoId = MusicVideoService.videoIdFor(song)
                val t1 = System.currentTimeMillis()
                val file = MusicVideoService.prepare(song)
                val t2 = System.currentTimeMillis()
                println("PROBE '${song.title}' (${song.id}, video=${song.isVideoSong}) -> video $videoId in ${t1 - t0} ms; file ${file?.length()?.div(1048576)} MB in ${t2 - t1} ms")
                if (file == null) continue

                val ready = CountDownLatch(1)
                var player: MediaPlayer? = null
                Platform.runLater {
                    val p = MediaPlayer(Media(file.toURI().toString())).apply { isMute = true }
                    player = p
                    p.setOnReady { println("PROBE   ready ${p.media.width}x${p.media.height} ${p.media.duration.toSeconds().toInt()}s"); p.play(); ready.countDown() }
                    p.setOnError { println("PROBE   error ${p.error?.message}"); ready.countDown() }
                }
                ready.await(20, TimeUnit.SECONDS)
                Thread.sleep(1500)
                Platform.runLater { player?.seek(Duration.seconds(60.0)) }
                Thread.sleep(1500)
                Platform.runLater { println("PROBE   after seek(60) at ${"%.1f".format(player?.currentTime?.toSeconds())}s status=${player?.status}"); player?.dispose() }
                Thread.sleep(400)
            }
        }
    }
}
