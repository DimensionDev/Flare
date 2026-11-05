package dev.dimension.flare.ui.screen.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.counterPresenterBinding
import dev.dimension.flare.common.rememberRustPresenter
import dev.dimension.flare.rust.CounterPresenter
import dev.dimension.flare.rust.UiStateOfCounterState
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CounterScreen(
    onBack: () -> Unit,
    initialValue: Long = 0,
) {
    val presenter = rememberRustPresenter(counterPresenterBinding) { CounterPresenter(initialValue = initialValue) }
    val state: UiStateOfCounterState? by presenter.state.collectAsState()
    val error by presenter.error.collectAsState()
    val data = (state as? UiStateOfCounterState.Success)?.data
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.counter_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            when (val current = state) {
                null -> if (error == null) CircularProgressIndicator()
                is UiStateOfCounterState.Loading -> CircularProgressIndicator()
                is UiStateOfCounterState.Error -> Text(current.error.message, color = MaterialTheme.colorScheme.error)
                is UiStateOfCounterState.Success -> Unit
            }
            data?.let {
                Text(
                    it.value.toString(),
                    modifier = Modifier.testTag("counter-value"),
                    style = MaterialTheme.typography.displayLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { presenter.perform { decrement() } },
                    enabled = data != null,
                    modifier = Modifier.testTag("counter-decrement"),
                ) {
                    Text(stringResource(R.string.counter_decrement))
                }
                Button(
                    onClick = { presenter.perform { increment() } },
                    enabled = data != null,
                    modifier = Modifier.testTag("counter-increment"),
                ) {
                    Text(stringResource(R.string.counter_increment))
                }
            }
            OutlinedButton(
                onClick = { presenter.perform { reset() } },
                enabled = data != null,
                modifier = Modifier.testTag("counter-reset"),
            ) {
                Text(stringResource(R.string.counter_reset))
            }
            error?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("counter-error")) }
        }
    }
}
