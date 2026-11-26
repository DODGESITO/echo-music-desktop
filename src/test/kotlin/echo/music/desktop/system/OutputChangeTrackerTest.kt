package echo.music.desktop.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OutputChangeTrackerTest {
    private val speakers = AudioOutput("spk", "Altavoces", 1, "HDAUDIO", 1)
    private val buds = AudioOutput("bt", "Galaxy Buds", 1, "BTHENUM", 3)
    private val wired = AudioOutput("hp", "Auriculares", 1, "HDAUDIO", 3)

    @Test
    fun firstReadingIsNotAnEvent() {
        val t = OutputChangeTracker { 1 }
        assertNull(t.update(buds))
        assertNull(t.update(buds))
    }

    @Test
    fun bluetoothGoneIsADisconnection() {
        val states = mutableMapOf("bt" to 1)
        val t = OutputChangeTracker { states[it] ?: 0 }
        t.update(buds)
        states["bt"] = 4 // not present
        val e = t.update(speakers)
        assertIs<OutputEvent.PersonalDisconnected>(e)
        assertEquals("bt", e.device.id)
    }

    @Test
    fun choosingSpeakersByHandIsNotADisconnection() {
        val t = OutputChangeTracker { 1 } // headphones still connected
        t.update(wired)
        assertIs<OutputEvent.Changed>(t.update(speakers))
    }

    @Test
    fun speakersGoingAwayDoNotPause() {
        val t = OutputChangeTracker { 4 }
        t.update(speakers)
        assertIs<OutputEvent.Changed>(t.update(buds))
    }

    @Test
    fun noOutputLeftStillCounts() {
        val t = OutputChangeTracker { 0 }
        t.update(buds)
        assertIs<OutputEvent.PersonalDisconnected>(t.update(null))
    }
}
