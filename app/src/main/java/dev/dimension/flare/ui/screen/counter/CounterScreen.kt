package dev.dimension.flare.ui.screen.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import dev.dimension.flare.common.rememberCruxViewModel
import dev.dimension.flare.counter.Core
import dev.dimension.flare.counter.CounterEvent
import dev.dimension.flare.counter.EffectHandler
import dev.dimension.flare.counter.FfiBridge
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold

internal typealias CounterViewModel = Core

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CounterScreen(
    onBack: () -> Unit,
    initialValue: Long = 0,
) {
    val viewModel = rememberCounterViewModel(initialValue)
    val state by viewModel.view.collectAsState()
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
            Text(
                state.value.toString(),
                modifier = Modifier.testTag("counter-value"),
                style = MaterialTheme.typography.displayLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.update(CounterEvent.Decrement) },
                    modifier = Modifier.testTag("counter-decrement"),
                ) {
                    Text(stringResource(R.string.counter_decrement))
                }
                Button(
                    onClick = { viewModel.update(CounterEvent.Increment) },
                    modifier = Modifier.testTag("counter-increment"),
                ) {
                    Text(stringResource(R.string.counter_increment))
                }
            }
            OutlinedButton(
                onClick = { viewModel.update(CounterEvent.Reset) },
                modifier = Modifier.testTag("counter-reset"),
            ) {
                Text(stringResource(R.string.counter_reset))
            }
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("counter-error")) }
        }
    }
}

@Composable
internal fun rememberCounterViewModel(initialValue: Long): CounterViewModel =
    rememberCruxViewModel(key = "counter", createBridge = { FfiBridge() }) { bridge, scope ->
        CounterViewModel(bridge, object : EffectHandler {}, scope).apply {
            update(CounterEvent.Initialize(initialValue))
        }
    }
