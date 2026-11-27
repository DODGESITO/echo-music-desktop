package echo.music.desktop.connect

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Echo Connect wire format, shared with Echo Music for Android (LanSyncManager) and the official
 * Echo Music Desktop (internal/lansync). Field names must match theirs exactly.
 */

internal val connectJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class TrackDto(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val durationSec: Int = 0,
    val thumbnail: String? = null,
    val isPodcast: Boolean = false
)

@Serializable
data class EpisodeDto(
    val videoId: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val finished: Boolean = false,
    val updatedAt: Long = 0,
    val title: String? = null,
    val show: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class PeerStateDto(
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "desktop",
    val playing: Boolean = false,
    val volume: Double = 1.0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "off",
    val current: TrackDto? = null,
    val queue: List<TrackDto> = emptyList(),
    val index: Int = 0,
    val source: String? = null,
    val episodes: List<EpisodeDto> = emptyList(),
    val updatedAt: Long = 0
)

@Serializable
data class ControlCommand(
    /** play, pause, toggle, next, previous, seek, volume, play_index, queue_add, shuffle, repeat */
    val action: String = "",
    val positionMs: Long = 0,
    val volume: Double = 0.0,
    val index: Int = 0,
    val shuffle: Boolean? = null,
    val repeat: String? = null,
    val controllerPeerId: String? = null,
    val item: TrackDto? = null
)

@Serializable
data class HandoffPayload(
    val items: List<TrackDto> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val source: String? = null,
    val playImmediately: Boolean = true,
    val episodes: List<EpisodeDto> = emptyList()
)

@Serializable
data class EpisodesSyncPayload(val episodes: List<EpisodeDto> = emptyList())

@Serializable
data class PairStartPayload(
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val host: String? = null,
    val port: Int = 0,
    val expiresAtMs: Long = 0
)

@Serializable
data class PairVerifyRequest(val deviceId: String = "", val deviceName: String = "", val code: String = "")

@Serializable
data class PairVerifyResponse(val ok: Boolean = false, val pairToken: String? = null, val error: String? = null)

@Serializable
data class UnpairPayload(val deviceId: String = "")

@Serializable
data class AccountSyncDto(
    val cookie: String? = null,
    val name: String? = null,
    val email: String? = null,
    val channelHandle: String? = null,
    val updatedAtMs: Long = 0
)

@Serializable
data class PlaylistSyncDto(
    val syncId: String = "",
    val title: String = "",
    val thumbnail: String? = null,
    val isSpotify: Boolean = false,
    val updatedAtMs: Long = 0,
    val tracks: List<TrackDto> = emptyList()
)

@Serializable
data class SyncBundle(
    val senderId: String = "",
    val senderName: String = "",
    val sentAtMs: Long = 0,
    val googleAccount: AccountSyncDto? = null,
    val localPlaylists: List<PlaylistSyncDto> = emptyList(),
    val spotifyPlaylists: List<PlaylistSyncDto> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList()
)

@Serializable
data class OkResponse(val ok: Boolean = true)

/** A device found on the network (mDNS) or that asked to pair. */
data class ConnectPeer(
    val id: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int
) {
    val baseUrl: String get() = "http://${if (':' in host) "[$host]" else host}:$port"
    val isPhone: Boolean get() = platform.equals("android", ignoreCase = true)
}

fun SongItem.toTrackDto(): TrackDto = TrackDto(
    videoId = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name,
    durationSec = duration ?: 0,
    thumbnail = thumbnail.takeIf { it.isNotBlank() },
    isPodcast = isPodcastEpisode
)

fun TrackDto.toSongItem(): SongItem = SongItem(
    id = videoId,
    title = title.ifBlank { videoId },
    artists = listOf(Artist(name = artist.ifBlank { "Artista desconocido" }, id = null)),
    duration = durationSec.takeIf { it > 0 },
    thumbnail = thumbnail.orEmpty(),
    musicVideoType = if (isPodcast) com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_PODCAST_EPISODE else null
)

/** Songs of a playlist without repeats (by video id, or title + artist when there is none). */
internal fun dedupeTracks(tracks: List<TrackDto>): List<TrackDto> {
    val seen = HashSet<String>()
    return tracks.filter { t ->
        val key = t.videoId.ifBlank { t.title.trim().lowercase() + "|" + t.artist.trim().lowercase() }
        key != "|" && key.isNotBlank() && seen.add(key)
    }
}
