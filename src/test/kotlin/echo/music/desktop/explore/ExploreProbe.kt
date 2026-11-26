package echo.music.desktop.explore
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeLocale
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
/** Manual network check of Explorar → Descubrir (-DlyricsProbe=true). */
class ExploreProbe {
    @Test fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            YouTube.locale = YouTubeLocale(gl = "MX", hl = "es-419")
            val d = ExploreService.loadDiscover()
            println("PROBE newReleases=${d.newReleases?.size} moods=${d.moodGroups?.map { "${it.title}:${it.items.size}" }}")
            d.charts?.forEach { println("PROBE chart '${it.title}' ${it.items.size} ${it.items.map { i -> i::class.simpleName }.distinct()} e.g. ${it.items.take(3).map { i -> i.title }}") }
            val mood = d.moodGroups?.lastOrNull()?.items?.firstOrNull()
            if (mood != null) {
                val page = ExploreService.loadMood(mood.title, mood.endpoint).getOrNull()
                println("PROBE mood '${page?.title}' sections=${page?.sections?.map { "${it.title}:${it.items.size}" }}")
            }
        }
    }
}
