package com.danilkinkin.buckwheat.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.danilkinkin.buckwheat.base.ModalBottomSheetState
import com.danilkinkin.buckwheat.base.ModalBottomSheetValue
import com.danilkinkin.buckwheat.base.WavyShape
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorGood
import com.danilkinkin.buckwheat.ui.colorNotGood
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem
import com.danilkinkin.buckwheat.util.clamp
import com.danilkinkin.buckwheat.util.combineColors
import com.danilkinkin.buckwheat.util.harmonize
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.prettyDate
import com.danilkinkin.buckwheat.util.toPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DASHBOARD_SHEET = "dashboard"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    sheetState: ModalBottomSheetState? = null,
    onQuickAdd: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onReviewPending: () -> Unit = {},
    onEditTransaction: (Transaction) -> Unit = {},
    onClose: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val maxSheetHeight = maxHeight * BuckwheatDesignSystem.Drawers.xl

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
                // Centered Material You Drag Handle Pill with 1:1 drag-down tracking & velocity snap
                DashboardDragHandle(
                    modifier = Modifier.sheetDragDownGesture(sheetState, coroutineScope, onClose)
                )

                // Clean Header Title & Date with 1:1 drag-down tracking & velocity snap
                DashboardHeader(
                    modifier = Modifier.sheetDragDownGesture(sheetState, coroutineScope, onClose)
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(BuckwheatDesignSystem.Spacing.sectionGap),
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

                    Spacer(modifier = Modifier.height(BuckwheatDesignSystem.Spacing.s))
                }

                // Quick-Add Sticky Action Bar with swipe-down dismissal
                QuickAddBottomBar(
                    onQuickAdd = onQuickAdd,
                    bottomPadding = navigationBarHeight,
                    modifier = Modifier.sheetDragDownGesture(sheetState, coroutineScope, onClose),
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
}

/**
 * Standardized centered dashboard header with titleLarge typography matching ViewerHistory & Settings.
 */
@Composable
private fun DashboardHeader(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = BuckwheatDesignSystem.Typography.drawerTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 1:1 Touch tracking and velocity-sensitive fling snap gesture for dismissing the dashboard sheet.
 * Uses a forgiving 40.dp threshold and 150f velocity cutoff to ensure smooth, effortless dismissal like Settings.
 */
@OptIn(ExperimentalMaterialApi::class)
fun Modifier.sheetDragDownGesture(
    sheetState: ModalBottomSheetState?,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit,
): Modifier = if (sheetState == null) this else this.pointerInput(sheetState) {
    val velocityTracker = VelocityTracker()
    val dismissThresholdPx = with(this@pointerInput) { BuckwheatDesignSystem.Physics.dismissThreshold.toPx() }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var totalDragY = 0f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val dragY = change.position.y - change.previousPosition.y
            if (dragY != 0f) {
                totalDragY += dragY
                if (totalDragY > 0f) {
                    change.consume()
                    sheetState.performDrag(dragY)
                }
            }
        }

        val velocity = velocityTracker.calculateVelocity()
        coroutineScope.launch {
            if (totalDragY > dismissThresholdPx || velocity.y > BuckwheatDesignSystem.Physics.dismissVelocityThreshold) {
                sheetState.hide()
                onClose()
            } else {
                sheetState.animateTo(ModalBottomSheetValue.Expanded)
            }
        }
    }
}

/**
 * Hero Card presenting today's safe allowance with live animated wavy background fill,
 * dynamic color harmonization based on remaining allowance, prominent typography, and cycle progress.
 * Harmonized to match the visual fidelity and physics of the Budget drawer.
 */
