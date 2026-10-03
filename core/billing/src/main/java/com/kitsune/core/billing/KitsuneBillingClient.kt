package com.kitsune.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.SkuInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class KitsuneBillingClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendClient: KitsuneBackendClient
) {
    private var billingClient: BillingClient? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>(replay = 0, extraBufferCapacity = 5)
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    suspend fun initialize() {
        if (billingClient != null) {
            if (_connectionState.value == ConnectionState.CONNECTED) return
        } else {
            billingClient = BillingClient.newBuilder(context)
                .setListener { result, purchases ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                        purchases.forEach { handlePurchase(it) }
                    } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                        _purchaseResult.tryEmit(PurchaseResult.Error("Purchase failed: ${result.responseCode}"))
                    }
                }
                .enablePendingPurchases()
                .build()

            billingClient?.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        _connectionState.value = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            ConnectionState.CONNECTED
                        } else {
                            ConnectionState.ERROR
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            )
        }

        withTimeoutOrNull(10_000) {
            _connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.ERROR }
        }
        if (_connectionState.value != ConnectionState.CONNECTED) {
            error("Failed to connect to Google Play Billing")
        }
    }

    suspend fun queryProducts(skuList: List<SkuInfo>): List<ProductDetails> {
        val client = billingClient
        if (client == null || !client.isReady) return emptyList()

        val inAppProducts = skuList.filter { it.type != "SUBSCRIPTION" }
        val subscriptionProducts = skuList.filter { it.type == "SUBSCRIPTION" }

        val results = mutableListOf<ProductDetails>()

        if (inAppProducts.isNotEmpty()) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    inAppProducts.map { sku ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(sku.sku)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()

            val result = queryProductDetails(client, params)
            results.addAll(result)
        }

        if (subscriptionProducts.isNotEmpty()) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    subscriptionProducts.map { sku ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(sku.sku)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
                )
                .build()

            val result = queryProductDetails(client, params)
            results.addAll(result)
        }

        _availableProducts.value = results
        return results
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = null
    ): Result<Unit> {
        val client = billingClient
        if (client == null || !client.isReady) {
            return Result.failure(Exception("Billing client not ready"))
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS && offerToken != null) {
            productParams.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Purchase flow failed: ${result.responseCode}"))
        }
    }

    private suspend fun queryProductDetails(
        client: BillingClient,
        params: QueryProductDetailsParams
    ): List<ProductDetails> = suspendCancellableCoroutine { continuation ->
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(productDetailsList ?: emptyList())
            } else {
                continuation.resume(emptyList())
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val sku = purchase.products.firstOrNull() ?: return
            val token = purchase.purchaseToken
            val orderId = purchase.orderId ?: return

            CoroutineScope(Dispatchers.IO).launch {
                val result = backendClient.verifyPurchase(sku, token, orderId)
                result.fold(
                    onSuccess = {
                        if (!purchase.isAcknowledged) {
                            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(token)
                                .build()
                            billingClient?.acknowledgePurchase(acknowledgeParams) { _ -> }
                        }
                        _purchaseResult.tryEmit(PurchaseResult.Success(sku, orderId))
                    },
                    onFailure = { e ->
                        _purchaseResult.tryEmit(PurchaseResult.Error(e.message ?: "Verification failed"))
                    }
                )
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED,
        ERROR
    }

    sealed class PurchaseResult {
        data class Success(val sku: String, val orderId: String) : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }
}
