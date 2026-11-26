package echo.music.desktop.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Manual visual check (-DcanvasWindow=true): shows a canvas in a real Compose window for 12 s. */
class CanvasWindowProbe {
    @Test fun show() {
        if (System.getProperty("canvasWindow") != "true") return
        val file = runBlocking { CanvasService.canvasFor("probe-ojitos", "Ojitos Lindos", "Bad Bunny", "Un Verano Sin Ti") } ?: return
        application(exitProcessOnExit = false) {
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(12_000); exitApplication() }
            Window(onCloseRequest = ::exitApplication, title = "Echo Music Canvas Test", state = rememberWindowState(size = DpSize(460.dp, 480.dp))) {
                Box(Modifier.fillMaxSize().background(Color(0xFF080808)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(340.dp).clip(RoundedCornerShape(20.dp)).background(Color.DarkGray)) {
                        CanvasVideoView(file, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
