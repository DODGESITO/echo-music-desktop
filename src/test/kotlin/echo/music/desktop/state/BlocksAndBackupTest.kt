package echo.music.desktop.state

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterBlockedArtists
import com.music.innertube.models.filterDislikedSongs
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlocksAndBackupTest {
    companion object {
        // One temp home for the class; never touches the real ~/.echomusic.
        private val home: File = Files.createTempDirectory("echo-blocks-test").toFile()
            .also { System.setProperty("user.home", it.absolutePath) }
        private val dataDir get() = File(home, ".echomusic").apply { mkdirs() }
    }

    private fun song(id: String, artist: String, artistId: String?) =
        SongItem(id = id, title = "Song $id", artists = listOf(Artist(name = artist, id = artistId)), thumbnail = "")

    @Test
    fun blockedArtistAndDislikedSongAreFilteredBySharedFilters() {
        ContentBlockStore.update { ContentBlocks() }
        ContentBlockStore.update {
            it.copy(
                artists = listOf(BlockedArtist("UCblocked", "Blocked Artist")),
                songs = listOf(DislikedSong("bad1", "Bad Song"))
            )
        }
        val items = listOf(
            song("ok1", "Good Artist", "UCgood"),
            song("x1", "Blocked Artist", "UCblocked"),
            song("x2", "Blocked Artist", null), // matched by name
            song("bad1", "Good Artist", "UCgood")
        )
        val visible = items.filterBlockedArtists().filterDislikedSongs()
        assertEquals(listOf("ok1"), visible.map { it.id })

        ContentBlockStore.reload() // survives a restart (read back from blocked.json)
        assertEquals(1, ContentBlockStore.current.artists.size)
        ContentBlockStore.update { ContentBlocks() }
        assertEquals(items.size, items.filterBlockedArtists().filterDislikedSongs().size)
    }

    @Test
    fun backupRoundTripWithoutSecrets() {
        File(dataDir, "playlists.json").writeText("""[{"id":"local_1","title":"Mi lista"}]""")
        File(dataDir, "preferences.json").writeText("""{"volume":0.5,"translationApiKey":"dpapi:SECRET","listenBrainzToken":"dpapi:LBSECRET","lastFmSessionKey":"dpapi:LFSECRET"}""")
        File(dataDir, "account.json").writeText("""{"name":"x","cookie":"dpapi:COOKIE"}""")

        val zip = File(home, "copia.zip")
        val summary = BackupService.export(zip).getOrThrow()
        assertTrue("playlists.json" in summary.files)
        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }
            assertFalse("account.json" in names, "account must never be exported")
            val prefs = z.getInputStream(z.getEntry("preferences.json")).readBytes().toString(Charsets.UTF_8)
            assertFalse(prefs.contains("SECRET"), "keys and tokens must not be exported: $prefs")
        }

        // Change local data, then restore: playlists come back, this PC's key is kept.
        File(dataDir, "playlists.json").writeText("[]")
        File(dataDir, "preferences.json").writeText("""{"volume":0.9,"translationApiKey":"dpapi:LOCAL"}""")
        BackupService.import(zip).getOrThrow()
        assertTrue(File(dataDir, "playlists.json").readText().contains("Mi lista"))
        val prefs = File(dataDir, "preferences.json").readText()
        assertTrue(prefs.contains("0.5") && prefs.contains("LOCAL"), prefs)
    }

    @Test
    fun importIgnoresUnknownFilesAndRejectsBadJson() {
        val evil = File(home, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../../outside.json")); z.write("{}".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("account.json")); z.write("{}".toByteArray()); z.closeEntry()
        }
        assertTrue(BackupService.import(evil).isFailure, "zip without known files is not a backup")
        assertFalse(File(home, "outside.json").exists())

        val broken = File(home, "broken.zip")
        ZipOutputStream(broken.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("library.json")); z.write("not json".toByteArray()); z.closeEntry()
        }
        File(dataDir, "library.json").writeText("""{"favorites":[]}""")
        assertTrue(BackupService.import(broken).isFailure)
        assertEquals("""{"favorites":[]}""", File(dataDir, "library.json").readText(), "nothing written on failure")
    }
}
