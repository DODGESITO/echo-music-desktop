package echo.music.desktop.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsParserTest {

    @Test
    fun standardLrcWithMetadataAndRepeatedTimestamps() {
        val lyrics = LyricsService.parseLyrics(
            """
            [ar:Artist]
            [ti:Song]
            [00:01.50]First line
            [00:05.00][00:20.00]Chorus
            """.trimIndent()
        )
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1500L, 5000L, 20000L), lyrics.lines.map { it.timeMs })
        assertEquals("Chorus", lyrics.lines.last().text)
        assertFalse(lyrics.hasWordSync)
    }

    @Test
    fun enhancedRichSyncWords() {
        val lyrics = LyricsService.parseLyrics(
            """
            [00:10.000]<00:10.000>Hola <00:10.500>mundo
            [00:12.000]{bg}<00:12.000>(eco)
            """.trimIndent()
        )
        val first = lyrics.lines.first()
        assertEquals("Hola mundo", first.text)
        assertEquals(listOf("Hola", "mundo"), first.words!!.map { it.text })
        assertEquals(10_500L, first.words!![0].endMs)
        // Last word ends where the next line starts
        assertEquals(12_000L, first.words!![1].endMs)
        assertTrue(lyrics.lines[1].isBackground)
        assertTrue(lyrics.hasWordSync)
    }

    @Test
    fun echoWordLineFormatWithAgent() {
        val lyrics = LyricsService.parseLyrics(
            """
            [00:03.20]{agent:v1}Say: hello
            <Say::3.2:3.6|hello:3.6:4.1>
            [00:05.00]No words here
            """.trimIndent()
        )
        val first = lyrics.lines[0]
        assertEquals("v1", first.agent)
        assertEquals("Say: hello", first.text)
        assertEquals(listOf("Say:", "hello"), first.words!!.map { it.text })
        assertEquals(3_600L, first.words!![1].startMs)
        assertNull(lyrics.lines[1].words)
    }

    @Test
    fun plainLyricsAreUnsynced() {
        val lyrics = LyricsService.parseLyrics("Line one\nLine two")
        assertFalse(lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
    }

    @Test
    fun escapedNewlinesAndEntities() {
        val lyrics = LyricsService.parseLyrics("\"[00:01.00]Rock &amp; roll\\n[00:02.00]It&#39;s me\"")
        assertEquals(listOf("Rock & roll", "It's me"), lyrics.lines.map { it.text })
    }
}
