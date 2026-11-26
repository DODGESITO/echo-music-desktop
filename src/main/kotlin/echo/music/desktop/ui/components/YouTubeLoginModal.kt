package echo.music.desktop.ui.components

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
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
import echo.music.desktop.account.EdgeAuthService
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

enum class LoginTab {
    BROWSER_ASSISTED,
    MANUAL
}

@Composable
fun YouTubeLoginModal(
    appModel: DesktopAppModel,
    isSyncing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LoginTab.BROWSER_ASSISTED) }
    var cookieInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isBrowserActive by remember { mutableStateOf(false) }
    var browserStatusMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            EdgeAuthService.cancel()
        }
    }

    // Backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    EdgeAuthService.cancel()
                    onClose()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dialog Card
        Box(
            modifier = Modifier
                .width(660.dp)
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
                                .background(NothingColors.AccentRed)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "YOUTUBE MUSIC • INICIO DE SESIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = NothingColors.TextSecondary
                            )
                        )
                    }

                    IconButton(
                        onClick = {
                            EdgeAuthService.cancel()
                            onClose()
                        },
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

                // Title & Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Conectar cuenta de Google / YouTube Music",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Sincroniza tus listas personales y canciones favoritas de forma oficial y segura.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NothingColors.TextSecondary,
                            lineHeight = 20.sp
                        )
                    )
                }

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NothingColors.Surface)
                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Browser Assisted Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == LoginTab.BROWSER_ASSISTED) NothingColors.SurfaceElevated else Color.Transparent)
                            .clickable {
                                selectedTab = LoginTab.BROWSER_ASSISTED
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = if (selectedTab == LoginTab.BROWSER_ASSISTED) NothingColors.AccentRed else NothingColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Navegador Asistido (Google)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selectedTab == LoginTab.BROWSER_ASSISTED) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == LoginTab.BROWSER_ASSISTED) NothingColors.TextPrimary else NothingColors.TextSecondary
                                )
                            )
                        }
                    }

                    // Manual Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == LoginTab.MANUAL) NothingColors.SurfaceElevated else Color.Transparent)
                            .clickable {
                                selectedTab = LoginTab.MANUAL
                                EdgeAuthService.cancel()
                                isBrowserActive = false
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = if (selectedTab == LoginTab.MANUAL) NothingColors.AccentRed else NothingColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manual (Cookie)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selectedTab == LoginTab.MANUAL) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == LoginTab.MANUAL) NothingColors.TextPrimary else NothingColors.TextSecondary
                                )
                            )
                        }
                    }
                }

                if (selectedTab == LoginTab.BROWSER_ASSISTED) {
                    // Browser Assisted Flow
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NothingColors.Surface)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NothingColors.AccentRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INICIO DE SESIÓN CON NAVEGADOR OFICIAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                        }

                        Text(
                            text = "Abre una ventana oficial de Google mediante Microsoft Edge o Google Chrome. Al iniciar sesión en tu cuenta de Google, Echo Music detecta la sesión automáticamente y se vincula sin que tengas que copiar ningún código.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = NothingColors.TextSecondary,
                                lineHeight = 19.sp
                            )
                        )

                        if (!isBrowserActive) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    successMessage = null
                                    isBrowserActive = true
                                    browserStatusMessage = "Iniciando ventana de navegador..."
                                    EdgeAuthService.launch(
                                        onSuccess = { capturedCookie ->
                                            isBrowserActive = false
                                            browserStatusMessage = null
                                            successMessage = "¡Sesión detectada exitosamente! Conectando..."
                                            appModel.loginYouTube(capturedCookie) { ok, err ->
                                                if (ok) {
                                                    successMessage = "¡Conectado exitosamente!"
                                                    onClose()
                                                } else {
                                                    errorMessage = err ?: "Error al autenticar sesión"
                                                }
                                            }
                                        },
                                        onError = { err ->
                                            isBrowserActive = false
                                            browserStatusMessage = null
                                            errorMessage = err
                                        },
                                        onStatus = { status ->
                                            browserStatusMessage = status
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingColors.AccentRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Iniciar Sesión con Google (Abrir Ventana)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        } else {
                            // Active browser login state
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(10.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NothingColors.AccentRed,
                                    strokeWidth = 2.dp
                                )

                                Text(
                                    text = browserStatusMessage ?: "Esperando inicio de sesión en la ventana emergente...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = NothingColors.TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )

                                Text(
                                    text = "Inicia sesión con tu cuenta de Google en la ventana que se abrió. Cuando termines, se cerrará automáticamente.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = NothingColors.TextMuted,
                                        lineHeight = 16.sp
                                    )
                                )

                                OutlinedButton(
                                    onClick = {
                                        EdgeAuthService.cancel()
                                        isBrowserActive = false
                                        browserStatusMessage = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = NothingColors.TextSecondary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancelar inicio asistido")
                                }
                            }
                        }
                    }
                } else {
                    // Manual Flow
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NothingColors.Surface)
                            .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = NothingColors.AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "¿CÓMO OBTENER TU COOKIE COMPLETA?",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = NothingColors.TextPrimary
                                )
                            )
                        }

                        Text(
                            text = "1. Abre music.youtube.com en tu navegador e inicia sesión.\n" +
                                   "2. Presiona F12 y ve a la pestaña Red (Network).\n" +
                                   "3. En el filtro escribe \"browse\" y haz clic en cualquier petición.\n" +
                                   "4. En la sección Encabezados de solicitud (Request Headers), copia el valor de \"cookie:\".\n" +
                                   "5. Pégalo a continuación y presiona Conectar.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = NothingColors.TextSecondary,
                                lineHeight = 18.sp
                            )
                        )
                    }

                    // Cookie input field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "COOKIE DE SESIÓN (INCLUYE LOGIN_INFO Y SAPISID)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NothingColors.TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = cookieInput,
                            onValueChange = {
                                cookieInput = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text(
                                    "SAPISID=...; SSID=...; HSID=...; LOGIN_INFO=...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = NothingColors.TextMuted
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 140.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = NothingColors.TextPrimary,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingColors.AccentRed,
                                unfocusedBorderColor = NothingColors.SurfaceBorder,
                                focusedContainerColor = NothingColors.Surface,
                                unfocusedContainerColor = NothingColors.Surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Error / Success feedback
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NothingColors.AccentRed,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                if (successMessage != null) {
                    Text(
                        text = successMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF4CAF50),
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
                        onClick = {
                            EdgeAuthService.cancel()
                            onClose()
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Cancelar",
                            color = NothingColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (selectedTab == LoginTab.MANUAL) {
                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                successMessage = null
                                appModel.loginYouTube(cookieInput) { success, error ->
                                    if (success) {
                                        successMessage = "¡Conectado exitosamente! Sincronizando playlists..."
                                        onClose()
                                    } else {
                                        errorMessage = error ?: "No se pudo conectar con YouTube Music"
                                    }
                                }
                            },
                            enabled = cookieInput.isNotBlank() && !isSyncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingColors.AccentRed,
                                disabledContainerColor = NothingColors.AccentRed.copy(alpha = 0.4f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conectando...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Conectar e Importar",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
