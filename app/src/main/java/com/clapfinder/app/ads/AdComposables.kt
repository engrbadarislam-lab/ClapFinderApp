package com.clapfinder.app.ads

import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.AdLoader
import com.clapfinder.app.billing.PremiumManager

import com.clapfinder.app.R

/**
 * Ad composables — the "visit = request" rule lives here.
 *
 *  • Nothing renders (no request) if premium OR the RC screen key is off
 *  • Shimmer while loading; failure hides the placeholder entirely
 *  • AdView / NativeAd are destroyed in onDispose (no leaks on rotation
 *    or nav change)
 *  • Cycle-aware: the AndroidView reuses the same view across
 *    recompositions, and the DisposableEffect frees it once
 */

// ────────────────────────────────────────────────────────────────
//  Adaptive banner
// ────────────────────────────────────────────────────────────────
@Composable
fun AdaptiveBannerAd(
    screenKey: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true          // collapses to a small bar with expand arrow
) {
    if (PremiumManager.isPremium || !AdManager.canShowAds || !AdConfig.isEnabled(screenKey)) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Anchored adaptive size — Google's recommended banner shape
    val adSize = remember(configuration) {
        val metrics = DisplayMetrics().apply {
            @Suppress("DEPRECATION")
            (context as android.app.Activity).windowManager
                .defaultDisplay.getMetrics(this)
        }
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    val adView = remember {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = AdConfig.adaptiveUnit()
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true; failed = false
                }

                override fun onAdFailedToLoad(e: LoadAdError) {
                    loaded = false; failed = true
                }

                override fun onAdClicked() {
                    // Coming back from the advertiser must not trigger Welcome Back.
                    AppOpenAdManager.skipNextForeground()
                }
            }
            loadAd(AdManager.request())
        }
    }
//    val adView = remember {
//        AdView(context).apply {
//            setAdSize(adSize)
//            adUnitId = AdConfig.adaptiveUnit(screenKey)
//            adListener = object : AdListener() {
//                override fun onAdLoaded() {
//                    loaded = true; failed = false
//                }
//
//                override fun onAdFailedToLoad(e: LoadAdError) {
//                    loaded = false; failed = true
//                }
//            }
//            val request = if (collapsible) {
//                val extras = android.os.Bundle().apply {
//                    // "bottom" = collapses toward the bottom edge (banner is
//                    // pinned above the nav). Use "top" for top-anchored banners.
//                    putString("collapsible", "bottom")
//                }
//                com.google.android.gms.ads.AdRequest.Builder()
//                    .addNetworkExtrasBundle(
//                        com.google.ads.mediation.admob.AdMobAdapter::class.java, extras
//                    )
//                    .build()
//            } else AdManager.request()
//            loadAd(request)
//        }
//    }
    DisposableEffect(Unit) { onDispose { adView.destroy() } }

    if (failed) return

    Box(
        modifier
            .fillMaxWidth()
            .height(adSize.height.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) ShimmerBox(Modifier.fillMaxSize())
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun NativeAdView(
    screenKey: String,
    modifier: Modifier = Modifier
) {
    if (PremiumManager.isPremium || !AdManager.canShowAds || !AdConfig.isEnabled(screenKey)) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(screenKey) {
        val loader = AdLoader.Builder(context, AdConfig.nativeUnit(screenKey))
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                failed = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(err: LoadAdError) {
                    failed = true
                }

                override fun onAdClicked() {
                    AppOpenAdManager.skipNextForeground()
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
        loader.loadAd(AdManager.request())
        onDispose { nativeAd?.destroy(); nativeAd = null }
    }

    if (failed) return

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val ad = nativeAd
        if (ad == null) {
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFFEFEFEF), RoundedCornerShape(16.dp))
            )
        } else {
            ComposeNativeAd(ad)
        }
    }
}

@Composable
private fun ComposeNativeAd(ad: NativeAd) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { c -> buildNativeAdView(c) },
        update = { adView -> bindNativeAd(adView, ad) }
    )
}

private fun buildNativeAdView(c: android.content.Context): NativeAdView {
    val density = c.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val adView = NativeAdView(c)

    val card = android.widget.LinearLayout(c).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(android.graphics.Color.WHITE)
            setStroke(dp(1), 0xFFE1E4E8.toInt())
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Media — at the top of the card
    val media = com.google.android.gms.ads.nativead.MediaView(c).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(130)
        ).apply { bottomMargin = dp(12) }
    }
    card.addView(media)

    // Row: icon + text block
    val row = android.widget.LinearLayout(c).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    val badge = android.widget.TextView(c).apply {
        text = "Ad"
        setTextColor(0xFF5A6B87.toInt())
        ; textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(2), dp(4), dp(2))

        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) }
    }

    val icon = android.widget.ImageView(c).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(44), dp(44))
    }
    val textCol = android.widget.LinearLayout(c).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(10) }
    }
    val headlineRow = android.widget.LinearLayout(c).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }
    val headline = android.widget.TextView(c).apply {
        setTextColor(0xFF1B2B4B.toInt()); textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
    }
    val advertiser = android.widget.TextView(c).apply {
        setTextColor(0xFF5A6B87.toInt()); textSize = 12f
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    headlineRow.addView(badge)
    headlineRow.addView(headline)
    textCol.addView(headlineRow)
    textCol.addView(advertiser)

    row.addView(icon)
    row.addView(textCol)
    card.addView(row)

    // Body
    val body = android.widget.TextView(c).apply {
        setTextColor(0xFF5A6B87.toInt()); textSize = 13f
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    }
    card.addView(body)

    // CTA — at the bottom of the card
    val cta = android.widget.Button(c).apply {
        isAllCaps = false
        setTextColor(android.graphics.Color.WHITE); textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(0xFF2D7CF6.toInt())
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { topMargin = dp(12) }
    }
    card.addView(cta)

    adView.addView(card)

    adView.mediaView = media
    adView.headlineView = headline
    adView.bodyView = body
    adView.iconView = icon
    adView.advertiserView = advertiser
    adView.callToActionView = cta

    return adView
}

private fun bindNativeAd(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? android.widget.TextView)?.text = ad.headline
    (adView.bodyView as? android.widget.TextView)?.apply {
        text = ad.body ?: ""
        visibility =
            if (ad.body.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    (adView.callToActionView as? android.widget.Button)?.apply {
        text = ad.callToAction ?: "Learn more"
        visibility =
            if (ad.callToAction.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    (adView.iconView as? android.widget.ImageView)?.apply {
        val d = ad.icon?.drawable
        if (d != null) {
            setImageDrawable(d); visibility = android.view.View.VISIBLE
        } else visibility = android.view.View.GONE
    }
    (adView.advertiserView as? android.widget.TextView)?.apply {
        val txt = ad.advertiser ?: ad.store
        text = txt ?: ""
        visibility = if (txt.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    (adView.mediaView)?.mediaContent = ad.mediaContent

    adView.setNativeAd(ad)
}

// ────────────────────────────────────────────────────────────────
//  Shimmer placeholder
// ────────────────────────────────────────────────────────────────
@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition("adShimmer")
    val alpha by transition.animateFloat(
        0.35f, 0.75f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier
            .background(
                Color(0xFFE1E4E8).copy(alpha = alpha),
                RoundedCornerShape(8.dp)
            )
    )
}

// ────────────────────────────────────────────────────────────────
//  Interstitial / Rewarded loading dialog
// ────────────────────────────────────────────────────────────────
@Composable
fun AdLoadingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )
    }
}