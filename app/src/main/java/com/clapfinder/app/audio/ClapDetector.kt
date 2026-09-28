package com.clapfinder.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Clap detector that distinguishes a real clap from ordinary loud sounds (speech, music, a door
 * slam, a bass thump) instead of firing on any loud moment.
 *
 * A clap has three acoustic fingerprints, and we require ALL of them:
 *
 *  1. Sharp attack from near-silence – the frame right before the spike must be close to the
 *     background level. Sustained sounds are already loud before their peaks, so they fail this.
 *  2. Impulsive / short duration – after a candidate spike we watch the next few frames; the
 *     energy must collapse back down fast. If it stays loud it is sustained noise → rejected.
 *  3. Bright / broadband – a clap is high-frequency rich, so its zero-crossing rate is high.
 *     This rejects low-frequency thumps (footsteps, door slams, kick drums).
 *
 * Sensitivity (0 = very high … 2 = average) loosens or tightens these gates together.
 */
class ClapDetector(
    private val sensitivity: Int,
    private val onClap: () -> Unit
) {
    private val sampleRate = 16000
    private val frameSize = 256                 // ~16 ms per frame at 16 kHz

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    // --- gates, scaled by sensitivity ---
    // how many times above the noise floor a spike must be
    private val trigger = when (sensitivity) { 0 -> 3.0; 1 -> 4.2; else -> 6.0 }
    // the frame before the spike must be quieter than floor * this (sharp attack)
    private val quietBefore = when (sensitivity) { 0 -> 3.2; 1 -> 2.6; else -> 2.1 }
    // minimum zero-crossing rate (brightness) – rejects bass thumps
    private val zcrMin = when (sensitivity) { 0 -> 0.07; 1 -> 0.10; else -> 0.14 }
    // absolute floor so we never fire in a silent room where the noise floor is tiny
    private val absMin = when (sensitivity) { 0 -> 1100.0; 1 -> 1700.0; else -> 2500.0 }

    private val minFloor = 120.0
    private val decayRatio = 0.45               // spike must fall below 45% of its peak…
    private val maxBurstFrames = 6              // …within ~96 ms, else it is sustained → reject
    private val refractoryMs = 500L

    private var noiseFloor = 400.0
    private var prevEnergy = 0.0
    private var lastClapAt = 0L

    // burst-verification state
    private var checking = false
    private var burstFrames = 0
    private var peakEnergy = 0.0

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf, frameSize * 8)
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return }
        record = ar
        running = true
        ar.startRecording()

        thread = Thread {
            val frame = ShortArray(frameSize)
            while (running) {
                var read = 0
                while (read < frameSize && running) {
                    val n = ar.read(frame, read, frameSize - read)
                    if (n <= 0) break
                    read += n
                }
                if (read == frameSize) analyse(frame)
            }
        }.apply { priority = Thread.MAX_PRIORITY - 1; start() }
    }

    private fun analyse(frame: ShortArray) {
        // RMS energy
        var sumSq = 0.0
        var crossings = 0
        for (i in frame.indices) {
            val s = frame[i].toInt()
            sumSq += (s * s).toDouble()
            if (i > 0) {
                val prevPos = frame[i - 1] >= 0
                val curPos = frame[i] >= 0
                if (prevPos != curPos) crossings++
            }
        }
        val energy = sqrt(sumSq / frame.size)
        val zcr = crossings.toDouble() / frame.size

        val floor = max(noiseFloor, minFloor)
        val now = System.currentTimeMillis()

        if (checking) {
            burstFrames++
            peakEnergy = max(peakEnergy, energy)
            when {
                energy < peakEnergy * decayRatio -> {   // collapsed fast → impulsive → clap
                    checking = false
                    if (now - lastClapAt > refractoryMs) {
                        lastClapAt = now
                        onClap()
                    }
                }
                burstFrames > maxBurstFrames -> {        // stayed loud → sustained → reject
                    checking = false
                }
            }
            prevEnergy = energy
            return
        }

        // adapt the background level only when idle (never during a burst)
        noiseFloor = noiseFloor * 0.98 + energy * 0.02

        val loud = energy > floor * trigger && energy > absMin
        val sharpAttack = prevEnergy < floor * quietBefore
        val bright = zcr > zcrMin

        if (loud && sharpAttack && bright && now - lastClapAt > refractoryMs) {
            // candidate spike – verify it is short before firing
            checking = true
            burstFrames = 0
            peakEnergy = energy
        }
        prevEnergy = energy
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
        checking = false
    }
}