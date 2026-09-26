package com.danilkinkin.buckwheat.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorGood
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.prettyDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DASHBOARD_SHEET = "dashboard"

@Composable
fun DashboardScreen(
    onQuickAdd: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onReviewPending: () -> Unit = {},
    onEditTransaction: (Transaction) -> Unit = {},
    onClose: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Constrain sheet height to 88% of screen height to leave clear top breathing room and visible backdrop
        val maxSheetHeight = maxHeight * 0.88f

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Centered Material You Drag Handle Pill
                DashboardDragHandle()

                // Clean Header Title & Date (No close or wallet icons)
                DashboardHeader()

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Hero Card: Safe Allowance Today & Period Progress
                    HeroAllowanceCard(
                        uiState = uiState,
                        onOpenWallet = onOpenWallet,
                    )

                    // Action Banner: Auto-detected transactions pending review
                    ActionBanner(
                        pendingCount = uiState.pendingCapturedCount,
                        onReview = onReviewPending,
                    )

                    // Upcoming Bills Row (Next 7 Days)
                    UpcomingBillsSection(
                        upcomingBills = uiState.upcomingRecurring,
                        currency = uiState.currency,
                        onManageRecurring = onOpenRecurring,
                    )

                    // Recent Activity Section (Latest 3-5 entries)
                    RecentActivitySection(
                        recentTransactions = uiState.recentTransactions,
                        currency = uiState.currency,
                        onEditTransaction = onEditTransaction,
                        onViewHistory = onOpenHistory,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Quick-Add Sticky Action Bar
                QuickAddBottomBar(
                    onQuickAdd = onQuickAdd,
                    bottomPadding = navigationBarHeight,
                )
            }
        }
    }
}

/**
 * Centered Material You drag handle pill providing intuitive swipe affordance.
 */
@Composable
fun DashboardDragHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Minimalist dashboard header showing title and current date without redundant close/wallet action buttons.
 */
@Composable
private fun DashboardHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = prettyDate(java.util.Date(), showTime = false, human = true),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Modernized Hero Card presenting today's safe allowance with prominent typography,
 * an integrated spend indicator, and cycle progress.
 */
@Composable
private fun HeroAllowanceCard(
    uiState: DashboardUiState,
    onOpenWallet: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (!uiState.isBudgetSet) {
                // No active budget state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_balance_wallet),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dashboard_no_budget_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_no_budget_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenWallet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(text = stringResource(R.string.dashboard_set_budget))
                }
            } else {
                // Active Budget Hero Content
                val isOverBudget = uiState.todayAllowance < BigDecimal.ZERO
                val target = uiState.dailyTarget
                val spent = uiState.todaySpent

                val progressTarget = if (target > BigDecimal.ZERO) {
                    (spent.divide(target, 4, RoundingMode.HALF_EVEN)).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                val animatedProgress by animateFloatAsState(
                    targetValue = progressTarget,
                    animationSpec = tween(durationMillis = 600),
                    label = "dailySpendProgress",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_hero_subtitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )

                    // Status Pill
                    Surface(
                        shape = CircleShape,
                        color = if (isOverBudget) colorBad.copy(alpha = 0.15f) else colorGood.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = stringResource(if (isOverBudget) R.string.dashboard_over_budget else R.string.dashboard_on_track),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverBudget) colorBad else colorGood,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Safe Allowance Amount
                Text(
                    text = numberFormat(
                        context = context,
                        value = uiState.todayAllowance.coerceAtLeast(BigDecimal.ZERO),
                        currency = uiState.currency,
                        trimDecimalPlaces = false,
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverBudget) colorBad else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Integrated Linear Spend Indicator
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (isOverBudget) colorBad else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Spent today vs daily target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_spent_today,
                            numberFormat(context, spent, uiState.currency, trimDecimalPlaces = true),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.dashboard_daily_target,
                            numberFormat(context, target, uiState.currency, trimDecimalPlaces = true),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(12.dp))

                // Cycle Period Statistics Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenWallet),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.dashboard_period_remaining,
                                numberFormat(context, uiState.periodRemainingPool, uiState.currency, trimDecimalPlaces = true),
                                numberFormat(context, uiState.periodTotalBudget, uiState.currency, trimDecimalPlaces = true),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = stringResource(R.string.dashboard_period_days_left, uiState.periodDaysLeft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Action Banner alerting users of newly captured transactions pending review.
 */
@Composable
private fun ActionBanner(
    pendingCount: Int,
    onReview: () -> Unit,
) {
    AnimatedVisibility(
        visible = pendingCount > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_money),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Column {
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_pending_detected,
                                pendingCount,
                                pendingCount,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_pending_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = onReview,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_pending_review),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Section displaying upcoming recurring transactions and subscriptions due in the next 7 days.
 */
@Composable
private fun UpcomingBillsSection(
    upcomingBills: List<RecurringTransaction>,
    currency: com.danilkinkin.buckwheat.data.ExtendCurrency,
    onManageRecurring: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_upcoming_bills_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onManageRecurring) {
                Text(
                    text = stringResource(R.string.dashboard_upcoming_bills_manage),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (upcomingBills.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageRecurring),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_autorenew),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.dashboard_upcoming_no_bills),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(upcomingBills, key = { it.id }) { item ->
                    UpcomingBillCard(
                        item = item,
                        currency = currency,
                        onClick = onManageRecurring,
                    )
                }
            }
        }
    }
}

