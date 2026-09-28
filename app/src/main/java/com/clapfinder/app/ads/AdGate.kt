package com.clapfinder.app.ads

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Button-tap gate for interstitial + rewarded, LOAD ON DEMAND.
 * Owns the loading dialog (shown while the ad loads).
 *
 *   val gate = rememberAdGate()
 *   Button(onClick = { gate.showInterstitial("key") { proceed() } }) { }
 *   gate.showRewarded("key", onEarned = { unlock() })
 *
 * ROBUST: the caller's callbacks (which often navigate) are NOT executed inside
 * the ad-dismiss callback — that fires at an unpredictable lifecycle moment and
 * navigating there throws IllegalStateException. Instead they are queued and run
 * by a LaunchedEffect within the composition lifecycle, where navigation is safe.
 */
class AdGate internal constructor(
    private val activity: Activity,
    private val setLoading: (Boolean) -> Unit,
    private val queue: (() -> Unit) -> Unit
) {
    fun showInterstitial(screenKey: String, onProceed: () -> Unit) {
        AdManager.showInterstitial(
            activity, screenKey,
            onLoading = { setLoading(it) },
            onDone = { queue(onProceed) }        // defer → safe
        )
    }

    fun showRewarded(
        screenKey: String,
        onEarned: () -> Unit,
        onDone: () -> Unit = {}
    ) {
        // earned flag captured here; both run deferred, in order.
        var earned = false
        AdManager.showRewarded(
            activity, screenKey,
            onLoading = { setLoading(it) },
            onEarned = { earned = true },
            onDone = {
                queue {
                    if (earned) onEarned()
                    onDone()
                }
            }
        )
    }
}

@Composable
fun rememberAdGate(): AdGate {
    val activity = LocalContext.current as Activity
    var loading by remember { mutableStateOf(false) }

    // Pending action queued by an ad callback, run safely by the effect below.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (loading) AdLoadingDialog()

    LaunchedEffect(pending) {
        pending?.let { action ->
            pending = null
            action()
        }
    }

    return remember {
        AdGate(
            activity = activity,
            setLoading = { loading = it },
            queue = { pending = it }
        )
    }
}