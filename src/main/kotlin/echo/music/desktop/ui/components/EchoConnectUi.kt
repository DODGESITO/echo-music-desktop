package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.connect.ControlCommand
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.screens.SettingRowFrame
import echo.music.desktop.ui.screens.SettingTexts
import echo.music.desktop.ui.screens.SettingsCard
import echo.music.desktop.ui.screens.ToggleRow
import echo.music.desktop.ui.theme.NothingColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------- Settings → Cuentas

@Composable
fun EchoConnectSettingsCard(appModel: DesktopAppModel) {
    val connect = appModel.echoConnect
    val settings by connect.settings.collectAsState()
    val running by connect.running.collectAsState()
    val peers by connect.peers.collectAsState()
    val log by connect.log.collectAsState()
    val syncing by connect.syncing.collectAsState()

    LaunchedEffect(running) { if (running) connect.refreshDevices() }
    // The address is known a moment after Echo Connect starts.
    val address by produceState<String?>(null, running) {
        repeat(20) {
            value = connect.localAddress()
            if (value != null) return@produceState
            delay(500)
        }
    }

    SettingsCard(title = "ECHO CONNECT", icon = Icons.Default.PhoneAndroid) {
        ToggleRow(
            title = "Conectar con el celular",
            description = "Echo Music del celular y esta PC se encuentran en la misma Wi-Fi: pasa la música de uno a otro, " +
                "contrólalos entre sí y sincroniza tus playlists. Si Windows pregunta, permite el acceso en redes privadas.",
            checked = settings.enabled,
            onChange = { connect.setEnabled(it) }
        )
        if (!settings.enabled) return@SettingsCard

        Spacer(Modifier.height(10.dp))
        val paired = connect.pairedPeer()
        if (paired != null) {
            val lastSync = settings.lastSyncAtMs.takeIf { it > 0 }?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            val online = peers.any { it.id == paired.id }
            SettingRowFrame {
                SettingTexts(
                    "Emparejado con ${paired.name}",
                    buildString {
                        append(if (online) "En esta red" else "No aparece en la red ahora")
                        if (lastSync != null) append(" · Última sincronización $lastSync")
                    }
                )
                if (syncing) {
                    CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    TextButton(onClick = { connect.syncNow() }) { Text("Sincronizar", color = NothingColors.AccentRed) }
                }
                TextButton(onClick = { connect.unpair() }) { Text("Desemparejar", color = NothingColors.TextSecondary) }
            }
        } else {
            val phones = peers
            if (phones.isEmpty()) {
                SettingRowFrame {
                    SettingTexts(
                        if (running) "Buscando celulares…" else "Iniciando…",
                        "Abre Echo Music en el celular, en la misma Wi-Fi, y entra a Ajustes → Echo Connect. " +
                            "Esta PC aparece como ${connect.deviceName}" + (address?.let { " ($it)" } ?: "") + "."
                    )
                    TextButton(onClick = { connect.refreshDevices() }) { Text("Buscar", color = NothingColors.AccentRed) }
                }
            }
            phones.forEachIndexed { i, peer ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                SettingRowFrame {
                    Icon(Icons.Default.PhoneAndroid, null, tint = NothingColors.TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    SettingTexts(peer.name, "Celular en esta red · ${peer.host}")
                    TextButton(onClick = { connect.startPairing(peer) }) { Text("Emparejar", color = NothingColors.AccentRed) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Compartir mi cuenta de YouTube Music",
            description = "Envía la sesión de esta PC al celular emparejado para que inicie sesión solo. " +
                "Es como compartir una contraseña: actívalo solo en tu red de casa.",
            checked = settings.shareAccount,
            onChange = { connect.setShareAccount(it) }
        )

        if (log.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "ACTIVIDAD",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = NothingColors.TextMuted
                )
            )
            Spacer(Modifier.height(6.dp))
            log.take(5).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- pairing dialogs

/** Pairing dialogs, drawn above everything (a phone can ask to pair at any moment). */
@Composable
fun EchoConnectDialogs(appModel: DesktopAppModel) {
    val connect = appModel.echoConnect
    val hostCode by connect.hostPairing.collectAsState()
    val incoming by connect.incomingPair.collectAsState()

    hostCode?.let { code ->
        val secondsLeft = rememberSecondsLeft(code.expiresAtMs)
        ModalFrame(title = "ECHO CONNECT", subtitle = "Emparejar con ${code.peer.name}", onClose = { connect.cancelPairing() }) {
            Text(
                "Escribe este código en el aviso que apareció en ${code.peer.name}.",
                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                code.code.take(3) + " " + code.code.drop(3),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = NothingColors.TextPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cambia en $secondsLeft s",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextMuted)
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { connect.cancelPairing() }) { Text("Cancelar", color = NothingColors.TextSecondary) }
            }
        }
    }

    incoming?.let { request ->
        var code by remember(request.peer.id) { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(request.peer.id) { runCatching { focus.requestFocus() } }
        DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }
        val secondsLeft = rememberSecondsLeft(request.expiresAtMs)
        ModalFrame(title = "ECHO CONNECT", subtitle = "${request.peer.name} quiere conectarse", onClose = { connect.dismissIncomingPair() }) {
            Text(
                "Escribe el código de 6 números que muestra ${request.peer.name}. Si no pediste esto, cancela.",
                style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextSecondary)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter(Char::isDigit).take(6) },
                label = { Text("Código") },
                singleLine = true,
                enabled = !request.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connect.submitIncomingCode(code) }),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NothingColors.AccentRed,
                    unfocusedBorderColor = NothingColors.SurfaceBorder,
                    focusedLabelColor = NothingColors.AccentRed,
                    cursorColor = NothingColors.AccentRed,
                    focusedTextColor = NothingColors.TextPrimary,
                    unfocusedTextColor = NothingColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { appModel.isInputFocused = it.isFocused }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                request.error ?: if (secondsLeft > 0) "El código del celular cambia en $secondsLeft s" else "Usa el código nuevo que muestra el celular",
                style = MaterialTheme.typography.bodySmall.copy(color = if (request.error != null) NothingColors.AccentRed else NothingColors.TextMuted)
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (request.busy) {
                    CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(onClick = { connect.dismissIncomingPair() }) { Text("Cancelar", color = NothingColors.TextSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { connect.submitIncomingCode(code) },
                    enabled = code.length == 6 && !request.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Emparejar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun rememberSecondsLeft(expiresAtMs: Long): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return ((expiresAtMs - now) / 1000).coerceAtLeast(0)
}

// ---------------------------------------------------------------------- player bar

/** Button in the player bar: where the music plays (this PC or the paired phone). */
@Composable
fun EchoConnectDeviceButton(appModel: DesktopAppModel) {
    val connect = appModel.echoConnect
    val settings by connect.settings.collectAsState()
    val remote by connect.remote.collectAsState()
    if (!settings.enabled || !settings.isPaired) return
    val peer = connect.pairedPeer() ?: return
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {
            Icon(
                if (remote != null) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = "Echo Connect: elegir dónde suena",
                tint = if (remote != null) NothingColors.AccentRed else NothingColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(NothingColors.SurfaceElevated)
        ) {
            Text(
                "REPRODUCIR EN",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = NothingColors.TextMuted)
            )
            DeviceMenuItem(
                icon = Icons.Default.Computer,
                title = "Esta PC",
                subtitle = connect.deviceName,
                selected = remote == null,
                onClick = {
                    open = false
                    if (remote != null) connect.playHere()
                }
            )
            DeviceMenuItem(
                icon = Icons.Default.PhoneAndroid,
                title = peer.name,
                subtitle = if (remote != null) "Sonando ahora" else "Celular emparejado",
                selected = remote != null,
                onClick = {
                    open = false
                    if (remote == null) connect.playOnPhone()
                }
            )
        }
    }
}

@Composable
private fun DeviceMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null, tint = if (selected) NothingColors.AccentRed else NothingColors.TextSecondary, modifier = Modifier.size(20.dp)) },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = NothingColors.TextPrimary, fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary))
            }
        },
        trailingIcon = if (selected) {
            { Box(Modifier.size(8.dp).clip(CircleShape).background(NothingColors.AccentRed)) }
        } else null,
        onClick = onClick
    )
}

