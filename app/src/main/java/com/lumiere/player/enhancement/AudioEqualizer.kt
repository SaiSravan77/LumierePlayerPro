package com.lumiere.player.enhancement

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.BassBoost

/**
 * 5-band equalizer with presets + volume boost + virtual surround.
 */
class AudioEqualizer(private val audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null

    var isEnabled = false
        private set

    // EQ bands in milliBels. Bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    val bandLevels = FloatArray(5) { 0f }

    companion object {
        val PRESET_FLAT     = floatArrayOf(0f,  0f,  0f,  0f,  0f)
        val PRESET_BASS     = floatArrayOf(6f,  4f,  0f, -2f, -2f)
        val PRESET_TREBLE   = floatArrayOf(-2f,-1f,  0f,  4f,  6f)
        val PRESET_VOCAL    = floatArrayOf(-2f, 0f,  4f,  4f,  2f)
        val PRESET_CINEMA   = floatArrayOf(3f,  2f,  0f,  2f,  3f)
        val PRESET_CLASSIC  = floatArrayOf(4f,  2f,  0f,  0f,  4f)

        val ALL_PRESETS = listOf(
            "Flat"    to PRESET_FLAT,
            "Bass"    to PRESET_BASS,
            "Treble"  to PRESET_TREBLE,
            "Vocal"   to PRESET_VOCAL,
            "Cinema"  to PRESET_CINEMA,
            "Classic" to PRESET_CLASSIC
        )
    }

    fun init() {
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = false }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = false }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = false }
            isEnabled = true
        } catch (e: Exception) {
            isEnabled = false
        }
    }

    fun setBand(band: Int, levelDb: Float) {
        if (band !in 0..4) return
        bandLevels[band] = levelDb
        equalizer?.setBandLevel(band.toShort(), (levelDb * 100).toInt().toShort())
    }

    fun applyPreset(levels: FloatArray) {
        levels.forEachIndexed { i, v -> setBand(i, v) }
    }

    fun setVolumeBoost(gainMb: Int) {
        loudnessEnhancer?.setTargetGain(gainMb)
        loudnessEnhancer?.enabled = gainMb > 0
    }

    fun setVirtualSurround(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
        virtualizer?.enabled = strength > 0
    }

    fun setBassBoost(strength: Int) {
        bassBoost?.setStrength(strength.toShort())
        bassBoost?.enabled = strength > 0
    }

    fun setEqEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    fun getMinBandLevel(): Float = (equalizer?.bandLevelRange?.get(0)?.toFloat() ?: -1500f) / 100f
    fun getMaxBandLevel(): Float = (equalizer?.bandLevelRange?.get(1)?.toFloat() ?: 1500f) / 100f

    fun release() {
        equalizer?.release()
        loudnessEnhancer?.release()
        virtualizer?.release()
        bassBoost?.release()
    }
}
