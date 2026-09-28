package com.clapfinder.app.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clapfinder.app.R
import com.clapfinder.app.ads.AdaptiveBannerAd
import com.clapfinder.app.ads.rememberAdClick
import com.clapfinder.app.service.ClapDetectionService
import com.clapfinder.app.ui.components.ActivateRing
import com.clapfinder.app.ui.components.CfIcon
import com.clapfinder.app.ui.components.ChevronCardRow
import com.clapfinder.app.ui.components.SwitchCardRow
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.borderlessClickable
import com.clapfinder.app.ui.components.findActivity
import com.clapfinder.app.ui.components.negativeTopMargin
import com.clapfinder.app.ui.components.rememberBreathingScale
import com.clapfinder.app.ui.components.rememberFinderActivation
import com.clapfinder.app.ui.components.rememberPrefsVersion
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.ui.theme.CardTitle
import com.clapfinder.app.util.PermissionHelper
import com.clapfinder.app.util.Prefs
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

/** True once Home has been shown this session (so the next time = user came back). */
private var homeShownBefore = false

/** Ask for a review at most once per app session. */
private var reviewAskedThisSession = false

/** Was MainActivity + activity_main.xml. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit, // Added parameter
    onOpenHowTo: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenWhistle: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val version = rememberPrefsVersion(prefs)          // re-read prefs on resume / change
    val running = remember(version) { prefs.serviceRunning }
    val flashOn = remember(version) { prefs.flashMode }
    val vibrationOn = remember(version) { prefs.vibrationMode }

    // --- Added Bouncing Animation Setup ---
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_anim"
    )

    LaunchedEffect(Unit) {
        if (!homeShownBefore) {
            homeShownBefore = true          // first time Home appears → not a "back"
        } else if (!reviewAskedThisSession) {
            reviewAskedThisSession = true
            delay(600)                      // let the back transition finish
            context.findActivity()?.let { launchInAppReview(it) }
        }
    }

    // Pulse only while listening AND on screen (old onPause → stopPulse)
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val pulsing = running && resumed
    val breathing = rememberBreathingScale(pulsing)

    val adClick = rememberAdClick()
    val activation = rememberFinderActivation(ClapDetectionService.MODE_CLAP)

    // Push notifications (FCM) need POST_NOTIFICATIONS on Android 13+. Ask once when Home
    // first opens, so people who never tap Activate still get pushes (Activate still asks too).
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — nothing else to do; FCM checks it before posting */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.notificationPromptShown &&
            !PermissionHelper.hasNotifications(context)
        ) {
            prefs.notificationPromptShown = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Back → exit dialog; "Yes" stops listening and closes the app
    var showExit by remember { mutableStateOf(false) }
    BackHandler(enabled = !showExit) { showExit = true }
    if (showExit) {
        ExitAppDialog(
            onDismiss = { showExit = false },
            onNo = { showExit = false },
            onYes = {
                showExit = false
                ClapDetectionService.stop(context)
                prefs.serviceRunning = false
                context.findActivity()?.finish()
            }
        )
    }

    SystemBarIcons(dark = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
            .systemBarsPadding()
    ) {
        // ===== Header =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.weight(1f),
                    color = CF.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                // Premium Paywall Icon added here
                Icon(
                    painter = painterResource(R.drawable.premium_icon),
                    contentDescription = "Premium",
                    tint = CF.White,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            // --- Added scale for bouncing animation ---
                            scaleX = bounceScale
                            scaleY = bounceScale
                        }
                        .borderlessClickable {
                            // No ad gate here, let users pay!
                            onOpenPaywall()
                        }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Settings Icon
                CfIcon(
                    id = R.drawable.ic_settings,
                    size = 30.dp,
                    tint = CF.White,
                    modifier = Modifier.borderlessClickable {
                        adClick { onOpenSettings() }
                    }
                )
            }

            ActivateRing(
                running = running,
                pulsing = pulsing,
                onClick = { activation.onActivateClicked() },
                modifier = Modifier.padding(top = 26.dp)
            ) {
                CfIcon(
                    id = R.drawable.ic_power,
                    size = 76.dp,
                    tint = if (running) CF.GreenPrimary else CF.TextPrimary,
                    modifier = Modifier.graphicsLayer {
                        scaleX = breathing.value
                        scaleY = breathing.value
                    }
                )
            }

            Text(
                text = stringResource(if (running) R.string.tap_to_stop else R.string.click_to_activate),
                modifier = Modifier.padding(top = 18.dp),
                color = CF.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ===== Sheet (scrolls up 24dp under the header, like marginTop=-24dp) =====
        Column(
            modifier = Modifier
                .weight(1f)
                .negativeTopMargin(24.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 30.dp)
        ) {
            // How to use + Sound
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CF.CardShape)
                        .background(CF.Card)
                        .clickable { adClick { onOpenHowTo() } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(CF.GreenSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CfIcon(R.drawable.ic_help, 30.dp)
                    }
                    Text(
                        text = stringResource(R.string.how_to_use),
                        modifier = Modifier.padding(top = 10.dp),
                        style = CardTitle
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CF.CardShape)
                        .background(CF.Card)
                        .clickable { adClick { onOpenSound() } }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CfIcon(R.drawable.ic_music, 30.dp, tint = CF.GreenPrimary)
                        Text(
                            text = stringResource(R.string.sound),
                            modifier = Modifier.padding(top = 6.dp),
                            style = CardTitle
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(30.dp)
                            .background(CF.GreenSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CfIcon(R.drawable.ic_play, 16.dp)
                    }
                }
            }

            ChevronCardRow(
                icon = R.drawable.whistle_icon,
                iconTint = CF.GreenPrimary,
                title = stringResource(R.string.whistle_finder),
                onClick = { adClick { onOpenWhistle() } },
                modifier = Modifier.padding(top = 14.dp)
            )
            ChevronCardRow(
                icon = R.drawable.ic_settings,
                iconTint = CF.GreenPrimary,
                title = stringResource(R.string.more_setting),
                onClick = { adClick { onOpenSettings() } },
                modifier = Modifier.padding(top = 14.dp)
            )

            SwitchCardRow(
                icon = R.drawable.ic_flash,
                title = stringResource(R.string.flash_mode),
                checked = flashOn,
                onCheckedChange = { prefs.flashMode = it },
                modifier = Modifier.padding(top = 14.dp)
            )
            SwitchCardRow(
                icon = R.drawable.ic_vibration,
                title = stringResource(R.string.vibration_mode),
                checked = vibrationOn,
                onCheckedChange = { prefs.vibrationMode = it },
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        AdaptiveBannerAd(screenKey = "ads_home_banner")
    }
}

/** Opens the Google Play in-app review dialog (Google decides if it actually shows). */
private fun launchInAppReview(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
            manager.launchReviewFlow(activity, task.result)
        }
    }
}