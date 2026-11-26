package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.music.desktop.system.UpdateInfo
import echo.music.desktop.ui.theme.NothingColors

/** "New version available" card shown over the main window. */
@Composable
fun UpdateBanner(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .width(340.dp)
            .clip(shape)
            .background(NothingColors.SurfaceElevated)
            .border(1.dp, NothingColors.SurfaceBorder, shape)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = NothingColors.AccentRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Echo Music ${info.version} disponible",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
            )
        }
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                info.notes,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) { Text("Omitir versión", color = NothingColors.TextMuted) }
            TextButton(onClick = onLater) { Text("Más tarde", color = NothingColors.TextSecondary) }
            TextButton(onClick = onDownload) { Text("Descargar", color = NothingColors.AccentRed) }
        }
    }
}
