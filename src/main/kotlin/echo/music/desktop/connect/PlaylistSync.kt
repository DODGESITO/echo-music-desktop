package echo.music.desktop.connect

import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.SavedSong
import echo.music.desktop.state.UserPlaylist

/**
 * Playlists created in Echo Music (not YouTube/Spotify copies) travel between the PC and the phone.
 * Like Android, a playlist is matched by its title and songs are only added, never removed, so a
 * sync can never make either side lose songs.
 */
object PlaylistSync {
    fun outgoing(playlists: List<UserPlaylist>, now: Long): List<PlaylistSyncDto> =
        playlists.filter { it.sourceType == PlaylistSourceType.LOCAL }.map { playlist ->
            PlaylistSyncDto(
                syncId = "desktop:${playlist.id}",
                title = playlist.title,
                thumbnail = playlist.thumbnailUrl,
                isSpotify = false,
                updatedAtMs = now,
                tracks = playlist.songs.map { it.toTrackDto() }
            )
        }

    /**
     * Returns the playlists that changed (new ones included), ready to be saved. Songs without a
     * YouTube id cannot be played here and are skipped.
     */
    fun merge(
        existing: List<UserPlaylist>,
        incoming: List<PlaylistSyncDto>,
        author: String,
        now: Long
    ): List<UserPlaylist> {
        val byTitle = existing.filter { it.sourceType == PlaylistSourceType.LOCAL }
            .associateBy { it.title.trim().lowercase() }.toMutableMap()
        val changed = LinkedHashMap<String, UserPlaylist>()
        incoming.filterNot { it.isSpotify }.forEachIndexed { i, remote ->
            val title = remote.title.trim()
            if (title.isEmpty()) return@forEachIndexed
            val key = title.lowercase()
            val songs = dedupeTracks(remote.tracks).filter { it.videoId.isNotBlank() }
            val current = byTitle[key]
            if (current == null) {
                val created = UserPlaylist(
                    id = "connect_${now}_$i",
                    title = title,
                    author = author,
                    thumbnailUrl = remote.thumbnail ?: songs.firstOrNull()?.thumbnail,
                    sourceType = PlaylistSourceType.LOCAL,
                    savedSongs = songs.map { SavedSong.fromSongItem(it.toSongItem()) }
                )
                byTitle[key] = created
                changed[created.id] = created
            } else {
                val known = current.savedSongs.mapTo(HashSet()) { it.id }
                val added = songs.filter { known.add(it.videoId) }
                if (added.isNotEmpty()) {
                    val updated = current.copy(
                        savedSongs = current.savedSongs + added.map { SavedSong.fromSongItem(it.toSongItem()) },
                        thumbnailUrl = current.thumbnailUrl ?: remote.thumbnail
                    )
                    byTitle[key] = updated
                    changed[updated.id] = updated
                }
            }
        }
        return changed.values.toList()
    }
}
