package com.danilkinkin.buckwheat.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ModalBottomSheetState
import com.danilkinkin.buckwheat.dashboard.sheetDragDownGesture
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.history.History
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem

const val VIEWER_HISTORY_SHEET = "viewerHistory"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ViewerHistory(
    sheetState: ModalBottomSheetState? = null,
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
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

                // Clean Header Title without close X button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetDragDownGesture(sheetState, coroutineScope, onClose)
                        .padding(horizontal = BuckwheatDesignSystem.Spacing.screenPadding, vertical = BuckwheatDesignSystem.Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = BuckwheatDesignSystem.Typography.drawerTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // History Content
                History(
                    modifier = Modifier.weight(1f),
                    readOnly = false,
                    onClose = onClose,
                )
            }
        }
    }
}