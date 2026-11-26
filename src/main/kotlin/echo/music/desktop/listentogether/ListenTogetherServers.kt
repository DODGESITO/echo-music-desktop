package echo.music.desktop.listentogether

import kotlinx.serialization.Serializable

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String = ""
)

object ListenTogetherServers {
    val defaultServers = listOf(
        ListenTogetherServer(
            name = "Metrolist Server",
            url = "wss://metroserverx.meowery.eu/ws",
            location = "Europa / Metro",
            operator = "Metrolist (Recomendado)"
        ),
        ListenTogetherServer(
            name = "Hugging Face Sync",
            url = "wss://devilmi-vivi-music-listen-together.hf.space",
            location = "Global / HF Spaces",
            operator = "Echo / Vivi"
        )
    )

    val defaultServerUrl: String = defaultServers.first().url
}
