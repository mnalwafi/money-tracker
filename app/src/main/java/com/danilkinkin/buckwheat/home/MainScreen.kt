package com.danilkinkin.buckwheat.home

import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.danilkinkin.buckwheat.dashboard.DASHBOARD_SHEET
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.LocalWindowSize
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.SwipeableSnackbarHost
import com.danilkinkin.buckwheat.base.TopSheetLayout
import com.danilkinkin.buckwheat.base.TopSheetValue
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.data.SystemBarState
import com.danilkinkin.buckwheat.editor.Editor
import com.danilkinkin.buckwheat.analytics.ANALYTICS_SHEET
import com.danilkinkin.buckwheat.history.History
import com.danilkinkin.buckwheat.keyboard.Keyboard
import com.danilkinkin.buckwheat.onboarding.ON_BOARDING_SHEET
import com.danilkinkin.buckwheat.recalcBudget.RECALCULATE_DAILY_BUDGET_SHEET
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.danilkinkin.buckwheat.base.ModalBottomSheetValue
import com.danilkinkin.buckwheat.base.rememberModalBottomSheetState
import com.danilkinkin.buckwheat.ui.colorBackground
import com.danilkinkin.buckwheat.ui.colorEditor
import com.danilkinkin.buckwheat.ui.colorOnEditor
import com.danilkinkin.buckwheat.ui.designsystem.BuckwheatDesignSystem
import com.danilkinkin.buckwheat.ui.isNightMode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.danilkinkin.buckwheat.util.observeLiveData
import com.danilkinkin.buckwheat.util.setSystemStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    activityResultRegistryOwner: ActivityResultRegistryOwner?,
    spendsViewModel: SpendsViewModel = viewModel(),
    appViewModel: AppViewModel = viewModel(),
) {
    val topSheetState = rememberSwipeableState(TopSheetValue.HalfExpanded)
    val dashboardSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        animationSpec = spring(
            dampingRatio = BuckwheatDesignSystem.Physics.springDampingRatio,
            stiffness = BuckwheatDesignSystem.Physics.springStiffness,
        ),
    )
    val coroutineScope = rememberCoroutineScope()
    val nightMode = remember { mutableStateOf(false) }

    val localDensity = LocalDensity.current
    val windowSizeClass = LocalWindowSize.current
    val windowInsets = LocalWindowInsets.current

    val snackBarMessage = stringResource(R.string.remove_spent)
    val snackBarAction = stringResource(R.string.remove_spent_undo)

    nightMode.value = isNightMode()

    setSystemStyle(
        style = {
            SystemBarState(
                statusBarColor = Color.Transparent,
                statusBarDarkIcons = !nightMode.value,
                navigationBarDarkIcons = !nightMode.value,
                navigationBarColor = Color.Transparent,
            )
        },
        key = nightMode.value,
    )

    observeLiveData(spendsViewModel.lastRemovedTransaction) {
        appViewModel.showSnackbar(
            message = snackBarMessage,
            actionLabel = snackBarAction,
            duration = SnackbarDuration.Long,
        ) { snackbarResult ->
            if (snackbarResult == SnackbarResult.ActionPerformed) {
                spendsViewModel.undoRemoveSpent()
            }
        }
    }

    observeLiveData(spendsViewModel.requireDistributionRestedBudget) {
        if (it) appViewModel.openSheet(PathState(RECALCULATE_DAILY_BUDGET_SHEET))
    }

    observeLiveData(spendsViewModel.requireSetBudget) {
        if (it) appViewModel.openSheet(PathState(ON_BOARDING_SHEET))
    }

    observeLiveData(spendsViewModel.periodFinished) {
        if (it) appViewModel.openSheet(PathState(ANALYTICS_SHEET))
    }

    CompositionLocalProvider(LocalDashboardSheetState provides dashboardSheetState) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colorBackground),
        ) {
        val contentHeight = constraints.maxHeight.toFloat()
        val contentWidth = constraints.maxWidth.toFloat()

        val keyboardAdditionalOffset = windowInsets
            .calculateBottomPadding()
            .minus(16.dp)
            .coerceAtLeast(0.dp)

        val navigationBarOffset = windowInsets
            .calculateBottomPadding()
            .coerceAtLeast(16.dp)

        val internalKeyboardHeight = if (windowSizeClass == WindowWidthSizeClass.Compact) {
            contentWidth
        } else {
            contentWidth / 2f
        }
            .coerceAtMost(with(localDensity) { 500.dp.toPx() })
            .coerceAtMost(contentHeight / 2)

        val imeTargetPx = WindowInsets.imeAnimationTarget.getBottom(localDensity).toFloat()
        val imeCurrentPx = WindowInsets.ime.getBottom(localDensity).toFloat()
        var rememberedImeHeight by remember { mutableFloatStateOf(0f) }

        if (imeTargetPx > 0f) {
            rememberedImeHeight = imeTargetPx
        } else if (imeCurrentPx > 0f) {
            rememberedImeHeight = imeCurrentPx
        }

        val isShowSystemKeyboard = appViewModel.showSystemKeyboard.value
        val targetSystemKeyboardHeight = if (rememberedImeHeight > 0f) rememberedImeHeight else internalKeyboardHeight

        val currentKeyboardHeight = if (isShowSystemKeyboard) {
            targetSystemKeyboardHeight
        } else {
            internalKeyboardHeight
        }

        val currentKeyboardPadding = if (isShowSystemKeyboard) {
            with(localDensity) { 16.dp.toPx() }
        } else {
            with(localDensity) { keyboardAdditionalOffset.toPx() }
        }

        val editorHeight by remember(
            contentHeight,
            currentKeyboardHeight,
            currentKeyboardPadding,
            navigationBarOffset
        ) {
            derivedStateOf {
                (contentHeight - (currentKeyboardHeight + currentKeyboardPadding).coerceAtLeast(0f))
                    .coerceAtMost(contentHeight - with(localDensity) { navigationBarOffset.toPx() + 96.dp.toPx() })
            }
        }

        val editorHeightAnimated by animateFloatAsState(
            label = "editorHeightAnimatedValue",
            targetValue = editorHeight,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        )

        Row {
            if (windowSizeClass != WindowWidthSizeClass.Compact) {
                Surface(
                    color = colorEditor,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .navigationBarsPadding(),
                ) {
                    Box {
                        History()
                        StatusBarStub()
                        SnackbarHost()
                    }
                }
                Spacer(
                    Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isShowSystemKeyboard,
                    enter = fadeIn(
                        tween(
                            durationMillis = 150,
                            easing = EaseInOutQuad,
                        )
                    ) + slideInVertically(
                        tween(
                            durationMillis = 150,
                            easing = EaseInOutQuad,
                        )
                    ) { with(localDensity) { 10.dp.toPx().toInt() } },
                    exit = fadeOut(
                        tween(
                            durationMillis = 150,
                            easing = EaseInOutQuad,
                        )
                    ) + slideOutVertically(
                        tween(
                            durationMillis = 150,
                            easing = EaseInOutQuad,
                        )
                    ) { with(localDensity) { 10.dp.toPx().toInt() } },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = keyboardAdditionalOffset),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Keyboard(
                            modifier = Modifier
                                .height(with(localDensity) { internalKeyboardHeight.toDp() })
                                .fillMaxWidth()
                                .keyboardDashboardSwipeGesture {
                                    if (dashboardSheetState.isAnimationRunning ||
                                        dashboardSheetState.currentValue == ModalBottomSheetValue.Expanded
                                    ) {
                                        return@keyboardDashboardSwipeGesture
                                    }
                                    appViewModel.openSheet(PathState(DASHBOARD_SHEET))
                                }
                        )
                    }
                }

                if (windowSizeClass == WindowWidthSizeClass.Compact) {
                    CompactEditorTopSheet(
                        topSheetState = topSheetState,
                        editorHeightAnimated = editorHeightAnimated,
                        lockSwipeable = appViewModel.lockSwipeable,
                        lockDraggable = appViewModel.lockDraggable,
                        onOpenHistory = {
                            coroutineScope.launch {
                                topSheetState.animateTo(TopSheetValue.Expanded)
                            }
                        },
                        onCloseHistory = {
                            coroutineScope.launch {
                                topSheetState.animateTo(TopSheetValue.HalfExpanded)
                            }
                        }
                    )

                    StatusBarStub()
                } else {
                    val nonCompactEditorHeight = with(localDensity) { editorHeightAnimated.toDp() }
                    Card(
                        shape = RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorEditor,
                            contentColor = colorOnEditor,
                        ),
                    ) {
                        Editor(
                            modifier = Modifier.requiredHeight(nonCompactEditorHeight),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = windowInsets.calculateTopPadding() + 8.dp)
                .fillMaxWidth()
        ) {
            PendingExpenseBanner(
                onEditExpense = {
                    if (windowSizeClass == WindowWidthSizeClass.Compact) {
                        coroutineScope.launch {
                            topSheetState.animateTo(TopSheetValue.HalfExpanded)
                        }
                    }
                }
            )
        }

        BottomSheets(
            activityResultRegistryOwner = activityResultRegistryOwner,
            dashboardSheetState = dashboardSheetState,
        )

        if (windowSizeClass == WindowWidthSizeClass.Compact) {
            SnackbarHost()
        }
    }
}
}

