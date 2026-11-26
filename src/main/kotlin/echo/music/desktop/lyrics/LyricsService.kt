package echo.music.desktop.lyrics

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.kugou.KuGou
import com.music.lrclib.LrcLib
import com.music.paxsenix.Paxsenix
import com.music.simpmusic.SimpMusicLyrics
import com.music.youlyplus.YouLyPlus
import echo.music.iad1tya.betterlyrics.BetterLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One word (or syllable group) of a word-synced line. Times in milliseconds. */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord>? = null,
    /** Background vocals / ad-libs (shown smaller). */
    val isBackground: Boolean = false,
    /** Singer id for duets (e.g. "v1", "v2"), when the provider gives it. */
    val agent: String? = null
)

data class SongLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    /** Provider that supplied these lyrics. */
    val provider: LyricsProviderId? = null
) {
    val hasWordSync: Boolean get() = lines.any { !it.words.isNullOrEmpty() }
}

/** Lyrics sources, in the same default priority as the phone app. */
enum class LyricsProviderId(val displayName: String) {
    YOULYPLUS("YouLy+"),
    PAXSENIX("Paxsenix"),
    BETTERLYRICS("BetterLyrics"),
    SIMPMUSIC("SimpMusic"),
    LRCLIB("LRCLIB"),
    KUGOU("KuGou"),
    YOUTUBE("YouTube Music")
}

data class LyricsRequest(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val album: String? = null
)

object LyricsService {
    private const val PROVIDER_TIMEOUT_MS = 9_000L
    private const val CACHE_SIZE = 60

