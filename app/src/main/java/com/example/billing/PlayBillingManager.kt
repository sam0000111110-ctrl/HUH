package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing lifecycle (`BillingClient`) for consumable Coin Packs.
 * Never grants coins locally: always forwards `purchaseToken` to the backend
 * (`/api/billing/verify-play-purchase`) and calls `consumeAsync` only after server verification succeeds.
 */
class PlayBillingManager(
    context: Context,
    private val onVerifiedPurchaseReady: (productId: String, purchaseToken: String, orderId: String?) -> Unit,
    private val onBillingError: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"
        val COIN_PRODUCT_IDS = listOf(
            "ayer_coins_100",
            "ayer_coins_550",
            "ayer_coins_1200",
            "ayer_coins_3000",
            "ayer_coins_7500"
        )

        fun sha256Hex(input: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _formattedPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val formattedPrices: StateFlow<Map<String, String>> = _formattedPrices.asStateFlow()

    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient? = try {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    } catch (t: Throwable) {
        Log.w(TAG, "BillingClient unavailable in this environment", t)
        null
    }

    fun startConnection() {
        val client = billingClient ?: return
        if (client.isReady) {
            queryProductCatalog()
            queryUnconsumedPurchases()
            return
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isBillingReady.value = true
                        queryProductCatalog()
                        queryUnconsumedPurchases()
                    } else {
                        _isBillingReady.value = false
                        Log.w(TAG, "Play Billing setup failed: ${billingResult.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _isBillingReady.value = false
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start Play Billing connection", t)
            _isBillingReady.value = false
        }
    }

    fun queryProductCatalog() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val productList = COIN_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val priceMap = mutableMapOf<String, String>()
                detailsList.forEach { details ->
                    productDetailsById[details.productId] = details
                    details.oneTimePurchaseOfferDetails?.formattedPrice?.let { price ->
                        priceMap[details.productId] = price
                    }
                }
                _formattedPrices.value = priceMap
            }
        }
    }

    fun queryUnconsumedPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity?, productId: String, userUid: String? = null) {
        val client = billingClient
        if (client == null || !client.isReady || activity == null) {
            onBillingError("متجر Google Play غير متاح على هذا الجهاز حالياً. لا يمكن شحن النقاط بدون تحقق رسمي.")
            return
        }
        val details = productDetailsById[productId]
        if (details == null) {
            queryProductCatalog()
            onBillingError("جاري تحميل أسعار باقة ($productId) من متجر Google Play. يرجى المحاولة بعد لحظات.")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        if (!userUid.isNullOrBlank()) {
            flowParamsBuilder.setObfuscatedAccountId(sha256Hex(userUid))
        }

        val flowParams = flowParamsBuilder.build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onBillingError("تعذر فتح نافذة الدفع (${result.debugMessage})")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onBillingError("تم إلغاء عملية الشراء.")
            }
            else -> {
                onBillingError("تعذر إتمام الدفع عبر Google Play: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val productId = purchase.products.firstOrNull().orEmpty()
                if (productId.isNotBlank() && purchase.purchaseToken.isNotBlank()) {
                    onVerifiedPurchaseReady(productId, purchase.purchaseToken, purchase.orderId)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                onBillingError("عملية الدفع قيد الانتظار لدى Google Play. ستُضاف النقاط تلقائياً فور التفعيل.")
            }
            else -> Unit
        }
    }

    fun consumeVerifiedPurchase(purchaseToken: String) {
        val client = billingClient ?: return
        if (!client.isReady || purchaseToken.isBlank()) return
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "consumeAsync returned code=${result.responseCode}: ${result.debugMessage}")
            }
        }
    }

    fun endConnection() {
        runCatching { billingClient?.endConnection() }
    }
}
