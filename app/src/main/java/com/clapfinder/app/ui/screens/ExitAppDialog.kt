package com.clapfinder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import com.clapfinder.app.R
import com.clapfinder.app.ads.AppOpenAdManager
import com.clapfinder.app.ads.NativeAdView
import com.clapfinder.app.ui.components.plainClickable
import com.clapfinder.app.ui.theme.AccelerateDecelerate
import com.clapfinder.app.ui.theme.CF
import kotlinx.coroutines.delay

private data class Promo(@DrawableRes val icon: Int, @StringRes val label: Int, val url: String)

private val promos = listOf(
    Promo(
        R.drawable.uvlight, R.string.uv_light,
        "https://play.google.com/store/apps/details?id=com.lw.uvlight.discolight.simulation"
    ),
    Promo(
        R.drawable.intruder_icon2, R.string.intruder_selfie,
        "https://play.google.com/store/apps/details?id=com.spy.hidden.camera.finder"
    ),
    Promo(
        R.drawable.fakecaller, R.string.fake_caller,
        "https://play.google.com/store/apps/details?id=com.lw.fake.call.prank"
    ),
)

/**
 * Full-screen exit dialog: "Yes" is locked for a 5-second countdown,
 * Native ad displayed in the center, and promo icons bounce at the bottom.
 */
@Composable
fun ExitAppDialog(onDismiss: () -> Unit, onNo: () -> Unit, onYes: () -> Unit) {
    var remaining by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    val yesEnabled = remaining == 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false // Ensures it can stretch fully edge-to-edge
        )
    ) {
        // Background dimming might not be visible due to full screen, but kept for safe measure
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(1.0f) }

        // Full screen surface
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CF.GreenPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_clap_512),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(R.string.are_you_sure_you_want_to_exit),
                    modifier = Modifier.padding(top = 16.dp),
                    color = CF.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Inserted Native Ad
                NativeAdView("ads_exit_native")

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onNo,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CF.DarkOrange,
                            contentColor = CF.White
                        )
                    ) {
                        Text(stringResource(R.string.no), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onYes,
                        enabled = yesEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CF.OrangePrimary,
                            contentColor = CF.White,
                            disabledContainerColor = CF.OrangePrimaryDisabled,
                            disabledContentColor = CF.White
                        )
                    ) {
                        Text(
                            text = if (yesEnabled) stringResource(R.string.yes1)
                            else stringResource(R.string.yes, remaining),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.try_our_other_apps),
                    modifier = Modifier.padding(top = 22.dp),
                    color = CF.White,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    promos.forEachIndexed { index, promo -> PromoIcon(promo, index) }
                }
            }
        }
    }
}

@Composable
private fun PromoIcon(promo: Promo, index: Int) {
    val context = LocalContext.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(index * 200L)
        scale.animateTo(
            1.15f,
            infiniteRepeatable(tween(650, easing = AccelerateDecelerate), RepeatMode.Reverse)
        )
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .plainClickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(promo.url)))
                    AppOpenAdManager.skipNextForeground()
                } catch (e: Exception) {
                    // no browser / Play Store — ignore, as before
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(65.dp)) {
            Image(
                painter = painterResource(promo.icon),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(R.string.ad_tag),
                modifier = Modifier.align(Alignment.TopEnd),
                color = CF.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(promo.label),
            modifier = Modifier.padding(top = 6.dp),
            color = CF.White,
            fontSize = 11.sp
        )
    }
}