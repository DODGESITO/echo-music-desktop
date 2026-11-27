package echo.music.desktop.recommend

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.desktop.lyrics.TranslationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The user's own AI account (the one set up for lyrics translation). */
data class AiAccount(val provider: TranslationProvider, val apiKey: String, val model: String, val baseUrl: String)

data class AiSongIdea(val title: String, val artist: String)

/**
 * AI playlists like the phone app (AiPlaylistGenerator, AiRecommendationHelper, AiPlaylistModifier):
 * the AI suggests "title - artist" pairs and each one is looked up on YouTube Music.
 */
object AiPlaylists {
    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build() }

    fun unsupportedReason(account: AiAccount): String? = when {
        account.apiKey.isBlank() -> "Falta la clave de IA. Configúrala en Ajustes → Letras: romanización y traducción."
        account.provider == TranslationProvider.DEEPL -> "DeepL solo traduce. Para crear playlists elige OpenRouter o Mistral en Ajustes."
        else -> null
    }

    // ------------------------------------------------------------------ the three features

    data class Generated(val name: String, val songs: List<SongItem>)

    /** "Crear playlist con IA": a mood or idea in your own words. */
    suspend fun generate(account: AiAccount, prompt: String, count: Int, log: suspend (String) -> Unit): Result<Generated> = runCatching {
        val system = """
            You are a highly accurate music curator and historian. The user will ask for an AI playlist.
            You must output ONLY a valid JSON object with a creative playlist name (in the same language as the user's request) and a list of exactly $count songs.

            CRITICAL RULES:
            1. YOU MUST VERIFY THE RELEASE YEAR, MOVIE, AND ARTIST for EVERY SINGLE TRACK.
            2. Include songs that EXACTLY match the user's prompt.
            3. You MUST output ONLY raw JSON. Do NOT include markdown formatting (like ```json), explanations, or conversational text.

            Example format:
            {
              "name": "Creative Playlist Name",
              "songs": [
                {"title": "Song Name", "artist": "Artist Name"}
              ]
            }
        """.trimIndent()
        log("Pidiendo ideas a la IA…")
        val answer = chat(account, system, "Mood: ${prompt.trim()}\nDesired playlist length: $count")
        log("Leyendo la respuesta…")
        val obj = parseJson(answer).jsonObject
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Playlist con IA"
        val ideas = songIdeas(obj["songs"]?.jsonArray ?: error("La IA no devolvió canciones"))
        Generated(name, resolve(ideas, log))
    }

    /** "De tus gustos": what you listen to most and like, and 20 similar songs. */
    suspend fun fromTaste(account: AiAccount, taste: List<SongItem>, log: suspend (String) -> Unit): Result<List<SongItem>> = runCatching {
        if (taste.isEmpty()) error("Todavía no hay suficiente historial. Escucha y marca algunas canciones con Me gusta.")
        log("Analizando lo que escuchas…")
        val list = taste.take(30).joinToString("\n") { s -> "${s.title} by ${s.artists.joinToString { it.name }}" }
        val prompt = """
            Based on the following list of songs the user likes:
            $list

            Please recommend 20 similar but different songs.
            CRITICAL RULES:
            1. You MUST output ONLY a valid JSON array of objects.
            2. Do NOT include any markdown formatting (like ```json).
            3. Do NOT include any conversational text or explanations. Just the raw JSON array.

            Example format:
            [
              {"title": "Song Name", "artist": "Artist Name"}
            ]
        """.trimIndent()
        log("Pidiendo recomendaciones a la IA…")
        val answer = chat(account, null, prompt)
        log("Leyendo la respuesta…")
        val known = taste.map { it.id }.toSet()
        resolve(songIdeas(parseJson(answer).jsonArray), log).filterNot { it.id in known }
    }

    data class Modification(val removeIndexes: Set<Int>, val additions: List<SongItem>)

    /** "Modificar con IA": e.g. "quita las tristes y añade éxitos de los 90". */
    suspend fun modify(account: AiAccount, songs: List<SongItem>, instruction: String, log: suspend (String) -> Unit): Result<Modification> = runCatching {
        val system = """
            You are a highly accurate and strict music historian. The user wants to modify an existing playlist based on a specific prompt.
            The current playlist is given as a JSON array of objects with "id", "title" and "artist".
            You must output ONLY a valid JSON object containing exactly two arrays: "remove_ids" (list of ids to remove) and "additions" (list of objects with "title" and "artist").

            CRITICAL RULES:
            1. YOU MUST VERIFY THE RELEASE YEAR, MOVIE, AND ARTIST for EVERY SINGLE TRACK you add or remove.
            2. Only remove songs that match the user's removal instruction.
            3. For additions, ONLY include songs that match the user's addition instruction.
            4. You MUST output ONLY raw JSON. Do NOT include any markdown formatting (like ```json), explanations, or conversational text.

            Example format:
            {
              "remove_ids": [3, 7],
              "additions": [{"title": "Song Name", "artist": "Artist Name"}]
            }
        """.trimIndent()
        val current = buildJsonArray {
            songs.forEachIndexed { i, s ->
                add(buildJsonObject {
                    put("id", i)
                    put("title", s.title)
                    put("artist", s.artists.joinToString { it.name })
                })
            }
        }
        log("Pidiendo los cambios a la IA…")
        val answer = chat(account, system, "Playlist: $current\nInstruction: ${instruction.trim()}")
        log("Leyendo la respuesta…")
        val obj = parseJson(answer).jsonObject
        val remove = obj["remove_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.filter { it in songs.indices }?.toSet().orEmpty()
        val ideas = obj["additions"]?.jsonArray?.let(::songIdeas).orEmpty()
        val keep = songs.filterIndexed { i, _ -> i !in remove }.map { it.id }.toSet()
        Modification(remove, resolve(ideas, log).filterNot { it.id in keep })
    }

    // ------------------------------------------------------------------ helpers

    /** Each idea → the first song result on YouTube Music. */
    private suspend fun resolve(ideas: List<AiSongIdea>, log: suspend (String) -> Unit): List<SongItem> = withContext(Dispatchers.IO) {
        val found = mutableListOf<SongItem>()
        ideas.forEachIndexed { i, idea ->
            log("Buscando ${i + 1}/${ideas.size}: ${idea.title} - ${idea.artist}")
            YouTube.search("${idea.title} ${idea.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                ?.let { song -> if (found.none { it.id == song.id }) found += song }
        }
        if (found.isEmpty()) error("No se encontró ninguna de las canciones sugeridas.")
        found
    }

    internal fun songIdeas(array: JsonArray): List<AiSongIdea> = array.mapNotNull { el ->
        val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
        val title = o["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val artist = o["artist"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) null else AiSongIdea(title, artist)
    }

    /** Models often wrap the JSON in ``` fences or add a sentence around it. */
    internal fun parseJson(text: String): kotlinx.serialization.json.JsonElement {
        val clean = text.replace("```json", "").replace("```", "").trim()
        runCatching { return json.parseToJsonElement(clean) }
        val start = clean.indexOfFirst { it == '{' || it == '[' }
        val end = clean.indexOfLast { it == '}' || it == ']' }
        if (start >= 0 && end > start) runCatching { return json.parseToJsonElement(clean.substring(start, end + 1)) }
        error("La IA respondió algo que no se pudo leer. Prueba otra vez u otro modelo.")
    }

    private suspend fun chat(account: AiAccount, system: String?, user: String): String = withContext(Dispatchers.IO) {
        unsupportedReason(account)?.let { error(it) }
        val url = when (account.provider) {
            TranslationProvider.MISTRAL -> MISTRAL_URL
            else -> account.baseUrl.ifBlank { OPENROUTER_URL }
        }
        if (!echo.music.iad1tya.api.OpenRouterService.isUrlSafe(url)) error("Dirección de IA no permitida")
        val body = buildJsonObject {
            put("model", account.model.ifBlank { account.provider.defaultModel })
            put("messages", buildJsonArray {
                if (system != null) add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val request = HttpRequest.newBuilder(URI(url))
            .header("Authorization", "Bearer ${account.apiKey}")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error(
                when (response.statusCode()) {
                    401, 403 -> "La IA rechazó la clave (${response.statusCode()}). Revísala en Ajustes."
                    429 -> "La IA está saturada o se acabó el límite gratis (429). Prueba en un rato."
                    else -> "La IA respondió ${response.statusCode()}."
                }
            )
        }
        val root = json.parseToJsonElement(response.body()).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?.jsonPrimitive?.contentOrNull
            ?: error("La IA no devolvió texto.")
    }

    @Suppress("unused")
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
}
