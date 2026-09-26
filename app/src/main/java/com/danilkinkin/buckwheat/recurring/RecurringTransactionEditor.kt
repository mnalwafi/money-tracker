package com.danilkinkin.buckwheat.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val RECURRING_TRANSACTION_EDITOR_SHEET = "recurring.editor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionEditor(
    initialTransaction: RecurringTransaction? = null,
    onSave: (RecurringTransaction) -> Unit,
    onDelete: ((RecurringTransaction) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    var name by remember { mutableStateOf(initialTransaction?.name ?: "") }
    var amountText by remember { mutableStateOf(initialTransaction?.amount?.toPlainString() ?: "") }
    var categoryTag by remember { mutableStateOf(initialTransaction?.categoryTag ?: "") }
    var interval by remember { mutableStateOf(initialTransaction?.interval ?: RecurrenceInterval.MONTHLY) }
    var nextOccurrence by remember { mutableStateOf(initialTransaction?.nextOccurrence ?: LocalDate.now()) }
    var autoDeduct by remember { mutableStateOf(initialTransaction?.autoDeduct ?: false) }
    var isActive by remember { mutableStateOf(initialTransaction?.isActive ?: true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = navigationBarHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (initialTransaction == null) {
                        stringResource(R.string.add_recurring_transaction)
                    } else {
                        stringResource(R.string.edit_recurring_transaction)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onClose) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text(stringResource(R.string.recurring_name_label)) },
                placeholder = { Text(stringResource(R.string.recurring_name_hint)) },
                isError = nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountError = false
                },
                label = { Text(stringResource(R.string.recurring_amount_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Recurrence Interval Chips
            Text(
                text = stringResource(R.string.recurring_interval_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecurrenceInterval.values().forEach { item ->
                    FilterChip(
                        selected = interval == item,
                        onClick = { interval = item },
                        label = {
                            Text(
                                when (item) {
                                    RecurrenceInterval.DAILY -> stringResource(R.string.interval_daily)
                                    RecurrenceInterval.WEEKLY -> stringResource(R.string.interval_weekly)
                                    RecurrenceInterval.MONTHLY -> stringResource(R.string.interval_monthly)
                                    RecurrenceInterval.YEARLY -> stringResource(R.string.interval_yearly)
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Next Due Date Selector
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.recurring_start_date_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = nextOccurrence.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Category / Tag
            OutlinedTextField(
                value = categoryTag,
                onValueChange = { categoryTag = it },
                label = { Text(stringResource(R.string.recurring_category_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Auto-deduct toggle
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.recurring_auto_deduct),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.recurring_auto_deduct_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoDeduct,
                        onCheckedChange = { autoDeduct = it },
                    )
                }
            }

            // Active toggle (if editing)
            if (initialTransaction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recurring_active),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (initialTransaction != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(initialTransaction) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.recurring_delete))
                    }
                }

                Button(
                    onClick = {
                        val parsedAmount = amountText.replace(',', '.').toBigDecimalOrNull()
                        val isValidName = name.isNotBlank()
                        val isValidAmount = parsedAmount != null && parsedAmount > BigDecimal.ZERO

                        if (!isValidName) nameError = true
                        if (!isValidAmount) amountError = true

                        if (isValidName && isValidAmount) {
                            val result = RecurringTransaction(
                                id = initialTransaction?.id ?: 0L,
                                name = name.trim(),
                                amount = parsedAmount!!,
                                categoryTag = categoryTag.trim().takeIf { it.isNotEmpty() },
                                interval = interval,
                                startDate = initialTransaction?.startDate ?: nextOccurrence,
                                nextOccurrence = nextOccurrence,
                                autoDeduct = autoDeduct,
                                isActive = isActive,
                            )
                            onSave(result)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = nextOccurrence.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            nextOccurrence = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}
