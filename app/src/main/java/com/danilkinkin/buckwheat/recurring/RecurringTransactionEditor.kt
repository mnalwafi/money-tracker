package com.danilkinkin.buckwheat.recurring

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem
import com.danilkinkin.buckwheat.util.fixedNumberString
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.visualTransformationAsCurrency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val RECURRING_TRANSACTION_EDITOR_SHEET = "recurring.editor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionEditor(
    initialTransaction: RecurringTransaction? = null,
    currency: ExtendCurrency = ExtendCurrency.none(),
    onSave: (RecurringTransaction) -> Unit,
    onDelete: ((RecurringTransaction) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    var name by remember { mutableStateOf(initialTransaction?.name ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (initialTransaction != null) {
                fixedNumberString(initialTransaction.amount.toPlainString())
            } else {
                ""
            }
        )
    }
    var categoryTag by remember { mutableStateOf(initialTransaction?.categoryTag ?: "") }
    var interval by remember { mutableStateOf(initialTransaction?.interval ?: RecurrenceInterval.MONTHLY) }
    var nextOccurrence by remember { mutableStateOf(initialTransaction?.nextOccurrence ?: LocalDate.now()) }
    var autoDeduct by remember { mutableStateOf(initialTransaction?.autoDeduct ?: false) }
    var isActive by remember { mutableStateOf(initialTransaction?.isActive ?: true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val currSymbol = remember(currency) {
        numberFormat(
            context,
            BigDecimal.ZERO,
            currency,
            maximumFractionDigits = 0,
            minimumFractionDigits = 0,
        ).filter { it != '0' }.trim()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BuckwheatDesignSystem.Spacing.xxl)
                .padding(top = BuckwheatDesignSystem.Spacing.s, bottom = navigationBarHeight + BuckwheatDesignSystem.Spacing.l),
            verticalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.formFieldGap),
        ) {
            // Header with back navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(BuckwheatDesignSystem.Spacing.xs))
                Text(
                    text = if (initialTransaction == null) {
                        stringResource(R.string.add_recurring_transaction)
                    } else {
                        stringResource(R.string.edit_recurring_transaction)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                shape = BuckwheatDesignSystem.Shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            // Amount Input with Auto-Formatted Currency
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val sanitized = fixedNumberString(input.replace(',', '.'))
                    amountText = sanitized
                    amountError = false
                },
                label = { Text(stringResource(R.string.recurring_amount_label)) },
                placeholder = { Text("0") },
                prefix = if (currSymbol.isNotEmpty()) {
                    {
                        Text(
                            text = "$currSymbol ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else null,
                visualTransformation = visualTransformationAsCurrency(
                    context = context,
                    currency = currency,
                    hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = BuckwheatDesignSystem.Shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            // Recurrence Interval Chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.labelToInputGap),
            ) {
                Text(
                    text = stringResource(R.string.recurring_interval_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.s),
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
                            shape = BuckwheatDesignSystem.Shapes.medium,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Next Due Date Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = BuckwheatDesignSystem.Shapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BuckwheatDesignSystem.Colors.cardBorder,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BuckwheatDesignSystem.Spacing.cardPadding),
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
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
                shape = BuckwheatDesignSystem.Shapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            // Auto-deduct toggle
            Card(
                shape = BuckwheatDesignSystem.Shapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BuckwheatDesignSystem.Colors.cardBorder,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BuckwheatDesignSystem.Spacing.cardPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = BuckwheatDesignSystem.Spacing.l)
                    ) {
                        Text(
                            text = stringResource(R.string.recurring_auto_deduct),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
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
                Card(
                    shape = BuckwheatDesignSystem.Shapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    border = BuckwheatDesignSystem.Colors.cardBorder,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BuckwheatDesignSystem.Spacing.cardPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recurring_active),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.s))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.m),
            ) {
                if (initialTransaction != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(initialTransaction) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(BuckwheatDesignSystem.Controls.buttonHeight),
                        shape = BuckwheatDesignSystem.Shapes.button,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(BuckwheatDesignSystem.Controls.buttonHeight),
                    shape = BuckwheatDesignSystem.Shapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.apply),
                        fontWeight = FontWeight.Bold,
                    )
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
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}