    private val cache = object : LinkedHashMap<String, SongLyrics>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongLyrics>?) = size > CACHE_SIZE
    }

    @Volatile private var paxsenixReady = false

    private fun ensurePaxsenix() {
        if (!paxsenixReady) {
            Paxsenix.init(Context(versionName = "desktop"))
            paxsenixReady = true
        }
    }

    /**
     * Queries every provider at the same time and returns the best result by priority:
     * the first provider (in [order]) with synced lyrics; if none has them, the first with plain lyrics.
     */
    suspend fun fetchLyrics(
        request: LyricsRequest,
        order: List<LyricsProviderId> = LyricsProviderId.entries
    ): SongLyrics? {
        val cacheKey = request.videoId.ifBlank { "${request.title}|${request.artist}" }
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val result = coroutineScope {
            val jobs: List<Deferred<SongLyrics?>> = order.map { provider ->
                async(Dispatchers.IO) { fetchFrom(provider, request) }
            }
            var plainFallback: SongLyrics? = null
            var chosen: SongLyrics? = null
            for (job in jobs) {
                val lyrics = job.await() ?: continue
                if (lyrics.isSynced) {
                    chosen = lyrics
                    break
                }
                if (plainFallback == null) plainFallback = lyrics
            }
            jobs.forEach { it.cancel() }
            chosen ?: plainFallback
        }
        if (result != null) synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    /** Remembers lyrics the user picked by hand, so replaying the song keeps that choice. */
    fun remember(request: LyricsRequest, lyrics: SongLyrics) {
        val cacheKey = request.videoId.ifBlank { "${request.title}|${request.artist}" }
        synchronized(cache) { cache[cacheKey] = lyrics }
    }

    /** Fetches lyrics from one specific provider (used when the user picks another source). */
    suspend fun fetchFrom(provider: LyricsProviderId, request: LyricsRequest): SongLyrics? =
        withContext(Dispatchers.IO) {
            try {
                val raw = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { fetchRaw(provider, request) }
                    ?.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                parseLyrics(raw).takeIf { it.lines.isNotEmpty() }?.copy(provider = provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }

    private suspend fun fetchRaw(provider: LyricsProviderId, r: LyricsRequest): Result<String> {
        val duration = r.durationSec
        return when (provider) {
            LyricsProviderId.YOULYPLUS ->
                YouLyPlus.getLyrics(r.title, r.artist, duration, r.album, r.videoId.ifBlank { null })
            LyricsProviderId.PAXSENIX -> {
                ensurePaxsenix()
                Paxsenix.getLyrics(r.title, r.artist, duration, r.album)
            }
            LyricsProviderId.BETTERLYRICS -> BetterLyrics.getLyrics(r.title, r.artist, duration, r.album)
            LyricsProviderId.SIMPMUSIC ->
                if (r.videoId.isBlank()) Result.failure(IllegalArgumentException("no videoId"))
                else SimpMusicLyrics.getLyrics(r.videoId, duration)
            LyricsProviderId.LRCLIB ->
                LrcLib.getLyrics(r.title, r.artist, if (duration > 0) duration else -1, r.album)
            LyricsProviderId.KUGOU -> KuGou.getLyrics(r.title, r.artist, duration, r.album)
            LyricsProviderId.YOUTUBE -> runCatching {
                require(r.videoId.isNotBlank())
                val next = YouTube.next(WatchEndpoint(videoId = r.videoId)).getOrThrow()
                val endpoint = next.lyricsEndpoint ?: error("Lyrics endpoint not found")
                YouTube.lyrics(endpoint = endpoint).getOrThrow() ?: error("Lyrics unavailable")
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Parsing (port of the phone app's LyricsUtils legacy parser)
    //
    // Supported formats:
    //  * Standard LRC:            [mm:ss.xx]text   (several timestamps per line allowed)
    //  * Enhanced / rich sync:    [mm:ss.xx]<mm:ss.xxx>word <mm:ss.xxx>word
    //  * Echo word format:        [mm:ss.xx]text  followed by a line  <word:start:end|word:start:end>
    //  * Markers:                 {agent:v1} for duets, {bg} for background vocals
    //  * Plain text (no timestamps) -> unsynced lyrics
    // ---------------------------------------------------------------------------------------

    private val LINE_REGEX = "((\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\] ?)+)(.*)".toRegex()
    private val TIME_REGEX = "\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]".toRegex()
    private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)".toRegex()
    private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)".toRegex()
    private val RICH_SYNC_TAG_REGEX = "<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*".toRegex()
    private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
    private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()
    private val METADATA_REGEX =
        "^\\[(ti|ar|al|by|offset|re|ve|length|au|la|id):.*\\]$".toRegex(RegexOption.IGNORE_CASE)
    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    fun parseLyrics(raw: String): SongLyrics {
        val text = decodeHtmlEntities(
            raw.trim()
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        )
        val lines = text.lines()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !METADATA_REGEX.matches(it.trim()) }

        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) && RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }
        val synced = if (isRichSync) parseRichSync(lines) else parseStandard(lines)
        if (synced.isNotEmpty()) return SongLyrics(isSynced = true, lines = synced)

        // No timestamps at all: plain lyrics.
        val plain = lines.map { it.trim() }
            .filterNot { it.startsWith("<") && it.endsWith(">") }
            .map { LyricLine(-1L, it) }
        return SongLyrics(isSynced = false, lines = plain)
    }

    private fun parseRichSync(lines: List<String>): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        lines.forEachIndexed { index, rawLine ->
            val match = RICH_SYNC_LINE_REGEX.matchEntire(rawLine.trim()) ?: return@forEachIndexed
            val lineMs = toMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            var content = match.groupValues[4].trimStart()

            val agent = AGENT_REGEX.find(content)?.groupValues?.get(1)
            if (agent != null) content = content.replaceFirst(AGENT_REGEX, "")
            val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
            if (isBackground) content = content.replaceFirst(BACKGROUND_REGEX, "")

            val words = parseRichSyncWords(content, index, lines)
            val plain = content.replace(RICH_SYNC_TAG_REGEX, "").trim()
            if (plain.isNotBlank()) {
                result += LyricLine(lineMs, plain, words, isBackground, agent)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun parseRichSyncWords(content: String, index: Int, lines: List<String>): List<LyricWord>? {
        val matches = RICH_SYNC_WORD_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return null
        val words = matches.mapIndexedNotNull { i, m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val end = if (i < matches.lastIndex) {
                val n = matches[i + 1]
                toMs(n.groupValues[1], n.groupValues[2], n.groupValues[3])
            } else {
                nextLineStartMs(index, lines) ?: (start + 500L)
            }
            val word = m.groupValues[4].trim()
            if (word.isBlank()) null else LyricWord(word, start, end)
        }
        return words.ifEmpty { null }
    }

    private fun nextLineStartMs(index: Int, lines: List<String>): Long? {
        val next = lines.getOrNull(index + 1)?.trim() ?: return null
        val m = RICH_SYNC_LINE_REGEX.matchEntire(next) ?: return null
        return toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private fun parseStandard(lines: List<String>): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val isWordLine = line.startsWith("<") && line.endsWith(">")
            if (!isWordLine) {
                val next = lines.getOrNull(i + 1)?.trim()
                val words = if (next != null && next.startsWith("<") && next.endsWith(">")) {
                    parseWordTimestamps(next.removeSurrounding("<", ">"))
                } else null
                parseLine(line, words)?.let { result += it }
            }
            i++
        }
        return result.sortedBy { it.timeMs }
    }

    /** Parses `word:start:end|word:start:end` (times in seconds). Words may contain ':'. */
    private fun parseWordTimestamps(data: String): List<LyricWord>? {
        if (data.isBlank()) return null
        val words = data.split("|").mapNotNull { part ->
            val endSep = part.lastIndexOf(':')
            val startSep = if (endSep > 0) part.lastIndexOf(':', endSep - 1) else -1
            if (startSep <= 0) return@mapNotNull null
            val word = part.substring(0, startSep)
            val start = part.substring(startSep + 1, endSep).toDoubleOrNull() ?: return@mapNotNull null
            val end = part.substring(endSep + 1).toDoubleOrNull() ?: return@mapNotNull null
            if (word.isBlank()) null else LyricWord(word.trim(), (start * 1000).toLong(), (end * 1000).toLong())
        }
        return words.ifEmpty { null }
    }

    private fun parseLine(line: String, words: List<LyricWord>?): List<LyricLine>? {
        val match = LINE_REGEX.matchEntire(line) ?: return null
        var text = match.groupValues[3]
        val agent = AGENT_REGEX.find(text)?.groupValues?.get(1)
        if (agent != null) text = text.replaceFirst(AGENT_REGEX, "")
        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) text = text.replaceFirst(BACKGROUND_REGEX, "")
        text = text.trim()
        return TIME_REGEX.findAll(match.groupValues[1]).map { t ->
            LyricLine(toMs(t.groupValues[1], t.groupValues[2], t.groupValues[3]), text, words, isBackground, agent)
        }.toList()
    }

    private fun toMs(min: String, sec: String, fraction: String): Long {
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return (min.toLongOrNull() ?: 0L) * 60_000L + (sec.toLongOrNull() ?: 0L) * 1000L + millis
    }

    private fun decodeHtmlEntities(text: String): String =
        text
            .replace(HEX_ENTITY_REGEX) { m ->
                m.groupValues[1].toIntOrNull(16)?.takeIf { it in 0..0x10FFFF }
                    ?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace(DEC_ENTITY_REGEX) { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 0..0x10FFFF }
                    ?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
}
