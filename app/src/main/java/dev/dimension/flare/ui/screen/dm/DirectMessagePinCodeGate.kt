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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R

@Composable
internal fun DirectMessagePinCodeGate(
    isVerifying: Boolean,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinCode = rememberTextFieldState()
    val submit by rememberUpdatedState(onSubmit)

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
                text = stringResource(id = R.string.dm_pin_code_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(id = R.string.dm_pin_code_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedSecureTextField(
                state = pinCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying,
                label = {
                    Text(text = stringResource(id = R.string.dm_pin_code_label))
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying && pinCode.text.isNotBlank(),
                onClick = {
                    submit(pinCode.text.toString())
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    }
}
