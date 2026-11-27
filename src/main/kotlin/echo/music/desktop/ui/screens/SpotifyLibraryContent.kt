package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

private val SpotifyGreenColor = Color(0xFF1DB954)

/** Biblioteca → Spotify: sign in, then the account's playlists and "Tus me gusta". */
@Composable
fun SpotifyLibraryContent(appModel: DesktopAppModel, modifier: Modifier = Modifier) {
    val profile by appModel.spotifyProfile.collectAsState()
    val playlists by appModel.spotifyPlaylists.collectAsState()
    val likedCount by appModel.spotifyLikedCount.collectAsState()
    val loading by appModel.spotifyLoading.collectAsState()
    val status by appModel.spotifyStatus.collectAsState()
    val signingIn by appModel.spotifySigningIn.collectAsState()
    @Suppress("UNUSED_VARIABLE") val userPlaylists by appModel.userPlaylists.collectAsState() // refresh "En tu biblioteca"

    if (profile == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 460.dp)) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(SpotifyGreenColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LibraryMusic, null, tint = SpotifyGreenColor, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Conecta tu cuenta de Spotify", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Verás tus playlists y tus Me gusta de Spotify, y podrás escucharlos aquí: cada canción se busca en YouTube Music. " +
                        "También se activan los Canvas de Spotify en el reproductor.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { appModel.signInSpotify() },
                    enabled = !signingIn,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreenColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(if (signingIn) "Esperando el inicio de sesión…" else "Iniciar sesión con Spotify", fontWeight = FontWeight.Bold)
                }
                if (signingIn) {
                    TextButton(onClick = { appModel.cancelSpotifySignIn() }) { Text("Cancelar", color = NothingColors.TextSecondary) }
                }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextMuted))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Se abre una ventana de Edge solo para Spotify. Echo Music guarda la sesión cifrada con Windows y nunca ve tu contraseña.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted)
                )
            }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = profile?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(SpotifyGreenColor.copy(alpha = 0.3f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.name ?: "Spotify", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
                Text(
                    "${playlists.size} playlists · ${likedCount} Me gusta · al abrir una, sus canciones se buscan en YouTube Music",
                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                )
            }
            if (loading) CircularProgressIndicator(color = SpotifyGreenColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            IconButton(onClick = { appModel.loadSpotifyLibrary() }) {
                Icon(Icons.Default.Refresh, "Actualizar", tint = NothingColors.TextSecondary)
            }
            TextButton(onClick = { appModel.signOutSpotify() }) { Text("Cerrar sesión", color = NothingColors.TextMuted) }
        }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.AccentRed))
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SpotifyCard(
                    title = "Tus me gusta",
                    subtitle = "$likedCount canciones",
                    imageUrl = null,
                    liked = true,
                    imported = appModel.importedSpotifyPlaylist(echo.music.desktop.spotify.SpotifyLibrary.LIKED_ID) != null,
                    onOpen = { appModel.openSpotifyPlaylist(echo.music.desktop.spotify.SpotifyLibrary.LIKED_ID, "Tus me gusta de Spotify", profile?.name, null) },
                    onRefresh = { appModel.openSpotifyPlaylist(echo.music.desktop.spotify.SpotifyLibrary.LIKED_ID, "Tus me gusta de Spotify", profile?.name, null, refresh = true) }
                )
            }
            items(playlists, key = { it.id }) { p ->
                val image = p.images.firstOrNull()?.url
                SpotifyCard(
                    title = p.name,
                    subtitle = listOfNotNull(p.owner?.displayName?.let { "De $it" }, p.tracks?.total?.let { "$it canciones" }).joinToString(" · "),
                    imageUrl = image,
                    liked = false,
                    imported = appModel.importedSpotifyPlaylist(p.id) != null,
                    onOpen = { appModel.openSpotifyPlaylist(p.id, p.name, p.owner?.displayName, image) },
                    onRefresh = { appModel.openSpotifyPlaylist(p.id, p.name, p.owner?.displayName, image, refresh = true) }
                )
            }
        }
    }
}

@Composable
private fun SpotifyCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    liked: Boolean,
    imported: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
            if (liked) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(NothingColors.SurfaceElevated)
                )
            }
            if (imported) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Sync, "Volver a traer de Spotify", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
        Text(
            if (imported) "En tu biblioteca · $subtitle" else subtitle,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(color = if (imported) SpotifyGreenColor else NothingColors.TextSecondary)
        )
    }
}
