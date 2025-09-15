package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    OutlinedTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { it.invoke() } },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
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
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { it.invoke() } },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
    )
}
