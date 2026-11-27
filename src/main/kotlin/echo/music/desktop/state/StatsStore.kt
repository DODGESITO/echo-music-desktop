package echo.music.desktop.state

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class SongPlayRecord(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSongItem(): SongItem {
        return SongItem(
            id = songId,
            title = title,
            artists = listOf(Artist(name = artist, id = null)),
            thumbnail = thumbnail,
            duration = durationSeconds
        )
    }
}

@Serializable
data class StatsData(
    val totalPlayTimeSeconds: Long = 0L,
    val totalPlaysCount: Int = 0,
    val songPlayCounts: Map<String, Int> = emptyMap(),
    val songCache: Map<String, SongPlayRecord> = emptyMap(),
    val artistListeningSeconds: Map<String, Long> = emptyMap(),
    val dailyPlays: Map<String, Int> = emptyMap() // YYYY-MM-DD -> count
)

data class TopArtistStat(
    val name: String,
    val playTimeMinutes: Long,
    val percentage: Float
)

data class TopSongStat(
    val song: SongItem,
    val playCount: Int
)

data class WrappedSummary(
    val totalHours: Float,
    val totalMinutes: Long,
    val totalPlays: Int,
    val uniqueSongsCount: Int,
    val uniqueArtistsCount: Int,
    val streakDays: Int,
    val topArtists: List<TopArtistStat>,
    val topSongs: List<TopSongStat>,
    val topGenreOrArtist: String
)

object StatsStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir = File(System.getProperty("user.home"), ".echomusic").apply { mkdirs() }
    private val storageFile = File(storageDir, "stats.json")

    private var currentData: StatsData = load()

    /** Re-reads stats.json (after restoring a backup). */
    @Synchronized
    fun reload() {
        currentData = load()
    }

    @Synchronized
    private fun load(): StatsData {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                json.decodeFromString<StatsData>(content)
            } else {
                StatsData()
            }
        } catch (e: Exception) {
            println("[StatsStore] Error loading stats: ${e.message}")
            StatsData()
        }
    }

    @Synchronized
    private fun save(data: StatsData) {
        currentData = data
        try {
            storageFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            println("[StatsStore] Error saving stats: ${e.message}")
        }
    }

    /** Songs played on this PC, most recent first (works even with the history paused or cleared). */
    @Synchronized
    fun recentSongs(limit: Int = 50): List<SongItem> =
        currentData.songCache.values.sortedByDescending { it.timestamp }.take(limit).map { it.toSongItem() }

    @Synchronized
    fun recordPlayback(song: SongItem, durationSeconds: Int) {
        val playedSec = durationSeconds.coerceAtLeast(1)
        val todayStr = LocalDate.now().toString()
        val artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" }

        val newPlayCounts = currentData.songPlayCounts.toMutableMap()
        newPlayCounts[song.id] = (newPlayCounts[song.id] ?: 0) + 1

        val newArtistTime = currentData.artistListeningSeconds.toMutableMap()
        newArtistTime[artistName] = (newArtistTime[artistName] ?: 0L) + playedSec

        val newDailyPlays = currentData.dailyPlays.toMutableMap()
        newDailyPlays[todayStr] = (newDailyPlays[todayStr] ?: 0) + 1

        val newCache = currentData.songCache.toMutableMap()
        newCache[song.id] = SongPlayRecord(
            songId = song.id,
            title = song.title,
            artist = artistName,
            thumbnail = song.thumbnail,
            durationSeconds = song.duration ?: durationSeconds
        )

        val updated = currentData.copy(
            totalPlayTimeSeconds = currentData.totalPlayTimeSeconds + playedSec,
            totalPlaysCount = currentData.totalPlaysCount + 1,
            songPlayCounts = newPlayCounts,
            songCache = newCache,
            artistListeningSeconds = newArtistTime,
            dailyPlays = newDailyPlays
        )
        save(updated)
    }

    @Synchronized
    fun getSummary(): WrappedSummary {
        val totalSec = currentData.totalPlayTimeSeconds
        val totalMin = totalSec / 60
        val totalHours = totalSec / 3600f

        val uniqueSongs = currentData.songPlayCounts.size
        val uniqueArtists = currentData.artistListeningSeconds.size

        // Calculate top artists
        val totalArtistSec = currentData.artistListeningSeconds.values.sum().coerceAtLeast(1L)
        val sortedArtists = currentData.artistListeningSeconds.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (name, sec) ->
                TopArtistStat(
                    name = name,
                    playTimeMinutes = sec / 60,
                    percentage = (sec.toFloat() / totalArtistSec.toFloat()).coerceIn(0f, 1f)
                )
            }

        // Calculate top songs
        val sortedSongs = currentData.songPlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { (songId, count) ->
                val cached = currentData.songCache[songId]
                if (cached != null) {
                    TopSongStat(cached.toSongItem(), count)
                } else null
            }

        // Streak calculation (consecutive days)
        val today = LocalDate.now()
        var streak = 0
        var checkDate = today
        while (currentData.dailyPlays.containsKey(checkDate.toString())) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        val topFeature = sortedArtists.firstOrNull()?.name ?: "Sin registros aún"

        return WrappedSummary(
            totalHours = String.format("%.1f", totalHours).replace(',', '.').toFloatOrNull() ?: totalHours,
            totalMinutes = totalMin,
            totalPlays = currentData.totalPlaysCount,
            uniqueSongsCount = uniqueSongs,
            uniqueArtistsCount = uniqueArtists,
            streakDays = streak,
            topArtists = sortedArtists,
            topSongs = sortedSongs,
            topGenreOrArtist = topFeature
        )
    }

    @Synchronized
    fun resetStats() {
        save(StatsData())
    }
}