@Composable
fun BoxScope.SnackbarHost(
    appViewModel: AppViewModel = viewModel(),
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        SwipeableSnackbarHost(hostState = remember { appViewModel._snackbarHostState })
    }
}

@Composable
fun StatusBarStub() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(
                LocalWindowInsets.current.calculateTopPadding()
            )
            .background(colorEditor.copy(alpha = 0.9F))
    )
}

/**
 * Attaches upward swipe/drag gesture detection to the Keyboard area, safely above
 * the Android system navigation bar, ensuring normal keypad clicks remain snappy and instant.
 */
fun Modifier.keyboardDashboardSwipeGesture(
    onOpenDashboard: () -> Unit
): Modifier = this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var totalDragY = 0f
        var totalDragX = 0f
        var isUpwardDrag = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val dragY = change.position.y - change.previousPosition.y
            val dragX = change.position.x - change.previousPosition.x
            totalDragY += dragY
            totalDragX += dragX

            val currentVelocity = velocityTracker.calculateVelocity()
            // Detect either high-velocity upward flick or deliberate upward displacement
            val isFastUpwardFlick = currentVelocity.y < -400f && totalDragY < -touchSlop
            val isDisplacementDrag = totalDragY < -touchSlop * 1.75f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.5f

            if (isFastUpwardFlick || isDisplacementDrag) {
                isUpwardDrag = true
                change.consume()
                break
            }
        }

        if (isUpwardDrag) {
            onOpenDashboard()
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun CompactEditorTopSheet(
    topSheetState: androidx.compose.material.SwipeableState<TopSheetValue>,
    editorHeightAnimated: Float,
    lockSwipeable: androidx.compose.runtime.MutableState<Boolean>,
    lockDraggable: androidx.compose.runtime.MutableState<Boolean>,
    onOpenHistory: () -> Unit,
    onCloseHistory: () -> Unit,
) {
    val localDensity = LocalDensity.current
    val currentEditorHeight = with(localDensity) { editorHeightAnimated.toDp() }

    TopSheetLayout(
        swipeableState = topSheetState,
        customHalfHeight = editorHeightAnimated,
        lockSwipeable = lockSwipeable,
        lockDraggable = lockDraggable,
        sheetContentHalfExpand = {
            Editor(
                modifier = Modifier.requiredHeight(currentEditorHeight),
                onOpenHistory = onOpenHistory,
            )
        }
    ) {
        History(onClose = onCloseHistory)
    }
}

