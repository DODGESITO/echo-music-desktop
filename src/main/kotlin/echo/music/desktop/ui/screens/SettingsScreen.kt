package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.music.innertube.YouTube
import echo.music.desktop.listentogether.ConnectionState
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.DownloadStore
import echo.music.desktop.state.PreferencesStore
import echo.music.desktop.ui.theme.NothingColors
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SettingsScreen(
    appModel: DesktopAppModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var resetStatus by remember { mutableStateOf<String?>(null) }
    val ytAccount by appModel.youtubeAccount.collectAsState()
    val isSyncingYT by appModel.isSyncingYouTube.collectAsState()
    val eqSettings by appModel.equalizerSettings.collectAsState()
    val crossfadeSeconds by appModel.crossfadeSeconds.collectAsState()
    val isAutomixEnabled by appModel.isAutomixEnabled.collectAsState()
    val isAmbientGlowEnabled by appModel.isAmbientGlowEnabled.collectAsState()
    val sleepTimerRemainingSeconds by appModel.sleepTimerRemainingSeconds.collectAsState()
    val downloadedSongs by appModel.downloadedSongs.collectAsState()
    val downloadsDirectory by appModel.downloadsDirectory.collectAsState()
    var section by remember { mutableStateOf(SettingsSection.CUENTAS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title and sections (instead of one endless page)
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            SettingsSection.entries.forEach { s ->
                val selected = s == section
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) NothingColors.AccentRed.copy(alpha = 0.14f) else NothingColors.Surface)
                        .border(1.dp, if (selected) NothingColors.AccentRed.copy(alpha = 0.6f) else NothingColors.SurfaceBorder, RoundedCornerShape(20.dp))
                        .clickable { section = s }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(s.icon, contentDescription = null, tint = if (selected) NothingColors.AccentRed else NothingColors.TextMuted, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        s.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) NothingColors.TextPrimary else NothingColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        if (section == SettingsSection.CUENTAS) {
        // Section 1: YouTube Music Account Integration
        SettingsCard(
            title = "CUENTA DE YOUTUBE MUSIC",
            icon = Icons.Default.CloudSync
        ) {
            if (ytAccount != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NothingColors.SurfaceElevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ytAccount!!.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary
                            )
                        )
                        val sub = ytAccount!!.email ?: ytAccount!!.channelHandle ?: "Sesión activa"
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = NothingColors.TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { appModel.syncYouTubePlaylists() },
                        enabled = !isSyncingYT,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingColors.AccentRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSyncingYT) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizando...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizar Playlists y Me Gusta")
                        }
                    }

                    OutlinedButton(
                        onClick = { appModel.logoutYouTube() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NothingColors.AccentRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.AccentRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar Sesión")
                    }
                }
            } else {
                Text(
                    text = "No has iniciado sesión con Google / YouTube Music. Conecta tu sesión para transferir automáticamente todas tus listas de reproducción y canciones favoritas guardadas.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NothingColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { appModel.openYouTubeLoginModal() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingColors.AccentRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conectar Cuenta con Cookie",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        }

        if (section == SettingsSection.CUENTAS) {
            SpotifyAccountSettings(appModel)
        }

        if (section == SettingsSection.REPRODUCCION) {
        // Section: Audio DSP & Preferences
        SettingsCard(
            title = "AUDIO, ECUALIZADOR Y TRANSICIONES",
            icon = Icons.Default.GraphicEq
        ) {
            // Row 1: 10-Band Equalizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ecualizador de 10 Bandas",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (eqSettings.enabled) "Activo: Preset \"${eqSettings.currentPresetName}\"" else "Desactivado (Respuesta Plana)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (eqSettings.enabled) NothingColors.AccentRed else NothingColors.TextSecondary
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(
                        checked = eqSettings.enabled,
                        onCheckedChange = { appModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NothingColors.AccentRed,
                            uncheckedThumbColor = NothingColors.TextMuted,
                            uncheckedTrackColor = NothingColors.SurfaceBorder
                        )
                    )

                    Button(
                        onClick = { appModel.openEqualizerModal() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingColors.AccentRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ajustar Curva",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: Crossfade Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Crossfade (Fundido entre Pistas)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Transición suave de volumen al cambiar de canción",
                            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                        )
                    }

                    Text(
                        text = if (crossfadeSeconds == 0) "Desactivado" else "${crossfadeSeconds} segundos",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (crossfadeSeconds > 0) NothingColors.AccentRed else NothingColors.TextMuted
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = crossfadeSeconds.toFloat(),
                    onValueChange = { appModel.setCrossfadeDuration(it.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NothingColors.AccentRed,
                        activeTrackColor = NothingColors.AccentRed,
                        inactiveTrackColor = NothingColors.SurfaceBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 3: Infinite Automix Radio Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Radio Infinita (Automix)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Encola recomendaciones automáticas de YouTube Music cuando tu lista termine para música sin interrupciones.",
                        style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                    )
                }

                Switch(
                    checked = isAutomixEnabled,
                    onCheckedChange = { appModel.toggleAutomix() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NothingColors.AccentRed,
                        uncheckedThumbColor = NothingColors.TextMuted,
                        uncheckedTrackColor = NothingColors.SurfaceBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 4: Ambient Glow Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Resplandor Ambiental (Ambient Glow)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Aura de luz pulsante y reactiva detrás del arte del álbum en el reproductor expandido (Nothing OS 5.0).",
                        style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                    )
                }

                Switch(
                    checked = isAmbientGlowEnabled,
                    onCheckedChange = { appModel.toggleAmbientGlow() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NothingColors.AccentRed,
                        uncheckedThumbColor = NothingColors.TextMuted,
                        uncheckedTrackColor = NothingColors.SurfaceBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 5: Sleep Timer Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Temporizador de Apagado (Sleep Timer)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pausa la música automáticamente para que puedas dormir tranquilo",
                            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary)
                        )
                    }

                    Text(
                        text = if (sleepTimerRemainingSeconds != null) {
                            val mins = sleepTimerRemainingSeconds!! / 60
                            val secs = sleepTimerRemainingSeconds!! % 60
                            String.format(java.util.Locale.US, "Activo: %02d:%02d", mins, secs)
                        } else {
                            "Inactivo"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (sleepTimerRemainingSeconds != null) NothingColors.AccentRed else NothingColors.TextMuted
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        OutlinedButton(
                            onClick = { appModel.startSleepTimer(minutes) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NothingColors.TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${minutes}m", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedButton(
                        onClick = { appModel.setSleepTimerEndOfSong() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NothingColors.TextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Fin de pista", style = MaterialTheme.typography.labelSmall)
                    }

                    if (sleepTimerRemainingSeconds != null) {
                        Button(
                            onClick = { appModel.cancelSleepTimer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingColors.AccentRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Cancelar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
        }

        if (section == SettingsSection.ACERCA) {
        // Section 3: Audio Engine
        SettingsCard(
            title = "MOTOR DE REPRODUCCIÓN",
            icon = Icons.Default.Speed
        ) {
            SettingRow(
                label = "Motor de Audio",
                value = "JavaFX 21 Media • GStreamer-lite (Nativo)"
            )
            SettingRow(
                label = "Resolución de Stream",
                value = "VisionOS Direct AAC (128 kbps) + yt-dlp"
            )
            SettingRow(
                label = "Proxy Local",
                value = "127.0.0.1 con inyección de cabeceras y Range 206"
            )
            SettingRow(
                label = "Sesión Activa",
                value = if (YouTube.visitorData != null) "Visitor Token Activo (YouTube Music)" else "Sesión pendiente"
            )
        }
        }

        if (section == SettingsSection.SISTEMA) {
        // Section 4: Storage & Session
        SettingsCard(
            title = "SESIÓN Y ALMACENAMIENTO",
            icon = Icons.Default.Refresh
        ) {
            val localBaseDir = remember { File(System.getProperty("user.home"), ".echomusic") }
            val defaultDownloadsDir = remember { File(System.getProperty("user.home"), ".echomusic/downloads") }

            // Biblioteca Local
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biblioteca Local",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = File(localBaseDir, "library.json").absolutePath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = NothingColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = { openFolderInExplorer(localBaseDir) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Abrir", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Carpeta de Descargas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Carpeta de Descargas",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${downloadedSongs.size} canciones (${DownloadStore.getFormattedDiskSpace()})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NothingColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = downloadsDirectory.absolutePath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = NothingColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cambiar Carpeta
                        OutlinedButton(
                            onClick = {
                                chooseDirectory(downloadsDirectory) { selected ->
                                    appModel.setDownloadsDirectory(selected)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cambiar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        // Abrir en Explorador
                        OutlinedButton(
                            onClick = { openFolderInExplorer(downloadsDirectory) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        // Restablecer si es diferente al default
                        if (downloadsDirectory.absolutePath != defaultDownloadsDir.absolutePath) {
                            IconButton(
                                onClick = { appModel.resetDownloadsDirectory() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Restablecer por defecto",
                                    tint = NothingColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        resetStatus = "Renovando sesión con YouTube..."
                        withContext(Dispatchers.IO) {
                            try {
                                YouTube.refreshVisitorData()
                                resetStatus = "¡Sesión de visitante renovada con éxito!"
                            } catch (e: Exception) {
                                resetStatus = "Error: ${e.message}"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingColors.SurfaceElevated,
                    contentColor = NothingColors.TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder)
            ) {
                Text(
                    text = "Rotar Sesión de Visitante (Anti-Bot)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (resetStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resetStatus!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NothingColors.AccentRed,
                        fontSize = 12.sp
                    )
                )
            }
        }
        }

        if (section == SettingsSection.CUENTAS) {
        // Section: Listen Together (Escuchar Juntos)
        val ltManager = appModel.listenTogetherManager
        val ltConnectionState by ltManager.connectionState.collectAsState()
        val ltRoomState by ltManager.roomState.collectAsState()
        var ltAutoApprove by remember { mutableStateOf(PreferencesStore.current.listenTogetherAutoApprove) }

        SettingsCard(
            title = "ESCUCHAR JUNTOS (LISTEN TOGETHER)",
            icon = Icons.Default.Headphones
        ) {
            Text(
                text = "Sincroniza lo que estás escuchando en tiempo real con la aplicación Echo Music de Android, otra PC o un bot de Discord mediante salas compartidas.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = NothingColors.TextSecondary,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Estado de Sincronización",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (ltRoomState != null) "En sala activa: [${ltRoomState!!.roomCode}] (${if (ltManager.isHost) "Anfitrión" else "Invitado"})"
                               else if (ltConnectionState == ConnectionState.CONNECTED) "Servidor conectado listo para crear o unirse a sala"
                               else "Desconectado",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (ltRoomState != null) Color(0xFF4CAF50) else NothingColors.TextSecondary,
                            fontWeight = if (ltRoomState != null) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                Button(
                    onClick = { appModel.openListenTogetherModal() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingColors.AccentRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir Panel")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggle Auto-Approve
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aprobación Automática de Invitados",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cuando crees una sala como anfitrión, los amigos que ingresen tu código se unirán al instante sin esperar confirmación.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = ltAutoApprove,
                    onCheckedChange = {
                        ltAutoApprove = it
                        PreferencesStore.update { p -> p.copy(listenTogetherAutoApprove = it) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NothingColors.AccentRed
                    )
                )
            }
        }
        }

        // Sections: Playback (skip, normalization, speed, quality) and Content filters
        PlaybackAndContentSettings(appModel, section)

        if (section == SettingsSection.SISTEMA) {
        // Section: System Integration & Shortcuts
        SettingsCard(
            title = "INTEGRACIÓN DEL SISTEMA Y ATAJOS",
            icon = Icons.Default.Keyboard
        ) {
            var minimizeToTray by remember { mutableStateOf(PreferencesStore.current.minimizeToTray) }

            // Toggle Minimize to Tray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Minimizar a la Bandeja del Sistema",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Al cerrar o minimizar la ventana, la música continúa reproduciéndose en segundo plano junto al reloj de Windows.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = minimizeToTray,
                    onCheckedChange = {
                        minimizeToTray = it
                        PreferencesStore.update { p -> p.copy(minimizeToTray = it) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NothingColors.AccentRed,
                        uncheckedThumbColor = NothingColors.TextMuted,
                        uncheckedTrackColor = NothingColors.Surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle global media keys
            val globalMediaKeys by appModel.globalMediaKeysEnabled.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Teclas Multimedia Globales",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Las teclas ⏯ ⏭ ⏮ del teclado controlan Echo Music aunque estés en otro programa o la ventana esté en la bandeja.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = globalMediaKeys,
                    onCheckedChange = { appModel.setGlobalMediaKeysEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NothingColors.AccentRed,
                        uncheckedThumbColor = NothingColors.TextMuted,
                        uncheckedTrackColor = NothingColors.Surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HeadphoneSettingsRows(appModel)

            Spacer(modifier = Modifier.height(10.dp))
            IslandSettingsRows(appModel)

            Spacer(modifier = Modifier.height(10.dp))
            StartupSettingsRows(appModel)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ATAJOS DE TECLADO RÁPIDOS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NothingColors.TextMuted,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            val shortcuts = listOf(
                "Espacio" to "Reproducir / Pausar",
                "← / →" to "Retroceder / Adelantar 5 segundos",
                "↑ / ↓" to "Subir / Bajar volumen (5%)",
                "M" to "Silenciar / Desilenciar (Mute)",
                "N / P" to "Pista siguiente / Pista anterior",
                "L" to "Agregar / Quitar de Favoritos",
                "S / R" to "Alternar Aleatorio / Repetición",
                "Ctrl + F" to "Enfocar barra de búsqueda",
                "Ctrl + M" to "Abrir el mini reproductor",
                "Esc" to "Cerrar modal / vista expandida"
            )

            shortcuts.forEach { (key, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NothingColors.SurfaceElevated)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.AccentRed
                            )
                        )
                    }

                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.TextSecondary
                        )
                    )
                }
            }
        }
        }

        if (section == SettingsSection.SISTEMA) {
        UpdateSettings(appModel)
        }

        if (section == SettingsSection.ACERCA) {
        // Section 5: About
        SettingsCard(
            title = "ACERCA DE ECHO MUSIC DESKTOP",
            icon = Icons.Default.Info
        ) {
            SettingRow(
                label = "Versión",
                value = "${echo.music.desktop.system.AppInfo.version} Desktop (Edición Nothing OS 5.0)"
            )
            SettingRow(
                label = "Framework",
                value = "Compose Multiplatform 1.7.3 & Kotlin 2.1.10 (JVM 21)"
            )
            SettingRow(
                label = "Licencia",
                value = "GPL-3.0 Open Source"
            )
            SettingRow(
                label = "Diseño UI",
                value = "Inspirado en Nothing OS 5.0 (Dark Minimalist & Dot Matrix)"
            )
        }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NothingColors.Surface)
            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = NothingColors.AccentRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = NothingColors.TextSecondary
                )
            )
        }
        content()
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = NothingColors.TextSecondary,
                fontSize = 13.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = NothingColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        )
    }
}

private fun chooseDirectory(initialDir: File?, onSelected: (File) -> Unit) {
    java.awt.EventQueue.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {}
        val chooser = JFileChooser(initialDir ?: File(System.getProperty("user.home"))).apply {
            dialogTitle = "Seleccionar Carpeta de Descargas"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            onSelected(chooser.selectedFile)
        }
    }
}

private fun openFolderInExplorer(dir: File) {
    try {
        if (!dir.exists()) dir.mkdirs()
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
        } else {
            Runtime.getRuntime().exec(arrayOf("explorer.exe", dir.absolutePath))
        }
    } catch (e: Exception) {
        println("[SettingsScreen] Error al abrir carpeta: ${e.message}")
    }
}

/** Settings are grouped by topic, one tab each. */
enum class SettingsSection(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CUENTAS("Cuentas", Icons.Default.AccountCircle),
    REPRODUCCION("Reproducción", Icons.Default.GraphicEq),
    LETRAS("Letras e IA", Icons.Default.Translate),
    APARIENCIA("Apariencia", Icons.Default.Palette),
    PRIVACIDAD("Privacidad", Icons.Default.Lock),
    SISTEMA("Sistema", Icons.Default.Computer),
    ACERCA("Acerca de", Icons.Default.Info)
}
