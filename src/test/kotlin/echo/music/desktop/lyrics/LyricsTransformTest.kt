package echo.music.desktop.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsTransformTest {

    @Test
    fun romanizesEachScriptAndLeavesLatinAlone() = runBlocking {
        val lines = listOf("東京に行きます", "안녕하세요", "你好世界", "Hola mundo")
        val out = LyricsTransformService.romanize(lines)
        println("ROMANIZED $out")
        assertNotNull(out[0])
        assertTrue(out[0]!!.all { it.code < 128 }, "Japanese -> latin: ${out[0]}")
        assertNotNull(out[1])
        assertTrue(out[1]!!.contains("an"), "Korean -> ${out[1]}")
        assertNotNull(out[2])
        assertTrue(out[2]!!.contains("ni") || out[2]!!.contains("NI"), "Chinese -> ${out[2]}")
        assertNull(out[3], "Latin text needs no romanization")
    }

    @Test
    fun romanizesCyrillicSong() = runBlocking {
        val out = LyricsTransformService.romanize(listOf("Привет, мир", "Я тебя люблю"))
        println("ROMANIZED $out")
        assertNotNull(out[0])
        assertTrue(out[0]!!.lowercase().startsWith("privet"), "${out[0]}")
    }

    @Test
    fun detectsWhenRomanizationIsUseful() {
        assertTrue(LyricsTransformService.needsRomanization(listOf("Hello", "사랑해")))
        assertFalse(LyricsTransformService.needsRomanization(listOf("Hello", "Te quiero")))
    }

    @Test
    fun translationWithoutKeyFailsClearly() = runBlocking {
        val r = LyricsTransformService.translate(
            listOf("Hello"),
            TranslationSettings(TranslationProvider.OPENROUTER, apiKey = "", targetLanguage = "es")
        )
        assertTrue(r.isFailure)
        assertEquals(TranslationProvider.MISTRAL, TranslationProvider.from("MISTRAL"))
    }
}
