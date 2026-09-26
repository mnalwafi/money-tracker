package com.danilkinkin.buckwheat.data

import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class GatewayDefinition(
    val id: String,
    val packagePatterns: List<String>,
    val keywords: List<String>
)

sealed class PendingExpenseAddResult {
    data class Added(val expense: ParsedExpense) : PendingExpenseAddResult()
    data class Merged(val mergedExpense: ParsedExpense) : PendingExpenseAddResult()
    object IgnoredDuplicate : PendingExpenseAddResult()
}

@Singleton
class PendingExpenseRepository @Inject constructor() {

    companion object {
        const val DEDUPLICATION_WINDOW_MS = 30_000L // 30 seconds

        // Known payment processors / gateways that bridge banks with merchants
        private val GATEWAY_DEFINITIONS = listOf(
            GatewayDefinition(
                id = "google",
                packagePatterns = listOf(
                    "com.google.android.apps.walletnfcrel",
                    "com.android.vending",
                    "com.google.android.apps.nbu.paisa"
                ),
                keywords = listOf("google", "google pay", "google play", "gpay", "google one")
            ),
            GatewayDefinition(
                id = "paypal",
                packagePatterns = listOf("com.paypal.android.p2pmobile"),
                keywords = listOf("paypal")
            ),
            GatewayDefinition(
                id = "gopay",
                packagePatterns = listOf("com.gojek.app"),
                keywords = listOf("gopay", "gojek")
            ),
            GatewayDefinition(
                id = "ovo",
                packagePatterns = listOf("ovo.id"),
                keywords = listOf("ovo")
            ),
            GatewayDefinition(
                id = "dana",
                packagePatterns = listOf("id.dana"),
                keywords = listOf("dana")
            ),
            GatewayDefinition(
                id = "shopeepay",
                packagePatterns = listOf("com.shopee.id", "com.shopee.app"),
                keywords = listOf("shopeepay", "shopee pay", "spay")
            ),
            GatewayDefinition(
                id = "grab",
                packagePatterns = listOf("com.grabtaxi.passenger"),
                keywords = listOf("grab", "grabpay")
            ),
            GatewayDefinition(
                id = "apple",
                packagePatterns = emptyList(),
                keywords = listOf("apple", "itunes", "apple.com/bill")
            )
        )
    }

    private val _pendingExpenses = MutableStateFlow<List<ParsedExpense>>(emptyList())
    val pendingExpenses: StateFlow<List<ParsedExpense>> = _pendingExpenses.asStateFlow()

    fun addPendingExpense(expense: ParsedExpense): PendingExpenseAddResult {
        var result: PendingExpenseAddResult = PendingExpenseAddResult.Added(expense)

        _pendingExpenses.update { current ->
            for (i in current.indices) {
                val existing = current[i]
                val sameAmount = existing.amount.compareTo(expense.amount) == 0
                val timeDiff = Math.abs(expense.date.time - existing.date.time)

                if (sameAmount && timeDiff <= DEDUPLICATION_WINDOW_MS) {
                    // Case 1: Exact identical merchant
                    if (existing.merchant.equals(expense.merchant, ignoreCase = true)) {
                        result = PendingExpenseAddResult.IgnoredDuplicate
                        return@update current
                    }

                    // Case 2: Cross-app gateway correlation (e.g. BRImo -> Google Play / Google One)
                    val gateway = findGatewayCorrelation(existing, expense)
                    if (gateway != null) {
                        val isExistingGatewayApp = gateway.packagePatterns.any {
                            existing.packageName.contains(it, ignoreCase = true)
                        }
                        val isIncomingGatewayApp = gateway.packagePatterns.any {
                            expense.packageName.contains(it, ignoreCase = true)
                        }

                        // Prefer specific end-merchant from the store/gateway app over generic gateway name from the bank
                        val preferredMerchant = if (!isExistingGatewayApp && isIncomingGatewayApp) {
                            expense.merchant
                        } else {
                            existing.merchant
                        }

                        val merged = existing.copy(
                            merchant = preferredMerchant,
                            rawText = "${existing.rawText} | ${expense.rawText}"
                        )

                        result = PendingExpenseAddResult.Merged(merged)
                        val updatedList = current.toMutableList()
                        updatedList[i] = merged
                        return@update updatedList
                    }
                }
            }

            result = PendingExpenseAddResult.Added(expense)
            listOf(expense) + current
        }

        return result
    }

    private fun findGatewayCorrelation(a: ParsedExpense, b: ParsedExpense): GatewayDefinition? {
        for (g in GATEWAY_DEFINITIONS) {
            if (touchesGateway(a, g) && touchesGateway(b, g)) {
                return g
            }
        }
        return null
    }

    private fun touchesGateway(expense: ParsedExpense, gateway: GatewayDefinition): Boolean {
        val pkg = expense.packageName.lowercase()
        val merchant = expense.merchant.lowercase()
        val raw = expense.rawText.lowercase()

        val matchesPackage = gateway.packagePatterns.any { pkg.contains(it.lowercase()) }
        val matchesKeyword = gateway.keywords.any { merchant.contains(it) || raw.contains(it) }

        return matchesPackage || matchesKeyword
    }

    fun removePendingExpense(id: String) {
        _pendingExpenses.update { current ->
            current.filterNot { it.id == id }
        }
    }

    fun getPendingExpenseById(id: String): ParsedExpense? {
        return _pendingExpenses.value.find { it.id == id }
    }

    fun clearAll() {
        _pendingExpenses.value = emptyList()
    }
}
