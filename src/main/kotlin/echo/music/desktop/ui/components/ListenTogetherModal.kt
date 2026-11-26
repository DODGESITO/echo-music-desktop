package echo.music.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import echo.music.desktop.listentogether.ConnectionState
import echo.music.desktop.listentogether.ListenTogetherServers
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.PreferencesStore
import echo.music.desktop.ui.theme.NothingColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import echo.music.desktop.listentogether.ListenTogetherEvent
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ListenTogetherModal(
    appModel: DesktopAppModel,
    onClose: () -> Unit
) {
    val manager = appModel.listenTogetherManager
    val client = manager.client

    val connectionState by manager.connectionState.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val role by manager.role.collectAsState()
    val currentSong by appModel.currentSong.collectAsState()
    val pendingRequests by manager.pendingJoinRequests.collectAsState()
    val chatMessages by manager.chatMessages.collectAsState()
    val rtt by manager.rtt.collectAsState()

    var usernameInput by remember {
        mutableStateOf(
            PreferencesStore.current.listenTogetherUsername.ifBlank {
                appModel.youtubeAccount.value?.name ?: (System.getProperty("user.name") ?: "Usuario Echo")
            }
        )
    }
    var roomCodeInput by remember { mutableStateOf("") }
    var selectedServerUrl by remember { mutableStateOf(PreferencesStore.current.listenTogetherServerUrl) }
    var customUrlInput by remember { mutableStateOf("") }
    var isCustomServerSelected by remember {
        mutableStateOf(
            ListenTogetherServers.defaultServers.none { it.url == PreferencesStore.current.listenTogetherServerUrl }
        )
    }

    var chatInput by remember { mutableStateOf("") }
    var copiedToClipboard by remember { mutableStateOf(false) }

    var isCreatingRoom by remember { mutableStateOf(false) }
    var isJoiningRoom by remember { mutableStateOf(false) }
    var actionErrorMessage by remember { mutableStateOf<String?>(null) }
    var autoApprove by remember { mutableStateOf(PreferencesStore.current.listenTogetherAutoApprove) }

    val coroutineScope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()

    // Listen to client events for loading reset and errors
    LaunchedEffect(Unit) {
        client.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    actionErrorMessage = null
                }
                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    actionErrorMessage = null
                }
                is ListenTogetherEvent.JoinRejected -> {
                    isJoiningRoom = false
                    actionErrorMessage = "Solicitud rechazada: ${event.reason}"
                }
                is ListenTogetherEvent.ServerError -> {
                    isCreatingRoom = false
                    isJoiningRoom = false
                    actionErrorMessage = "Error de servidor: ${event.message}"
                }
                is ListenTogetherEvent.ConnectionError -> {
                    isCreatingRoom = false
                    isJoiningRoom = false
                    actionErrorMessage = "Error de conexión: ${event.error}"
                }
                else -> {}
            }
        }
    }

    // Reset loading once room state becomes active
    LaunchedEffect(manager.isInRoom) {
        if (manager.isInRoom) {
            isCreatingRoom = false
            isJoiningRoom = false
        }
    }

    // Timeout safety for creation
    LaunchedEffect(isCreatingRoom) {
        if (isCreatingRoom) {
            delay(12000L)
            if (isCreatingRoom && !manager.isInRoom) {
                isCreatingRoom = false
                actionErrorMessage = "Tiempo de espera agotado al crear sala en el servidor."
            }
        }
    }

    // Timeout safety for joining
    LaunchedEffect(isJoiningRoom) {
        if (isJoiningRoom) {
            delay(12000L)
            if (isJoiningRoom && !manager.isInRoom) {
                isJoiningRoom = false
                actionErrorMessage = "Tiempo de espera agotado al conectar a la sala."
            }
        }
    }

    // Auto scroll chat to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Auto-connect if disconnected when modal opens
    LaunchedEffect(Unit) {
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            client.connect(selectedServerUrl)
        }
    }

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NothingColors.Surface)
                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NothingColors.AccentRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ESCUCHAR JUNTOS",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp,
                                        color = NothingColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NothingColors.AccentRed)
                                )
                            }
                            Text(
                                text = "Sincroniza en tiempo real con Celular, PC o Bot de Discord",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NothingColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Connection status badge & Close button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusText, statusColor) = when (connectionState) {
                            ConnectionState.CONNECTED -> "Conectado" to Color(0xFF4CAF50)
                            ConnectionState.CONNECTING -> "Conectando..." to Color(0xFFFFB300)
                            ConnectionState.RECONNECTING -> "Reconectando..." to Color(0xFFFFB300)
                            ConnectionState.ERROR -> "Error" to Color(0xFFE53935)
                            ConnectionState.DISCONNECTED -> "Desconectado" to NothingColors.TextMuted
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                                if (rtt > 0 && connectionState == ConnectionState.CONNECTED) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${rtt}ms",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NothingColors.TextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NothingColors.SurfaceElevated)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = NothingColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = NothingColors.SurfaceBorder)

                // Body content: in-room or setup
                if (manager.isInRoom && roomState != null) {
                    val room = roomState!!

                    // ACTIVE ROOM VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Room code & role banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NothingColors.SurfaceElevated)
                                .border(1.dp, NothingColors.AccentRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "CÓDIGO DE SALA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            color = NothingColors.TextMuted,
                                            fontSize = 10.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = room.roomCode,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            color = NothingColors.TextPrimary,
                                            letterSpacing = 3.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (manager.isHost) "Eres el ANFITRIÓN de la sesión" else "Conectado como INVITADO",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (manager.isHost) NothingColors.AccentRed else Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            try {
                                                val selection = StringSelection(room.roomCode)
                                                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                                copiedToClipboard = true
                                            } catch (_: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (copiedToClipboard) Color(0xFF4CAF50) else NothingColors.Surface,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder)
                                    ) {
                                        Icon(
                                            if (copiedToClipboard) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (copiedToClipboard) "¡Copiado!" else "Copiar Código")
                                    }
                                }
                            }
                        }

                        // Currently synced track info card
                        val displayTrack = room.currentTrack ?: currentSong?.let {
                            echo.music.desktop.listentogether.TrackInfo(
                                id = it.id,
                                title = it.title,
                                artist = it.artists.joinToString(", ") { a -> a.name },
                                duration = (it.duration ?: 0).toLong(),
                                thumbnail = it.thumbnail
                            )
                        }

                        if (displayTrack != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NothingColors.Surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!displayTrack.thumbnail.isNullOrBlank()) {
                                        AsyncImage(
                                            model = displayTrack.thumbnail,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = NothingColors.TextMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayTrack.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = NothingColors.TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = displayTrack.artist,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = NothingColors.TextSecondary,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NothingColors.AccentRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (room.isPlaying) "EN REPRODUCCIÓN" else "PAUSADO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NothingColors.AccentRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Host Settings & Info banner
                        if (manager.isHost) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Aprobar solicitudes automáticamente",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = NothingColors.TextPrimary,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Text(
                                        text = "Permite que tu celular u otros oyentes se unan de inmediato",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NothingColors.TextMuted,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                Switch(
                                    checked = autoApprove,
                                    onCheckedChange = {
                                        autoApprove = it
                                        PreferencesStore.update { p -> p.copy(listenTogetherAutoApprove = it) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NothingColors.AccentRed
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Control de música: El Anfitrión controla la reproducción. Toca la estrella ⭐ junto a cualquier oyente para cederle el control.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NothingColors.TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            if (pendingRequests.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "SOLICITUDES DE UNIÓN PENDIENTES (${pendingRequests.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = NothingColors.TextMuted,
                                            fontSize = 10.sp
                                        )
                                    )
                                    pendingRequests.forEach { req ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NothingColors.SurfaceElevated)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = req.username,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = NothingColors.TextPrimary
                                                )
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { client.approveJoin(req.userId) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Aceptar", fontSize = 11.sp)
                                                }
                                                Button(
                                                    onClick = { client.rejectJoin(req.userId) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Rechazar", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Connected Users & Live Chat row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Left column: Users
                            Column(
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "PARTICIPANTES (${room.users.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = NothingColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                )
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(room.users) { user ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NothingColors.Surface)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (user.isHost) Color(0xFFFFB300) else NothingColors.SurfaceElevated),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (user.isHost) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = "Anfitrión",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                } else {
                                                    Text(
                                                        text = user.username.take(1).uppercase(),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.username,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = NothingColors.TextPrimary
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (user.isHost) {
                                                    Text(
                                                        text = "Anfitrión • Controla",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFFFFB300),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Oyente",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = NothingColors.TextMuted,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                            }
                                            if (manager.isHost && user.userId != client.userId.value) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { client.transferHost(user.userId) },
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Star,
                                                            contentDescription = "Hacer Anfitrión (Ceder control)",
                                                            tint = Color(0xFFFFB300),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { client.kickUser(user.userId) },
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(NothingColors.SurfaceElevated)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Expulsar",
                                                            tint = NothingColors.TextMuted,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Right column: Chat
                            Column(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CHAT DE LA SALA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = NothingColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                )

                                LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (chatMessages.isEmpty()) {
                                        item {
                                            Text(
                                                text = "Aún no hay mensajes. ¡Saluda a los participantes!",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = NothingColors.TextMuted,
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(top = 20.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        items(chatMessages) { chat ->
                                            val timeStr = remember(chat.timestamp) {
                                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.timestamp))
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(NothingColors.Surface)
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = chat.username,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = NothingColors.AccentRed,
                                                            fontSize = 11.sp
                                                        )
                                                    )
                                                    Text(
                                                        text = timeStr,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = NothingColors.TextMuted,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = chat.message,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = NothingColors.TextPrimary,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Chat input row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = chatInput,
                                        onValueChange = { chatInput = it },
                                        placeholder = { Text("Escribe un mensaje...", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NothingColors.AccentRed,
                                            unfocusedBorderColor = NothingColors.SurfaceBorder,
                                            focusedTextColor = NothingColors.TextPrimary,
                                            unfocusedTextColor = NothingColors.TextPrimary,
                                            focusedContainerColor = NothingColors.Surface,
                                            unfocusedContainerColor = NothingColors.Surface
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (chatInput.isNotBlank()) {
                                                client.sendChatMessage(chatInput.trim())
                                                chatInput = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NothingColors.AccentRed)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Enviar",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Actions: Sync & Leave
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { client.requestSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resincronizar")
                            }

                            Button(
                                onClick = { client.leaveRoom() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingColors.AccentRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abandonar Sala")
                            }
                        }
                    }
                } else {
                    // SETUP VIEW: CREATE OR JOIN ROOM
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Username & Server settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = {
                                    usernameInput = it
                                    PreferencesStore.update { p -> p.copy(listenTogetherUsername = it) }
                                },
                                label = { Text("Tu Nombre / Apodo") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = NothingColors.TextMuted)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NothingColors.AccentRed,
                                    unfocusedBorderColor = NothingColors.SurfaceBorder,
                                    focusedTextColor = NothingColors.TextPrimary,
                                    unfocusedTextColor = NothingColors.TextPrimary,
                                    focusedContainerColor = NothingColors.SurfaceElevated,
                                    unfocusedContainerColor = NothingColors.SurfaceElevated
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Server Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SERVIDOR WEBSOCKET",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingColors.TextMuted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ListenTogetherServers.defaultServers.forEach { server ->
                                    val isSelected = !isCustomServerSelected && selectedServerUrl == server.url
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) NothingColors.AccentRed.copy(alpha = 0.15f) else NothingColors.SurfaceElevated)
                                            .border(
                                                1.dp,
                                                if (isSelected) NothingColors.AccentRed else NothingColors.SurfaceBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                isCustomServerSelected = false
                                                selectedServerUrl = server.url
                                                PreferencesStore.update { p -> p.copy(listenTogetherServerUrl = server.url) }
                                                client.connect(server.url)
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = server.name,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) NothingColors.AccentRed else NothingColors.TextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            Text(
                                                text = server.operator,
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

                        if (actionErrorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE53935).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = actionErrorMessage!!,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFFE53935),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    IconButton(
                                        onClick = { actionErrorMessage = null },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Cerrar alerta",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Two Main Action Cards
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Card 1: Crear Sala (Host)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                                    .padding(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(NothingColors.AccentRed.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CellTower,
                                                contentDescription = null,
                                                tint = NothingColors.AccentRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Text(
                                            text = "Crear una Sala",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = NothingColors.TextPrimary
                                            )
                                        )

                                        Text(
                                            text = "Inicia una sala y transmite lo que escuchas en tiempo real. Comparte el código con amigos en móvil, PC o bot de Discord.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = NothingColors.TextSecondary,
                                                lineHeight = 16.sp,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (!isCreatingRoom && !isJoiningRoom) {
                                                isCreatingRoom = true
                                                actionErrorMessage = null
                                                client.createRoom(usernameInput)
                                            }
                                        },
                                        enabled = !isCreatingRoom && !isJoiningRoom,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NothingColors.AccentRed,
                                            contentColor = Color.White,
                                            disabledContainerColor = NothingColors.AccentRed.copy(alpha = 0.6f),
                                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isCreatingRoom) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Creando sala...", fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Crear Sala Ahora", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Card 2: Unirse a Sala (Guest)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NothingColors.SurfaceElevated)
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                                    .padding(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Login,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Text(
                                            text = "Unirse a una Sala",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = NothingColors.TextPrimary
                                            )
                                        )

                                        Text(
                                            text = "Introduce el código de 6-8 caracteres generado en el celular, Discord u otra PC para sincronizar tu reproducción.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = NothingColors.TextSecondary,
                                                lineHeight = 16.sp,
                                                fontSize = 12.sp
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        OutlinedTextField(
                                            value = roomCodeInput,
                                            onValueChange = {
                                                roomCodeInput = it.uppercase().take(12)
                                            },
                                            placeholder = { Text("Ej. X7K9M2", fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            enabled = !isJoiningRoom && !isCreatingRoom,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NothingColors.AccentRed,
                                                unfocusedBorderColor = NothingColors.SurfaceBorder,
                                                focusedTextColor = NothingColors.TextPrimary,
                                                unfocusedTextColor = NothingColors.TextPrimary,
                                                focusedContainerColor = NothingColors.Surface,
                                                unfocusedContainerColor = NothingColors.Surface
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (!isJoiningRoom && !isCreatingRoom && roomCodeInput.trim().length >= 4) {
                                                isJoiningRoom = true
                                                actionErrorMessage = null
                                                client.joinRoom(roomCodeInput, usernameInput)
                                            }
                                        },
                                        enabled = !isJoiningRoom && !isCreatingRoom && roomCodeInput.trim().length >= 4,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2196F3),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isJoiningRoom) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Uniéndose...", fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Unirse a la Sala", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Info note
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NothingColors.SurfaceElevated.copy(alpha = 0.5f))
                                .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NothingColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Compatible al 100% con la app Echo Music de celular y bots de Discord que usen el servidor de Metroserver.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = NothingColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
