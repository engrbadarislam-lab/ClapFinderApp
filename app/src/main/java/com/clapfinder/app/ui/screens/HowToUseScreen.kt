package com.clapfinder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clapfinder.app.R
import com.clapfinder.app.ads.NativeAdView
import com.clapfinder.app.ui.components.BackTopBar
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.theme.CF

private data class HowToStep(val emoji: String, val title: String, val body: String)

private val steps = listOf(
    HowToStep(
        "👆", "1. Activate",
        "Tap the big power ring on the home screen to start listening."
    ),
    HowToStep(
        "🔑", "2. Allow permissions",
        "Grant microphone, notification and overlay access so it can listen and alert reliably."
    ),
    HowToStep(
        "👏", "3. Clap to find",
        "Whenever you misplace your phone, clap firmly. It will ring, flash and vibrate."
    ),
    HowToStep(
        "⚙️", "4. Fine-tune",
        "Change the sound, volume, alert length and sensitivity, or schedule quiet hours in Settings."
    ),
)

/** Was HowToUseActivity + activity_how_to_use.xml. */
@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    SystemBarIcons(dark = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
            .systemBarsPadding()
    ) {
        BackTopBar(title = stringResource(R.string.how_to_use), backSize = 34.dp, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            steps.forEach { step ->
                // style/HowToStep
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(CF.Card, CF.CardShape)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = step.emoji, fontSize = 34.sp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = step.title,
                            color = CF.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = step.body,
                            modifier = Modifier.padding(top = 3.dp),
                            color = CF.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Inserted Native Ad
            NativeAdView("ads_howtouse_native")
        }
    }
}