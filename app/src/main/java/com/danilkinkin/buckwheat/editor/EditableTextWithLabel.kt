package com.danilkinkin.buckwheat.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.base.TextFieldWithPaddings
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.keyboard.KeyboardAction
import com.danilkinkin.buckwheat.keyboard.rememberAppKeyboard
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.util.combineColors
import com.danilkinkin.buckwheat.util.visualTransformationAsCurrency

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditableTextWithLabel(
    modifier: Modifier = Modifier,
    value: String,
    currency: ExtendCurrency? = null,
    onChangeValue: (value: String) -> Unit = {},
    contentPaddingValues: PaddingValues = PaddingValues(start = 36.dp, end = 36.dp),
    focusRequester: FocusRequester = remember { FocusRequester() },
    freezeMeasurement: Boolean = false,
) {
    val context = LocalContext.current
    val currentOnChangeValue = rememberUpdatedState(onChangeValue)

    val colorScheme = MaterialTheme.colorScheme
    val blendedBg = remember(colorScheme.primaryContainer, colorScheme.surfaceVariant) {
        combineColors(
            colorScheme.primaryContainer,
            colorScheme.surfaceVariant,
            angle = 0.9F,
        )
    }
    val color = contentColorFor(blendedBg)

    val visualTransformation = remember(context, currency, color) {
        visualTransformationAsCurrency(
            context,
            currency = currency ?: ExtendCurrency.none(),
            hintColor = color.copy(alpha = 0.2f),
        )
    }

    val keyboardHandler = rememberAppKeyboard(manualDispatcher = { action, _ ->
        if (action == KeyboardAction.REMOVE_LAST && value == "") {
            currentOnChangeValue.value("")
        }
    })

    val cursorBrush = remember(colorScheme.primary) {
        SolidColor(colorScheme.primary)
    }

    Column(modifier) {
        InterceptPlatformTextInput(keyboardHandler) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextFieldWithPaddings(
                    value = value,
                    onChangeValue = { currentOnChangeValue.value(it) },
                    cursorBrush = cursorBrush,
                    visualTransformation = visualTransformation,
                    currency = currency,
                    focusRequester = focusRequester,
                    contentPadding = contentPaddingValues,
                    freezeMeasurement = freezeMeasurement,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewDefault() {
    BuckwheatTheme {
        EditableTextWithLabel(
            value = "1 245 234 234 P",
        )
    }
}