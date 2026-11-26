package echo.music.desktop.lyrics

import echo.music.iad1tya.api.DeepLService
import echo.music.iad1tya.api.MistralService
import echo.music.iad1tya.api.OpenRouterService
import echo.music.iad1tya.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** AI services the phone app uses to translate lyrics (each needs the user's own API key). */
enum class TranslationProvider(val displayName: String, val defaultModel: String) {
    OPENROUTER("OpenRouter", "openrouter/free"),
    DEEPL("DeepL", ""),
    MISTRAL("Mistral", "mistral-small-latest");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: OPENROUTER
    }
}

data class TranslationSettings(
    val provider: TranslationProvider,
    val apiKey: String,
    /** Target language code, e.g. "es", "en", "pt-BR". */
    val targetLanguage: String,
    val model: String = "",
    val baseUrl: String = ""
)

/**
 * Translation and romanization of lyrics lines, reusing the phone app's code:
 * the AI services (OpenRouterService, DeepLService, MistralService) and LyricsUtils romanizers.
 */
object LyricsTransformService {
    const val DEFAULT_OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

    private val cache = object : LinkedHashMap<String, List<String?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String?>>?) = size > 40
    }

    /**
     * Translates every line. Returns one entry per input line (null where the service returned
     * fewer lines than expected).
     */
    suspend fun translate(lines: List<String>, settings: TranslationSettings): Result<List<String?>> =
        withContext(Dispatchers.IO) {
            if (settings.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Falta la clave de ${settings.provider.displayName}"))
            }
            if (lines.isEmpty()) return@withContext Result.success(emptyList())
            val key = "${settings.provider}|${settings.targetLanguage}|${settings.model}|${lines.hashCode()}"
            synchronized(cache) { cache[key] }?.let { return@withContext Result.success(it) }

            val text = lines.joinToString("\n")
            // The AI prompts take a language name ("Spanish"); DeepL takes the code.
            val languageName = Locale.forLanguageTag(settings.targetLanguage).getDisplayLanguage(Locale.ENGLISH)
                .ifBlank { settings.targetLanguage }
            val model = settings.model.ifBlank { settings.provider.defaultModel }
            val result = when (settings.provider) {
                TranslationProvider.DEEPL ->
                    DeepLService.translate(text = text, targetLanguage = settings.targetLanguage, apiKey = settings.apiKey)
                TranslationProvider.MISTRAL ->
                    MistralService.translate(
                        text = text, targetLanguage = languageName, apiKey = settings.apiKey,
                        model = model, mode = "Literal"
                    )
                TranslationProvider.OPENROUTER ->
                    OpenRouterService.translate(
                        text = text, targetLanguage = languageName, apiKey = settings.apiKey,
                        baseUrl = settings.baseUrl.ifBlank { DEFAULT_OPENROUTER_URL }, model = model, mode = "Literal"
                    )
            }
            result.map { translated ->
                val aligned = lines.indices.map { i -> translated.getOrNull(i)?.takeIf { it.isNotBlank() } }
                synchronized(cache) { cache[key] = aligned }
                aligned
            }
        }

    /** True when at least one line is in a script that can be romanized. */
    fun needsRomanization(lines: List<String>): Boolean = lines.any { line ->
        LyricsUtils.isJapanese(line) || LyricsUtils.isKorean(line) || LyricsUtils.isChinese(line) ||
            LyricsUtils.isHindi(line) || LyricsUtils.isPunjabi(line) || hasCyrillic(line)
    }

    /**
     * Romanizes each line that is in Japanese, Korean, Chinese, Cyrillic, Hindi or Punjabi
     * (null for lines already in Latin script). Same order of checks as the phone app.
     */
    suspend fun romanize(lines: List<String>): List<String?> = withContext(Dispatchers.Default) {
        val whole = lines.joinToString("\n")
        // Like the phone (default "whole song" mode): decide the Cyrillic language once per song.
        val cyrillicSong = listOf(
            LyricsUtils::isRussian, LyricsUtils::isUkrainian, LyricsUtils::isSerbian, LyricsUtils::isBulgarian,
            LyricsUtils::isBelarusian, LyricsUtils::isKyrgyz, LyricsUtils::isMacedonian
        ).any { it(whole) }
        lines.map { line ->
            val romanized = runCatching {
                when {
                    LyricsUtils.isJapanese(line) && !LyricsUtils.isChinese(line) -> LyricsUtils.romanizeJapanese(line)
                    LyricsUtils.isKorean(line) -> LyricsUtils.romanizeKorean(line)
                    cyrillicSong && hasCyrillic(line) -> LyricsUtils.romanizeCyrillic(line)
                    LyricsUtils.isChinese(line) -> LyricsUtils.romanizeChinese(line)
                    LyricsUtils.isHindi(line) -> LyricsUtils.romanizeHindi(line)
                    LyricsUtils.isPunjabi(line) -> LyricsUtils.romanizePunjabi(line)
                    else -> null
                }
            }.getOrNull()
            romanized?.trim()?.takeIf { it.isNotEmpty() && it != line.trim() }
        }
    }

    private fun hasCyrillic(text: String) = text.any { it in 'Ѐ'..'ӿ' }
}
