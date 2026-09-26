package com.danilkinkin.buckwheat.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.recurring.RECURRING_TRANSACTIONS_SHEET

@Composable
fun RecurringTransactionsRow(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    ButtonRow(
        modifier = modifier,
        icon = painterResource(R.drawable.ic_autorenew),
        text = stringResource(R.string.recurring_transactions_title),
        description = stringResource(R.string.recurring_transactions_desc),
        onClick = {
            appViewModel.openSheet(PathState(RECURRING_TRANSACTIONS_SHEET))
        },
    )
}
