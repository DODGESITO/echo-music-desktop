package echo.music.desktop.importer

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.PlaylistStore
import echo.music.desktop.state.UserPlaylist
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class SpotifyRawTrack(
    val title: String,
    val artist: String,
    val durationMs: Long
)

data class SpotifyRawPlaylist(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val tracks: List<SpotifyRawTrack>
)

object SpotifyImporter {
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parsePlaylistId(input: String): String? {
        val trimmed = input.trim()
        val urlRegex = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
        val uriRegex = Regex("""spotify:playlist:([a-zA-Z0-9]+)""")
        val idRegex = Regex("""^[a-zA-Z0-9]{22}$""")

        val urlMatch = urlRegex.find(trimmed)
        if (urlMatch != null) return urlMatch.groupValues[1]

        val uriMatch = uriRegex.find(trimmed)
        if (uriMatch != null) return uriMatch.groupValues[1]

        if (idRegex.matches(trimmed)) return trimmed

        return null
    }

    suspend fun fetchRawPlaylist(playlistId: String): Result<SpotifyRawPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val html = client.get(embedUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }.bodyAsText()

            val scriptRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = scriptRegex.find(html) ?: throw IllegalStateException("No se pudo obtener datos de la playlist de Spotify")
            val jsonText = match.groupValues[1]

            val root = json.parseToJsonElement(jsonText).jsonObject
            val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
            val state = props?.get("state")?.jsonObject?.get("data")?.jsonObject
            val entity = state?.get("entity")?.jsonObject ?: throw IllegalStateException("Playlist no encontrada o es privada")

            val title = entity["name"]?.jsonPrimitive?.contentOrNull ?: "Playlist de Spotify"
            val author = entity["subtitle"]?.jsonPrimitive?.contentOrNull ?: "Spotify"
            val coverArt = entity["coverArt"]?.jsonObject?.get("sources")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

            val trackListElement = entity["trackList"]?.jsonArray.orEmpty()
            val tracks = mutableListOf<SpotifyRawTrack>()

            for (elem in trackListElement) {
                val tObj = elem.jsonObject
                val tTitle = tObj["title"]?.jsonPrimitive?.contentOrNull ?: continue
                val tArtist = tObj["subtitle"]?.jsonPrimitive?.contentOrNull ?: "Artista desconocido"
                val tDuration = tObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L
                tracks.add(SpotifyRawTrack(tTitle, tArtist, tDuration))
            }

            SpotifyRawPlaylist(
                id = playlistId,
                title = title,
                author = author,
                thumbnailUrl = coverArt,
                tracks = tracks
            )
        }
    }

    suspend fun importPlaylist(
        urlOrId: String,
        onProgress: (current: Int, total: Int, songName: String) -> Unit
    ): Result<UserPlaylist> = withContext(Dispatchers.IO) {
        val playlistId = parsePlaylistId(urlOrId) ?: return@withContext Result.failure(
            IllegalArgumentException("Enlace de Spotify no válido. Debe ser del formato https://open.spotify.com/playlist/...")
        )

        val rawResult = fetchRawPlaylist(playlistId)
        if (rawResult.isFailure) {
            return@withContext Result.failure(rawResult.exceptionOrNull() ?: Exception("Error al consultar Spotify"))
        }

        val rawPlaylist = rawResult.getOrThrow()
        val total = rawPlaylist.tracks.size
        val matchedSongs = mutableListOf<SongItem>()

        for ((idx, track) in rawPlaylist.tracks.withIndex()) {
            onProgress(idx + 1, total, "${track.title} - ${track.artist}")
            try {
                val query = "${track.title} ${track.artist}".trim()
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                if (searchResult.isSuccess) {
                    val candidate = searchResult.getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                    if (candidate != null) {
                        matchedSongs.add(candidate)
                    }
                }
            } catch (ignored: Exception) {}
        }

        val userPlaylist = UserPlaylist.fromSongs(
            id = "spotify_$playlistId",
            title = rawPlaylist.title,
            author = "Spotify • ${rawPlaylist.author}",
            thumbnailUrl = rawPlaylist.thumbnailUrl,
            sourceType = PlaylistSourceType.SPOTIFY,
            songs = matchedSongs
        )

        PlaylistStore.savePlaylist(userPlaylist)
        Result.success(userPlaylist)
    }
}
