package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.desktop.state.DesktopAppModel
import echo.music.desktop.state.EqualizerStore
import echo.music.desktop.ui.theme.NothingColors
import kotlin.math.roundToInt

@Composable
fun EqualizerModal(
    appModel: DesktopAppModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eqSettings by appModel.equalizerSettings.collectAsState()

    // Semi-transparent backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        // Modal Card
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
                .padding(26.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                                .background(if (eqSettings.enabled) NothingColors.AccentRed else NothingColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ECUALIZADOR GRÁFICO • 10 BANDAS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = NothingColors.TextSecondary
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Master Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (eqSettings.enabled) "ACTIVO" else "DESACTIVADO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (eqSettings.enabled) NothingColors.AccentRed else NothingColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = eqSettings.enabled,
                                onCheckedChange = { appModel.setEqualizerEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NothingColors.AccentRed,
                                    uncheckedThumbColor = NothingColors.TextMuted,
                                    uncheckedTrackColor = NothingColors.Surface
                                )
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Presets Horizontal Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PREAJUSTES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NothingColors.TextMuted,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EqualizerStore.PRESETS.forEach { preset ->
                            val isSelected = eqSettings.currentPresetName == preset.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NothingColors.AccentRed else NothingColors.Surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NothingColors.AccentRed else NothingColors.SurfaceBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        appModel.applyEqualizerPreset(preset)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NothingColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 10-Band Sliders Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NothingColors.Surface)
                        .border(1.dp, NothingColors.SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bandLabels = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

                        bandLabels.forEachIndexed { index, label ->
                            val gain = eqSettings.bandGains.getOrElse(index) { 0f }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // dB value
                                Text(
                                    text = if (gain > 0) "+${gain.roundToInt()}" else "${gain.roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (gain != 0f && eqSettings.enabled) NothingColors.AccentRed else NothingColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )

                                // Vertical Bar Slider (-12 to +12 dB)
                                VerticalGainBar(
                                    gain = gain,
                                    enabled = eqSettings.enabled,
                                    onGainChange = { newGain ->
                                        appModel.setEqualizerBandGain(index, newGain)
                                    }
                                )

                                // Frequency label
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Bottom actions: Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rango: -12 dB a +12 dB • Procesamiento por hardware",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NothingColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            val flat = EqualizerStore.PRESETS.first { it.name == "Plano" }
                            appModel.applyEqualizerPreset(flat)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingColors.TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingColors.SurfaceBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = NothingColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Restablecer",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalGainBar(
    gain: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    // Height 140dp vertical slider
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(130.dp),
        contentAlignment = Alignment.Center
    ) {
        // Track
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(NothingColors.SurfaceElevated)
        )

        // Center 0 dB marker
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(NothingColors.SurfaceBorder)
        )

        // Clickable / Draggable area using slider
        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -12f..12f,
            steps = 23,
            enabled = enabled,
            modifier = Modifier
                .requiredWidth(130.dp)
                .requiredHeight(28.dp)
                .rotate(-90f),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) NothingColors.AccentRed else NothingColors.TextMuted,
                activeTrackColor = if (enabled) NothingColors.AccentRed else NothingColors.TextMuted,
                inactiveTrackColor = NothingColors.SurfaceElevated
            )
        )
    }
}
