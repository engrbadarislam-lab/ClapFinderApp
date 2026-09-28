package com.clapfinder.app.ads

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Print-button ad gate — shows an interstitial on EVERY print, with the
 * app-styled loading dialog while it loads.
 *
 *   val printAd = rememberPrintAd()
 *   Button(onClick = { printAd { PrintUtils.printPdf(context, file) } }) { }
 *
 * Ad shows → dismiss → action runs. Premium/gated/failed → action runs directly.
 *
 * ROBUST: the caller's action (which often navigates) is NOT executed inside
 * the ad-dismiss callback — that fires at an unpredictable lifecycle moment and
 * navigating there throws IllegalStateException. Instead the callback stores the
 * action, and a LaunchedEffect runs it within the composition's lifecycle, where
 * navigation is always valid.
 */
@Composable
fun rememberPrintAd(
    screenKey: String = "ads_print_inter"
): (action: () -> Unit) -> Unit {
    val activity = LocalContext.current as Activity
    var loading by remember { mutableStateOf(false) }

    // Pending action queued by the ad callback, run safely by the effect below.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (loading) AdLoadingDialog()

    // Runs the queued action inside the composition lifecycle (safe to navigate).
    LaunchedEffect(pending) {
        pending?.let { action ->
            pending = null
            action()
        }
    }

    return remember {
        { action ->
            AdManager.showInterstitialNow(
                activity = activity,
                screenKey = screenKey,
                onLoading = { loading = it },
                onProceed = {
                    // Don't run here (bad lifecycle moment) — queue it.
                    pending = action
                }
            )
        }
    }
}