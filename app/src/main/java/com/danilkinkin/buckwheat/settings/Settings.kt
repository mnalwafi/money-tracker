package com.danilkinkin.buckwheat.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.BuildConfig
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ModalBottomSheetState
import com.danilkinkin.buckwheat.base.TextRow
import com.danilkinkin.buckwheat.dashboard.sheetDragDownGesture
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem

const val SETTINGS_SHEET = "settings"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Settings(
    sheetState: ModalBottomSheetState? = null,
    onTriedWidget: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetDragDownGesture(sheetState, coroutineScope, onClose)
                        .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.s),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = navigationBarHeight)
                ) {
                    ThemeSwitcher()
                    LangSwitcher()
                    NotificationCaptureSwitcher()
                    RecurringTransactionsRow()
                    TryWidget(onTried = onTriedWidget)
                    TextRow(
                        text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    )
                    About(Modifier.padding(start = 16.dp, end = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Preview(name = "Default")
@Composable
private fun PreviewDefault() {
    BuckwheatTheme {
        Settings()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Preview(name = "Night mode", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNightMode() {
    BuckwheatTheme {
        Settings()
    }
}
