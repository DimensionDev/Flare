package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.component.SecureTextField
import io.github.composefluent.component.TextField

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
    TextField(
        state = state,
        modifier = modifier,
        lineLimits = lineLimits,
        leadingIcon = leadingIcon,
        trailing = trailingIcon?.let { { it.invoke() } },
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        enabled = enabled,
        header = label,
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
    SecureTextField(
        state = state,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailing = trailingIcon?.let { { it.invoke() } },
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        enabled = enabled,
        header = label,
    )
}
