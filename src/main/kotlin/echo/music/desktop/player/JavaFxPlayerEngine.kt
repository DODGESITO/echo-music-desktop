package echo.music.desktop.player

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

class JavaFxPlayerEngine : AudioPlayerEngine {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var progressJob: Job? = null

    private var currentEqEnabled = false
    private var currentEqGains = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private var crossfadeDurationSeconds = 0
    @Volatile private var volumeGain = 1.0
    @Volatile private var playbackRate = 1.0

    /** What the speakers get: user volume x normalization gain, never above 100%. */
    private fun effectiveVolume(): Double = (_state.value.volume * volumeGain).coerceIn(0.0, 1.0)

    companion object {
        private val isFxInitialized = AtomicBoolean(false)
        private val fxReady = java.util.concurrent.CountDownLatch(1)

        /**
         * Starts the JavaFX toolkit once and waits (up to 5 s) until it is ready, so callers can
         * use Platform.runLater right away (it throws "Toolkit not initialized" before that).
         */
        fun ensureFxInitialized() {
            if (isFxInitialized.compareAndSet(false, true)) {
                Thread {
                    try {
                        Platform.startup { fxReady.countDown() }
                        Platform.setImplicitExit(false)
                    } catch (ignored: IllegalStateException) {
                        // Already started elsewhere.
                        fxReady.countDown()
                    } catch (ignored: Throwable) {
                        fxReady.countDown()
                    }
                }.apply {
                    isDaemon = true
                    name = "JavaFxAudioInit"
                    start()
                }
            }
            fxReady.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private var trackEndedCallback: (() -> Unit)? = null

    init {
        ensureFxInitialized()
    }

    override fun play(videoId: String, url: String, title: String, artist: String, artworkUrl: String?, durationMs: Long) {
        _state.update {
            it.copy(
                videoId = videoId,
                isBuffering = true,
                isPlaying = false,
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                currentPositionMs = 0L,
                errorMessage = null
            )
        }

        Platform.runLater {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()

                val media = Media(url)
                val player = MediaPlayer(media)
                mediaPlayer = player

                // Apply initial volume (with normalization) and speed
                player.volume = effectiveVolume()
                player.rate = playbackRate

                // Apply hardware/native AudioEqualizer
                try {
                    val eq = player.audioEqualizer
                    eq.isEnabled = currentEqEnabled
                    for (i in 0 until minOf(eq.bands.size, currentEqGains.size)) {
                        eq.bands[i].gain = currentEqGains[i].toDouble()
                    }
                } catch (ignored: Throwable) {}

                player.setOnReady {
                    val actualDuration = media.duration.toMillis().toLong().coerceAtLeast(durationMs)
                    _state.update { it.copy(durationMs = actualDuration, isBuffering = false, isPlaying = true) }
                    player.rate = playbackRate
                    player.play()
                    startProgressTracking()
                }

                player.setOnPlaying {
                    _state.update { it.copy(isPlaying = true, isBuffering = false) }
                }

                player.setOnPaused {
                    _state.update { it.copy(isPlaying = false) }
                }

                player.setOnEndOfMedia {
                    _state.update { it.copy(isPlaying = false, currentPositionMs = it.durationMs) }
                    stopProgressTracking()
                    trackEndedCallback?.invoke()
                }

                player.setOnError {
                    val err = player.error?.message ?: "Error al reproducir audio"
                    println("MediaPlayer error: $err")
                    _state.update { it.copy(isPlaying = false, isBuffering = false, errorMessage = err) }
                    stopProgressTracking()
                }

                player.play()
            } catch (e: Exception) {
                println("Failed to start media player: ${e.message}")
                _state.update { it.copy(isBuffering = false, isPlaying = false, errorMessage = e.message) }
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                delay(200)
                mediaPlayer?.let { player ->
                    Platform.runLater {
                        val pos = player.currentTime.toMillis().toLong()
                        val total = _state.value.durationMs
                        val baseVol = effectiveVolume()

                        // Smooth Crossfade volume envelope
                        if (crossfadeDurationSeconds > 0 && total > 2000L) {
                            val cfMs = (crossfadeDurationSeconds * 1000L).coerceAtMost(total / 2)
                            val remaining = total - pos

                            if (remaining in 0..cfMs) {
                                // Fade out at end of track
                                val factor = (remaining.toDouble() / cfMs.toDouble()).coerceIn(0.0, 1.0)
                                player.volume = baseVol * factor
                            } else if (pos < 800L) {
                                // Fade in at beginning of track
                                val factor = (pos.toDouble() / 800.0).coerceIn(0.0, 1.0)
                                player.volume = baseVol * factor
                            } else {
                                player.volume = baseVol
                            }
                        }

                        _state.update { it.copy(currentPositionMs = pos) }
                    }
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else resume()
    }

    override fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    override fun resume() {
        Platform.runLater {
            mediaPlayer?.play()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    override fun seekTo(positionMs: Long) {
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
            _state.update { it.copy(currentPositionMs = positionMs) }
        }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _state.update { it.copy(volume = clamped) }
        Platform.runLater {
            mediaPlayer?.volume = effectiveVolume()
        }
    }

    override fun setVolumeGain(gain: Float) {
        volumeGain = gain.toDouble().coerceIn(0.05, 4.0)
        Platform.runLater {
            mediaPlayer?.volume = effectiveVolume()
        }
    }

    override fun setPlaybackRate(rate: Float) {
        playbackRate = rate.toDouble().coerceIn(0.5, 2.0)
        Platform.runLater {
            mediaPlayer?.rate = playbackRate
        }
    }

    override fun setEqualizerEnabled(enabled: Boolean) {
        currentEqEnabled = enabled
        Platform.runLater {
            mediaPlayer?.audioEqualizer?.isEnabled = enabled
        }
    }

    override fun setEqualizerBands(gains: List<Float>) {
        currentEqGains = gains
        Platform.runLater {
            mediaPlayer?.let { player ->
                val eq = player.audioEqualizer
                for (i in 0 until minOf(eq.bands.size, gains.size)) {
                    eq.bands[i].gain = gains[i].toDouble()
                }
            }
        }
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = seconds.coerceIn(0, 15)
    }

    override fun setOnTrackEnded(callback: () -> Unit) {
        trackEndedCallback = callback
    }

    override fun release() {
        stopProgressTracking()
        scope.cancel()
        Platform.runLater {
            mediaPlayer?.stop()
            mediaPlayer?.dispose()
            mediaPlayer = null
        }
    }
}
