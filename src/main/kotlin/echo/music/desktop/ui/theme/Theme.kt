package echo.music.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/** Accent colors offered in Ajustes → Apariencia. */
enum class AccentTheme(val label: String, val color: Color) {
    RED("Echo", Color(0xFFED5564)),
    BLUE("Azul", Color(0xFF4F8EF7)),
    GREEN("Verde", Color(0xFF3DDC84)),
    PURPLE("Morado", Color(0xFF9C6ADE)),
    ORANGE("Naranja", Color(0xFFFF8A3D)),
    PINK("Rosa", Color(0xFFFF5FA2)),
    CYAN("Cian", Color(0xFF22C3D6)),
    YELLOW("Amarillo", Color(0xFFF5C542));

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: RED
    }
}

/** Background styles offered in Ajustes → Apariencia. */
enum class BackgroundTheme(
    val label: String,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceBorder: Color
) {
    DARK("Oscuro", Color(0xFF080808), Color(0xFF121212), Color(0xFF1A1A1A), Color(0xFF262626)),
    BLACK("Negro puro", Color(0xFF000000), Color(0xFF0B0B0B), Color(0xFF141414), Color(0xFF222222)),
    GRAPHITE("Grafito", Color(0xFF141416), Color(0xFF1E1E21), Color(0xFF28282C), Color(0xFF36363B));

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: DARK
    }
}

/**
 * App colors. Backed by Compose state, so changing the theme recolors every screen at once
 * (composables re-read these getters).
 */
object NothingColors {
    private val accent = mutableStateOf(AccentTheme.RED)
    private val backgroundTheme = mutableStateOf(BackgroundTheme.DARK)

    val Background: Color get() = backgroundTheme.value.background
    val Surface: Color get() = backgroundTheme.value.surface
    val SurfaceElevated: Color get() = backgroundTheme.value.surfaceElevated
    val SurfaceBorder: Color get() = backgroundTheme.value.surfaceBorder
    val PrimaryRed: Color get() = accent.value.color
    /** The accent color (named "Red" for historical reasons; follows the chosen theme). */
    val AccentRed: Color get() = accent.value.color
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextMuted = Color(0xFF606060)

    fun applyTheme(accentName: String, backgroundName: String) {
        accent.value = AccentTheme.from(accentName)
        backgroundTheme.value = BackgroundTheme.from(backgroundName)
    }
}

@Composable
fun EchoDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NothingColors.AccentRed,
            onPrimary = Color.White,
            background = NothingColors.Background,
            onBackground = NothingColors.TextPrimary,
            surface = NothingColors.Surface,
            onSurface = NothingColors.TextPrimary,
            surfaceVariant = NothingColors.SurfaceElevated,
            onSurfaceVariant = NothingColors.TextSecondary,
            outline = NothingColors.SurfaceBorder
        ),
        content = content
    )
}
