package com.danilkinkin.buckwheat.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
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
    var showListenerPermissionDialog by remember { mutableStateOf(false) }
    var showPostNotificationDialog by remember { mutableStateOf(false) }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (!NotificationListenerUtils.isNotificationListenerEnabled(context)) {
                showListenerPermissionDialog = true
            } else {
                viewModel.setEnabled(true)
                NotificationListenerUtils.ensureListenerConnected(context)
            }
        } else {
            showPostNotificationDialog = true
        }
    }

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
                        // 1. First ensure system notifications can be posted (especially on Android 13+)
                        if (!NotificationListenerUtils.isPostNotificationPermissionGranted(context)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                showPostNotificationDialog = true
                            }
                            return@Switch
                        }

                        // 2. Ensure notification listener access is granted
                        if (!NotificationListenerUtils.isNotificationListenerEnabled(context)) {
                            showListenerPermissionDialog = true
                            return@Switch
                        }

                        viewModel.setEnabled(true)
                        NotificationListenerUtils.ensureListenerConnected(context)
                    } else {
                        viewModel.setEnabled(false)
                    }
                }
            )
        }
    )

    // Dialog for Notification Listener access (reading incoming bank notifications)
    if (showListenerPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showListenerPermissionDialog = false },
            title = {
                Text(text = stringResource(R.string.notification_permission_needed_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.notification_permission_needed_dialog_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showListenerPermissionDialog = false
                        viewModel.setEnabled(true)
                        NotificationListenerUtils.openNotificationListenerSettings(context)
                    }
                ) {
                    Text(text = stringResource(R.string.notification_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showListenerPermissionDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog for Post Notifications access (showing prompt when app is closed)
    if (showPostNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showPostNotificationDialog = false },
            title = {
                Text(text = stringResource(R.string.notification_permission_post_title))
            },
            text = {
                Text(text = stringResource(R.string.notification_permission_post_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPostNotificationDialog = false
                        NotificationListenerUtils.openAppNotificationSettings(context)
                    }
                ) {
                    Text(text = stringResource(R.string.notification_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostNotificationDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
