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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PendingExpenseViewModel @Inject constructor(
    val pendingExpenseRepository: PendingExpenseRepository,
) : ViewModel()

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
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
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
                                painter = painterResource(R.drawable.ic_money),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pending_expense_banner_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.pendingExpenseRepository.removePendingExpense(latestExpense.id)
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
                        text = stringResource(
                            R.string.pending_expense_prompt,
                            formattedAmount,
                            latestExpense.merchant,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
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
                                viewModel.pendingExpenseRepository.removePendingExpense(latestExpense.id)
                                onEditExpense()
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(text = stringResource(R.string.pending_expense_edit))
                        }

                        Button(
                            onClick = {
                                val transaction = Transaction(
                                    type = TransactionType.SPENT,
                                    value = latestExpense.amount,
                                    date = latestExpense.date,
                                    comment = latestExpense.merchant,
                                )
                                spendsViewModel.addSpent(transaction)
                                viewModel.pendingExpenseRepository.removePendingExpense(latestExpense.id)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(text = stringResource(R.string.pending_expense_confirm))
                        }
                    }
                }
            }
        }
    }
}
