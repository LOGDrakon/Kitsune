package com.kitsune.feature.store

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.SkuInfo
import com.kitsune.core.billing.KitsuneBillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.BillingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val billingClient: KitsuneBillingClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<StoreUiState>(StoreUiState.Loading)
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    private val _creditBalance = MutableStateFlow(0)
    val creditBalance: StateFlow<Int> = _creditBalance.asStateFlow()

    /** "NONE"/"PLUS"/"MAX" — lets the storefront mark the subscription the user already has
     * instead of offering both as if neither were active. */
    val subscriptionTier: StateFlow<String> = backendClient.subscriptionTier

    private val _purchaseFeedback = MutableSharedFlow<PurchaseFeedback>(replay = 0, extraBufferCapacity = 5)
    val purchaseFeedback: SharedFlow<PurchaseFeedback> = _purchaseFeedback.asSharedFlow()

    init {
        loadStoreData()
        observeBalance()
        observePurchaseResults()
    }

    fun reload() = loadStoreData()

    private fun loadStoreData() {
        viewModelScope.launch {
            try {
                backendClient.getSkuCatalog().fold(
                    onSuccess = { catalog ->
                        val skus = catalog.skus
                        billingClient.initialize()
                        val products = billingClient.queryProducts(skus)
                        _uiState.value = StoreUiState.Ready(skus, products)
                    },
                    onFailure = { e ->
                        _uiState.value = StoreUiState.Error(e.message ?: "Failed to load catalog")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = StoreUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun observeBalance() {
        viewModelScope.launch {
            backendClient.creditBalance.collect { balance ->
                _creditBalance.value = balance
            }
        }
    }

    private fun observePurchaseResults() {
        viewModelScope.launch {
            billingClient.purchaseResult.collect { result ->
                when (result) {
                    is KitsuneBillingClient.PurchaseResult.Success -> {
                        _purchaseFeedback.tryEmit(PurchaseFeedback.Success(result.sku))
                        refreshBalance()
                    }
                    is KitsuneBillingClient.PurchaseResult.Error -> {
                        _purchaseFeedback.tryEmit(PurchaseFeedback.Error(result.message))
                    }
                }
            }
        }
    }

    fun purchaseProduct(activity: Activity, sku: SkuInfo) {
        val state = _uiState.value as? StoreUiState.Ready ?: return
        val product = state.products.find { it.productId == sku.sku }
        if (product == null) {
            viewModelScope.launch { _purchaseFeedback.tryEmit(PurchaseFeedback.Error("Produit introuvable sur Google Play")) }
            return
        }

        val offerToken = if (sku.type == "SUBSCRIPTION") {
            product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        } else null

        val result = billingClient.launchPurchaseFlow(activity, product, offerToken)
        result.onFailure { e ->
            viewModelScope.launch { _purchaseFeedback.tryEmit(PurchaseFeedback.Error(e.message ?: "Erreur inconnue")) }
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            backendClient.refreshBalance()
        }
    }
}

/** [sku] lets observers react selectively — e.g. only refresh cosmetics ownership when the
 * purchased sku is a cosmetic, not a credit pack. */
sealed class PurchaseFeedback {
    data class Success(val sku: String) : PurchaseFeedback()
    data class Error(val message: String) : PurchaseFeedback()
}

sealed class StoreUiState {
    data object Loading : StoreUiState()
    data class Ready(val skus: List<SkuInfo>, val products: List<ProductDetails>) : StoreUiState()
    data class Error(val message: String) : StoreUiState()
}
