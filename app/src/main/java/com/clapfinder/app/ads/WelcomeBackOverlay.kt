package com.clapfinder.app.ads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clapfinder.app.R
import kotlinx.coroutines.delay

/**
 * Full-screen "Welcome Back" overlay, styled like the ClapFinder splash
 * (orange gradient, power badge with the Home screen's sonar rings, white
 * loader). When AppOpenAdManager flags a return-to-foreground, it covers the
 * UI, loads + shows the App Open ad, then disappears.
 */
@Composable
fun WelcomeBackHost(content: @Composable () -> Unit) {
    var showing by remember { mutableStateOf(false) }

    // Poll the manager's flag (it's set from a process-lifecycle callback)
    LaunchedEffect(Unit) {
        while (true) {
            if (AppOpenAdManager.welcomeBackRequested && !showing) {
                AppOpenAdManager.consumeWelcomeBackRequest()
                showing = true
                // Overlay is up; now load + show the ad (no preload)
                AppOpenAdManager.loadAndShow { showing = false }
                // Safety: never trap the user if the SDK stalls. Waits just past the
                // manager's own 10s load timeout, so the overlay and the ad give up together.
                var waited = 0
                while (showing && waited++ < 105) delay(100)
                showing = false
            }
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = showing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WelcomeBackScreen()
        }
    }
}

@Composable
private fun WelcomeBackScreen() {
    val orange = Color(0xFFF57C00)      // @color/green_primary
    val orangeSoft = Color(0xFFFFF0E1)  // @color/green_soft

    // Two staggered "sonar" rings — same rhythm as the Home activate button
    val transition = rememberInfiniteTransition(label = "welcome")
    val ring1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1"
    )
    val ring2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(900, StartOffsetType.FastForward)
        ),
        label = "ring2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallow taps so nothing underneath is clickable
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            // Same gradient as res/drawable/bg_splash.xml
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFFF57C00),
                    0.5f to Color(0xFFFF8F2E),
                    1f to Color(0xFFDB6A00)
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    for (p in listOf(ring1, ring2)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = 1f + 0.6f * p
                                    scaleX = s
                                    scaleY = s
                                    alpha = 0.35f * (1f - p)
                                }
                                .background(Color.White, CircleShape)
                        )
                    }
                    // Splash badge: soft circle + power icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(orangeSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_power),
                            contentDescription = null,
                            modifier = Modifier.size(66.dp),
                            colorFilter = ColorFilter.tint(orange)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    "Welcome Back",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.app_name),
                    color = Color(0xDDFFFFFF),
                    fontSize = 15.sp
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Loading…", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
