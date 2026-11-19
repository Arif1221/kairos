package com.skarif.kairos.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Virtualizer
import android.util.Log
import com.skarif.kairos.data.OutputMode
import com.skarif.kairos.data.Settings

/**
 * 10-band EQ, bass boost and virtualizer on the player's audio session.
 *
 * Uses DynamicsProcessing (Android 9+) rather than the old Equalizer effect, because Equalizer's
 * band count depends on the phone (often only 5). The design has 10 bands.
 * Everything is bypassed in bit-perfect mode, since any processing would change the bits.
 */
class AudioEffects {
    private var session = 0
    private var dp: DynamicsProcessing? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null

    fun attach(sessionId: Int, s: Settings) {
        if (sessionId != session || dp == null) {
            release()
            session = sessionId
            if (sessionId <= 0) return
            dp = runCatching {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,          // stereo
                    true, BANDS, // pre-EQ with 10 bands
                    false, 0,   // no multiband compressor
                    false, 0,   // no post-EQ
                    false,      // no limiter
                ).build()
                DynamicsProcessing(0, sessionId, cfg)
            }.onFailure { Log.w(TAG, "DynamicsProcessing unavailable", it) }.getOrNull()
            bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
            virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        }
        apply(s)
    }

    fun apply(s: Settings) {
        val bypass = s.outputMode == OutputMode.BIT_PERFECT
        dp?.let { d ->
            val on = s.eqEnabled && !bypass
            runCatching {
                for (i in 0 until BANDS) {
                    d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], if (on) s.eqGains[i] else 0f))
                }
                // Lower the input by the biggest boost so boosted bands do not clip.
                d.setInputGainAllChannelsTo(if (on) -(s.eqGains.maxOrNull() ?: 0f).coerceAtLeast(0f) else 0f)
                d.enabled = on
            }
        }
        bass?.let { b -> runCatching { b.setStrength((s.bassBoost * 100).toShort()); b.enabled = !bypass && s.bassBoost > 0 } }
        virt?.let { v -> runCatching { v.setStrength((s.virtualizer * 100).toShort()); v.enabled = !bypass && s.virtualizer > 0 } }
    }

    fun release() {
        runCatching { dp?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        dp = null; bass = null; virt = null; session = 0
    }

    companion object {
        private const val TAG = "AudioEffects"
        const val BANDS = 10
        /** Upper edge of each band, halfway (geometrically) between the design's centres 31 Hz ... 16 kHz. */
        val CUTOFFS = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f)
    }
}
