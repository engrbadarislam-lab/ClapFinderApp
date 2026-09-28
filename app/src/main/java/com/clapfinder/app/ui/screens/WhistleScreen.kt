package com.clapfinder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clapfinder.app.R
import com.clapfinder.app.ads.AdaptiveBannerAd
import com.clapfinder.app.service.ClapDetectionService
import com.clapfinder.app.ui.components.ActivateRing
import com.clapfinder.app.ui.components.CfIcon
import com.clapfinder.app.ui.components.CfSlider
import com.clapfinder.app.ui.components.SwitchCardRow
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.negativeTopMargin
import com.clapfinder.app.ui.components.plainClickable
import com.clapfinder.app.ui.components.rememberFinderActivation
import com.clapfinder.app.ui.components.rememberPrefsVersion
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.ui.theme.CardTitle
import com.clapfinder.app.util.Prefs
import kotlin.math.roundToInt

/** Was WhistleActivity + activity_whistle.xml. */
@Composable
fun WhistleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val version = rememberPrefsVersion(prefs)
    val running = remember(version) { prefs.whistleServiceRunning }
    val flashOn = remember(version) { prefs.flashMode }
    val vibrationOn = remember(version) { prefs.vibrationMode }

    var sensitivity by remember {
        mutableFloatStateOf(prefs.whistleSensitivity.toFloat().coerceIn(1f, 10f))
    }
    LifecycleResumeEffect(Unit) {
        sensitivity = prefs.whistleSensitivity.toFloat().coerceIn(1f, 10f)
        onPauseOrDispose { }
    }

    val activation = rememberFinderActivation(ClapDetectionService.MODE_WHISTLE)

    SystemBarIcons(dark = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.SplashGradient)          // Theme.ClapFinder.Other window background
            .systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // ===== Header (bg_header_gradient) =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CF.GreenPrimary)
                    .padding(bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CfIcon(
                        id = R.drawable.ic_back,
                        size = 30.dp,
                        tint = CF.White,
                        modifier = Modifier.plainClickable(onBack)
                    )
                    Text(
                        text = stringResource(R.string.whistle_finder),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        color = CF.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                ActivateRing(
                    running = running,
                    pulsing = false,
                    onClick = { activation.onActivateClicked() },
                    modifier = Modifier.padding(top = 26.dp)
                ) {
                    CfIcon(R.drawable.whistle_icon, 76.dp, tint = CF.GreenPrimary)
                }

                Text(
                    text = stringResource(
                        if (running) R.string.tap_to_stop else R.string.click_to_activate_whistle
                    ),
                    modifier = Modifier.padding(top = 18.dp),
                    color = CF.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // ===== Sheet (bg_sheet_top_round, marginTop -24dp) =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .negativeTopMargin(24.dp)
                    .background(CF.GreenPrimary, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 30.dp)
            ) {
                // Whistle sensitivity 1..10
                Column(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(CF.Card, CF.CardShape)
                        .padding(18.dp)
                ) {
                    Text(text = stringResource(R.string.whistle_sensitivity), style = CardTitle)
                    CfSlider(
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            prefs.whistleSensitivity = it.roundToInt()
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        trackHeight = 4.dp,
                        activeColor = CF.GreenPrimary,
                        inactiveColor = CF.GreenPrimary,
                        tickColor = CF.White,
                        showValueLabel = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    )
                }

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

            // Below the sheet (the old layout let the banner cover the sheet's bottom —
            // AdMob doesn't allow ads over app content, so it sits under it now)
            AdaptiveBannerAd(screenKey = "ads_whistle_banner")
        }
    }
}
