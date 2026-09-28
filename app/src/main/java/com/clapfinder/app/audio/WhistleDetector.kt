package com.clapfinder.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Detects a WHISTLE specifically — not just any loud sound.
 *
 * A whistle is a narrow, near-pure tone: almost all of its energy sits at ONE
 * frequency somewhere in ~0.9–4.5 kHz, and it is sustained for a fraction of a
 * second. Ordinary loud sounds (speech, music, claps, clatter, water) spread
 * their energy across many frequencies, so they fail the two tests below.
 *
 * For each analysis block we:
 *   1. Scan a band of candidate frequencies with Goertzel and find the loudest one.
 *   2. DOMINANCE  = peak-bin power / total signal energy.  High only for a pure
 *      tone. This is (roughly) independent of how loud the sound is or of the
 *      phone's mic gain, so it behaves consistently across devices.
 *   3. TONALITY   = peak-bin power / average bin power.  High when a single
 *      frequency sticks out far above the rest → a tone, not broadband noise.
 *   4. Require both, PLUS the peak to have a real absolute level (so a silent
 *      room can't trigger on ratio noise), PLUS the whistle to persist for a few
 *      consecutive blocks (rejects short impulsive sounds like a clap).
 *
 * Same constructor + start()/stop() as before, so it is a drop-in replacement.
 */
class WhistleDetector(
    private val sensitivity: Int,        // 1..10, higher = easier to trigger
    private val onWhistle: () -> Unit
) {
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    private val sampleRate = 44100
    private val n = 2048                  // ~46 ms per block
    private val buffer = ShortArray(n)
    private var bufIdx = 0

    // Candidate whistle frequencies (Hz). Human whistles land roughly here.
    private val freqs: DoubleArray = run {
        val list = ArrayList<Double>()
        var f = 900.0
        while (f <= 4500.0) { list.add(f); f += 100.0 }
        list.toDoubleArray()
    }
    // Pre-computed Goertzel coefficients per frequency.
    private val coeffs = DoubleArray(freqs.size) { i ->
        val k = (0.5 + n * freqs[i] / sampleRate).toInt()
        2.0 * cos(2.0 * PI * k / n)
    }

    // How many consecutive qualifying blocks are needed (~3 * 46 ms ≈ 140 ms).
    private val needed = 3
    private var hits = 0

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.Default).launch {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = minBuf.coerceAtLeast(4096)

            @Suppress("MissingPermission")
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
            audioRecord = ar
            if (ar.state != AudioRecord.STATE_INITIALIZED) return@launch

            ar.startRecording()
            val readBuf = ShortArray(bufSize)

            while (isActive && ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = ar.read(readBuf, 0, bufSize)
                if (read <= 0) continue

                for (i in 0 until read) {
                    buffer[bufIdx++] = readBuf[i]
                    if (bufIdx >= n) {
                        bufIdx = 0
                        if (analyseBlock()) {
                            hits++
                            if (hits >= needed) {
                                hits = 0
                                onWhistle()
                                delay(2000)     // debounce after a confirmed whistle
                            }
                        } else {
                            hits = 0            // must be consecutive
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        audioRecord?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        audioRecord = null
        bufIdx = 0
        hits = 0
    }

    /** True if this block looks like a sustained pure tone (a whistle). */
    private fun analyseBlock(): Boolean {
        // Total time-domain energy of the block.
        var totalEnergy = 0.0
        for (s in buffer) totalEnergy += (s.toDouble() * s.toDouble())
        // Ignore near-silence outright (protects the ratios from noise).
        if (totalEnergy < minTotalEnergy) return false

        // Goertzel power for every candidate frequency; track the peak.
        var maxPower = 0.0
        var sumPower = 0.0
        for (b in freqs.indices) {
            val p = goertzelPower(coeffs[b])
            sumPower += p
            if (p > maxPower) maxPower = p
        }
        val meanPower = sumPower / freqs.size

        val dominance = maxPower / (totalEnergy + 1.0)     // ~N/2 for a pure tone
        val tonality  = maxPower / (meanPower + 1.0)        // peak vs the rest of the band

        return maxPower > peakMin && dominance > dominanceMin && tonality > tonalityMin
    }

    private fun goertzelPower(coeff: Double): Double {
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val s0 = coeff * s1 - s2 + buffer[i]
            s2 = s1
            s1 = s0
        }
        // |X|^2 = s1^2 + s2^2 - coeff*s1*s2
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    /* ---- thresholds, scaled by sensitivity (1 = strict … 10 = loose) ---- */

    private val t = ((sensitivity.coerceIn(1, 10) - 1) / 9.0)   // 0.0 .. 1.0

    // Absolute peak power a real whistle easily exceeds (~1e11–1e12).
    private val peakMin      = lerp(3.0e11, 4.0e10, t)
    // Peak must dominate the whole signal (pure tone ≈ N/2 ≈ 1024).
    private val dominanceMin = lerp(300.0, 80.0, t)
    // Peak must stand far above the average bin (tone vs noise).
    private val tonalityMin  = lerp(12.0, 5.0, t)
    // Below this the room is effectively silent — never trigger.
    private val minTotalEnergy = 2.0e7

    private fun lerp(strict: Double, loose: Double, f: Double) = strict + (loose - strict) * f
}