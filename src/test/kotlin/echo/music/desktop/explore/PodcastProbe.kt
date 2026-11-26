package echo.music.desktop.explore
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
class PodcastProbe {
    @Test fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val r = YouTube.search("La Cotorrisa", YouTube.SearchFilter.FILTER_PODCAST).getOrNull()?.items.orEmpty()
            println("PROBE podcasts=${r.take(3).map { "${it::class.simpleName}:${it.id}:${it.title}:${(it as? PlaylistItem)?.isPodcast}" }}")
            val show = r.filterIsInstance<PlaylistItem>().firstOrNull() ?: return@runBlocking
            val page = YouTube.podcast(show.id.removePrefix("MPSP")).getOrNull()
            println("PROBE show '${page?.playlist?.title}' episodes=${page?.songs?.size} first=${page?.songs?.firstOrNull()?.let { "${it.id}:${it.title}:${it.duration}:${it.isPodcastEpisode}" }} cont=${page?.songsContinuation != null}")
            val e = YouTube.search("La Cotorrisa", YouTube.SearchFilter.FILTER_EPISODE).getOrNull()?.items.orEmpty()
            println("PROBE episodes=${e.take(2).map { "${it::class.simpleName}:${it.id}:${(it as? SongItem)?.isPodcastEpisode}" }}")
        }
    }
}
