package com.danilkinkin.buckwheat.editor.tagging

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.balloon.detectTapUnconsumed
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.editor.EditStage
import com.danilkinkin.buckwheat.editor.EditorViewModel
import com.danilkinkin.buckwheat.editor.FocusController
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.util.observeLiveData
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CustomTag(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    editorFocusController: FocusController,
    extendWidth: Dp = 0.dp,
    onlyIcon: Boolean = false,
    onEdit: (Boolean) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val localDensity = LocalDensity.current

    val tags by spendsViewModel.tags.observeAsState(emptyList())

    var isEdit by remember { mutableStateOf(false) }
    var value by remember {
        mutableStateOf(
            TextFieldValue(
                "",
                TextRange(0),
            )
        )
    }
    var isShowSuggestions by remember { mutableStateOf(false) }
    var renderPopup by remember { mutableStateOf(false) }

    observeLiveData(editorViewModel.stage) {
        if (it === EditStage.CREATING_SPENT) {
            value = TextFieldValue(
                "",
                TextRange(0),
            )
        }
    }

    observeLiveData(editorViewModel.currentComment) {
        value = TextFieldValue(
            it ?: "",
            TextRange((it ?: "").length),
        )
    }

    DisposableEffect(editorViewModel.currentComment) {
        value = TextFieldValue(
            editorViewModel.currentComment.value ?: "",
            TextRange((editorViewModel.currentComment.value ?: "").length),
        )

        onDispose { }
    }

    val close = {
        isEdit = false
        isShowSuggestions = false
        onEdit(false)
        appViewModel.showSystemKeyboard.value = false
        appViewModel.lockDraggable.value = false
        editorViewModel.currentComment.value = value.text.trim()
    }

    ExposedDropdownMenuBox(expanded = isShowSuggestions, onExpandedChange = {}) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .menuAnchor()
                .clip(CircleShape)
                .then(if (isEdit) {
                    Modifier
                } else {
                    Modifier.clickable {
                        editorFocusController.blur()
                        focusManager.clearFocus()
                        isEdit = true
                        onEdit(true)
                        appViewModel.lockDraggable.value = true
                    }
                })
        ) {
            Row(
                modifier = Modifier
                    .widthIn(0.dp, extendWidth)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(if (isEdit) 8.dp else 4.dp))
                Icon(
                    modifier = Modifier
                        .width(20.dp)
                        .height(44.dp),
                    painter = painterResource(R.drawable.ic_label),
                    contentDescription = null,
                )
                Spacer(Modifier.width(if (onlyIcon && !isEdit) 12.dp else 8.dp))

                AnimatedContent(
                    label = "openCloseTaggingEditor",
                    targetState = isEdit,
                    transitionSpec = {
                        (fadeIn(
                            tween(durationMillis = 250)
                        ) togetherWith fadeOut(
                            tween(durationMillis = 250)
                        )).using(
                            SizeTransform(clip = false)
                        )
                    }
                ) { targetIsEdit ->
                    if (this.transition.currentState == this.transition.targetState && targetIsEdit) {
                        renderPopup = true
                    }

                    if (targetIsEdit) {
                        CommentEditor(
                            value = value,
                            onChange = { value = it },
                            onApply = { close() },
                            onFocusReady = {
                                appViewModel.showSystemKeyboard.value = true
                            }
                        )
                    } else if (!onlyIcon || value.text.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
                            text = value.text.ifEmpty { stringResource(R.string.add_comment) },
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (renderPopup) {
            val filteredItems = remember(tags, value.text) {
                tags.filter {
                    it.contains(value.text, ignoreCase = true)
                }
            }

            val topBarHeight = LocalWindowInsets.current.calculateTopPadding()

            val popupPositionProvider = remember(localDensity, topBarHeight) {
                DropdownMenuPositionProvider(
                    DpOffset(0.dp, 8.dp),
                    localDensity,
                    topBarHeight,
                )
            }

            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = {},
            ) {
                val dismissEvent = remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapUnconsumed {
                                if (!dismissEvent.value) close()
                                dismissEvent.value = false
                            }
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(
                        visible = isShowSuggestions,
                        enter = expandVertically(tween(150)),
                        exit = shrinkVertically(tween(150)),
                    ) {
                        if (filteredItems.isNotEmpty() && !(filteredItems.size == 1 && filteredItems[0] == value.text)) {
                            Surface(
                                modifier = Modifier
                                    .width(extendWidth)
                                    .heightIn(max = 240.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            dismissEvent.value = true
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                LazyColumn(
                                    userScrollEnabled = true,
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    items(
                                        items = filteredItems,
                                        key = { it }
                                    ) { suggestion ->
                                        SuggestItemRow(
                                            name = suggestion,
                                            onClick = {
                                                dismissEvent.value = true
                                                value = TextFieldValue(
                                                    suggestion,
                                                    TextRange(suggestion.length),
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                renderPopup = false
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                isShowSuggestions = true
            }
        }
    }
}

@Composable
private fun SuggestItemRow(
    name: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .heightIn(42.dp)
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = name,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
    }
}

internal data class DropdownMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
    val topBarHeight: Dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val topBarHeightPx = with(density) { topBarHeight.roundToPx() }
        val contentOffsetX = with(density) { contentOffset.x.roundToPx() }
        val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

        val toRight = anchorBounds.left + contentOffsetX
        val toLeft = anchorBounds.right - contentOffsetX - popupContentSize.width
        val toDisplayRight = windowSize.width - popupContentSize.width
        val toDisplayLeft = 0
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            sequenceOf(toRight, toLeft, if (anchorBounds.left >= 0) toDisplayRight else toDisplayLeft)
        } else {
            sequenceOf(toLeft, toRight, if (anchorBounds.right <= windowSize.width) toDisplayLeft else toDisplayRight)
        }.firstOrNull { it >= 0 && it + popupContentSize.width <= windowSize.width } ?: toLeft

        val y = (anchorBounds.top - contentOffsetY - popupContentSize.height).coerceAtLeast(topBarHeightPx)
        return IntOffset(x, y)
    }
}

@Composable
fun CommentEditor(
    modifier: Modifier = Modifier,
    value: TextFieldValue,
    onChange: (comment: TextFieldValue) -> Unit,
    onApply: () -> Unit,
    onFocusReady: () -> Unit = {},
) {
    var focusIsTracking by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    BasicTextField(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus && focusIsTracking) {
                    onApply()
                }
            },
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onApply() }
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.add_comment),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                FilledIconButton(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 4.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    onClick = onApply,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_apply),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        // Wait for chip expand transition (250ms) to complete cleanly
        delay(260L)
        onFocusReady()
        focusRequester.requestFocus()
        focusIsTracking = true
    }
}

@Preview
@Composable
private fun Preview() {
    BuckwheatTheme {
        CustomTag(editorFocusController = FocusController())
    }
}
