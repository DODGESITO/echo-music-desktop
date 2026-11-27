package echo.music.desktop.state

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesMergeTest {
    private fun song(id: String) = SongItem(
        id = id,
        title = "Song $id",
        artists = listOf(Artist(name = "Artist", id = null)),
        thumbnail = ""
    )

    @Test
    fun firstSyncKeepsYouTubeOrder() {
        val merged = FavoritesStore.mergeFavorites(emptyList(), listOf(song("newest"), song("middle"), song("oldest")))
        assertEquals(listOf("newest", "middle", "oldest"), merged.map { it.id })
        assertTrue(merged.zipWithNext().all { (a, b) -> a.timestamp > b.timestamp })
    }

    @Test
    fun newLikesGoOnTopAndLocalOnesKeepTheirPlace() {
        val local = listOf(SavedSong.fromSongItem(song("a")), SavedSong.fromSongItem(song("b")))
        val merged = FavoritesStore.mergeFavorites(local, listOf(song("x"), song("a"), song("y"), song("x")))
        assertEquals(listOf("x", "y", "a", "b"), merged.map { it.id })
    }

    @Test
    fun emptyRemoteChangesNothing() {
        val local = listOf(SavedSong.fromSongItem(song("a")))
        assertEquals(local, FavoritesStore.mergeFavorites(local, emptyList()))
    }
}
