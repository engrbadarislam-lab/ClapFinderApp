package com.clapfinder.app.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {

    private const val TAG = "ConsentManager"

    /** True once the UMP flow has resolved and ads may be requested. Reactive. */
    var canShowAds by mutableStateOf(false)
        private set

    /**
     * True once the UMP flow has finished, WHATEVER the outcome (consented,
     * declined, or failed). The splash gates on THIS, not canShowAds — a user
     * who declines must still get past the splash; we just won't show ads.
     */
    var resolved by mutableStateOf(false)
        private set

    /** True while the consent form is on screen. The splash must wait on this. */
    var formShowing by mutableStateOf(false)
        private set

    /** One call. Gathers consent (shows form if required), then invokes onReady. */
    fun start(activity: Activity, onReady: () -> Unit = {}) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    formShowing = true
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    formShowing = false
                    finish(info, onReady)
                }
            },
            { err ->
                Log.w(TAG, "consent update failed: ${err.message}")
                finish(info, onReady)   // fail open
            }
        )
    }

    private fun finish(info: ConsentInformation, onReady: () -> Unit) {
        canShowAds = info.canRequestAds()
        resolved = true                 // ← flow is done, regardless of outcome
        if (canShowAds) onReady()
    }

    /** Optional: reopen the form later from Settings. */
    fun showPrivacyOptions(activity: Activity) =
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {}
}