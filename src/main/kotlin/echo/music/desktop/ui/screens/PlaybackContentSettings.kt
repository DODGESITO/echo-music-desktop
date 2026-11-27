package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.desktop.lyrics.TranslationProvider
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.ui.theme.NothingColors

/** Content languages offered (code to name). "" = same as Windows. */
private val contentLanguages = listOf(
    "" to "Igual que Windows",
    "es" to "Español",
    "es-419" to "Español (Latinoamérica)",
    "en" to "English",
    "pt-BR" to "Português (Brasil)",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "ja" to "日本語",
    "ko" to "한국어"
)

/** Languages offered for lyrics translation (code to name). */
private val translationLanguages = listOf(
    "es" to "Español",
    "en" to "English",
    "pt-BR" to "Português (Brasil)",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
    "ru" to "Русский",
    "hi" to "हिन्दी"
)

/** Content countries offered (ISO code to name). "" = same as Windows. */
private val contentCountries = listOf(
    "" to "Igual que Windows",
    "MX" to "México",
    "CO" to "Colombia",
    "AR" to "Argentina",
    "CL" to "Chile",
    "PE" to "Perú",
    "VE" to "Venezuela",
    "EC" to "Ecuador",
    "ES" to "España",
    "US" to "Estados Unidos",
    "BR" to "Brasil",
    "PR" to "Puerto Rico",
    "DO" to "República Dominicana",
    "GB" to "Reino Unido"
)

