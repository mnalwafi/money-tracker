package com.danilkinkin.buckwheat.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.util.numberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val RECURRING_TRANSACTIONS_SHEET = "recurring.list"

@Composable
fun RecurringTransactionsSheet(
    onClose: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    val dueSoon by viewModel.dueSoon.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val monthlyCommitment by viewModel.monthlyCommitment.collectAsState()
    val reservedThisPeriod by viewModel.reservedThisPeriod.collectAsState()
    val currency by viewModel.currency.collectAsState()

    var editingTransaction by remember { mutableStateOf<RecurringTransaction?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        if (editingTransaction != null || isCreatingNew) {
            RecurringTransactionEditor(
                initialTransaction = editingTransaction,
                onSave = { saved ->
                    viewModel.saveRecurring(saved)
                    editingTransaction = null
                    isCreatingNew = false
                },
                onDelete = { toDelete ->
                    viewModel.deleteRecurring(toDelete)
                    editingTransaction = null
                    isCreatingNew = false
                },
                onClose = {
                    editingTransaction = null
                    isCreatingNew = false
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navigationBarHeight),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recurring_transactions_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isCreatingNew = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = stringResource(R.string.add_recurring_transaction),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = null,
                            )
                        }
                    }
                }

                // Summary Card
                val formattedMonthly = numberFormat(context, monthlyCommitment, currency = currency)
                val formattedReserved = numberFormat(context, reservedThisPeriod, currency = currency)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.monthly_commitment_total, formattedMonthly),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.reserved_in_period, formattedReserved),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_autorenew),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (allTransactions.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.recurring_no_items),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.recurring_no_items_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { isCreatingNew = true },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.add_recurring_transaction))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (dueSoon.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.recurring_due_soon),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                )
                            }
                            items(dueSoon, key = { "soon_${it.id}" }) { item ->
                                RecurringItemCard(
                                    item = item,
                                    currency = currency,
                                    currentDate = viewModel.currentDate(),
                                    onClick = { editingTransaction = item },
                                    onPayNow = { viewModel.markAsPaid(item) },
                                    onSkip = { viewModel.skipOccurrence(item) },
                                    onToggleActive = { active -> viewModel.toggleActive(item, active) },
                                )
                            }
                        }

                        if (upcoming.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.recurring_upcoming),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                )
                            }
                            items(upcoming, key = { "up_${it.id}" }) { item ->
                                RecurringItemCard(
                                    item = item,
                                    currency = currency,
                                    currentDate = viewModel.currentDate(),
                                    onClick = { editingTransaction = item },
                                    onPayNow = { viewModel.markAsPaid(item) },
                                    onSkip = { viewModel.skipOccurrence(item) },
                                    onToggleActive = { active -> viewModel.toggleActive(item, active) },
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringItemCard(
    item: RecurringTransaction,
    currency: ExtendCurrency,
    currentDate: LocalDate,
    onClick: () -> Unit,
    onPayNow: () -> Unit,
    onSkip: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val formattedAmount = numberFormat(context, item.amount, currency = currency)

    val daysDiff = ChronoUnit.DAYS.between(currentDate, item.nextOccurrence).toInt()
    val dueText = when {
        daysDiff == 0 -> stringResource(R.string.due_today)
        daysDiff > 0 -> pluralStringResource(R.plurals.due_in_days, daysDiff, daysDiff)
        else -> pluralStringResource(R.plurals.overdue_by_days, -daysDiff, -daysDiff)
    }

    val intervalText = when (item.interval) {
        RecurrenceInterval.DAILY -> stringResource(R.string.interval_daily)
        RecurrenceInterval.WEEKLY -> stringResource(R.string.interval_weekly)
        RecurrenceInterval.MONTHLY -> stringResource(R.string.interval_monthly)
        RecurrenceInterval.YEARLY -> stringResource(R.string.interval_yearly)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = intervalText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (item.autoDeduct) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysDiff <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (item.isActive && daysDiff <= 7) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.recurring_skip))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPayNow,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.recurring_mark_paid))
                    }
                }
            }
        }
    }
}
