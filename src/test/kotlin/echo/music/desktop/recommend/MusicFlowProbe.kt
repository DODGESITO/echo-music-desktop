package echo.music.desktop.recommend

import echo.music.desktop.state.FavoritesStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Manual network probe (-DlyricsProbe=true): a real Flow from this PC's history and likes. */
class MusicFlowProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val history = FavoritesStore.getHistory().ifEmpty { echo.music.desktop.state.StatsStore.recentSongs() }
            val liked = (FavoritesStore.getFavorites() + echo.music.desktop.state.PlaylistStore.getPlaylists().flatMap { it.songs }).distinctBy { it.id }
            println("PROBE seeds: history=${history.size} liked=${liked.size}")
            val session = FlowSession(
                YouTubeFlowSource(
                    FlowInputs(
                        history = { history }, liked = { liked }, quickPicks = { emptyList() }, downloaded = { emptyList() },
                        hideRecent = { true }, contentFilter = { it }, neverRecommend = { emptySet() }
                    )
                )
            )
            val t0 = System.currentTimeMillis()
            session.start(null)
            val s = session.state.value
            println("PROBE start ${System.currentTimeMillis() - t0} ms: ${s.items.size} songs, error=${s.error}, hasMore=${s.hasMore}")
            s.items.take(12).forEachIndexed { i, it -> println("PROBE  ${i + 1}. ${it.song.title} — ${it.song.artists.joinToString { a -> a.name }} [${it.source}]") }
            val more = session.loadMore()
            println("PROBE loadMore +${more.size} (total ${session.state.value.items.size})")
            val artists = session.state.value.items.map { it.primaryArtistKey }
            val repeats = artists.zipWithNext().count { (a, b) -> a != null && a == b }
            println("PROBE same artist back-to-back: $repeats; recent left out: ${session.state.value.items.none { it.songId in history.take(30).map { h -> h.id } }}")
        }
    }
}
