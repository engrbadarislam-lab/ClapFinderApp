package com.clapfinder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.clapfinder.app.ads.AppOpenAdManager
import com.clapfinder.app.push.ClapFinderMessagingService

class ClapApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()

        // App-open ("Welcome Back"): tracks the foreground activity and flags every
        // return from background. MobileAds itself is initialised after the Google
        // consent check (MainActivity → AdManager.ensureConsent).
        AppOpenAdManager.register(this)
    }

    // ---- Notification channels (unchanged) ----
    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        val service = NotificationChannel(
            CH_SERVICE,
            getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification while ClapFinder is listening"
            setShowBadge(false)
        }

        val alert = NotificationChannel(
            CH_ALERT,
            getString(R.string.channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shown when a clap is detected"
            enableVibration(true)
        }

        // Push notifications (FCM). Created up front so pushes that arrive while the app is in
        // the background land here — it's the manifest's default_notification_channel_id.
        val updates = NotificationChannel(
            ClapFinderMessagingService.CHANNEL_ID,
            getString(R.string.notif_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notif_channel_updates_desc)
        }

        nm.createNotificationChannel(service)
        nm.createNotificationChannel(alert)
        nm.createNotificationChannel(updates)
    }

    companion object {
        const val CH_SERVICE = "clap_service"
        const val CH_ALERT = "clap_alert"
    }
}