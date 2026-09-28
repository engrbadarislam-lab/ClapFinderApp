package com.clapfinder.app.ads

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.clapfinder.app.billing.PremiumManager

/**
 * Global click → interstitial every Nth click, LOAD ON DEMAND with a
 * loading dialog shown while the ad loads.
 *
 *   val adClick = rememberAdClick()
 *   Button(onClick = { adClick { onOpenTools() } }) { ... }
 *
 * ROBUST: the caller's action (navigation, or launching a file picker /
 * ActivityResultLauncher) is NOT executed inside the ad-dismiss callback —
 * that fires at an unpredictable lifecycle moment where the launcher may be
 * unregistered / the NavController not RESUMED, causing IllegalStateException.
 * The callback queues the action, and a LaunchedEffect runs it within the
 * composition lifecycle, where it's always valid.
 */

private var globalClickCount = 0

/** Global loading state — drawn by AdClickLoadingHost() at the app root. */
var adClickLoading by mutableStateOf(false)
    private set

/**
 * The click logic (shared by rememberAdClick and AppContainer.registerClick).
 * Counts the click; every Nth (Remote Config) loads + shows the ad, then onDone.
 */
fun onAdClick(
    activity: Activity,
    screenKey: String = "ads_global_click",
    onDone: () -> Unit
) {
    if (PremiumManager.isPremium || !AdConfig.isEnabled(screenKey)) {
        onDone()
    } else {
        globalClickCount++
        val threshold = AdConfig.globalClickThreshold()
        if (globalClickCount >= threshold) {
            globalClickCount = 0
            AdManager.showInterstitial(
                activity = activity,
                screenKey = screenKey,
                onLoading = { adClickLoading = it },
                onDone = {
                    adClickLoading = false
                    onDone()
                }
            )
        } else {
            onDone()
        }
    }
}

/** val adClick = rememberAdClick()  →  onClick = { adClick { onOpenX() } } */
@Composable
fun rememberAdClick(screenKey: String = "ads_global_click"): (() -> Unit) -> Unit {
    val activity = LocalContext.current as Activity

    // Action queued by the click / ad callback, run by the effect below.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(pending) {
        pending?.let { action ->
            pending = null
            action()
        }
    }

    return remember(activity, screenKey) {
        { action -> onAdClick(activity, screenKey) { pending = action } }
    }
}

/** Put ONCE at the app root — shows the loading dialog for every click ad. */
@Composable
fun AdClickLoadingHost() {
    if (adClickLoading) AdLoadingDialog()
}