package echo.music.desktop.system

import kotlin.test.Test

/** Manual probe (-DlyricsProbe=true): lists the audio outputs of this PC. */
class AudioDevicesProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        println("PROBE default=${AudioDevices.defaultOutput()}")
        AudioDevices.allOutputs().forEach { println("PROBE  $it personal=${it.isPersonal}") }
        println("PROBE notifications registered=${AudioDeviceNotifications.register { println("PROBE event") }}")
        println("PROBE state of default=${AudioDevices.defaultOutput()?.let { AudioDevices.stateOf(it.id) }}")
    }
}
