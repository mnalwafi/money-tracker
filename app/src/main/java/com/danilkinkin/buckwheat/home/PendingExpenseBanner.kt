package com.danilkinkin.buckwheat.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.PendingExpenseRepository
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.editor.EditorViewModel

import com.danilkinkin.buckwheat.service.ExpenseNotificationHelper
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PendingExpenseViewModel @Inject constructor(
    val pendingExpenseRepository: PendingExpenseRepository,
    val notificationHelper: ExpenseNotificationHelper,
) : ViewModel() {
    fun dismiss(expenseId: String) {
        pendingExpenseRepository.removePendingExpense(expenseId)
        notificationHelper.dismissExpenseNotification(expenseId)
    }
}

@Composable
fun PendingExpenseBanner(
    modifier: Modifier = Modifier,
    viewModel: PendingExpenseViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    onEditExpense: () -> Unit = {},
) {
    val pendingExpenses by viewModel.pendingExpenseRepository.pendingExpenses.collectAsState()
    val latestExpense = pendingExpenses.firstOrNull()

    AnimatedVisibility(
        visible = latestExpense != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        if (latestExpense != null) {
            val isIncome = latestExpense.type == com.danilkinkin.buckwheat.data.entities.TransactionCaptureType.INCOME
            val confidencePct = (latestExpense.confidence * 100).toInt().coerceIn(1, 100)

            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.s),
                shape = BuckwheatDesignSystem.Shapes.button,
                colors = CardDefaults.cardColors(
                    containerColor = if (isIncome) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isIncome) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = BuckwheatDesignSystem.Elevation.card),
            ) {
                Column(
                    modifier = Modifier.padding(BuckwheatDesignSystem.Spacing.cardPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(if (isIncome) R.drawable.ic_balance_wallet else R.drawable.ic_money),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(BuckwheatDesignSystem.Spacing.s))
                            Text(
                                text = stringResource(if (isIncome) R.string.pending_income_banner_title else R.string.pending_expense_banner_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(BuckwheatDesignSystem.Spacing.s))
                            Text(
                                text = stringResource(R.string.ai_confidence_badge, confidencePct),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.dismiss(latestExpense.id)
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_do_disturb),
                                contentDescription = stringResource(R.string.dismiss_action),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    val formattedAmount = "${latestExpense.currencySymbol ?: ""} ${latestExpense.amount}".trim()
                    Text(
                        text = if (isIncome) {
                            stringResource(
                                R.string.pending_income_prompt,
                                formattedAmount,
                                latestExpense.merchant,
                            )
                        } else {
                            stringResource(
                                R.string.pending_expense_prompt,
                                formattedAmount,
                                latestExpense.merchant,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = BuckwheatDesignSystem.Spacing.s),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = {
                                editorViewModel.prepareSpentFromCapture(
                                    amount = latestExpense.amount,
                                    merchant = latestExpense.merchant,
                                    date = latestExpense.date,
                                    )
                                viewModel.dismiss(latestExpense.id)
                                onEditExpense()
                            },
                            modifier = Modifier.padding(end = BuckwheatDesignSystem.Spacing.s),
                        ) {
                            Text(text = stringResource(R.string.pending_expense_edit))
                        }

                        Button(
                            onClick = {
                                val transaction = Transaction(
                                    type = if (isIncome) TransactionType.INCOME else TransactionType.SPENT,
                                    value = latestExpense.amount,
                                    date = latestExpense.date,
                                    comment = latestExpense.merchant,
                                )
                                if (isIncome) {
                                    spendsViewModel.addIncome(transaction)
                                } else {
                                    spendsViewModel.addSpent(transaction)
                                }
                                viewModel.dismiss(latestExpense.id)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                contentColor = if (isIncome) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(text = stringResource(if (isIncome) R.string.pending_income_confirm else R.string.pending_expense_confirm))
                        }
                    }
                }
            }
        }
    }
}
