package echo.music.desktop.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationTab(val title: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    EXPLORE("Explorar", Icons.Default.Explore),
    LIBRARY("Biblioteca", Icons.Default.LibraryMusic),
    SETTINGS("Ajustes", Icons.Default.Settings)
}

enum class LibrarySubTab(val title: String) {
    FAVORITES("Favoritos"),
    HISTORY("Historial"),
    PLAYLISTS("Playlists"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    DOWNLOADS("Descargas"),
    LOCAL("En este PC"),
    SPOTIFY("Spotify")
}
