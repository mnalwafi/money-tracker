package com.danilkinkin.buckwheat.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem
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

    val inEditor = editingTransaction != null || isCreatingNew

    // BackHandler: Handle back press while in editor to return to the list view
    BackHandler(enabled = inEditor) {
        editingTransaction = null
        isCreatingNew = false
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Constrain sheet height to 88% of screen height to leave clear top breathing room
        val maxSheetHeight = maxHeight * BuckwheatDesignSystem.Physics.sheetMaxHeightRatio

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight),
            shape = BuckwheatDesignSystem.Shapes.sheet,
            color = BuckwheatDesignSystem.Colors.sheetContainer,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Centered Material You Drag Handle Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = BuckwheatDesignSystem.Spacing.m, bottom = BuckwheatDesignSystem.Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(BuckwheatDesignSystem.Controls.dragHandleWidth)
                            .height(BuckwheatDesignSystem.Controls.dragHandleHeight)
                            .clip(CircleShape)
                            .background(BuckwheatDesignSystem.Colors.dragHandle),
                    )
                }

                AnimatedContent(
                    targetState = inEditor,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                            ) + fadeIn(animationSpec = tween(durationMillis = 300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> (-fullWidth * 0.2f).toInt() }
                                ) + fadeOut(animationSpec = tween(durationMillis = 250))
                            )
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (-fullWidth * 0.2f).toInt() }
                            ) + fadeIn(animationSpec = tween(durationMillis = 300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                                ) + fadeOut(animationSpec = tween(durationMillis = 250))
                            )
                        }
                    },
                    label = "recurringSheetTransition",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { editorActive ->
                    if (editorActive) {
                        RecurringTransactionEditor(
                            initialTransaction = editingTransaction,
                            currency = currency,
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
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Clean Header Title (No close 'X' and no top '+' icon)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = BuckwheatDesignSystem.Spacing.xxl, vertical = BuckwheatDesignSystem.Spacing.s),
                            ) {
                                Text(
                                    text = stringResource(R.string.recurring_transactions_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            if (allTransactions.isEmpty()) {
                                // Perfectly Centered Empty State
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = BuckwheatDesignSystem.Spacing.xxxl, vertical = BuckwheatDesignSystem.Spacing.xxl),
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
                                        Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.l))
                                        Text(
                                            text = stringResource(R.string.recurring_no_items),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.s))
                                        Text(
                                            text = stringResource(R.string.recurring_no_items_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.xxl))
                                        Button(
                                            onClick = { isCreatingNew = true },
                                            shape = BuckwheatDesignSystem.Shapes.input,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_add),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(BuckwheatDesignSystem.Spacing.s))
                                            Text(stringResource(R.string.add_recurring_transaction))
                                        }
                                    }
                                }
                            } else {
                                // Scrollable Content
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding),
                                    verticalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.m),
                                ) {
                                    // Summary Card
                                    item {
                                        val formattedMonthly = numberFormat(context, monthlyCommitment, currency = currency)
                                        val formattedReserved = numberFormat(context, reservedThisPeriod, currency = currency)

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = BuckwheatDesignSystem.Spacing.xs),
                                            shape = BuckwheatDesignSystem.Shapes.card,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                            border = BuckwheatDesignSystem.Colors.cardBorder,
                                        ) {
                                            Column(modifier = Modifier.padding(BuckwheatDesignSystem.Spacing.cardPadding)) {
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
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.xs))
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
                                    }

                                    if (dueSoon.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = stringResource(R.string.recurring_due_soon),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = BuckwheatDesignSystem.Spacing.m, bottom = BuckwheatDesignSystem.Spacing.xs, start = BuckwheatDesignSystem.Spacing.xs),
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
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = BuckwheatDesignSystem.Spacing.m, bottom = BuckwheatDesignSystem.Spacing.xs, start = BuckwheatDesignSystem.Spacing.xs),
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
                                        Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.l))
                                    }
                                }

                                // Sticky Bottom Action Bar when items exist
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                    tonalElevation = 2.dp,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.m)
                                            .padding(bottom = navigationBarHeight),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Button(
                                            onClick = { isCreatingNew = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(BuckwheatDesignSystem.Controls.buttonHeight),
                                            shape = BuckwheatDesignSystem.Shapes.button,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_add),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.width(BuckwheatDesignSystem.Spacing.s))
                                            Text(
                                                text = stringResource(R.string.add_recurring_transaction),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
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
        shape = BuckwheatDesignSystem.Shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
            },
        ),
        border = BuckwheatDesignSystem.Colors.cardBorder,
    ) {
        Column(modifier = Modifier.padding(BuckwheatDesignSystem.Spacing.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.s),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(top = BuckwheatDesignSystem.Spacing.xs),
                        ) {
                            Text(
                                text = intervalText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = BuckwheatDesignSystem.Spacing.s, vertical = BuckwheatDesignSystem.Spacing.xxs),
                            )
                        }
                        if (item.autoDeduct) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(top = BuckwheatDesignSystem.Spacing.xs),
                            ) {
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = BuckwheatDesignSystem.Spacing.s, vertical = BuckwheatDesignSystem.Spacing.xxs),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (daysDiff <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (item.isActive && daysDiff <= 7) {
                Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.m))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.recurring_skip))
                    }
                    Spacer(modifier = Modifier.width(BuckwheatDesignSystem.Spacing.s))
                    Button(
                        onClick = onPayNow,
                        contentPadding = PaddingValues(horizontal = BuckwheatDesignSystem.Spacing.l, vertical = 6.dp),
                        shape = BuckwheatDesignSystem.Shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.recurring_mark_paid))
                    }
                }
            }
        }
    }
}
