package echo.music.desktop.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A part of a video to skip, in milliseconds. */
data class SkipSegment(val startMs: Long, val endMs: Long, val category: String, val uuid: String)

/**
 * SponsorBlock (sponsor.ajay.app): community-marked parts of YouTube videos — intros, outros,
 * sponsors, self-promotion, "subscribe" reminders and non-music sections of music videos.
 * Same categories as the phone app.
 */
object SponsorBlockService {
    private val categories = listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic")
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > 200
    }

    suspend fun segmentsFor(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[videoId] }?.let { return@withContext it }
        val categoriesParam = categories.joinToString(separator = "%22,%22", prefix = "[%22", postfix = "%22]")
        val request = Request.Builder()
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categoriesParam")
            .build()
        val segments = try {
            client.newCall(request).execute().use { response ->
                // 404 = no segments for this video.
                if (!response.isSuccessful) emptyList() else parse(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            return@withContext emptyList() // network error: don't cache, try again next time
        }
        synchronized(cache) { cache[videoId] = segments }
        segments
    }

    fun parse(body: String): List<SkipSegment> = try {
        Json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            if (obj["actionType"]?.jsonPrimitive?.content.let { it != null && it != "skip" }) return@mapNotNull null
            val range = obj["segment"]?.jsonArray ?: return@mapNotNull null
            val start = range.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = range.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            SkipSegment(
                startMs = (start * 1000).toLong(),
                endMs = (end * 1000).toLong(),
                category = obj["category"]?.jsonPrimitive?.content.orEmpty(),
                uuid = obj["UUID"]?.jsonPrimitive?.content.orEmpty()
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Spanish label for the status message. */
    fun label(category: String) = when (category) {
        "intro" -> "la intro"
        "outro" -> "el final"
        "sponsor" -> "un patrocinio"
        "selfpromo" -> "una autopromoción"
        "interaction" -> "un recordatorio de suscripción"
        "music_offtopic" -> "una parte sin música"
        else -> "un segmento"
    }
}
