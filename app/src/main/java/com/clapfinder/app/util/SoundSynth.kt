package com.clapfinder.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.clapfinder.app.model.SoundItem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates loud alert tones on the fly and plays them for a fixed duration.
 * Keeps the APK tiny (no audio files) while still producing a distinct sound per item.
 *
 * To use real recordings instead: put files in res/raw and swap this for MediaPlayer.
 */
class SoundSynth {

    private val sr = 44100
    private var track: AudioTrack? = null
    private val main = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    /** volume 0..100, durationMs how long to keep looping the sound. */
    fun play(item: SoundItem, volume: Int, durationMs: Long) {
        stop()
        val buffer = build(item)
        applyGain(buffer, volume / 100f)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val bytes = buffer.size * 2
        val t = AudioTrack(attrs, format, bytes, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
        t.write(buffer, 0, buffer.size)
        t.setLoopPoints(0, buffer.size, -1)   // loop forever until we stop
        t.play()
        track = t

        stopRunnable = Runnable { stop() }
        main.postDelayed(stopRunnable!!, durationMs)
    }

    fun stop() {
        stopRunnable?.let { main.removeCallbacks(it) }
        stopRunnable = null
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    private fun applyGain(buf: ShortArray, gain: Float) {
        val g = gain.coerceIn(0f, 1f)
        for (i in buf.indices) buf[i] = (buf[i] * g).toInt().coerceIn(-32768, 32767).toShort()
    }

    // ---- synthesis -------------------------------------------------------

    private fun build(item: SoundItem): ShortArray = when (item) {
        SoundItem.STEAM_AIR_HORN -> twoTone(0.9, 277.0, 330.0, square = true)
        SoundItem.HORN           -> twoTone(0.7, 370.0, 440.0, square = true)
        SoundItem.DOOR_BELL      -> dingDong()
        SoundItem.CHURCH         -> bell(659.0, 1.6)
        SoundItem.BOOM           -> boom()
        SoundItem.DRUM           -> drum()
        SoundItem.DOG            -> bark()
        SoundItem.CAT            -> meow()
        SoundItem.BABY_LAUGH     -> giggle()
    }

    private fun samples(sec: Double) = (sr * sec).toInt()

    private fun twoTone(sec: Double, f1: Double, f2: Double, square: Boolean): ShortArray {
        val n = samples(sec); val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            var v = sin(2 * PI * f1 * t) + sin(2 * PI * f2 * t)
            if (square) v = if (v >= 0) 0.9 else -0.9
            out[i] = (v * 12000).toInt().toShort()
        }
        return out
    }

    private fun dingDong(): ShortArray {
        val a = bell(880.0, 0.6); val b = bell(660.0, 0.9)
        return a + b
    }

    private fun bell(freq: Double, sec: Double): ShortArray {
        val n = samples(sec); val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = exp(-3.0 * t)
            val v = (sin(2 * PI * freq * t) +
                    0.5 * sin(2 * PI * freq * 2 * t) +
                    0.25 * sin(2 * PI * freq * 3 * t)) * env
            out[i] = (v * 9000).toInt().toShort()
        }
        return out
    }

    private fun boom(): ShortArray {
        val n = samples(0.9); val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = exp(-4.0 * t)
            val body = sin(2 * PI * 60 * t) * env
            val noise = (Random.nextDouble(-1.0, 1.0)) * env * 0.4
            out[i] = ((body + noise) * 15000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun drum(): ShortArray {
        val hit = ShortArray(samples(0.18))
        for (i in hit.indices) {
            val t = i.toDouble() / sr
            val env = exp(-25.0 * t)
            val v = (sin(2 * PI * 180 * t) * 0.6 + Random.nextDouble(-1.0, 1.0) * 0.4) * env
            hit[i] = (v * 14000).toInt().toShort()
        }
        val gap = ShortArray(samples(0.12))
        return hit + gap + hit + gap
    }

    private fun bark(): ShortArray {
        fun one(): ShortArray {
            val n = samples(0.14); val o = ShortArray(n)
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                val env = exp(-9.0 * t) * (1 - exp(-80.0 * t))
                val f = 320.0 - 120.0 * t / 0.14
                val v = (sin(2 * PI * f * t) + 0.4 * Random.nextDouble(-1.0, 1.0)) * env
                o[i] = (v * 13000).toInt().toShort()
            }
            return o
        }
        val gap = ShortArray(samples(0.1))
        return one() + gap + one()
    }

    private fun meow(): ShortArray {
        val n = samples(0.7); val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val prog = t / 0.7
            val f = 500.0 + 300.0 * sin(PI * prog)      // rise then fall
            val env = (1 - exp(-30.0 * t)) * exp(-2.0 * t)
            val v = (sin(2 * PI * f * t) + 0.3 * sin(2 * PI * f * 2 * t)) * env
            out[i] = (v * 10000).toInt().toShort()
        }
        return out
    }

    private fun giggle(): ShortArray {
        val out = ArrayList<Short>()
        repeat(4) {
            val n = samples(0.1)
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                val f = 700.0 + 150.0 * sin(2 * PI * 8 * t)
                val env = exp(-14.0 * t)
                val v = sin(2 * PI * f * t) * env
                out.add((v * 9000).toInt().toShort())
            }
            repeat(samples(0.06)) { out.add(0) }
        }
        return out.toShortArray()
    }
}
