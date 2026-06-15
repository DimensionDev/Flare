package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.dm_pin_code_label
import dev.dimension.flare.dm_pin_code_message
import dev.dimension.flare.dm_pin_code_title
import dev.dimension.flare.ok
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.SecureTextField
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DirectMessagePinCodeGate(
    isVerifying: Boolean,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinCode = rememberTextFieldState()
    val submit by rememberUpdatedState(onSubmit)

    fun submitIfReady() {
        if (!isVerifying && pinCode.text.isNotBlank()) {
            submit(pinCode.text.toString())
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.dm_pin_code_title),
            )
            Text(
                text = stringResource(Res.string.dm_pin_code_message),
            )
            SecureTextField(
                state = pinCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying,
                header = {
                    Text(text = stringResource(Res.string.dm_pin_code_label))
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                onKeyboardAction = {
                    submitIfReady()
                },
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = FluentTheme.colors.system.critical,
                )
            }
            AccentButton(
                onClick = ::submitIfReady,
                modifier = Modifier.fillMaxWidth(),
                disabled = isVerifying || pinCode.text.isBlank(),
            ) {
                Text(text = stringResource(Res.string.ok))
            }
        }
    }
}
