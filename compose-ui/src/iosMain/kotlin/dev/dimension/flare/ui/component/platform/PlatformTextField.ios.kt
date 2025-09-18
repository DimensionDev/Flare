package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.component.CupertinoBorderedSecureTextField
import dev.dimension.flare.ui.component.CupertinoBorderedTextField

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
    CupertinoBorderedTextField(
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
        shape = RoundedCornerShape(100),
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
    CupertinoBorderedSecureTextField(
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
        shape = RoundedCornerShape(100),
    )
}
