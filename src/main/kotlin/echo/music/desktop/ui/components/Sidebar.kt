package echo.music.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.state.LibrarySubTab
import echo.music.desktop.state.NavigationTab
import echo.music.desktop.state.UserAccountData
import echo.music.desktop.ui.theme.NothingColors

/**
 * Left navigation: the four sections and the "Descubre" tools. Scrolls when the window is short,
 * and in [compact] mode (narrow windows) shows icons only, with the names as tooltips.
 * (The library's own tabs live in Biblioteca; they are not repeated here.)
 */
@Composable
fun Sidebar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    @Suppress("UNUSED_PARAMETER") currentLibrarySubTab: LibrarySubTab? = null,
    @Suppress("UNUSED_PARAMETER") onSelectLibrarySubTab: (LibrarySubTab) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") favoritesCount: Int = 0,
    @Suppress("UNUSED_PARAMETER") historyCount: Int = 0,
    @Suppress("UNUSED_PARAMETER") playlistsCount: Int = 0,
    @Suppress("UNUSED_PARAMETER") downloadsCount: Int = 0,
    @Suppress("UNUSED_PARAMETER") localCount: Int = 0,
    userAccount: UserAccountData? = null,
    onOpenLoginModal: () -> Unit = {},
    onOpenStatsModal: () -> Unit = {},
    isListenTogetherInRoom: Boolean = false,
    listenTogetherRoomCode: String? = null,
    onOpenListenTogetherModal: () -> Unit = {},
    onOpenEchoFind: () -> Unit = {},
    onOpenMusicFlow: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(if (compact) 76.dp else 232.dp)
            .fillMaxHeight()
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Logo(compact)
        Spacer(Modifier.height(22.dp))

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
        ) {
            SectionLabel("MENÚ", compact)
            NavigationTab.entries.forEach { tab ->
                NavItem(tab.icon, tab.title, selected = currentTab == tab, compact = compact) { onTabSelected(tab) }
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("DESCUBRE", compact)
            NavItem(Icons.Default.Waves, "Music Flow", compact = compact, onClick = onOpenMusicFlow)
            NavItem(Icons.Default.GraphicEq, "Echo Find", compact = compact, onClick = onOpenEchoFind)
            NavItem(
                Icons.Default.Headphones,
                "Escuchar juntos",
                compact = compact,
                badge = listenTogetherRoomCode?.takeIf { isListenTogetherInRoom },
                onClick = onOpenListenTogetherModal
            )
            NavItem(Icons.AutoMirrored.Filled.TrendingUp, "Echo Wrapped", compact = compact, onClick = onOpenStatsModal)
        }

        Spacer(Modifier.height(12.dp))
        AccountCard(userAccount, compact, onOpenLoginModal)
    }
}

@Composable
private fun Logo(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (compact) 0.dp else 6.dp, top = 2.dp)) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(NothingColors.AccentRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = "Echo Music", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text(
                "ECHO",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = NothingColors.TextPrimary)
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(NothingColors.AccentRed))
        }
    }
}

@Composable
private fun SectionLabel(text: String, compact: Boolean) {
    if (compact) {
        Box(Modifier.padding(vertical = 6.dp).width(24.dp).height(1.dp).background(NothingColors.SurfaceBorder))
        return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = NothingColors.TextMuted,
            letterSpacing = 1.5.sp
        ),
        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp, top = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    compact: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val item: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth())
                .padding(vertical = 2.dp)
                .clip(shape)
                .background(if (selected) NothingColors.SurfaceElevated else Color.Transparent)
                .border(1.dp, if (selected) NothingColors.AccentRed.copy(alpha = 0.5f) else Color.Transparent, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 0.dp else 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
        ) {
            Box {
                Icon(icon, contentDescription = label, tint = if (selected) NothingColors.AccentRed else NothingColors.TextSecondary, modifier = Modifier.size(20.dp))
                if (compact && badge != null) {
                    Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(NothingColors.AccentRed))
                }
            }
            if (!compact) {
                Spacer(Modifier.width(14.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = NothingColors.AccentRed, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(NothingColors.AccentRed.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
    if (compact) {
        TooltipArea(
            tooltip = {
                Text(
                    label,
                    color = NothingColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(NothingColors.SurfaceElevated).padding(horizontal = 10.dp, vertical = 6.dp)
                )
            },
            delayMillis = 300,
            tooltipPlacement = TooltipPlacement.ComponentRect(alignment = Alignment.CenterEnd, anchor = Alignment.CenterEnd, offset = DpOffset(8.dp, 0.dp))
        ) { item() }
    } else {
        item()
    }
}

@Composable
private fun AccountCard(userAccount: UserAccountData?, compact: Boolean, onOpenLoginModal: () -> Unit) {
    Row(
        modifier = Modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(14.dp))
            .background(NothingColors.SurfaceElevated)
            .clickable(onClick = onOpenLoginModal)
            .padding(if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(NothingColors.Surface)
                .border(1.5.dp, if (userAccount != null) NothingColors.AccentRed.copy(alpha = 0.7f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                userAccount == null -> Icon(Icons.Default.Person, contentDescription = "Iniciar sesión", tint = NothingColors.TextMuted, modifier = Modifier.size(20.dp))
                !userAccount.avatarUrl.isNullOrBlank() -> AsyncImage(
                    model = userAccount.avatarUrl, contentDescription = userAccount.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                else -> Text(userAccount.name.take(1).uppercase(), color = NothingColors.AccentRed, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    userAccount?.name ?: "Iniciar sesión",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary, fontSize = 13.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    userAccount?.let { it.channelHandle ?: it.email ?: "YouTube Music" } ?: "Conectar YouTube Music",
                    style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted, fontSize = 10.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
