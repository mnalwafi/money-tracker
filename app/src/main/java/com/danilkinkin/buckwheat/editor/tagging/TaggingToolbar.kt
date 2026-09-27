package com.danilkinkin.buckwheat.editor.tagging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.editor.EditStage
import com.danilkinkin.buckwheat.editor.EditorViewModel
import com.danilkinkin.buckwheat.editor.FocusController
import com.danilkinkin.buckwheat.util.observeLiveData

@Composable
fun TaggingToolbar(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    editorFocusController: FocusController
) {
    val localDensity = LocalDensity.current

    val tags by spendsViewModel.tags.observeAsState(emptyList())
    val currentComment by editorViewModel.currentComment.observeAsState("")

    var showAddComment by remember { mutableStateOf(false) }
    var isEdit by remember { mutableStateOf(false) }

    observeLiveData(editorViewModel.stage) {
        showAddComment = it === EditStage.EDIT_SPENT
    }

    val displayTags = remember(tags, currentComment) {
        tags.take(5).reversed().filter { it != currentComment }
    }

    val onSelectTag = remember(editorViewModel) {
        { selectedTag: String ->
            editorViewModel.currentComment.value = selectedTag
        }
    }

    val onEditCallback = remember {
        { edit: Boolean ->
            isEdit = edit
        }
    }

    val tagClickHandlers = remember(displayTags, onSelectTag) {
        displayTags.associateWith { tag -> { onSelectTag(tag) } }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val extendWidth = (screenWidth - 48.dp).coerceAtLeast(200.dp)

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(44.dp)
                .horizontalScroll(
                    state = rememberScrollState(),
                    enabled = !isEdit,
                    reverseScrolling = true,
                )
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            displayTags.forEach { tag ->
                key(tag) {
                    val onClick = tagClickHandlers[tag] ?: {}
                    AnimatedVisibility(
                        visible = showAddComment,
                        enter = fadeIn(
                            tween(
                                durationMillis = 150,
                                easing = EaseInOutQuad,
                            )
                        ) + slideInHorizontally(
                            tween(
                                durationMillis = 150,
                                easing = EaseInOutQuad,
                            )
                        ) { with(localDensity) { 30.dp.toPx().toInt() } },
                        exit = fadeOut(
                            tween(
                                durationMillis = 150,
                                easing = EaseInOutQuad,
                            )
                        ) + slideOutHorizontally(
                            tween(
                                durationMillis = 150,
                                easing = EaseInOutQuad,
                            )
                        ) { with(localDensity) { 30.dp.toPx().toInt() } },
                    ) {
                        Tag(
                            value = tag,
                            onClick = onClick,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            AnimatedVisibility(
                visible = showAddComment,
                enter = fadeIn(
                    tween(
                        durationMillis = 150,
                        easing = EaseInOutQuad,
                    )
                ) + slideInHorizontally(
                    tween(
                        durationMillis = 150,
                        easing = EaseInOutQuad,
                    )
                ) { with(localDensity) { 30.dp.toPx().toInt() } },
                exit = fadeOut(
                    tween(
                        durationMillis = 150,
                        easing = EaseInOutQuad,
                    )
                ) + slideOutHorizontally(
                    tween(
                        durationMillis = 150,
                        easing = EaseInOutQuad,
                    )
                ) { with(localDensity) { 30.dp.toPx().toInt() } },
            ) {
                @OptIn(ExperimentalFoundationApi::class)
                CustomTag(
                    modifier = Modifier.bringIntoViewResponder(
                        remember { SuppressBringIntoViewResponder { isEdit } }
                    ),
                    onlyIcon = tags.isNotEmpty(),
                    editorFocusController = editorFocusController,
                    extendWidth = extendWidth,
                    onEdit = onEditCallback,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class SuppressBringIntoViewResponder(
    private val isSuppressed: () -> Boolean,
) : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect {
        return if (isSuppressed()) Rect.Zero else localRect
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        // Suppress BringIntoView during edit transition to avoid scroll fighting
    }
}