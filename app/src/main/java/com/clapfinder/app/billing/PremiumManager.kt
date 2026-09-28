package com.clapfinder.app.billing

import android.content.Context
import com.android.billingclient.api.ProductDetails
import com.clapfinder.app.prefs.Prefs.isPremium
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Premium state holder. Same public API as the original DataStore version,
 * but backed by the app's existing SharedPreferences (Prefs.isPremium) so
 * every existing `context.isPremium` check in the app stays in sync with
 * billing automatically — no extra dependency.
 */
object PremiumManager {

    private var appContext: Context? = null

    private val _isPremium = MutableStateFlow(false)
    val isPremiumFlow: StateFlow<Boolean> = _isPremium

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _isPremium.value = context.applicationContext.isPremium
    }

    val isPremium: Boolean
        get() = _isPremium.value

    fun setPremium(value: Boolean) {
        appContext?.isPremium = value
        _isPremium.value = value
    }
}


object BillingCache {
    private val _products = MutableStateFlow<Map<String, ProductDetails>?>(null)
    val productsFlow: StateFlow<Map<String, ProductDetails>?> = _products.asStateFlow()

    /** Backing field kept for any existing code that reads/writes BillingCache.products directly. */
    var products: Map<String, ProductDetails>?
        get() = _products.value
        set(value) { _products.value = value }
}

/**
 * ClapFinder has no in-app purchase yet, so ads are always on.
 * The ads package only reads [isPremium] — when you add Play Billing ("Remove ADs"),
 * make this reactive (e.g. `var isPremium by mutableStateOf(false)`) and set it
 * from your billing client; banners and interstitials will stop automatically.
 */
