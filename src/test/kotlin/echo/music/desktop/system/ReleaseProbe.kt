package echo.music.desktop.system

import kotlin.test.Test

/** Manual probe (-DlyricsProbe=true): what the update check sees on GitHub, without logging in. */
class ReleaseProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        println("PROBE as 1.2.0: ${UpdateChecker.check(UpdateChecker.DEFAULT_REPO, "1.2.0")}")
        println("PROBE installer: ${(UpdateChecker.check(UpdateChecker.DEFAULT_REPO, "1.1.0") as? UpdateResult.Available)?.info?.installer}")
    }
}
