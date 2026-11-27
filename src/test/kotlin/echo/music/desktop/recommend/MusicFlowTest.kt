package echo.music.desktop.recommend

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun song(id: String, artist: String, title: String = "Song $id") =
    SongItem(id = id, title = title, artists = listOf(Artist(artist, "A-$artist")), thumbnail = "")

private fun item(id: String, artist: String, source: FlowSource = FlowSource.HISTORY, title: String = "Song $id") =
    FlowItem(song(id, artist, title), source)

class MusicFlowTest {

    @Test
    fun sameArtistIsSpacedOut() {
        val ranked = rankFlowCandidates(
            listOf(item("1", "x"), item("2", "x"), item("3", "x"), item("4", "y"), item("5", "z"))
        )
        val artists = ranked.map { it.song.artists.first().name }
        for (i in 1 until artists.size) {
            // never twice in a row while other artists are available
            if (artists[i] == artists[i - 1]) assertTrue(artists.drop(i).all { it == artists[i] })
        }
        assertEquals(listOf("x", "y", "z"), artists.take(3))
    }

    @Test
    fun feedbackAndLikesMoveArtists() {
        val ranked = rankFlowCandidates(
            listOf(item("1", "boring"), item("2", "loved", FlowSource.CATALOG), item("3", "other")),
            likedArtists = setOf("A-loved"),
            lessLikeArtists = setOf("A-boring")
        )
        assertEquals("2", ranked.first().songId, "liked artist first even from the catalog")
        assertEquals("1", ranked.last().songId, "\"menos como esto\" goes last")
    }

    @Test
    fun filterDropsSeenRecentHiddenAndDuplicates() {
        val kept = filterFlowCandidates(
            listOf(
                item("seen", "a"), item("recent", "a"), item("hidden", "a"),
                item("dupA", "b", title = "Same"), item("dupB", "b", title = "Same"),
                item("never", "c", title = "Nope"), item("ok", "d")
            ),
            FlowFilter(
                seenSongIds = setOf("seen"),
                recentSongIds = setOf("recent"),
                hiddenSongIds = setOf("hidden"),
                excludedTrackKeys = setOf(trackKeyOf("Nope", "c"))
            )
        )
        assertEquals(listOf("dupA", "ok"), kept.map { it.songId })
    }

    private class FakeSource(private val pages: MutableList<List<FlowItem>>, private val failWith: Exception? = null) : FlowFeedSource {
        override suspend fun signals() = FlowSignals(FlowFilter(recentSongIds = setOf("recent")), emptySet())
        override fun open(nowPlaying: SongItem?) = object : FlowCursor {
            override val hasMore get() = pages.isNotEmpty()
            override suspend fun next(): List<FlowItem> {
                failWith?.let { throw it }
                return pages.removeAt(0)
            }
        }
        override suspend fun offlineCandidates() = listOf(item("offline", "o", FlowSource.OFFLINE))
    }

    @Test
    fun sessionStartsWithNowPlayingAndGrows() = runBlocking {
        val pages = MutableList(4) { p -> (0 until 5).map { i -> item("p$p-$i", "artist$p$i") } }
        pages[0] = pages[0] + item("recent", "r") + item("now", "n")
        val session = FlowSession(FakeSource(pages))
        session.start(item("now", "n", FlowSource.NOW_PLAYING))
        val state = session.state.value
        assertEquals("now", state.items.first().songId)
        assertTrue(state.items.size >= FlowSession.INITIAL_TARGET)
        assertFalse(state.items.any { it.songId == "recent" }, "recently played left out")
        assertEquals(1, state.items.count { it.songId == "now" }, "the playing song is not repeated")
        assertNull(state.error)

        val before = state.items.size
        val added = session.loadMore()
        assertEquals(session.state.value.items.size - before, added.size)
        session.hide(added.first().songId)
        assertEquals(added.first().songId, session.state.value.items[before].songId, "pages already shown never change")
    }

    @Test
    fun offlineFallsBackToDownloads() = runBlocking {
        val session = FlowSession(FakeSource(mutableListOf(emptyList()), failWith = FlowOfflineException(null)))
        session.start(null)
        val state = session.state.value
        assertTrue(state.isOffline)
        assertEquals("offline", state.items.single().songId)
    }

    @Test
    fun aiAnswersAreReadEvenWhenWrapped() {
        val fenced = "Aquí está:\n```json\n{\"name\":\"Noche\",\"songs\":[{\"title\":\"A\",\"artist\":\"B\"},{\"title\":\"\"}]}\n```"
        val obj = AiPlaylists.parseJson(fenced)
        val ideas = AiPlaylists.songIdeas(
            (obj as kotlinx.serialization.json.JsonObject)["songs"] as kotlinx.serialization.json.JsonArray
        )
        assertEquals(listOf(AiSongIdea("A", "B")), ideas)
        assertTrue(AiPlaylists.parseJson("[{\"title\":\"x\",\"artist\":\"y\"}]") is kotlinx.serialization.json.JsonArray)
    }
}
