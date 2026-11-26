package echo.music.desktop.player

import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import echo.music.desktop.state.AccountStore
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackFeaturesTest {

    private fun close(a: Float, b: Float) = abs(a - b) < 0.01f

    @Test
    fun loudSongIsLoweredQuietSongIsRaised() {
        // +6 dB louder than reference -> -6 dB -> about half amplitude
        assertTrue(close(Loudness.gainFor(6.0, true, LoudnessPreset.NORMAL), 0.501f))
        // -3 dB quieter -> +3 dB
        assertTrue(close(Loudness.gainFor(-3.0, true, LoudnessPreset.NORMAL), 1.413f))
    }

    @Test
    fun presetAppliesEvenWithoutNormalizationOrData() {
        assertEquals(1f, Loudness.gainFor(null, true, LoudnessPreset.NORMAL))
        assertEquals(1f, Loudness.gainFor(8.0, false, LoudnessPreset.NORMAL))
        assertTrue(close(Loudness.gainFor(null, false, LoudnessPreset.QUIET), 0.501f))
        assertTrue(close(Loudness.gainFor(null, false, LoudnessPreset.LOUD), 1.413f))
    }

    @Test
    fun gainIsLimited() {
        assertTrue(close(Loudness.gainFor(40.0, true, LoudnessPreset.NORMAL), 0.178f)) // capped at -15 dB
        assertTrue(close(Loudness.gainFor(-40.0, true, LoudnessPreset.AGGRESSIVE), 3.162f)) // capped at +10 dB
    }

    @Test
    fun presetNamesFromPreferences() {
        assertEquals(LoudnessPreset.LOUD, LoudnessPreset.from("LOUD"))
        assertEquals(LoudnessPreset.NORMAL, LoudnessPreset.from("whatever"))
    }

    /**
     * Manual network checks (-DlyricsProbe=true): stream quality + loudness for a public video,
     * and the signed-in user's saved albums/artists (prints only counts).
     */
    @Test
    fun networkProbe() {
        if (System.getProperty("lyricsProbe") != "true") return
        runBlocking {
            val id = "kJQP7kiw5Fk"
            val response = YouTube.player(id, client = com.music.innertube.models.YouTubeClient.VISIONOS).getOrNull()
            val aac = response?.streamingData?.adaptiveFormats.orEmpty()
                .filter { it.isAudio && it.mimeType.contains("audio/mp4") }
                .map { "${it.itag}:${it.bitrate / 1000}kbps" }
            println("PROBE AAC formats=$aac loudness=${response?.playerConfig?.audioConfig?.loudnessDb}")
            val high = DesktopStreamResolver.resolveStreamUrl(id, lowQuality = false)
            val low = DesktopStreamResolver.resolveStreamUrl(id, lowQuality = true)
            println("PROBE resolve high=${high.isSuccess} low=${low.isSuccess} loudnessCached=${DesktopStreamResolver.loudnessDbFor(id)}")

            val account = AccountStore.getYouTubeAccount()
            if (account == null) {
                println("PROBE library: no account")
            } else {
                YouTube.cookie = account.cookie
                val albums = YouTube.library("FEmusic_liked_albums").completed()
                val artists = YouTube.library("FEmusic_library_corpus_artists").completed()
                println(
                    "PROBE library albums=${albums.getOrNull()?.items?.size} (${albums.exceptionOrNull()?.message}) " +
                        "artists=${artists.getOrNull()?.items?.size} (${artists.exceptionOrNull()?.message}) " +
                        "types=${(albums.getOrNull()?.items.orEmpty() + artists.getOrNull()?.items.orEmpty()).map { it::class.simpleName }.distinct()}"
                )
            }
        }
    }
}
