package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.common.AgentFailure
import dev.dimension.flare.feature.agent.common.AgentFailureKind

@Composable
internal fun AgentErrorCard(
    failure: AgentFailure,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    historyError: Boolean = false,
) {
    var detailsVisible by remember(failure) { mutableStateOf(false) }
    val message = if (historyError) stringResource(R.string.agent_error_history) else failure.displayMessage()
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Text(message, modifier = Modifier.heightIn(max = 144.dp).verticalScroll(rememberScrollState()))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.agent_error_retry)) }
                }
                if (failure.message.isNotBlank() && (historyError || failure.kind != AgentFailureKind.Unknown)) {
                    TextButton(onClick = { detailsVisible = !detailsVisible }) {
                        Text(stringResource(R.string.agent_error_details))
                    }
                }
            }
            if (detailsVisible) {
                SelectionContainer {
                    Text(failure.message, modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
                }
            }
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.agent_error_title), style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun AgentFailure.displayMessage(): String =
    if (kind == AgentFailureKind.Unknown && message.isNotBlank()) {
        message
    } else {
        stringResource(
            when (kind) {
                AgentFailureKind.Network -> R.string.agent_error_network
                AgentFailureKind.RateLimited -> R.string.agent_error_rate_limited
                AgentFailureKind.Service -> R.string.agent_error_service
                AgentFailureKind.Configuration -> R.string.agent_error_configuration
                AgentFailureKind.Authentication -> R.string.agent_error_authentication
                AgentFailureKind.AccessDenied -> R.string.agent_error_access_denied
                AgentFailureKind.Quota -> R.string.agent_error_quota
                AgentFailureKind.Interrupted -> R.string.agent_error_interrupted
                AgentFailureKind.Unknown -> R.string.agent_error_unknown
            },
        )
    }
