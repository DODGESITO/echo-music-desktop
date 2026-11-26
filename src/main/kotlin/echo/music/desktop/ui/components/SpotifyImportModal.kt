package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SpotifyImportModal(
    appModel: DesktopAppModel,
    isImporting: Boolean,
    progressText: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isImporting) onClose() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dialog Card
        Box(
            modifier = Modifier
                .width(600.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NothingColors.SurfaceElevated)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click through */ }
                )
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SPOTIFY • IMPORTADOR DIRECTO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = SpotifyGreen
                            )
                        )
                    }

                    if (!isImporting) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Title & Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Importar Playlist desde Spotify",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Ingresa el enlace público de cualquier playlist de Spotify. Las pistas serán extraídas y emparejadas con streams de audio de YouTube Music.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NothingColors.TextSecondary,
                            lineHeight = 20.sp
                        )
                    )
                }

                // Input Box
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ENLACE DE LA PLAYLIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextMuted
                        )
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        enabled = !isImporting,
                        placeholder = {
                            Text(
                                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NothingColors.TextMuted
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = if (urlInput.isNotBlank()) SpotifyGreen else NothingColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = NothingColors.SurfaceBorder,
                            focusedContainerColor = NothingColors.Surface,
                            unfocusedContainerColor = NothingColors.Surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Importing Progress Banner
                if (isImporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NothingColors.Surface)
                            .border(1.dp, SpotifyGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SpotifyGreen,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = progressText ?: "Procesando canciones...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = NothingColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = SpotifyGreen,
                            trackColor = NothingColors.SurfaceBorder
                        )
                    }
                }

                // Error feedback
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.AccentRed,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClose,
                        enabled = !isImporting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Cancelar",
                            color = NothingColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            appModel.importSpotifyPlaylist(urlInput) { success, err ->
                                if (success) {
                                    onClose()
                                } else {
                                    errorMessage = err ?: "Error al importar playlist"
                                }
                            }
                        },
                        enabled = urlInput.isNotBlank() && !isImporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            disabledContainerColor = SpotifyGreen.copy(alpha = 0.4f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Importar Playlist",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
