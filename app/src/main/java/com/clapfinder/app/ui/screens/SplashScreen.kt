package com.clapfinder.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clapfinder.app.R
import com.clapfinder.app.ads.AppOpenAdManager
import com.clapfinder.app.ads.ConsentManager
import com.clapfinder.app.ads.rememberAdGate // Added import for the interstitial gate
import com.clapfinder.app.ui.components.CfIcon
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.findActivity
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.util.Prefs
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Was SplashActivity: forced (IMMEDIATE) in-app update check, then after 1.6s go to
 * Home (onboarding done) or Onboarding. Also waits for the Google consent form (UMP),
 * which MainActivity starts — ads are initialised once consent resolves.
 *
 * @param onFinished true → Home, false → Onboarding
 */
@Composable
fun SplashScreen(onFinished: (onboardingDone: Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }
    val finish by rememberUpdatedState(onFinished)

    // Ad gate for the splash interstitial
    val gate = rememberAdGate()

    var proceed by remember { mutableStateOf(false) }   // old: isNavigating
    var checkRound by remember { mutableIntStateOf(0) }

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                // Relaunch fresh, like before
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                activity?.finish()
                intent?.let { context.startActivity(it) }
            }
            Activity.RESULT_CANCELED -> activity?.finishAffinity()
            else -> {
                proceed = false
                checkRound++          // update failed → check again
            }
        }
    }

    fun startImmediateUpdate(info: AppUpdateInfo) {
        val started = appUpdateManager.startUpdateFlowForResult(
            info,
            updateLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        )
        // The Play update screen is not an "app open" → no Welcome Back when it closes
        if (started) AppOpenAdManager.skipNextForeground()
    }

    // checkForUpdate()
    LaunchedEffect(checkRound) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    startImmediateUpdate(info)
                } else {
                    proceed = true
                }
            }
            .addOnFailureListener { proceed = true }
    }

    // old onResume(): resume an update that was already in progress
    LifecycleResumeEffect(Unit) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate(info)
            }
        }
        onPauseOrDispose { }
    }

    // proceedToNextScreen(): 1.6s, then (once consent is answered) navigate
    LaunchedEffect(proceed) {
        if (!proceed) return@LaunchedEffect
        delay(1600)
        // Google consent (UMP) usually resolves instantly; wait for it (max 10s) …
        withTimeoutOrNull(10_000) {
            snapshotFlow { ConsentManager.resolved }.first { it }
        }
        // … but never leave while the consent form is still on screen
        snapshotFlow { ConsentManager.resolved || !ConsentManager.formShowing }.first { it }

        // Show Splash Interstitial before navigating to Home/Onboarding
        gate.showInterstitial("ads_splash_inter") {
            finish(Prefs.get(context).onboardingDone)
        }
    }

    SystemBarIcons(dark = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.SplashGradient)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(CF.GreenSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CfIcon(R.drawable.ic_power, 66.dp, tint = CF.GreenPrimary)
            }
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(top = 24.dp),
                color = CF.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em
            )
            Text(
                text = "Clap • Find • Relax",
                modifier = Modifier.padding(top = 6.dp),
                color = CF.WhiteSoft,
                fontSize = 16.sp
            )
        }

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .size(50.dp),
            color = CF.White
        )
    }
}