package com.clapfinder.app.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import kotlinx.coroutines.*

class BillingManager(
    private val context: Context,
    private val listener: BillingListener
) {

    interface BillingListener {
        fun onProductsLoaded(products: Map<String, ProductDetails>)
        fun onPurchaseSuccess()
        fun onPurchaseFailed(msg: String)
    }

    companion object {
        // Only keeping the one-time lifetime product
        const val LIFETIME_ID = "clap_finder_lifetime"
    }

    private val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private var billingClient: BillingClient? = null
    private val productMap = mutableMapOf<String, ProductDetails>()

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetry = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { processPurchase(it, isRestore = false) }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    listener.onPurchaseFailed("User cancelled")
                }
                else -> {
                    listener.onPurchaseFailed(result.debugMessage)
                }
            }
        }

    fun start() {
        billingClient?.endConnection()
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryCount = 0
                    queryInApp()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                reconnect()
            }
        })
    }

    private fun reconnect() {
        if (retryCount >= maxRetry) {
            listener.onPurchaseFailed("Billing unavailable")
            return
        }
        retryCount++
        handler.postDelayed({ start() }, (2000L * retryCount))
    }

    private fun queryInApp() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryResult.productDetailsList.forEach { productMap[it.productId] = it }
                listener.onProductsLoaded(productMap.toMap())
            }
        }
    }

    fun launchOneTime(activity: Activity) {
        val product = productMap[LIFETIME_ID]
            ?: return listener.onPurchaseFailed("Lifetime product not found")

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()

        product.oneTimePurchaseOfferDetails?.let {
            analytics.logEvent(FirebaseAnalytics.Event.BEGIN_CHECKOUT) {
                param(FirebaseAnalytics.Param.CURRENCY, it.priceCurrencyCode)
                param(FirebaseAnalytics.Param.VALUE, it.priceAmountMicros / 1_000_000.0)
                param(FirebaseAnalytics.Param.ITEM_ID, LIFETIME_ID)
                param(FirebaseAnalytics.Param.ITEM_NAME, "lifetime")
            }
        }

        billingClient?.launchBillingFlow(activity, params)
    }

    private fun processPurchase(purchase: Purchase, isRestore: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.acknowledgePurchase(ackParams) {}
        }

        setPremium(true)

        val boughtId = purchase.products.firstOrNull()
        val product = productMap[boughtId] ?: productMap[LIFETIME_ID]
        if (product != null) {
            PurchaseAnalytics.logPurchase(context, analytics, purchase, product)
        }

        if (!isRestore) {
            listener.onPurchaseSuccess()
        }
    }

    fun restorePurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val inappActive = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                purchases.forEach {
                    if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        processPurchase(it, isRestore = true)
                    }
                }
                setPremium(inappActive)
            } else {
                setPremium(false)
            }
        }
    }

    private fun setPremium(value: Boolean) {
        scope.launch {
            PremiumManager.setPremium(value)
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }
}