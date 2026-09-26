package com.danilkinkin.buckwheat.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.TextRow
import com.danilkinkin.buckwheat.di.SettingsRepository
import com.danilkinkin.buckwheat.service.NotificationListenerUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationCaptureViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isEnabled = settingsRepository.isAutoExpenseCaptureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.switchAutoExpenseCapture(enabled)
        }
    }
}

@Composable
fun NotificationCaptureSwitcher(
    modifier: Modifier = Modifier,
    viewModel: NotificationCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isEnabled by viewModel.isEnabled.collectAsState()
    var showPermissionDialog by remember { mutableStateOf(false) }

    TextRow(
        modifier = modifier,
        icon = painterResource(R.drawable.ic_email),
        text = stringResource(R.string.notification_capture_title),
        description = stringResource(R.string.notification_capture_desc),
        endContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (!NotificationListenerUtils.isNotificationListenerEnabled(context)) {
                            showPermissionDialog = true
                        } else {
                            viewModel.setEnabled(true)
                        }
                    } else {
                        viewModel.setEnabled(false)
                    }
                }
            )
        }
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(text = stringResource(R.string.notification_permission_needed_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.notification_permission_needed_dialog_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        viewModel.setEnabled(true)
                        NotificationListenerUtils.openNotificationListenerSettings(context)
                    }
                ) {
                    Text(text = stringResource(R.string.notification_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
