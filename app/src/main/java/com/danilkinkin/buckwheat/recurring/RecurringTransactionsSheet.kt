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

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import com.danilkinkin.buckwheat.base.ModalBottomSheetState
import com.danilkinkin.buckwheat.dashboard.sheetDragDownGesture
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorNotGood
import com.danilkinkin.buckwheat.ui.colorGood

const val RECURRING_TRANSACTIONS_SHEET = "recurring.list"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RecurringTransactionsSheet(
    onClose: () -> Unit,
    sheetState: ModalBottomSheetState? = null,
    viewModel: RecurringViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
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
        // Constrain sheet height to 88% of screen height (lg drawer)
        val maxSheetHeight = maxHeight * BuckwheatDesignSystem.Drawers.lg

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight),
            shape = BuckwheatDesignSystem.Shapes.sheet,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Centered Material You Drag Handle Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetDragDownGesture(sheetState, coroutineScope, onClose)
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
                            // Clean Centered Header Title
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sheetDragDownGesture(sheetState, coroutineScope, onClose)
                                    .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.s),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.recurring_transactions_title),
                                    style = BuckwheatDesignSystem.Typography.drawerTitle,
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
                                    // Summary Hero Card matching Wallet drawer fidelity
                                    item {
                                        val formattedMonthly = numberFormat(context, monthlyCommitment, currency = currency)
                                        val formattedReserved = numberFormat(context, reservedThisPeriod, currency = currency)
                                        val activeCount = allTransactions.count { it.isActive }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = BuckwheatDesignSystem.Spacing.xs)
                                                .clip(shape = BuckwheatDesignSystem.Shapes.cardHero)
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = BuckwheatDesignSystem.Shapes.cardHero,
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                            ) {
                                                val textColor = LocalContentColor.current
                                                Column(modifier = Modifier.padding(BuckwheatDesignSystem.Spacing.heroPadding)) {
                                                    // Header row with category and rotated active count chip
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.recurring_monthly_commitment),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = textColor.copy(alpha = 0.7f),
                                                            fontWeight = FontWeight.SemiBold,
                                                        )

                                                        Surface(
                                                            shape = CircleShape,
                                                            color = textColor,
                                                            contentColor = MaterialTheme.colorScheme.primaryContainer,
                                                            modifier = Modifier
                                                                .rotate(3f)
                                                                .padding(vertical = 2.dp),
                                                        ) {
                                                            Text(
                                                                text = pluralStringResource(R.plurals.active_subscriptions_count, activeCount, activeCount),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    // Large Monthly Commitment Amount
                                                    Text(
                                                        text = formattedMonthly,
                                                        style = MaterialTheme.typography.displayMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textColor,
                                                    )

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.recurring_monthly_commitment_desc),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = textColor.copy(alpha = 0.75f),
                                                    )

                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    // Period Reserved Row
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.ic_autorenew),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint = textColor.copy(alpha = 0.8f),
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.recurring_reserved_this_cycle),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = textColor.copy(alpha = 0.8f),
                                                            )
                                                        }

                                                        Text(
                                                            text = formattedReserved,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor,
                                                        )
                                                    }
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
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
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

    val urgencyColor = when {
        !item.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        daysDiff < 0 -> colorBad
        daysDiff <= 3 -> colorNotGood
        else -> MaterialTheme.colorScheme.primary
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
        shape = BuckwheatDesignSystem.Shapes.cardItem,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
            },
        ),
        border = BuckwheatDesignSystem.Colors.cardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isActive) urgencyColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_autorenew),
                            contentDescription = null,
                            tint = if (item.isActive) urgencyColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = intervalText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            if (item.autoDeduct) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = "Auto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = urgencyColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = urgencyColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (item.isActive && daysDiff <= 7) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))
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
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.recurring_mark_paid),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
