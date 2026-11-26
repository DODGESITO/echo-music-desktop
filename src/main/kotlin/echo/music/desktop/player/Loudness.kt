package echo.music.desktop.player

import kotlin.math.pow

/** Loudness presets, same values as the phone app (AudioLoudnessPreset, in dB). */
enum class LoudnessPreset(val offsetDb: Double) {
    QUIET(-6.0),
    NORMAL(0.0),
    LOUD(3.0),
    AGGRESSIVE(7.0);

    companion object {
        fun from(name: String): LoudnessPreset = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

object Loudness {
    /**
     * Volume multiplier for a song: `-loudnessDb` (when normalizing) plus the preset offset,
     * limited to -15..+10 dB. The phone app uses the same formula with its LoudnessEnhancer.
     */
    fun gainFor(loudnessDb: Double?, normalize: Boolean, preset: LoudnessPreset): Float {
        val normalizationDb = if (normalize && loudnessDb != null) -loudnessDb else 0.0
        val gainDb = (normalizationDb + preset.offsetDb).coerceIn(-15.0, 10.0)
        return 10.0.pow(gainDb / 20.0).toFloat()
    }
}
