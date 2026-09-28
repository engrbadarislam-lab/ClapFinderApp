package com.clapfinder.app.util

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Handles the torch strobe and vibration pattern that run during an alert. */
class AlertController(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchId: String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var strobing = false
    private var torchOn = false

    fun start(flash: Boolean, vibrate: Boolean, durationMs: Long) {
        if (flash) startStrobe(durationMs)
        if (vibrate) startVibration(durationMs)
    }

    fun stop() {
        strobing = false
        main.removeCallbacksAndMessages(null)
        setTorch(false)
        runCatching { vibrator?.cancel() }
    }

    // --- torch strobe ---
    private fun startStrobe(durationMs: Long) {
        if (torchId == null) return
        strobing = true
        val period = 220L
        val end = System.currentTimeMillis() + durationMs
        val tick = object : Runnable {
            override fun run() {
                if (!strobing || System.currentTimeMillis() >= end) { setTorch(false); strobing = false; return }
                setTorch(!torchOn)
                main.postDelayed(this, period)
            }
        }
        main.post(tick)
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        runCatching { cameraManager?.setTorchMode(id, on) }
        torchOn = on
    }

    // --- vibration ---
    private fun startVibration(durationMs: Long) {
        val v = vibrator ?: return
        val unit = 400L
        val pattern = longArrayOf(0, unit, 250, unit, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
        main.postDelayed({ runCatching { v.cancel() } }, durationMs)
    }
}
