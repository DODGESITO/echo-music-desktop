package echo.music.desktop.player

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SponsorBlockTest {
    @Test
    fun parsesSkipSegmentsOnly() {
        val body = """[
          {"segment":[0.0,12.5],"UUID":"a","category":"intro","actionType":"skip"},
          {"segment":[200.0,215.25],"UUID":"b","category":"music_offtopic","actionType":"skip"},
          {"segment":[50.0,60.0],"UUID":"c","category":"sponsor","actionType":"mute"},
          {"segment":[70.0,60.0],"UUID":"d","category":"outro","actionType":"skip"}
        ]"""
        val segments = SponsorBlockService.parse(body)
        assertEquals(listOf("a", "b"), segments.map { it.uuid })
        assertEquals(12_500L, segments[0].endMs)
        assertEquals(215_250L, segments[1].endMs)
        assertEquals(emptyList(), SponsorBlockService.parse("not json"))
    }

    /** Manual network check (-DlyricsProbe=true). */
    @Test
    fun liveProbe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val segs = SponsorBlockService.segmentsFor("kJQP7kiw5Fk")
            println("PROBE sponsorblock despacito=${segs.map { "${it.category}:${it.startMs / 1000}-${it.endMs / 1000}s" }}")
        }
    }
}
