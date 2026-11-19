package com.skarif.kairos.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Bit-perfect USB output through Android 14's preferred-mixer API.
 * Only works with a USB DAC that reports a bit-perfect mixer for the song's sample rate.
 */
object BitPerfect {
    private val musicAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun usbDevice(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }

    fun usbConnected(context: Context): Boolean =
        usbDevice(context.getSystemService(AudioManager::class.java)) != null

    /** Returns a label like "24-bit / 96 kHz" when bit-perfect was switched on, or null. */
    fun apply(context: Context, sampleRate: Int): String? {
        if (Build.VERSION.SDK_INT < 34) return null
        return apply34(context, sampleRate)
    }

    @RequiresApi(34)
    private fun apply34(context: Context, sampleRate: Int): String? {
        val am = context.getSystemService(AudioManager::class.java)
        val dev = usbDevice(am) ?: return null
        val pick = am.getSupportedMixerAttributes(dev)
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT && it.format.sampleRate == sampleRate }
            .maxByOrNull { bits(it.format.encoding) } ?: return null
        return if (am.setPreferredMixerAttributes(musicAttrs, dev, pick)) {
            "${bits(pick.format.encoding)}-bit / ${khz(sampleRate)} kHz"
        } else {
            null
        }
    }

    fun clear(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        val am = context.getSystemService(AudioManager::class.java)
        usbDevice(am)?.let { runCatching { am.clearPreferredMixerAttributes(musicAttrs, it) } }
    }

    fun bits(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    fun khz(rate: Int): String = if (rate % 1000 == 0) "${rate / 1000}" else String.format("%.1f", rate / 1000.0)
}