/**
 * Individual card representation of an upcoming bill.
 */
@Composable
private fun UpcomingBillCard(
    item: RecurringTransaction,
    currency: com.danilkinkin.buckwheat.data.ExtendCurrency,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), item.nextOccurrence)

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Due badge
            val dueText = when {
                daysUntil <= 0 -> stringResource(R.string.due_today)
                else -> pluralStringResource(R.plurals.due_in_days, daysUntil.toInt(), daysUntil.toInt())
            }

            Surface(
                shape = CircleShape,
                color = if (daysUntil <= 1) colorBad.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = dueText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (daysUntil <= 1) colorBad else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = numberFormat(context, item.amount, currency, trimDecimalPlaces = true),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.interval.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Section presenting latest transactions for immediate review.
 */
@Composable
private fun RecentActivitySection(
    recentTransactions: List<Transaction>,
    currency: com.danilkinkin.buckwheat.data.ExtendCurrency,
    onEditTransaction: (Transaction) -> Unit,
    onViewHistory: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_recent_activity_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onViewHistory) {
                Text(
                    text = stringResource(R.string.dashboard_recent_see_all),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (recentTransactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_no_transactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    recentTransactions.forEachIndexed { index, transaction ->
                        RecentTransactionRow(
                            transaction = transaction,
                            currency = currency,
                            onClick = { onEditTransaction(transaction) },
                        )
                        if (index < recentTransactions.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual row displaying transaction details.
 */
@Composable
private fun RecentTransactionRow(
    transaction: Transaction,
    currency: com.danilkinkin.buckwheat.data.ExtendCurrency,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val isIncome = transaction.type == TransactionType.INCOME

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIncome) colorGood.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isIncome) R.drawable.ic_balance_wallet else R.drawable.ic_money),
                    contentDescription = null,
                    tint = if (isIncome) colorGood else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column {
                val title = if (transaction.comment.isNotEmpty()) {
                    transaction.comment
                } else {
                    stringResource(if (isIncome) R.string.dashboard_income_label else R.string.dashboard_expense_label)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = prettyDate(transaction.date, human = true, shortMonth = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val prefix = if (isIncome) "+" else "-"
        Text(
            text = "$prefix${numberFormat(context, transaction.value, currency)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) colorGood else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Sticky action bar at the bottom allowing instant quick return to add expenses.
 */
@Composable
private fun QuickAddBottomBar(
    onQuickAdd: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onQuickAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_quick_add),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