@Composable
private fun HeroAllowanceCard(
    uiState: DashboardUiState,
    onOpenWallet: () -> Unit,
) {
    val context = LocalContext.current

    if (!uiState.isBudgetSet) {
        // No active budget state
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BuckwheatDesignSystem.Shapes.cardHero,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BuckwheatDesignSystem.Spacing.heroPadding),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_balance_wallet),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dashboard_no_budget_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_no_budget_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenWallet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = BuckwheatDesignSystem.Shapes.button,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_set_budget),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    } else {
        // Active Budget Hero Content with Live Wavy Background Fill
        val isOverBudget = uiState.todayAllowance < BigDecimal.ZERO
        val target = uiState.dailyTarget
        val spent = uiState.todaySpent
        val allowance = uiState.todayAllowance

        val percent = if (target > BigDecimal.ZERO) {
            (allowance.divide(target, 4, RoundingMode.HALF_EVEN)).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        val shift = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            fun anim() {
                coroutineScope.launch {
                    shift.animateTo(
                        1f,
                        animationSpec = FloatTweenSpec(6000, 0, LinearEasing)
                    )
                    shift.snapTo(0f)
                    anim()
                }
            }
            anim()
        }

        val harmonizedColor = toPalette(
            harmonize(
                combineColors(
                    listOf(
                        colorBad,
                        colorNotGood,
                        colorGood,
                    ),
                    percent,
                )
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = BuckwheatDesignSystem.Shapes.cardHero)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = BuckwheatDesignSystem.Shapes.cardHero,
                colors = CardDefaults.cardColors(
                    containerColor = harmonizedColor.container,
                    contentColor = harmonizedColor.onContainer,
                ),
            ) {
                val textColor = LocalContentColor.current
                Box(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                ) {
                    // Animated Wavy Liquid Fill (Matching Wallet's RestAndSpentBudgetCard)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    harmonizedColor.main,
                                    shape = WavyShape(
                                        period = 70.dp,
                                        amplitude = 3.5.dp * percent.clamp(0.96f, 1f),
                                        shift = shift.value,
                                    ),
                                )
                                .fillMaxHeight()
                                .fillMaxWidth(percent),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BuckwheatDesignSystem.Spacing.heroPadding),
                    ) {
                        // Header subtitle & Status pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_hero_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                            )

                            Surface(
                                shape = CircleShape,
                                color = if (isOverBudget) colorBad.copy(alpha = 0.2f) else colorGood.copy(alpha = 0.2f),
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

                        // Large Safe Allowance Amount
                        Text(
                            text = numberFormat(
                                context = context,
                                value = allowance.coerceAtLeast(BigDecimal.ZERO),
                                currency = uiState.currency,
                                trimDecimalPlaces = false,
                            ),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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
                                color = textColor.copy(alpha = 0.75f),
                            )
                            Text(
                                text = stringResource(
                                    R.string.dashboard_daily_target,
                                    numberFormat(context, target, uiState.currency, trimDecimalPlaces = true),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.75f),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))

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
                                    tint = textColor.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.dashboard_period_remaining,
                                        numberFormat(context, uiState.periodRemainingPool, uiState.currency, trimDecimalPlaces = true),
                                        numberFormat(context, uiState.periodTotalBudget, uiState.currency, trimDecimalPlaces = true),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // Rotated Days-Left Chip (styled after CountDaysChip in Wallet)
                            Surface(
                                shape = CircleShape,
                                color = textColor,
                                contentColor = harmonizedColor.container,
                                modifier = Modifier
                                    .rotate(4f)
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_period_days_left, uiState.periodDaysLeft),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
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
            shape = BuckwheatDesignSystem.Shapes.cardItem,
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
 * Individual card representation of an upcoming bill with urgency-coded pill and icon avatar.
 */
@Composable
private fun UpcomingBillCard(
    item: RecurringTransaction,
    currency: com.danilkinkin.buckwheat.data.ExtendCurrency,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), item.nextOccurrence)

    val urgencyColor = when {
        daysUntil <= 0 -> colorBad
        daysUntil <= 2 -> colorNotGood
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .width(170.dp)
            .clickable(onClick = onClick),
        shape = BuckwheatDesignSystem.Shapes.cardItem,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Due badge & avatar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(urgencyColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_autorenew),
                        contentDescription = null,
                        tint = urgencyColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                val dueText = when {
                    daysUntil <= 0 -> stringResource(R.string.due_today)
                    else -> pluralStringResource(R.plurals.due_in_days, daysUntil.toInt(), daysUntil.toInt())
                }

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
                        maxLines = 1,
                    )
                }
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

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = numberFormat(context, item.amount, currency, trimDecimalPlaces = true),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
                shape = BuckwheatDesignSystem.Shapes.cardItem,
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
                shape = BuckwheatDesignSystem.Shapes.cardHero,
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
                    .size(40.dp)
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
                    modifier = Modifier.size(20.dp),
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
 * Supports fluid downward swipe dismissal.
 */
@Composable
private fun QuickAddBottomBar(
    onQuickAdd: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
