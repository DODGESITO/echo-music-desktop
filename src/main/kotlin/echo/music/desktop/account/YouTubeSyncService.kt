package echo.music.desktop.account

import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import echo.music.desktop.state.AccountStore
import echo.music.desktop.state.FavoritesStore
import echo.music.desktop.state.PlaylistSourceType
import echo.music.desktop.state.PlaylistStore
import echo.music.desktop.state.UserAccountData
import echo.music.desktop.state.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YouTubeSyncService {

    suspend fun loginWithCookie(cookie: String): Result<UserAccountData> = withContext(Dispatchers.IO) {
        val cleanCookie = cookie.trim()
        if (cleanCookie.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("La cookie de YouTube Music no puede estar vacía."))
        }

        try {
            YouTube.cookie = cleanCookie
            val accountInfoResult = YouTube.accountInfo()
            if (accountInfoResult.isSuccess) {
                val info = accountInfoResult.getOrThrow()
                val account = UserAccountData(
                    name = info.name,
                    email = info.email,
                    channelHandle = info.channelHandle,
                    avatarUrl = info.thumbnailUrl,
                    cookie = cleanCookie
                )
                AccountStore.saveYouTubeAccount(account)
                Result.success(account)
            } else {
                val err = accountInfoResult.exceptionOrNull()?.message ?: "Cookie inválida o sesión expirada"
                Result.failure(Exception("Error de autenticación con YouTube Music: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccountInfo(): Result<UserAccountData> = withContext(Dispatchers.IO) {
        val account = AccountStore.getYouTubeAccount() ?: return@withContext Result.failure(Exception("No account saved"))
        if (account.cookie.isBlank()) return@withContext Result.failure(Exception("No cookie saved"))
        YouTube.cookie = account.cookie
        try {
            val accountInfoResult = YouTube.accountInfo()
            if (accountInfoResult.isSuccess) {
                val info = accountInfoResult.getOrThrow()
                val updated = account.copy(
                    name = info.name,
                    email = info.email,
                    channelHandle = info.channelHandle,
                    avatarUrl = info.thumbnailUrl
                )
                AccountStore.saveYouTubeAccount(updated)
                Result.success(updated)
            } else {
                Result.failure(accountInfoResult.exceptionOrNull() ?: Exception("Error fetching account info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all songs in a playlist with robust pagination and graceful error recovery.
     * Keeps all songs gathered so far if an individual continuation fails.
     */
    suspend fun fetchPlaylistSongs(playlistId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("yt_").removePrefix("VL")
        try {
            val firstPageResult = YouTube.playlist(cleanId)
            if (firstPageResult.isFailure) {
                println("[YouTubeSync] Failed to fetch playlist $cleanId: ${firstPageResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            val firstPage = firstPageResult.getOrThrow()
            val allSongs = firstPage.songs.toMutableList()
            var continuation = firstPage.songsContinuation
            val seen = mutableSetOf<String>()
            var pageCount = 0

            while (continuation != null && pageCount < 50) {
                if (continuation in seen) break
                seen.add(continuation)
                pageCount++

                val continuationResult = YouTube.playlistContinuation(continuation)
                if (continuationResult.isSuccess) {
                    val contPage = continuationResult.getOrThrow()
                    if (contPage.songs.isNotEmpty()) {
                        allSongs.addAll(contPage.songs)
                        continuation = contPage.continuation
                    } else {
                        break
                    }
                } else {
                    println("[YouTubeSync] Continuation $pageCount for $cleanId stopped: ${continuationResult.exceptionOrNull()?.message}. Preserving ${allSongs.size} songs already fetched.")
                    break
                }
            }
            println("[YouTubeSync] Successfully fetched ${allSongs.size} songs for playlist $cleanId")
            allSongs
        } catch (e: Exception) {
            println("[YouTubeSync] Error in fetchPlaylistSongs($cleanId): ${e.message}")
            emptyList()
        }
    }

    suspend fun syncPlaylists(
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null
    ): Result<List<UserPlaylist>> = withContext(Dispatchers.IO) {
        val account = AccountStore.getYouTubeAccount()
        if (account == null || account.cookie.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No hay ninguna cuenta de YouTube Music conectada."))
        }

        YouTube.cookie = account.cookie

        // Refresh account info (including avatar) in background
        try {
            refreshAccountInfo()
        } catch (ignored: Exception) {}

        try {
            // Fetch playlists from library
            val libResult = YouTube.library("FEmusic_liked_playlists").completed()
            val items = if (libResult.isSuccess) {
                libResult.getOrNull()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            } else {
                emptyList()
            }

            // Exclude special system endpoints (Liked Music 'LM' and Episodes 'SE')
            val filteredItems = items.filterNot { it.id == "LM" || it.id == "SE" }
            val syncedPlaylists = mutableListOf<UserPlaylist>()

            // If remote library returned playlists, sync them
            if (filteredItems.isNotEmpty()) {
                val total = filteredItems.size
                for ((index, item) in filteredItems.withIndex()) {
                    onProgress?.invoke(index + 1, total, item.title)
                    try {
                        val fetchedSongs = fetchPlaylistSongs(item.id)
                        val existingPlaylist = PlaylistStore.getPlaylist("yt_${item.id}")

                        // SAFETY: Never wipe out an existing non-empty playlist if remote fetch returned empty
                        val finalSongs = if (fetchedSongs.isNotEmpty()) {
                            fetchedSongs
                        } else if (existingPlaylist != null && existingPlaylist.songs.isNotEmpty()) {
                            println("[YouTubeSync] Remote returned 0 songs for ${item.title}, keeping ${existingPlaylist.songs.size} existing songs")
                            existingPlaylist.songs
                        } else {
                            emptyList()
                        }

                        val thumb = if (!item.thumbnail.isNullOrBlank()) item.thumbnail else existingPlaylist?.thumbnailUrl

                        val playlist = UserPlaylist.fromSongs(
                            id = "yt_${item.id}",
                            title = item.title,
                            author = item.author?.name ?: account.name,
                            thumbnailUrl = thumb,
                            sourceType = PlaylistSourceType.YOUTUBE_MUSIC,
                            songs = finalSongs
                        )
                        PlaylistStore.savePlaylist(playlist)
                        syncedPlaylists.add(playlist)
                    } catch (e: Exception) {
                        println("[YouTubeSync] Error syncing playlist ${item.title}: ${e.message}")
                    }
                }
            } else {
                // If library endpoint returned empty (e.g. user only has created playlists or structure changed),
                // refresh all previously known YouTube playlists in local store
                val localYtPlaylists = PlaylistStore.getPlaylists().filter { it.sourceType == PlaylistSourceType.YOUTUBE_MUSIC && it.id != "yt_LM" && it.id != "yt_SE" }
                val total = localYtPlaylists.size
                for ((index, pl) in localYtPlaylists.withIndex()) {
                    onProgress?.invoke(index + 1, total, pl.title)
                    val ytId = pl.id.removePrefix("yt_")
                    val fetchedSongs = fetchPlaylistSongs(ytId)
                    if (fetchedSongs.isNotEmpty()) {
                        val updated = UserPlaylist.fromSongs(
                            id = pl.id,
                            title = pl.title,
                            author = pl.author,
                            thumbnailUrl = pl.thumbnailUrl,
                            sourceType = pl.sourceType,
                            songs = fetchedSongs
                        )
                        PlaylistStore.savePlaylist(updated)
                        syncedPlaylists.add(updated)
                    } else {
                        syncedPlaylists.add(pl)
                    }
                }
            }

            // Also sync Liked Music (LM)
            try {
                val likedSongs = fetchPlaylistSongs("LM")
                for (song in likedSongs) {
                    FavoritesStore.addFavorite(song)
                }
                println("[YouTubeSync] Synced ${likedSongs.size} liked songs into favorites")
            } catch (ignored: Exception) {}

            Result.success(syncedPlaylists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
