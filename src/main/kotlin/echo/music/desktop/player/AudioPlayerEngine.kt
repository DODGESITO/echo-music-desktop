package echo.music.desktop.player

import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val videoId: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isBuffering: Boolean = false,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

interface AudioPlayerEngine {
    val state: StateFlow<PlaybackState>
    fun play(videoId: String, url: String, title: String, artist: String, artworkUrl: String?, durationMs: Long)
    fun togglePlayPause()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setEqualizerEnabled(enabled: Boolean)
    fun setEqualizerBands(gains: List<Float>)
    fun setCrossfadeDuration(seconds: Int)
    fun setOnTrackEnded(callback: () -> Unit)
    /** Extra gain on top of the user volume (loudness normalization). 1.0 = unchanged. */
    fun setVolumeGain(gain: Float) {}
    /** Playback speed (0.5x – 2x). Applies to the current and next tracks. */
    fun setPlaybackRate(rate: Float) {}
    /** Same song, another source (e.g. its music video file), continuing at [positionMs]. */
    fun switchSource(url: String, positionMs: Long) {}
    fun release()
}
