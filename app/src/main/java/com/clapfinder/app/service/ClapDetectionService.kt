package com.clapfinder.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.clapfinder.app.ClapApp
import com.clapfinder.app.R
import com.clapfinder.app.audio.ClapDetector
import com.clapfinder.app.audio.WhistleDetector
import com.clapfinder.app.model.SoundItem
import com.clapfinder.app.ui.MainActivity
import com.clapfinder.app.util.AlertController
import com.clapfinder.app.util.Prefs
import com.clapfinder.app.util.SoundSynth
import java.util.Calendar

class ClapDetectionService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var synth: SoundSynth
    private lateinit var alerts: AlertController
    private lateinit var overlay: FinderOverlay
    private var clapDetector: ClapDetector? = null
    private var whistleDetector: WhistleDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var alerting = false

    private var mode = MODE_CLAP

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        synth = SoundSynth()
        alerts = AlertController(this)
        overlay = FinderOverlay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
        }
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_CLAP
        startForegroundInternal()
        acquireWakeLock()
        startDetection()
        prefs.serviceRunning = (mode == MODE_CLAP)
        prefs.whistleServiceRunning = (mode == MODE_WHISTLE)
        return START_STICKY
    }

    private fun startForegroundInternal() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ClapDetectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleRes = if (mode == MODE_WHISTLE) R.string.notif_whistle_title else R.string.notif_listening_title
        val textRes  = if (mode == MODE_WHISTLE) R.string.notif_whistle_text  else R.string.notif_listening_text

        val notif: Notification = NotificationCompat.Builder(this, ClapApp.CH_SERVICE)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(textRes))
            .setSmallIcon(R.drawable.ic_ear)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clapfinder:listen").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun startDetection() {
        stopDetectors()
        if (mode == MODE_WHISTLE) {
            whistleDetector = WhistleDetector(prefs.whistleSensitivity) { onDetected() }
                .also { it.start() }
        } else {
            clapDetector = ClapDetector(prefs.sensitivity) { onDetected() }
                .also { it.start() }
        }
    }

    private fun onDetected() {
        if (alerting || isInDeactivateWindow()) return
        alerting = true
        val durationMs = prefs.durationSec * 1000L

        main.post {
            synth.play(SoundItem.fromId(prefs.soundId), prefs.volume, durationMs)
            alerts.start(prefs.flashMode, prefs.vibrationMode, durationMs)
            overlay.show(onDismiss = { stopAlert() })
            main.postDelayed({ stopAlert() }, durationMs)
        }
    }

    private fun stopAlert() {
        if (!alerting) return
        alerting = false
        synth.stop()
        alerts.stop()
        overlay.hide()
    }

    private fun isInDeactivateWindow(): Boolean {
        if (!prefs.scheduleEnabled) return false
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val start = prefs.scheduleStart
        val end = prefs.scheduleEnd
        return if (start <= end) now in start until end
        else now >= start || now < end
    }

    private fun stopDetectors() {
        clapDetector?.stop(); clapDetector = null
        whistleDetector?.stop(); whistleDetector = null
    }

    private fun stopEverything() {
        stopAlert()
        stopDetectors()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        prefs.serviceRunning = false
        prefs.whistleServiceRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.clapfinder.app.STOP"
        const val EXTRA_MODE = "mode"

        const val MODE_CLAP = "clap"
        const val MODE_WHISTLE = "whistle"

        fun start(ctx: Context, mode: String = MODE_CLAP) {
            val i = Intent(ctx, ClapDetectionService::class.java).apply {
                putExtra(EXTRA_MODE, mode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ClapDetectionService::class.java).setAction(ACTION_STOP))
        }
    }
}