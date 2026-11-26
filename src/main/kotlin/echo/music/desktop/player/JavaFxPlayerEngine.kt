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

    // Seeking in streamed songs (all FX thread except the copy download).
    private var generation = 0
    private var streamUrl: String? = null
    private var seekableCopy: java.io.File? = null
    private var usingCopy = false
    private var pendingSeekMs: Long? = null
    private var copyJob: Job? = null

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
            generation++
            streamUrl = url.takeIf { it.startsWith("http") }
            seekableCopy = null
            usingCopy = false
            pendingSeekMs = null
            copyJob?.cancel()
            copyJob = null
            createPlayer(url, startAtMs = null, autoPlay = true)
        }
    }

    /** Builds the JavaFX player for [url] (FX thread). [startAtMs] is applied once the media is ready. */
    private fun createPlayer(url: String, startAtMs: Long?, autoPlay: Boolean) {
        try {
            disposePlayer()

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
                val actualDuration = media.duration.toMillis().toLong().coerceAtLeast(_state.value.durationMs)
                _state.update { it.copy(durationMs = actualDuration, isBuffering = false, isPlaying = autoPlay) }
                player.rate = playbackRate
                if (startAtMs != null) player.seek(Duration.millis(startAtMs.toDouble()))
                if (autoPlay) player.play()
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

            if (autoPlay) player.play()
        } catch (e: Exception) {
            println("Failed to start media player: ${e.message}")
            _state.update { it.copy(isBuffering = false, isPlaying = false, errorMessage = e.message) }
        }
    }

    /** Stops the current player without letting its last events touch the new one's state. */
    private fun disposePlayer() {
        mediaPlayer?.let { old ->
            old.onReady = null
            old.onPlaying = null
            old.onPaused = null
            old.onEndOfMedia = null
            old.onError = null
            old.stop()
            old.dispose()
        }
        mediaPlayer = null
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
            _state.update { it.copy(currentPositionMs = positionMs) }
            val url = streamUrl
            when {
                // Local files (downloads, music on this PC) and the local copy seek natively.
                url == null || usingCopy -> mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
                seekableCopy != null -> switchToCopy(positionMs)
                else -> {
                    // YouTube streams are fragmented MP4: JavaFX cannot seek in them over HTTP.
                    // Keep playing, fetch a local copy (~1 s) and jump there once it is ready.
                    pendingSeekMs = positionMs
                    if (copyJob == null) startCopy(url)
                }
            }
        }
    }

    private fun startCopy(url: String) {
        val gen = generation
        copyJob = scope.launch(Dispatchers.IO) {
            val file = runCatching { StreamCopy.download(url, gen) }
                .onFailure { println("Stream copy failed: ${it.message}") }
                .getOrNull()
            Platform.runLater {
                if (gen != generation) return@runLater
                copyJob = null
                if (file == null) {
                    pendingSeekMs = null
                    return@runLater
                }
                seekableCopy = file
                pendingSeekMs?.let { switchToCopy(it) }
                pendingSeekMs = null
            }
        }
    }

    /** Continues the song from the local copy at [positionMs] (FX thread). */
    private fun switchToCopy(positionMs: Long) {
        val file = seekableCopy ?: return
        val keepPlaying = _state.value.isPlaying || mediaPlayer?.status == MediaPlayer.Status.PLAYING
        usingCopy = true
        stopProgressTracking()
        createPlayer(file.toURI().toString(), startAtMs = positionMs, autoPlay = keepPlaying)
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
            disposePlayer()
        }
        StreamCopy.clear()
    }
}

/** Local copies of streamed songs, so JavaFX can seek in them (one at a time, in the temp folder). */
internal object StreamCopy {
    private val dir = java.io.File(System.getProperty("java.io.tmpdir"), "echomusic-stream")

    fun download(url: String, tag: Int): java.io.File {
        dir.mkdirs()
        clear()
        val target = java.io.File(dir, "song-$tag.m4a")
        val temp = java.io.File(dir, "song-$tag.part")
        val connection = java.net.URI(url).toURL().openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        connection.getInputStream().use { input -> temp.outputStream().use { input.copyTo(it, 64 * 1024) } }
        check(temp.length() > 0) { "empty download" }
        if (!temp.renameTo(target)) error("could not save ${target.name}")
        return target
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
