package echo.music.desktop.video

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Manual probe (-DlyricsProbe=true): what the official-video search sees. */
class VideoSearchProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            for (q in listOf("\"Historia de Taxi\" Ricardo Arjona official music video", "\"Despacito\" Luis Fonsi official music video")) {
                println("PROBE query: $q")
                YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty().take(6).forEach { item ->
                    val s = item as? SongItem
                    println("PROBE   ${item.id} | ${item.title} | by=${s?.artists?.joinToString { it.name }} | videoSong=${s?.isVideoSong} | type=${s?.musicVideoType}")
                }
            }
        }
    }
}
