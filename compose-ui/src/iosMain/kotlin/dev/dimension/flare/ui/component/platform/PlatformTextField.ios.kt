package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.slapps.cupertino.LocalTextStyle
import dev.dimension.flare.ui.component.CupertinoTextFieldColors
import dev.dimension.flare.ui.component.CupertinoTextFieldDefaults

@Composable
internal actual fun PlatformTextField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    lineLimits: TextFieldLineLimits,
) {
    CupertinoTextField(
        state = state,
        modifier = modifier,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        placeholder = placeholder ?: label,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        isError = false,
        contentAlignment = Alignment.CenterVertically,
    )
}

@Composable
internal actual fun PlatformSecureTextField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
) {
    CupertinoSecureTextField(
        state = state,
        modifier = modifier,
        onKeyboardAction = onKeyboardAction,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        placeholder = placeholder ?: label,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        isError = false,
        contentAlignment = Alignment.CenterVertically,
    )
}

@Composable
private fun CupertinoTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    scrollState: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    contentAlignment: Alignment.Vertical = Alignment.CenterVertically,
    colors: CupertinoTextFieldColors = CupertinoTextFieldDefaults.colors(),
) {
    // If color is not provided via the text style, use content color as a default
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(
        LocalTextSelectionColors provides colors.selectionColors,
    ) {
        var layoutResult by remember {
            mutableStateOf<TextLayoutResult?>(null)
        }

        BasicTextField(
            state = state,
            modifier =
                Modifier
                    .defaultMinSize(
                        minWidth = CupertinoTextFieldDefaults.MinWidth,
                        minHeight = CupertinoTextFieldDefaults.MinHeight,
                    ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError).value),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            onTextLayout = {
                layoutResult = it.invoke()
            },
            lineLimits = lineLimits,
            onKeyboardAction = onKeyboardAction,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            scrollState = scrollState,
            decorator = {
                CupertinoTextFieldDefaults.DecorationBox(
                    modifier = modifier,
                    valueIsEmpty = state.text.isEmpty(),
                    innerTextField = it,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    contentAlignment = contentAlignment,
                    isError = isError,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    textLayoutResult = layoutResult,
                    trailingIcon = trailingIcon,
                )
            },
        )
    }
}

@Composable
private fun CupertinoSecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    scrollState: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    contentAlignment: Alignment.Vertical = Alignment.CenterVertically,
    colors: CupertinoTextFieldColors = CupertinoTextFieldDefaults.colors(),
) {
    // If color is not provided via the text style, use content color as a default
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(
        LocalTextSelectionColors provides colors.selectionColors,
    ) {
        var layoutResult by remember {
            mutableStateOf<TextLayoutResult?>(null)
        }

        BasicSecureTextField(
            state = state,
            modifier =
                Modifier
                    .defaultMinSize(
                        minWidth = CupertinoTextFieldDefaults.MinWidth,
                        minHeight = CupertinoTextFieldDefaults.MinHeight,
                    ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError).value),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            onTextLayout = {
                layoutResult = it.invoke()
            },
            onKeyboardAction = onKeyboardAction,
            inputTransformation = inputTransformation,
            scrollState = scrollState,
            decorator = {
                CupertinoTextFieldDefaults.DecorationBox(
                    modifier = modifier,
                    valueIsEmpty = state.text.isEmpty(),
                    innerTextField = it,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    contentAlignment = contentAlignment,
                    isError = isError,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    textLayoutResult = layoutResult,
                    trailingIcon = trailingIcon,
                )
            },
        )
    }
}