/** Settings cards (the parent column spaces them) for playback (skip, normalization, speed, quality) and content filters. */
@Composable
fun PlaybackAndContentSettings(appModel: DesktopAppModel, section: SettingsSection) {
    val prefs by appModel.prefs.collectAsState()

    if (section == SettingsSection.APARIENCIA) {
    SettingsCard(title = "APARIENCIA", icon = Icons.Default.Palette) {
        SettingRowFrame {
            SettingTexts("Color de acento", "Botones, canción actual y detalles de toda la app.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                echo.music.desktop.ui.theme.AccentTheme.entries.forEach { accent ->
                    val selected = prefs.themeAccent == accent.name
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accent.color)
                            .border(
                                if (selected) 3.dp else 0.dp,
                                if (selected) Color.White else Color.Transparent,
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .clickable { appModel.updatePreferences { it.copy(themeAccent = accent.name) } }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Portada animada (Canvas)",
            description = "En el reproductor expandido, muestra el video corto de la portada cuando la canción lo tiene (Apple Music / Tidal).",
            checked = prefs.showCanvas,
            onChange = { v -> appModel.updatePreferences { it.copy(showCanvas = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            title = "Fondo",
            description = "Negro puro ahorra energía en pantallas OLED; Grafito es un poco más claro.",
            options = echo.music.desktop.ui.theme.BackgroundTheme.entries.map { it.name to it.label },
            selected = prefs.themeBackground,
            onSelect = { v -> appModel.updatePreferences { it.copy(themeBackground = v) } }
        )
    }
    }

    if (section == SettingsSection.REPRODUCCION) {
    SettingsCard(title = "REPRODUCCIÓN", icon = Icons.Default.GraphicEq) {
        ToggleRow(
            title = "Saltar canción si falla",
            description = "Si una canción no se puede cargar o reproducir, pasa automáticamente a la siguiente.",
            checked = prefs.autoSkipOnError,
            onChange = { v -> appModel.updatePreferences { it.copy(autoSkipOnError = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Normalizar volumen",
            description = "Iguala el volumen entre canciones usando los datos de volumen de YouTube.",
            checked = prefs.normalizeAudio,
            onChange = { v -> appModel.updatePreferences { it.copy(normalizeAudio = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            title = "Nivel de volumen",
            description = "Ajuste extra sobre el volumen de todas las canciones.",
            options = listOf("QUIET" to "Bajo", "NORMAL" to "Normal", "LOUD" to "Alto", "AGGRESSIVE" to "Agresivo"),
            selected = prefs.loudnessPreset,
            onSelect = { v -> appModel.updatePreferences { it.copy(loudnessPreset = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            title = "Velocidad de reproducción",
            description = "Aplica a la canción actual y a las siguientes.",
            options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).map { it to speedLabel(it) },
            selected = prefs.playbackSpeed,
            onSelect = { v -> appModel.updatePreferences { it.copy(playbackSpeed = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "SponsorBlock",
            description = "Salta intros, finales, patrocinios y partes sin música de los videos musicales, marcados por la comunidad de SponsorBlock.",
            checked = prefs.sponsorBlock,
            onChange = { v -> appModel.updatePreferences { it.copy(sponsorBlock = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            title = "Calidad de audio",
            description = "Alta: el mejor audio AAC disponible (~128 kbps). Baja: ahorra datos (~48 kbps). Se aplica desde la próxima canción.",
            options = listOf("HIGH" to "Alta", "LOW" to "Baja"),
            selected = prefs.audioQuality,
            onSelect = { v -> appModel.updatePreferences { it.copy(audioQuality = v) } }
        )
    }
    }

    if (section == SettingsSection.REPRODUCCION) {
    SettingsCard(title = "CONTENIDO", icon = Icons.Default.FilterAlt) {
        ToggleRow(
            title = "Ocultar canciones explícitas",
            description = "Quita del Inicio, la búsqueda y las radios las canciones marcadas como explícitas.",
            checked = prefs.hideExplicit,
            onChange = { v -> appModel.updatePreferences { it.copy(hideExplicit = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Ocultar videos musicales",
            description = "Muestra solo canciones de audio (la categoría Videos de la búsqueda no se filtra).",
            checked = prefs.hideVideoSongs,
            onChange = { v -> appModel.updatePreferences { it.copy(hideVideoSongs = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Ocultar YouTube Shorts",
            description = "Quita las listas de Shorts del Inicio y la búsqueda.",
            checked = prefs.hideShorts,
            onChange = { v -> appModel.updatePreferences { it.copy(hideShorts = v) } }
        )
        Spacer(Modifier.height(10.dp))
        DropdownRow(
            title = "Idioma del contenido",
            description = "Idioma de los títulos y secciones que devuelve YouTube Music.",
            options = contentLanguages,
            selected = prefs.contentLanguage,
            onSelect = { v -> appModel.updatePreferences { it.copy(contentLanguage = v) } }
        )
        Spacer(Modifier.height(10.dp))
        DropdownRow(
            title = "País del contenido",
            description = "Cambia las tendencias y recomendaciones del Inicio.",
            options = contentCountries,
            selected = prefs.contentCountry,
            onSelect = { v -> appModel.updatePreferences { it.copy(contentCountry = v) } }
        )
    }
    }

    if (section == SettingsSection.LETRAS) {
    SettingsCard(title = "LETRAS: ROMANIZACIÓN Y TRADUCCIÓN", icon = Icons.Default.Translate) {
        ToggleRow(
            title = "Romanizar letras",
            description = "Muestra cómo suenan en letras latinas las canciones en japonés, coreano, chino, ruso y otros alfabetos cirílicos, hindi y punjabi. Funciona sin internet.",
            checked = prefs.lyricsRomanize,
            onChange = { v -> appModel.updatePreferences { it.copy(lyricsRomanize = v) } }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "La traducción usa inteligencia artificial con tu propia clave, igual que la app del celular. " +
                "OpenRouter tiene modelos gratuitos (openrouter.ai → Keys) y DeepL una cuenta gratuita (deepl.com → API).",
            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            title = "Servicio de traducción",
            description = "Dónde se envía la letra para traducirla.",
            options = TranslationProvider.entries.map { it.name to it.displayName },
            selected = prefs.translationProvider,
            onSelect = { v -> appModel.updatePreferences { it.copy(translationProvider = v, translationModel = "") } }
        )
        Spacer(Modifier.height(10.dp))
        val provider = TranslationProvider.from(prefs.translationProvider)
        SecretRow(
            appModel = appModel,
            title = "Clave de ${provider.displayName}",
            description = "Se guarda cifrada con tu usuario de Windows.",
            hasValue = prefs.translationApiKey.isNotBlank(),
            onSave = { appModel.setTranslationApiKey(it) }
        )
        Spacer(Modifier.height(10.dp))
        DropdownRow(
            title = "Traducir al",
            description = "Idioma de la traducción.",
            options = translationLanguages,
            selected = prefs.translationLanguage,
            onSelect = { v -> appModel.updatePreferences { it.copy(translationLanguage = v) } }
        )
        if (provider != TranslationProvider.DEEPL) {
            Spacer(Modifier.height(10.dp))
            TextRow(
                appModel = appModel,
                title = "Modelo (opcional)",
                description = "Vacío = ${provider.defaultModel}.",
                value = prefs.translationModel,
                placeholder = provider.defaultModel,
                onSave = { v -> appModel.updatePreferences { it.copy(translationModel = v.trim()) } }
            )
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Traducir automáticamente",
            description = "Traduce cada letra apenas se carga (usa tu clave en cada canción).",
            checked = prefs.lyricsAutoTranslate,
            onChange = { v -> appModel.updatePreferences { it.copy(lyricsAutoTranslate = v) } }
        )
    }
    }

    if (section == SettingsSection.REPRODUCCION) {
    MusicFlowSettings(appModel)
    }

    if (section == SettingsSection.PRIVACIDAD) {
    PrivacySettings(appModel)
    }

    if (section == SettingsSection.CUENTAS) {
    ScrobbleSettings(appModel)
    }

    if (section == SettingsSection.PRIVACIDAD) {
    BlocksAndBackupSettings(appModel)
    }
}

@Composable
private fun PrivacySettings(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    val history by appModel.history.collectAsState()
    val recognitions by appModel.recognitionHistory.collectAsState()
    var confirm by remember { mutableStateOf<String?>(null) }

    SettingsCard(title = "PRIVACIDAD", icon = Icons.Default.Lock) {
        ToggleRow(
            title = "Pausar historial",
            description = "Mientras está activo, lo que escuchas no se guarda en el historial, en Echo Wrapped ni en el historial de Echo Find.",
            checked = prefs.pauseHistory,
            onChange = { v -> appModel.updatePreferences { it.copy(pauseHistory = v) } }
        )
        Spacer(Modifier.height(10.dp))
        SettingRowFrame {
            SettingTexts("Historial de escucha", "${history.size} canciones guardadas.")
            TextButton(onClick = { confirm = "listen" }, enabled = history.isNotEmpty()) {
                Text("Borrar", color = if (history.isNotEmpty()) NothingColors.AccentRed else NothingColors.TextMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingRowFrame {
            SettingTexts("Historial de Echo Find", "${recognitions.size} canciones reconocidas.")
            TextButton(onClick = { confirm = "find" }, enabled = recognitions.isNotEmpty()) {
                Text("Borrar", color = if (recognitions.isNotEmpty()) NothingColors.AccentRed else NothingColors.TextMuted)
            }
        }
    }

    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = NothingColors.SurfaceElevated,
            title = { Text(if (which == "listen") "¿Borrar el historial de escucha?" else "¿Borrar el historial de Echo Find?", color = NothingColors.TextPrimary) },
            text = { Text("No se puede deshacer.", color = NothingColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    if (which == "listen") appModel.clearListenHistory() else appModel.clearRecognitionHistory()
                    confirm = null
                }) { Text("Borrar", color = NothingColors.AccentRed) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar", color = NothingColors.TextSecondary) } }
        )
    }
}

@Composable
private fun ScrobbleSettings(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    val pendingToken by appModel.lastFmPendingToken.collectAsState()

    SettingsCard(title = "REGISTRO DE ESCUCHAS (SCROBBLING)", icon = Icons.Default.History) {
        Text(
            "Guarda en tu perfil lo que escuchas, como en el celular. Una canción cuenta cuando escuchas la mitad o 4 minutos.",
            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        SecretRow(
            appModel = appModel,
            title = "ListenBrainz",
            description = "Pega tu token de usuario (listenbrainz.org → Settings → User token).",
            hasValue = prefs.listenBrainzToken.isNotBlank(),
            onSave = { appModel.setListenBrainzToken(it) }
        )
        Spacer(Modifier.height(10.dp))

        if (prefs.lastFmUsername.isNotBlank() && prefs.lastFmSessionKey.isNotBlank()) {
            SettingRowFrame {
                SettingTexts("Last.fm", "Conectado como ${prefs.lastFmUsername}.")
                TextButton(onClick = { appModel.disconnectLastFm() }) { Text("Desconectar", color = NothingColors.AccentRed) }
            }
        } else {
            TextRow(
                appModel = appModel,
                title = "Last.fm: API key",
                description = "Crea una cuenta de API gratis en last.fm/api/account/create (Echo Music del celular trae la suya; en PC usas la tuya).",
                value = prefs.lastFmApiKey,
                placeholder = "API key",
                onSave = { appModel.setLastFmCredentials(it, "") }
            )
            Spacer(Modifier.height(10.dp))
            SecretRow(
                appModel = appModel,
                title = "Last.fm: shared secret",
                description = "El \"Shared secret\" de esa misma cuenta de API.",
                hasValue = prefs.lastFmSecret.isNotBlank(),
                onSave = { appModel.setLastFmCredentials(prefs.lastFmApiKey, it) }
            )
            Spacer(Modifier.height(10.dp))
            SettingRowFrame {
                SettingTexts(
                    "Conectar tu cuenta de Last.fm",
                    if (pendingToken == null) "Se abrirá last.fm en el navegador para que autorices Echo Music."
                    else "Autoriza Echo Music en la página que se abrió y luego pulsa \"Ya lo autoricé\"."
                )
                if (pendingToken == null) {
                    Button(
                        onClick = { appModel.startLastFmLogin() },
                        enabled = prefs.lastFmApiKey.isNotBlank() && prefs.lastFmSecret.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Conectar con Last.fm") }
                } else {
                    Button(
                        onClick = { appModel.finishLastFmLogin() },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Ya lo autoricé") }
                }
            }
        }
    }
}

@Composable
private fun BlocksAndBackupSettings(appModel: DesktopAppModel) {
    val blocks by appModel.contentBlocks.collectAsState()

    SettingsCard(title = "BLOQUEOS", icon = Icons.Default.Block) {
        Text(
            "Los artistas bloqueados y las canciones marcadas con \"No me gusta\" no aparecen en Inicio, " +
                "Descubrir, búsquedas ni radios. Usa el menú ⋮ de una canción o la ficha del artista.",
            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        BlockList(
            title = "Artistas bloqueados (${blocks.artists.size})",
            empty = "No has bloqueado artistas.",
            items = blocks.artists.map { it.name to { appModel.unblockArtist(it) } },
            actionLabel = "Desbloquear"
        )
        Spacer(Modifier.height(10.dp))
        BlockList(
            title = "Canciones con \"No me gusta\" (${blocks.songs.size})",
            empty = "No has descartado canciones.",
            items = blocks.songs.map { s -> (if (s.artist.isBlank()) s.title else "${s.title} • ${s.artist}") to { appModel.undislikeSong(s) } },
            actionLabel = "Quitar"
        )
    }

    SettingsCard(title = "COPIA DE SEGURIDAD", icon = Icons.Default.Backup) {
        Text(
            "Guarda en un archivo .zip tus playlists, favoritos, historial, estadísticas, ajustes, ecualizador y bloqueos. " +
                "No incluye tu sesión de YouTube, las descargas ni las claves.",
            style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    chooseFile(save = true, title = "Guardar copia de seguridad",
                        defaultName = "EchoMusic-copia-${java.time.LocalDate.now()}.zip")
                        ?.let { appModel.exportBackup(it) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NothingColors.AccentRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Exportar copia") }
            OutlinedButton(
                onClick = {
                    chooseFile(save = false, title = "Restaurar copia de seguridad", defaultName = null)
                        ?.let { appModel.importBackup(it) }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Importar copia") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Al importar se reemplazan esos datos por los de la copia.",
            style = MaterialTheme.typography.labelSmall.copy(color = NothingColors.TextMuted),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Windows file dialog (blocks until the user picks a file). Returns null when cancelled. */
private fun chooseFile(save: Boolean, title: String, defaultName: String?): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD)
    dialog.file = defaultName ?: "*.zip"
    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".zip") }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val file = java.io.File(dialog.directory, name)
    return if (save && !file.name.lowercase().endsWith(".zip")) java.io.File(file.parentFile, file.name + ".zip") else file
}

@Composable
private fun BlockList(title: String, empty: String, items: List<Pair<String, () -> Unit>>, actionLabel: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NothingColors.SurfaceElevated)
            .padding(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary))
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextMuted))
        }
        items.forEach { (label, onAction) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAction) { Text(actionLabel, color = NothingColors.AccentRed) }
            }
        }
    }
}

/** Password-style field for an API key: shows only whether one is saved, never the key. */
@Composable
private fun SecretRow(
    appModel: DesktopAppModel,
    title: String,
    description: String,
    hasValue: Boolean,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }
    SettingRowFrame {
        SettingTexts(title, if (hasValue) "$description Hay una clave guardada." else description)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text(if (hasValue) "••••••••" else "Pega tu clave", color = NothingColors.TextMuted) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(230.dp).onFocusChanged { appModel.isInputFocused = it.isFocused },
            colors = fieldColors()
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { onSave(input); input = "" }, enabled = input.isNotBlank()) {
            Text("Guardar", color = if (input.isNotBlank()) NothingColors.AccentRed else NothingColors.TextMuted)
        }
        if (hasValue) {
            TextButton(onClick = { onSave("") }) { Text("Quitar", color = NothingColors.TextSecondary) }
        }
    }
}

@Composable
private fun TextRow(
    appModel: DesktopAppModel,
    title: String,
    description: String,
    value: String,
    placeholder: String,
    onSave: (String) -> Unit
) {
    var input by remember(value) { mutableStateOf(value) }
    DisposableEffect(Unit) { onDispose { appModel.isInputFocused = false } }
    SettingRowFrame {
        SettingTexts(title, description)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text(placeholder, color = NothingColors.TextMuted) },
            modifier = Modifier.width(230.dp).onFocusChanged { appModel.isInputFocused = it.isFocused },
            colors = fieldColors()
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { onSave(input) }, enabled = input != value) {
            Text("Guardar", color = if (input != value) NothingColors.AccentRed else NothingColors.TextMuted)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NothingColors.AccentRed,
    unfocusedBorderColor = NothingColors.SurfaceBorder,
    cursorColor = NothingColors.AccentRed,
    focusedTextColor = NothingColors.TextPrimary,
    unfocusedTextColor = NothingColors.TextPrimary
)

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "x"

@Composable
internal fun SettingRowFrame(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NothingColors.SurfaceElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

@Composable
internal fun RowScope.SettingTexts(title: String, description: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NothingColors.TextPrimary)
        )
        Spacer(Modifier.height(2.dp))
        Text(text = description, style = MaterialTheme.typography.bodySmall.copy(color = NothingColors.TextSecondary))
    }
    Spacer(Modifier.width(16.dp))
}

@Composable
internal fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRowFrame {
        SettingTexts(title, description)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NothingColors.AccentRed,
                uncheckedThumbColor = NothingColors.TextMuted,
                uncheckedTrackColor = NothingColors.Surface
            )
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    SettingRowFrame {
        SettingTexts(title, description)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NothingColors.AccentRed else NothingColors.Surface)
                        .border(1.dp, if (isSelected) NothingColors.AccentRed else NothingColors.SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) Color.White else NothingColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownRow(
    title: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    SettingRowFrame {
        SettingTexts(title, description)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NothingColors.Surface)
                    .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: selected,
                    style = MaterialTheme.typography.labelMedium.copy(color = NothingColors.TextPrimary)
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = NothingColors.TextSecondary)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(NothingColors.SurfaceElevated)
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = if (value == selected) NothingColors.AccentRed else NothingColors.TextPrimary
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

/** Headphone options (inside the system integration card). */
@Composable
fun HeadphoneSettingsRows(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    ToggleRow(
        title = "Pausar al desconectar los audífonos",
        description = "Si se apagan o desconectan tus audífonos Bluetooth o con cable, la música se pausa en vez de salir por las bocinas.",
        checked = prefs.pauseOnHeadphonesDisconnect,
        onChange = { v -> appModel.updatePreferences { it.copy(pauseOnHeadphonesDisconnect = v) } }
    )
    if (prefs.pauseOnHeadphonesDisconnect) {
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Seguir al reconectarlos",
            description = "Cuando esos mismos audífonos vuelven (en menos de 30 minutos), la música continúa sola.",
            checked = prefs.resumeOnHeadphonesReconnect,
            onChange = { v -> appModel.updatePreferences { it.copy(resumeOnHeadphonesReconnect = v) } }
        )
    }
}

/** Dynamic island options (inside the system integration card). */
@Composable
fun IslandSettingsRows(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    ToggleRow(
        title = "Isla dinámica",
        description = "Una píldora arriba de la pantalla, encima de cualquier programa. Pasa el mouse para ver el reproductor, la cola, los controles y la letra.",
        checked = prefs.dynamicIsland,
        onChange = { v -> appModel.updatePreferences { it.copy(dynamicIsland = v) } }
    )
    if (prefs.dynamicIsland) {
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Ocultar en pantalla completa",
            description = "Se esconde mientras juegas o ves un video a pantalla completa.",
            checked = prefs.islandHideFullscreen,
            onChange = { v -> appModel.updatePreferences { it.copy(islandHideFullscreen = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Mostrar sin música",
            description = "Deja la hora visible aunque no suene nada.",
            checked = prefs.islandShowIdle,
            onChange = { v -> appModel.updatePreferences { it.copy(islandShowIdle = v) } }
        )
    }
}

/** "Iniciar con Windows" and "Abrir en la bandeja" rows (inside the system integration card). */
@Composable
fun StartupSettingsRows(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    val startWithWindows by appModel.startWithWindows.collectAsState()
    val supported = echo.music.desktop.system.WindowsStartup.isSupported
    SettingRowFrame {
        SettingTexts(
            "Iniciar con Windows",
            if (supported) "Echo Music se abre solo al encender la PC."
            else "Disponible en la versión instalada (no al abrirla con run-desktop.bat)."
        )
        Switch(
            checked = startWithWindows,
            enabled = supported || startWithWindows,
            onCheckedChange = { appModel.setStartWithWindows(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NothingColors.AccentRed,
                uncheckedThumbColor = NothingColors.TextMuted,
                uncheckedTrackColor = NothingColors.Surface
            )
        )
    }
    if (startWithWindows) {
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Abrir en la bandeja",
            description = "Al iniciar con Windows queda junto al reloj, sin mostrar la ventana.",
            checked = prefs.startMinimized,
            onChange = { appModel.setStartMinimized(it) }
        )
    }
}

@Composable
fun UpdateSettings(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    val update by appModel.update.collectAsState()

    SettingsCard(title = "ACTUALIZACIONES", icon = Icons.Default.SystemUpdate) {
        SettingRowFrame {
            SettingTexts(
                "Versión ${echo.music.desktop.system.AppInfo.version}",
                update.message ?: "Busca versiones nuevas de Echo Music."
            )
            when {
                update.readyVersion != null -> TextButton(onClick = { appModel.restartToUpdate() }) {
                    Text("Reiniciar y actualizar", color = NothingColors.AccentRed)
                }
                update.downloading != null -> Text(
                    "Descargando ${((update.downloading ?: 0f) * 100).toInt()}%",
                    color = NothingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                else -> update.available?.let { info ->
                    TextButton(onClick = { appModel.openUpdatePage() }) {
                        Text("Descargar ${info.version}", color = NothingColors.AccentRed)
                    }
                }
            }
            TextButton(onClick = { appModel.checkForUpdates(manual = true) }, enabled = !update.checking) {
                if (update.checking) {
                    CircularProgressIndicator(color = NothingColors.AccentRed, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Buscar ahora", color = NothingColors.AccentRed)
                }
            }
        }
        update.lastFailure?.let { failure ->
            val time = java.time.Instant.ofEpochMilli(failure.at).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d/M HH:mm"))
            Spacer(Modifier.height(10.dp))
            SettingRowFrame {
                SettingTexts(
                    "No se pudo instalar la versión ${failure.version}",
                    "${echo.music.desktop.system.UpdateInstaller.describe(failure.code).replaceFirstChar { it.uppercase() }} (código ${failure.code}), $time."
                )
                TextButton(onClick = { appModel.openUpdateFolder() }) {
                    Text("Ver registro", color = NothingColors.AccentRed)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Buscar al iniciar",
            description = "Al abrir Echo Music y cada pocas horas mientras está abierto.",
            checked = prefs.checkUpdates,
            onChange = { v -> appModel.updatePreferences { it.copy(checkUpdates = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Instalar actualizaciones solas",
            description = if (appModel.canAutoInstallUpdates) {
                "La descarga en segundo plano y la instala sin pedir permisos al cerrar Echo Music, o sola si está en la bandeja sin sonar nada."
            } else {
                "Solo en la versión instalada con el instalador. Aquí solo te avisa y abre la página de descarga."
            },
            checked = prefs.autoInstallUpdates,
            onChange = { v -> appModel.updatePreferences { it.copy(autoInstallUpdates = v) } }
        )
    }
}

@Composable
private fun MusicFlowSettings(appModel: DesktopAppModel) {
    val prefs by appModel.prefs.collectAsState()
    SettingsCard(title = "MUSIC FLOW", icon = Icons.Default.Waves) {
        ToggleRow(
            title = "Reproducción automática",
            description = "Empieza a reproducir en cuanto se muestra una canción.",
            checked = prefs.flowAutoplay,
            onChange = { v -> appModel.updatePreferences { it.copy(flowAutoplay = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Avance automático",
            description = "Pasa a la siguiente canción cuando termina una.",
            checked = prefs.flowAutoNext,
            onChange = { v -> appModel.updatePreferences { it.copy(flowAutoNext = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Vista previa de la letra",
            description = "Muestra la línea actual de la letra en cada canción.",
            checked = prefs.flowLyrics,
            onChange = { v -> appModel.updatePreferences { it.copy(flowLyrics = v) } }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Ocultar reproducidas recientemente",
            description = "Omite las canciones que has escuchado hace poco.",
            checked = prefs.flowHideRecent,
            onChange = { v -> appModel.updatePreferences { it.copy(flowHideRecent = v) } }
        )
    }
}

/** Spotify account (Ajustes → Cuentas): sign in/out; the library itself is in Biblioteca → Spotify. */
@Composable
fun SpotifyAccountSettings(appModel: DesktopAppModel) {
    val profile by appModel.spotifyProfile.collectAsState()
    val signingIn by appModel.spotifySigningIn.collectAsState()
    val status by appModel.spotifyStatus.collectAsState()
    SettingsCard(title = "SPOTIFY", icon = Icons.Default.LibraryMusic) {
        SettingRowFrame {
            SettingTexts(
                profile?.name ?: "Sin conectar",
                if (profile != null) "Tus playlists y Me gusta están en Biblioteca → Spotify. También activa los Canvas de Spotify."
                else status ?: "Conecta tu cuenta para ver tus playlists de Spotify aquí y usar sus Canvas."
            )
            if (profile != null) {
                TextButton(onClick = {
                    appModel.selectTab(echo.music.desktop.state.NavigationTab.LIBRARY)
                    appModel.selectLibrarySubTab(echo.music.desktop.state.LibrarySubTab.SPOTIFY)
                }) { Text("Ver biblioteca", color = NothingColors.AccentRed) }
                TextButton(onClick = { appModel.signOutSpotify() }) { Text("Cerrar sesión", color = NothingColors.TextMuted) }
            } else if (signingIn) {
                TextButton(onClick = { appModel.cancelSpotifySignIn() }) { Text("Cancelar", color = NothingColors.TextMuted) }
            } else {
                TextButton(onClick = { appModel.signInSpotify() }) { Text("Iniciar sesión", color = Color(0xFF1DB954)) }
            }
        }
    }
}
