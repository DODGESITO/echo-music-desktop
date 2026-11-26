package echo.music.desktop.scrobble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrobbleRulesTest {
    @Test
    fun listenCountsAtHalfOrFourMinutes() {
        assertNull(ScrobbleService.scrobbleThresholdMs(20), "songs under 30 s are never scrobbled")
        assertEquals(100_000L, ScrobbleService.scrobbleThresholdMs(200)) // half of 3:20
        assertEquals(180_000L, ScrobbleService.scrobbleThresholdMs(600)) // capped (phone default: 180 s)
    }

    @Test
    fun configNeedsAllLastFmParts() {
        assertFalse(ScrobbleConfig(lastFmApiKey = "k", lastFmSecret = "s").lastFmOn)
        assertTrue(ScrobbleConfig(lastFmApiKey = "k", lastFmSecret = "s", lastFmSessionKey = "x").lastFmOn)
        assertTrue(ScrobbleConfig(listenBrainzToken = "t").listenBrainzOn)
    }
}
