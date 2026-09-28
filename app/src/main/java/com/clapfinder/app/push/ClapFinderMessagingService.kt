package com.clapfinder.app.push
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random
import com.clapfinder.app.R
import com.clapfinder.app.ui.MainActivity


/**
 * Receives Firebase Cloud Messaging pushes.
 *
 * WHEN THIS RUNS: only for "data" messages, or for "notification" messages
 * while the app is in the FOREGROUND. If a notification-type push arrives while
 * the app is backgrounded, Android builds and posts the notification itself and
 * this class is never called — which is why a push can look like it "works in
 * background but not foreground" if you don't implement onMessageReceived.
 *
 * Payload keys read here:
 *   title, body   — text (falls back to the notification block if present)
 *   route         — optional; passed to MainActivity so it can deep-link
 */
class ClapFinderMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Registration token for this install. Send it to your backend here if
        // you ever target individual devices; topic sends don't need it.
        Log.d(TAG, "FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"]
            ?: message.notification?.title
            ?: getString(R.string.app_name)
        val body = message.data["body"]
            ?: message.notification?.body
            ?: return                      // nothing to show
        val route = message.data["route"]

        showNotification(title, body, route)
    }

    private fun showNotification(title: String, body: String, route: String?) {
        createChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(
            this,
            Random.nextInt(),              // unique so extras aren't reused
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // POST_NOTIFICATIONS is a runtime permission on Android 13+.
        //
        // The check is written INLINE on purpose: lint only recognises a
        // permission guard in the same method as the call. Extracting it into a
        // helper made lint demand @RequiresPermission instead — and putting
        // that on onMessageReceived() would be wrong, since the caller there is
        // the FCM library, which can never satisfy it.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.w(TAG, "push dropped: POST_NOTIFICATIONS not granted")
            return
        }

        NotificationManagerCompat.from(this)
            .notify(Random.nextInt(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notif_channel_updates_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "clapFinderdFCM"
        const val CHANNEL_ID = "clap_finder_updates"
        const val EXTRA_ROUTE = "push_route"
    }
}