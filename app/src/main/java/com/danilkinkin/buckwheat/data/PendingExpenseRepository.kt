package com.danilkinkin.buckwheat.data

import android.content.Context
import android.util.Log
import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import com.danilkinkin.buckwheat.data.entities.TransactionCaptureType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Date
import java.util.UUID
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
class PendingExpenseRepository private constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") marker: Any?
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, null)

    // For unit tests without Android Context
    constructor() : this(null, null)

    companion object {
        private const val TAG = "PendingExpenseRepo"
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

    private val _pendingExpenses = MutableStateFlow<List<ParsedExpense>>(loadFromDisk())
    val pendingExpenses: StateFlow<List<ParsedExpense>> = _pendingExpenses.asStateFlow()

    private fun loadFromDisk(): List<ParsedExpense> {
        val ctx = context ?: return emptyList()
        val prefs = ctx.getSharedPreferences("buckwheat_pending_expenses", Context.MODE_PRIVATE)
        val json = prefs.getString("pending_list", null) ?: return emptyList()
        val list = mutableListOf<ParsedExpense>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ParsedExpense(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        type = TransactionCaptureType.valueOf(obj.optString("type", TransactionCaptureType.EXPENSE.name)),
                        amount = BigDecimal(obj.optString("amount", "0")),
                        merchant = obj.optString("merchant", ""),
                        date = Date(obj.optLong("date", System.currentTimeMillis())),
                        packageName = obj.optString("packageName", ""),
                        rawText = obj.optString("rawText", ""),
                        currencySymbol = obj.optString("currencySymbol").takeIf { it.isNotEmpty() },
                        confidence = obj.optDouble("confidence", 1.0).toFloat(),
                        source = obj.optString("source", "hybrid"),
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pending expenses from disk", e)
        }
        return list
    }

    private fun saveToDisk(list: List<ParsedExpense>) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("buckwheat_pending_expenses", Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("type", item.type.name)
                obj.put("amount", item.amount.toPlainString())
                obj.put("merchant", item.merchant)
                obj.put("date", item.date.time)
                obj.put("packageName", item.packageName)
                obj.put("rawText", item.rawText)
                obj.put("currencySymbol", item.currencySymbol ?: "")
                obj.put("confidence", item.confidence.toDouble())
                obj.put("source", item.source)
                arr.put(obj)
            }
            prefs.edit().putString("pending_list", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pending expenses to disk", e)
        }
    }

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
                        saveToDisk(updatedList)
                        return@update updatedList
                    }
                }
            }

            result = PendingExpenseAddResult.Added(expense)
            val updated = listOf(expense) + current
            saveToDisk(updated)
            updated
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
        context?.let { ctx ->
            try {
                val notificationId = com.danilkinkin.buckwheat.service.ExpenseNotificationHelper.getNotificationIdForExpense(id)
                androidx.core.app.NotificationManagerCompat.from(ctx).cancel(notificationId)
            } catch (_: Exception) {}
        }

        _pendingExpenses.update { current ->
            val updated = current.filterNot { it.id == id }
            saveToDisk(updated)
            updated
        }
    }

    fun getPendingExpenseById(id: String): ParsedExpense? {
        return _pendingExpenses.value.find { it.id == id }
    }

    fun clearAll() {
        context?.let { ctx ->
            try {
                for (item in _pendingExpenses.value) {
                    val notificationId = com.danilkinkin.buckwheat.service.ExpenseNotificationHelper.getNotificationIdForExpense(item.id)
                    androidx.core.app.NotificationManagerCompat.from(ctx).cancel(notificationId)
                }
            } catch (_: Exception) {}
        }
        _pendingExpenses.value = emptyList()
        saveToDisk(emptyList())
    }
}
