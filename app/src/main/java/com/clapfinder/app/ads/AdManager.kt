package com.clapfinder.app.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.clapfinder.app.billing.PremiumManager

object AdManager {

    private const val TAG = "AdManager"
    private const val SLOW_NET_TIMEOUT_MS = 10_000L
    private const val INTER_COOLDOWN_MS = 0L

    private var initialized = false
    private var lastInterShownAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    // ── Consent (UMP) ───────────────────────────────────────────
    /** True once UMP consent has resolved and ads may be requested. */
    val canShowAds: Boolean get() = ConsentManager.canShowAds

    /** True once the UMP flow finished (consented, declined, or failed). */
    val consentResolved: Boolean get() = ConsentManager.resolved

    /**
     * Gather UMP consent (shows the form only where required), then init ads.
     * Call once on the splash. Outside GDPR regions canShowAds flips true
     * almost immediately, so there is no added wait.
     */
    fun ensureConsent(activity: Activity) {
        ConsentManager.start(activity) { init(activity) }
    }

    /**
     * Initialize the Mobile Ads SDK OFF the main thread.
     *
     * MobileAds.initialize does heavy I/O and, with mediation, binds Custom
     * Tabs / adapter services — doing that on the main thread stalls it and
     * triggers ANRs (the GMS "customtabs" binder ANR). Google explicitly
     * recommends initializing on a background thread.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val appCtx = context.applicationContext
        Thread {
            runCatching {
                AdConfig.init(appCtx)
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder().setTestDeviceIds(emptyList()).build()
                )
                MobileAds.initialize(appCtx) {
                    Log.d(TAG, "MobileAds ready — ${it.adapterStatusMap.size} adapters")
                }
            }.onFailure { Log.w(TAG, "MobileAds init failed: ${it.message}") }
        }.apply { name = "MobileAdsInit"; isDaemon = true }.start()
    }

    fun request(): AdRequest = AdRequest.Builder().build()

    // ── Quick network check ──
    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ────────────────────────────────────────────────────────────
    //  Interstitial
    // ────────────────────────────────────────────────────────────
    fun showInterstitial(
        activity: Activity,
        screenKey: String,
        fallbackScreenKey: String? = null,
        onLoading: (Boolean) -> Unit = {},
        onDone: () -> Unit
    ) {
        // Guard: the completion callback must run exactly once, and always on
        // the main thread, so a late/duplicate ad-dismiss can't double-navigate.
        var finished = false
        val done = {
            if (!finished) {
                finished = true
                handler.post { onDone() }
            }
        }

        if (PremiumManager.isPremium || !canShowAds || !AdConfig.isEnabled(screenKey) ||
            System.currentTimeMillis() - lastInterShownAt < INTER_COOLDOWN_MS
        ) { done(); return }

        if (!isOnline(activity)) { done(); return }

        onLoading(true)
        val timeout = Runnable {
            Log.d(TAG, "Slow internet timeout — giving up")
            onLoading(false)
            done()
        }
        handler.postDelayed(timeout, SLOW_NET_TIMEOUT_MS)

        fun cleanup() = handler.removeCallbacks(timeout)

        fun attempt(unitKey: String, isFallback: Boolean = false) {
            InterstitialAd.load(
                activity, AdConfig.interstitialUnit(unitKey), request(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        cleanup()
                        // Timed out already → the user has moved on; never pop a late ad.
                        if (finished) return
                        onLoading(false)
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                AppOpenAdManager.suppressNextResume = true
                            }
                            override fun onAdClicked() {
                                // Coming back from the advertiser must not trigger Welcome Back.
                                AppOpenAdManager.skipNextForeground()
                            }
                            override fun onAdDismissedFullScreenContent() {
                                lastInterShownAt = System.currentTimeMillis()
                                done()
                            }
                            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                                done()
                            }
                        }
                        ad.show(activity)
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        if (!isFallback && fallbackScreenKey != null &&
                            AdConfig.isEnabled(fallbackScreenKey)
                        ) {
                            Log.d(TAG, "primary failed, trying fallback: $fallbackScreenKey")
                            attempt(fallbackScreenKey, isFallback = true)
                        } else {
                            cleanup()
                            Log.d(TAG, "interstitial load failed: ${err.message}")
                            onLoading(false)
                            done()
                        }
                    }
                }
            )
        }

        attempt(screenKey)
    }

    // ────────────────────────────────────────────────────────────
    //  Interstitial NOW
    // ────────────────────────────────────────────────────────────
    fun showInterstitialNow(
        activity: Activity,
        screenKey: String,
        onLoading: (Boolean) -> Unit = {},
        onProceed: () -> Unit
    ) {
        var finished = false
        val proceed = {
            if (!finished) {
                finished = true
                handler.post { onProceed() }
            }
        }

        if (PremiumManager.isPremium || !canShowAds || !AdConfig.isEnabled(screenKey)) {
            proceed(); return
        }

        if (!isOnline(activity)) { proceed(); return }

        onLoading(true)
        val timeout = Runnable {
            Log.d(TAG, "Slow internet timeout — giving up")
            onLoading(false)
            proceed()
        }
        handler.postDelayed(timeout, SLOW_NET_TIMEOUT_MS)

        fun cleanup() = handler.removeCallbacks(timeout)

        InterstitialAd.load(
            activity, AdConfig.interstitialUnit(screenKey), request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cleanup()
                    // Timed out already → the user has moved on; never pop a late ad.
                    if (finished) return
                    onLoading(false)
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            AppOpenAdManager.suppressNextResume = true
                        }
                        override fun onAdClicked() {
                            AppOpenAdManager.skipNextForeground()
                        }
                        override fun onAdDismissedFullScreenContent() { proceed() }
                        override fun onAdFailedToShowFullScreenContent(e: AdError) { proceed() }
                    }
                    ad.show(activity)
                }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    cleanup()
                    Log.d(TAG, "print interstitial load failed: ${err.message}")
                    onLoading(false)
                    proceed()
                }
            }
        )
    }

    // ────────────────────────────────────────────────────────────
    //  Rewarded  (with diagnostic logging)
    // ────────────────────────────────────────────────────────────
    fun showRewarded(
        activity: Activity,
        screenKey: String,
        onLoading: (Boolean) -> Unit = {},
        onEarned: () -> Unit,
        onDone: () -> Unit
    ) {
        var finished = false
        val done = {
            if (!finished) {
                finished = true
                handler.post { onDone() }
            }
        }

        if (PremiumManager.isPremium) {
            Log.d(TAG, "rewarded skipped: premium")
            onEarned(); done(); return
        }
        if (!AdConfig.isEnabled(screenKey)) {
            Log.d(TAG, "rewarded skipped: RC disabled for $screenKey")
            onEarned(); done(); return
        }
        if (!isOnline(activity)) {
            Log.d(TAG, "rewarded skipped: offline")
            done(); return
        }

        Log.d(TAG, "rewarded: loading unit=${AdConfig.rewardedUnit()} for $screenKey")
        onLoading(true)
        val timeout = Runnable {
            Log.d(TAG, "rewarded: TIMEOUT (10s) — giving up")
            onLoading(false)
            done()
        }
        handler.postDelayed(timeout, SLOW_NET_TIMEOUT_MS)

        fun cleanup() = handler.removeCallbacks(timeout)

        RewardedAd.load(
            activity, AdConfig.rewardedUnit(), request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "rewarded: LOADED, showing…")
                    cleanup()
                    // Timed out already → the user has moved on; never pop a late ad.
                    if (finished) return
                    onLoading(false)
                    var earned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "rewarded: SHOWED")
                            AppOpenAdManager.suppressNextResume = true
                        }
                        override fun onAdClicked() {
                            AppOpenAdManager.skipNextForeground()
                        }
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "rewarded: DISMISSED (earned=$earned)")
                            if (earned) onEarned()
                            done()
                        }
                        override fun onAdFailedToShowFullScreenContent(e: AdError) {
                            Log.d(TAG, "rewarded: FAILED TO SHOW — ${e.message}")
                            done()
                        }
                    }
                    ad.show(activity) { _: RewardItem ->
                        Log.d(TAG, "rewarded: REWARD EARNED")
                        earned = true
                    }
                }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    Log.d(TAG, "rewarded: FAILED TO LOAD — code=${err.code} msg=${err.message}")
                    cleanup()
                    onLoading(false)
                    done()
                }
            }
        )
    }
}