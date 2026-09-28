package com.clapfinder.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.clapfinder.app.R
import com.clapfinder.app.ads.AdaptiveBannerAd
import com.clapfinder.app.ads.rememberAdGate
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.plainClickable
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.util.Prefs
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class OnboardingPage(
    @DrawableRes val image: Int,
    @StringRes val title: Int,
    @StringRes val body: Int
)

private val pages = listOf(
    OnboardingPage(R.drawable.ob1, R.string.ob1_title, R.string.ob1_body),
    OnboardingPage(R.drawable.ob2, R.string.ob2_title, R.string.ob2_body),
    OnboardingPage(R.drawable.ob3, R.string.ob3_title, R.string.ob3_body),
)

/** Was OnboardingActivity (ViewPager2 + DotsIndicator). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val gate = rememberAdGate()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    fun finishOnboarding() {
        Prefs.get(context).onboardingDone = true
        gate.showInterstitial("ads_onboarding_inter") { onFinished() }
    }

    SystemBarIcons(dark = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
            .systemBarsPadding()
    ) {
        // Skip (INVISIBLE on the last page — keeps its space, not clickable)
        Text(
            text = stringResource(R.string.skip),
            modifier = Modifier
                .align(Alignment.End)
                .padding(12.dp)
                .then(if (isLast) Modifier else Modifier.plainClickable { finishOnboarding() })
                .padding(16.dp)
                .alpha(if (isLast) 0f else 1f),
            color = CF.White,
            fontSize = 15.sp
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        PagerDots(
            pagerState = pagerState,
            count = pages.size,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 24.dp)
        )

        // Action Button
        Box(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp) // Adjusted bottom padding to fit ad
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CF.White)
                .plainClickable {
                    if (pagerState.currentPage == pages.lastIndex) {
                        finishOnboarding()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(if (isLast) R.string.get_started else R.string.next),
                color = CF.GreenPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Adaptive Banner Ad, full width at the bottom
        AdaptiveBannerAd("ads_ob_banner")
    }
}

/** item_onboarding.xml */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(page.image),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = stringResource(page.title),
            modifier = Modifier.padding(top = 48.dp),
            color = CF.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(page.body),
            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 14.dp),
            color = CF.White,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * DotsIndicator (progressMode, widthFactor 2.5): current dot 20dp wide, dots up to the
 * current page white, the rest dark orange — follows the swipe like the old library.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagerDots(pagerState: PagerState, count: Int, modifier: Modifier = Modifier) {
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val distance = abs(position - i).coerceAtMost(1f)
            val filled = (position - i + 1f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = lerp(20.dp, 8.dp, distance), height = 8.dp)
                    .background(lerp(CF.DarkOrange, CF.White, filled), RoundedCornerShape(4.dp))
            )
        }
    }
}