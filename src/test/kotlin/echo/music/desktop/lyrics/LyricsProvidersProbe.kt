package echo.music.desktop.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Manual network check of every lyrics provider. Skipped unless -DlyricsProbe=true:
 *   gradlew -p desktop test --tests "*LyricsProvidersProbe*" -DlyricsProbe=true
 */
class LyricsProvidersProbe {
    @Test
    fun probe() {
        if (System.getProperty("lyricsProbe") != "true") return
        val request = LyricsRequest(
            videoId = "kJQP7kiw5Fk",
            title = "Despacito",
            artist = "Luis Fonsi",
            durationSec = 282,
            album = "VIDA"
        )
        runBlocking {
            for (provider in LyricsProviderId.entries) {
                val started = System.currentTimeMillis()
                val lyrics = LyricsService.fetchFrom(provider, request)
                val took = System.currentTimeMillis() - started
                println(
                    "PROBE ${provider.name.padEnd(13)} ${took}ms -> " +
                        if (lyrics == null) "none"
                        else "${lyrics.lines.size} lines, synced=${lyrics.isSynced}, words=${lyrics.hasWordSync}, " +
                            "first='${lyrics.lines.firstOrNull { it.text.isNotBlank() }?.text?.take(40)}'"
                )
            }
            val best = LyricsService.fetchLyrics(request)
            println("PROBE BEST -> ${best?.provider} synced=${best?.isSynced} words=${best?.hasWordSync}")
        }
    }
}
