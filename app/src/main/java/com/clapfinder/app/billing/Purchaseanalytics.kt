package com.clapfinder.app.billing
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Fires a single, canonical Firebase `purchase` event for BOTH one-time (INAPP)
 * products and subscriptions (SUBS), carrying the real value + ISO currency from
 * ProductDetails. This is the value signal Google Ads Target ROAS bids against.
 *
 * Rules baked in:
 *  - Only ever call this AFTER the purchase is verified + acknowledged.
 *  - Fires at most ONCE per purchaseToken (dedup guard) so restores / re-processing
 *    / onPurchasesUpdated re-delivery can't inflate your reported revenue.
 *  - Reads price from oneTimePurchaseOfferDetails (INAPP) or the first PAID pricing
 *    phase of the subscription offer (SUBS), skipping free-trial / free phases.
 *
 * Written against Play Billing 8/9 API: one-time (INAPP) prices come from
 * oneTimePurchaseOfferDetailsList (a LIST). On Billing 7.x the singular
 * oneTimePurchaseOfferDetails is used instead — see the note in resolvePrice().
 */
object PurchaseAnalytics {

    private const val TAG = "PurchaseAnalytics"
    private const val PREFS = "purchase_analytics_guard"
    private const val KEY_LOGGED_TOKENS = "logged_purchase_tokens"

    /**
     * @param context        any Context (application context is fine)
     * @param analytics      your FirebaseAnalytics instance
     * @param purchase       the acknowledged Purchase from Play Billing
     * @param productDetails the ProductDetails for the product in this purchase
     *                       (cache these from queryProductDetailsAsync and look up
     *                        by purchase.products.first())
     */
    fun logPurchase(
        context: Context,
        analytics: FirebaseAnalytics,
        purchase: Purchase,
        productDetails: ProductDetails,
    ) {
        // 1) Guard: only PURCHASED state, and never log the same token twice.
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val token = purchase.purchaseToken
        if (token.isBlank()) return
        if (alreadyLogged(context, token)) {
            Log.d(TAG, "Skipping already-logged purchase token")
            return
        }

        // 2) Resolve real value + currency from ProductDetails.
        val priced = resolvePrice(productDetails) ?: run {
            Log.w(TAG, "No price found for ${productDetails.productId}; not logging")
            return
        }
        val (valueMicros, currency) = priced
        val value = valueMicros / 1_000_000.0
        if (value <= 0.0) {
            // Free trial / $0 phase — nothing to report as revenue.
            Log.d(TAG, "Zero-value phase for ${productDetails.productId}; not logging")
            return
        }

        val productId = purchase.products.firstOrNull() ?: productDetails.productId
        val txnId = purchase.orderId ?: token // orderId can be null for test purchases

        // 3) Build a GA4-shaped purchase event (value + currency drive ROAS).
        val item = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, productDetails.title)
            putDouble(FirebaseAnalytics.Param.PRICE, value)
            putLong(FirebaseAnalytics.Param.QUANTITY, 1L)
        }
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, value)          // e.g. 4.99 (major units)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)    // ISO 4217, e.g. "USD"
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, txnId)
            putParcelableArray(FirebaseAnalytics.Param.ITEMS, arrayOf(item))
        }

        analytics.logEvent(FirebaseAnalytics.Event.PURCHASE, params)
        markLogged(context, token)
        Log.d(TAG, "Logged purchase: $productId value=$value $currency")
    }

    /**
     * Returns Pair(priceAmountMicros, currencyCode) or null.
     * INAPP -> one-time offer. SUBS -> first PAID pricing phase of the first offer.
     */
    private fun resolvePrice(details: ProductDetails): Pair<Long, String>? {
        // ---- One-time / INAPP (Billing 8/9: offers are a LIST) ----
        // On Billing 8/9 the singular oneTimePurchaseOfferDetails is null; the price
        // lives in oneTimePurchaseOfferDetailsList. Take the first offer that charges.
        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull { it.priceAmountMicros > 0L }
            ?.let { one ->
                return one.priceAmountMicros to one.priceCurrencyCode
            }
        // NOTE (Billing 7.x): if you ever downgrade, the field above doesn't exist —
        // use: details.oneTimePurchaseOfferDetails?.let { return it.priceAmountMicros to it.priceCurrencyCode }

        // ---- Subscription / SUBS ----
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
        // Skip free-trial / $0 phases; report the first phase that actually charges.
        val paidPhase = offer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros > 0L }
            ?: return null
        return paidPhase.priceAmountMicros to paidPhase.priceCurrencyCode
    }

    // ---- Dedup guard (SharedPreferences-backed set of tokens) ----

    private fun alreadyLogged(context: Context, token: String): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_LOGGED_TOKENS, emptySet())!!.contains(token)
    }

    private fun markLogged(context: Context, token: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_LOGGED_TOKENS, emptySet())!!.toMutableSet()
        current.add(token)
        // Keep the set from growing unbounded over the app's lifetime.
        val trimmed = if (current.size > 500) current.toList().takeLast(500).toSet() else current
        prefs.edit().putStringSet(KEY_LOGGED_TOKENS, trimmed).apply()
    }
}