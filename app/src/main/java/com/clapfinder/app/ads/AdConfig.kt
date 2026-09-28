package com.clapfinder.app.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings


object AdConfig {

    // ── Google Test Unit IDs (never generate revenue, always fill) ──
    private object Test {
        const val BANNER = "ca-app-pub-3940256099942544/9214589741"
        const val ADAPTIVE = "ca-app-pub-3940256099942544/9214589741"
        const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
        const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    }

    private lateinit var appContext: Context
    private var isDebug = false

    // Per-placement prod IDs (from ads.xml). Empty → falls back to test.
    private var prodMainBanner = ""   // bottom-nav / home banner
    private var prodIntroBanner = ""   // onboarding banner
    private var prodNative = ""   // QR / native / labels
    private var prodSplashInter = ""
    private var prodSplashInterFallback = ""   // ← NEW
    private var prodGlobalInter = ""
    private var prodPrintInter = ""
    private var prodRewarded = ""
    private var prodAppOpen = ""
    private var prodNative2 = ""          // ← NEW

    fun init(context: Context) {
        appContext = context.applicationContext
        isDebug = (appContext.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val res = appContext.resources
        val pkg = appContext.packageName
        fun s(name: String) = res.getIdentifier(name, "string", pkg)
            .takeIf { it != 0 }?.let { res.getString(it) }.orEmpty()
        prodMainBanner = s("admob_main_banner")
        prodIntroBanner = s("admob_intro_banner")
        prodNative = s("admob_native")
        prodSplashInter = s("admob_splash_inter")
        prodSplashInterFallback = s("admob_splash_inter_fallback")   // ← NEW
        prodGlobalInter = s("admob_global_inter")
        prodPrintInter = s("admob_print_inter")
        prodRewarded = s("admob_rewarded")
        prodAppOpen = s("admob_app_open")
        prodNative2 = s("admob_native_2")   // ← NEW


        initRemoteConfig()
    }

    // ── Ad unit IDs, mapped per screenKey ──
    // Banner: pick the right unit by which screen asked for it.
    fun adaptiveUnit(screenKey: String = ""): String {
        if (isDebug) return Test.ADAPTIVE
        val id = when (screenKey) {
            "ads_splash_banner",
            "ads_onboarding_banner" -> prodIntroBanner   // intero: splash + onboarding
            else -> prodMainBanner    // main: bottom nav + rest
        }
        return id.ifBlank { Test.ADAPTIVE }
    }

    fun bannerUnit(screenKey: String = ""): String = adaptiveUnit(screenKey)
    fun nativeUnit(screenKey: String = ""): String {
        if (isDebug) return Test.NATIVE
        val id = when (screenKey) {
            "ads_language_native_2" -> prodNative2   // ← NEW branch
            else -> prodNative
        }
        return id.ifBlank { Test.NATIVE }
    }

    // Interstitial: splash / global-click / print each have their own unit.
    fun interstitialUnit(screenKey: String = ""): String {
        if (isDebug) return Test.INTERSTITIAL
        val id = when (screenKey) {
            "ads_splash_inter" -> prodSplashInter
            "ads_splash_inter_fallback" -> prodSplashInterFallback
            "ads_print_inter" -> prodPrintInter
            else -> prodGlobalInter
        }
        return id.ifBlank { Test.INTERSTITIAL }
    }

    fun rewardedUnit(): String = if (isDebug) Test.REWARDED
    else prodRewarded.ifBlank { Test.REWARDED }

    fun appOpenUnit(): String = if (isDebug) Test.APP_OPEN
    else prodAppOpen.ifBlank { Test.APP_OPEN }

    private val rc by lazy { FirebaseRemoteConfig.getInstance() }

    private fun initRemoteConfig() {
        runCatching {
            rc.setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (isDebug) 0 else 3_600
            })
            rc.setDefaultsAsync(defaults)
            rc.fetchAndActivate()
        }
    }

    private val defaults: Map<String, Any> = mapOf(
        "ads_master_enabled" to true,
        "ads_splash_inter" to true,
        "ads_splash_inter_fallback" to true,
        "ads_app_open" to true,
        "ads_settings_banner" to true,
        "ads_global_click" to true,
        "ads_global_click_every" to 3,

        // ClapFinder placements (listed here so Remote Config can switch them off)
        "ads_home_banner" to true,
        "ads_whistle_banner" to true,
        "ads_onboarding_inter" to true,
        "ads_exit_native" to true,
        "ads_howtouse_native" to true,
        "ads_ob_banner" to true,
    )

    /** Screen-level check used by every ad view before it requests. */
    fun isEnabled(screenKey: String): Boolean {
        if (!rc.getBoolean("ads_master_enabled")) return false
        // If the specific key exists in defaults, obey RC; else default true.
        return if (defaults.containsKey(screenKey))
            rc.getBoolean(screenKey) else true
    }

    /** How many global clicks before an interstitial is shown. Defaults to 3. */
    fun globalClickThreshold(): Int {
        val raw = rc.getLong("ads_global_click_every")
        val value = raw.toInt().coerceAtLeast(1)
        Log.d("AdConfig", "globalClickThreshold raw=$raw resolved=$value")
        return value
    }
}