/** Shown above the player bar while the music plays on the phone: what it plays and its controls. */
@Composable
fun EchoConnectRemoteBar(appModel: DesktopAppModel, modifier: Modifier = Modifier) {
    val connect = appModel.echoConnect
    val remote by connect.remote.collectAsState()
    val session = remote ?: return
    val state = session.state
    val track = state?.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NothingColors.SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CastConnected, null, tint = NothingColors.AccentRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        if (track?.thumbnail != null) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Sonando en ${session.peer.name}",
                style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.AccentRed, fontWeight = FontWeight.Bold)
            )
            Text(
                track?.let { "${it.title} · ${it.artist}" } ?: "Conectando…",
                style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { connect.remoteControl(ControlCommand(action = "previous")) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.SkipPrevious, "Anterior en el celular", tint = NothingColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { connect.remoteControl(ControlCommand(action = "toggle")) }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (state?.playing == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (state?.playing == true) "Pausar en el celular" else "Reproducir en el celular",
                tint = NothingColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = { connect.remoteControl(ControlCommand(action = "next")) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.SkipNext, "Siguiente en el celular", tint = NothingColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { connect.playHere() }) { Text("Escuchar aquí", color = NothingColors.AccentRed) }
        IconButton(onClick = { connect.disconnectRemote() }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Dejar de mostrar el celular", tint = NothingColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}
