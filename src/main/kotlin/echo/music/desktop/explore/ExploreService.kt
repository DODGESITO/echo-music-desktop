package echo.music.desktop.explore

import com.music.innertube.InnerTube
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.BrowseResponse
import com.music.innertube.pages.LibraryPage
import com.music.innertube.pages.MoodAndGenres
import com.music.innertube.pages.RelatedPage
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** A titled row of items (charts carousel, or a section inside a mood/genre page). */
data class ExploreSection(val title: String, val items: List<YTItem>)

/** Everything shown in Explorar → Descubrir. Each part is null when it failed to load. */
data class DiscoverContent(
    val newReleases: List<AlbumItem>?,
    val charts: List<ExploreSection>?,
    val moodGroups: List<MoodAndGenres>?
)

/** A mood or genre opened by the user (e.g. "Concentración", "Bachata"). */
data class MoodPage(val title: String, val sections: List<ExploreSection>)

/**
 * Charts, moods & genres and new releases — the phone app's browsing screens.
 *
 * Charts are parsed here instead of with `YouTube.getChartsPage()`: YouTube now sends charts as
 * a "video rankings" carousel of playlists plus an "top artists" list, which the shared parser
 * does not recognize (it returns no sections). The item parsers themselves are the shared ones.
 */
object ExploreService {
    private const val CHARTS_PARAMS = "ggMGCgQIgAQ%3D"

    private val chartsClient by lazy { InnerTube() }

    suspend fun loadDiscover(): DiscoverContent = coroutineScope {
        val newReleases = async(Dispatchers.IO) { YouTube.newReleaseAlbums().getOrNull() }
        val charts = async(Dispatchers.IO) { loadCharts().getOrNull() }
        val moods = async(Dispatchers.IO) { YouTube.moodAndGenres().getOrNull() }
        DiscoverContent(newReleases.await(), charts.await(), moods.await())
    }

    suspend fun loadCharts(): Result<List<ExploreSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = chartsClient.apply {
                // Same language/country and session as the rest of the app.
                locale = YouTube.locale
                visitorData = YouTube.visitorData
            }
            val response = client.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_charts", params = CHARTS_PARAMS)
                .body<BrowseResponse>()
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                .orEmpty()
                .mapNotNull { content ->
                    val shelf = content.musicCarouselShelfRenderer ?: return@mapNotNull null
                    val title = shelf.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs
                        ?.joinToString("") { it.text }
                        ?: return@mapNotNull null
                    val items = shelf.contents.mapNotNull { item ->
                        item.musicTwoRowItemRenderer?.let(RelatedPage::fromMusicTwoRowItemRenderer)
                            ?: item.musicResponsiveListItemRenderer?.let(LibraryPage::fromMusicResponsiveListItemRenderer)
                    }
                    if (items.isEmpty()) null else ExploreSection(title, items)
                }
        }
    }

    suspend fun loadMood(title: String, endpoint: BrowseEndpoint): Result<MoodPage> = withContext(Dispatchers.IO) {
        YouTube.browse(endpoint.browseId, endpoint.params).map { result ->
            MoodPage(
                title = result.title ?: title,
                sections = result.items.mapNotNull { section ->
                    if (section.items.isEmpty()) null
                    else ExploreSection(section.title ?: "", section.items)
                }
            )
        }
    }
}
