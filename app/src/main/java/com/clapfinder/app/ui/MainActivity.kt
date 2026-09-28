package com.clapfinder.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clapfinder.app.ads.AdClickLoadingHost
import com.clapfinder.app.ads.AdManager
import com.clapfinder.app.ads.ConsentManager
import com.clapfinder.app.ads.WelcomeBackHost
import com.clapfinder.app.billing.BillingCache
import com.clapfinder.app.billing.BillingManager
import com.clapfinder.app.billing.PremiumManager
import com.clapfinder.app.ui.navigation.ClapFinderNavHost
import com.clapfinder.app.ui.theme.ClapFinderTheme

/**
 * The only Activity. Every old Activity is now a Compose route in [ClapFinderNavHost].
 * The service / FCM notifications still open this class, exactly as before.
 */
class MainActivity : ComponentActivity() {

    private var billingRestore: BillingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PremiumManager.init(this)

        billingRestore = BillingManager(this, object : BillingManager.BillingListener {
            override fun onProductsLoaded(products: Map<String, com.android.billingclient.api.ProductDetails>) {
            BillingCache.products = products
            }

            override fun onPurchaseSuccess() {}
            override fun onPurchaseFailed(msg: String) {}
        }).also { it.start() }
        // Transparent bars; every screen paints its own orange behind them and
        // picks its icon colour (SystemBarIcons), like the old per-activity themes.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Google consent (UMP) → then MobileAds init. The splash waits for it.
        if (!ConsentManager.resolved) AdManager.ensureConsent(this)

        setContent {
            ClapFinderTheme {
                // App open: covers the whole NavHost with "Welcome Back" when the
                // app returns from background, then shows the App Open ad.
                WelcomeBackHost {
                    ClapFinderNavHost()
                    AdClickLoadingHost()
                }
            }
        }
    }
}
