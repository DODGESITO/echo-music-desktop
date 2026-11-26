package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.state.LibrarySubTab
import echo.music.desktop.state.NavigationTab
import echo.music.desktop.state.UserAccountData
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun Sidebar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    currentLibrarySubTab: LibrarySubTab? = null,
    onSelectLibrarySubTab: (LibrarySubTab) -> Unit = {},
    favoritesCount: Int,
    historyCount: Int,
    playlistsCount: Int = 0,
    downloadsCount: Int = 0,
    localCount: Int = 0,
    userAccount: UserAccountData? = null,
    onOpenLoginModal: () -> Unit = {},
    onOpenStatsModal: () -> Unit = {},
    isListenTogetherInRoom: Boolean = false,
    listenTogetherRoomCode: String? = null,
    onOpenListenTogetherModal: () -> Unit = {},
    onOpenEchoFind: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(NothingColors.Surface)
            .border(
                width = 1.dp,
                color = NothingColors.SurfaceBorder,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(18.dp)
    ) {
        // App Logo & Dot Matrix branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 28.dp, top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NothingColors.AccentRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NothingColors.AccentRed)
                    )
                }
                Text(
                    text = "DESKTOP • NOTHING 5.0",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = NothingColors.TextMuted,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        // Section header
        Text(
            text = "MENU",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NothingColors.TextMuted,
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Main Navigation tabs
        NavigationTab.entries.forEach { tab ->
            val isSelected = currentTab == tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) NothingColors.SurfaceElevated else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.title,
                    tint = if (isSelected) NothingColors.AccentRed else NothingColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NothingColors.AccentRed)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick library stats
        Text(
            text = "TU COLECCIÓN",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NothingColors.TextMuted,
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Quick shortcut: Favorites
        CollectionRow(
            title = "Favoritos",
            count = favoritesCount,
            isSelected = currentTab == NavigationTab.LIBRARY && currentLibrarySubTab == LibrarySubTab.FAVORITES,
            onClick = { onSelectLibrarySubTab(LibrarySubTab.FAVORITES) }
        )

        // Quick shortcut: History
        CollectionRow(
            title = "Historial",
            count = historyCount,
            isSelected = currentTab == NavigationTab.LIBRARY && currentLibrarySubTab == LibrarySubTab.HISTORY,
            onClick = { onSelectLibrarySubTab(LibrarySubTab.HISTORY) }
        )

        // Quick shortcut: Playlists
        CollectionRow(
            title = "Listas",
            count = playlistsCount,
            isSelected = currentTab == NavigationTab.LIBRARY && currentLibrarySubTab == LibrarySubTab.PLAYLISTS,
            onClick = { onSelectLibrarySubTab(LibrarySubTab.PLAYLISTS) }
        )

        // Quick shortcut: Descargas
        CollectionRow(
            title = "Descargas",
            count = downloadsCount,
            isSelected = currentTab == NavigationTab.LIBRARY && currentLibrarySubTab == LibrarySubTab.DOWNLOADS,
            onClick = { onSelectLibrarySubTab(LibrarySubTab.DOWNLOADS) }
        )

        // Quick shortcut: música local
        CollectionRow(
            title = "En este PC",
            count = localCount,
            isSelected = currentTab == NavigationTab.LIBRARY && currentLibrarySubTab == LibrarySubTab.LOCAL,
            onClick = { onSelectLibrarySubTab(LibrarySubTab.LOCAL) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Echo Wrapped (Stats Modal) shortcut button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NothingColors.SurfaceElevated)
                .border(
                    1.dp,
                    NothingColors.AccentRed.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onOpenStatsModal)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Estadísticas",
                        tint = NothingColors.AccentRed,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Echo Wrapped",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingColors.AccentRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "STATS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Listen Together shortcut button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NothingColors.SurfaceElevated)
                .border(
                    1.dp,
                    if (isListenTogetherInRoom) Color(0xFF4CAF50).copy(alpha = 0.5f) else NothingColors.AccentRed.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onOpenListenTogetherModal)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "Escuchar Juntos",
                        tint = if (isListenTogetherInRoom) Color(0xFF4CAF50) else NothingColors.AccentRed,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Escuchar Juntos",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isListenTogetherInRoom) Color(0xFF4CAF50) else NothingColors.AccentRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isListenTogetherInRoom) (listenTogetherRoomCode ?: "EN VIVO") else "SYNC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Echo Find (song recognition) shortcut button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.AccentRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenEchoFind)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Echo Find",
                    tint = NothingColors.AccentRed,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Echo Find",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NothingColors.TextPrimary,
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom User Profile Card (replaces "Motor Activo")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
                .clickable {
                    if (userAccount != null) {
                        onTabSelected(NavigationTab.SETTINGS)
                    } else {
                        onOpenLoginModal()
                    }
                }
                .padding(10.dp)
        ) {
            if (userAccount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar Image with fallback
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NothingColors.Surface)
                            .border(1.5.dp, NothingColors.AccentRed.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userAccount.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userAccount.avatarUrl,
                                contentDescription = userAccount.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userAccount.name.take(1).uppercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.AccentRed,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userAccount.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                        Text(
                            text = userAccount.channelHandle ?: userAccount.email ?: "YouTube Music",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NothingColors.TextMuted,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NothingColors.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = NothingColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Iniciar Sesión",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Conectar YouTube Music",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NothingColors.TextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NothingColors.SurfaceElevated else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(NothingColors.AccentRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                    fontSize = 13.sp
                )
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) NothingColors.AccentRed else NothingColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
