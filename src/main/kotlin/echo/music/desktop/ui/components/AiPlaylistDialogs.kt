package echo.music.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

/** "Crear playlist con IA" and "Modificar con IA" (which one depends on [DesktopAppModel.aiPrompt]). */
@Composable
fun AiPromptDialog(appModel: DesktopAppModel) {
    val mode by appModel.aiPrompt.collectAsState()
    val current = mode ?: return
    val modifyId = current.removePrefix("modify:").takeIf { current.startsWith("modify:") }
    val unavailable = remember(current) { appModel.aiUnavailableReason() }
    var text by remember(current) { mutableStateOf("") }
    var count by remember(current) { mutableStateOf(20f) }

    AlertDialog(
        onDismissRequest = { appModel.closeAiPrompt() },
        containerColor = NothingColors.SurfaceElevated,
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = NothingColors.AccentRed) },
        title = {
            Text(
                if (modifyId != null) "Modificar con IA" else "Crear playlist con IA",
                color = NothingColors.TextPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(Modifier.width(420.dp)) {
                if (unavailable != null) {
                    Text(unavailable, color = NothingColors.AccentRed, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    if (modifyId != null) "Di qué quieres cambiar. La IA quita y añade canciones de esta playlist."
                    else "Describe lo que quieres escuchar. La IA elige las canciones y Echo Music las busca.",
                    color = NothingColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            if (modifyId != null) "p. ej., Quita las canciones tristes y añade éxitos de los 90"
                            else "p. ej., Rock en español de los 90 para manejar de noche",
                            color = NothingColors.TextMuted
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingColors.AccentRed,
                        unfocusedBorderColor = NothingColors.SurfaceBorder,
                        focusedTextColor = NothingColors.TextPrimary,
                        unfocusedTextColor = NothingColors.TextPrimary,
                        cursorColor = NothingColors.AccentRed
                    ),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { appModel.isInputFocused = it.isFocused }
                )
                if (modifyId == null) {
                    Spacer(Modifier.height(12.dp))
                    Text("${count.toInt()} canciones", color = NothingColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = count,
                        onValueChange = { count = it },
                        valueRange = 10f..50f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = NothingColors.AccentRed, activeTrackColor = NothingColors.AccentRed)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = unavailable == null && text.isNotBlank(),
                onClick = {
                    appModel.isInputFocused = false
                    if (modifyId != null) appModel.modifyPlaylistWithAi(modifyId, text)
                    else appModel.createAiPlaylist(text, count.toInt())
                }
            ) { Text(if (modifyId != null) "Aplicar" else "Crear", color = if (unavailable == null && text.isNotBlank()) NothingColors.AccentRed else NothingColors.TextMuted) }
        },
        dismissButton = {
            TextButton(onClick = { appModel.isInputFocused = false; appModel.closeAiPrompt() }) {
                Text("Cancelar", color = NothingColors.TextSecondary)
            }
        }
    )
}

/** Progress of an AI playlist job, then its result or error. */
@Composable
fun AiTaskDialog(appModel: DesktopAppModel) {
    val task by appModel.aiTask.collectAsState()
    val t = task ?: return
    AlertDialog(
        onDismissRequest = { appModel.dismissAiTask() },
        containerColor = NothingColors.SurfaceElevated,
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = NothingColors.AccentRed) },
        title = { Text(t.title, color = NothingColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Row(Modifier.width(400.dp), verticalAlignment = Alignment.CenterVertically) {
                if (t.running) {
                    CircularProgressIndicator(color = NothingColors.AccentRed, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    t.error ?: t.log,
                    color = if (t.error != null) NothingColors.AccentRed else NothingColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (!t.running) TextButton(onClick = { appModel.dismissAiTask() }) { Text("Cerrar", color = NothingColors.AccentRed) }
        }
    )
}
