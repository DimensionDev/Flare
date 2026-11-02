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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.agent_error_access_denied
import dev.dimension.flare.agent_error_authentication
import dev.dimension.flare.agent_error_configuration
import dev.dimension.flare.agent_error_details
import dev.dimension.flare.agent_error_history
import dev.dimension.flare.agent_error_interrupted
import dev.dimension.flare.agent_error_network
import dev.dimension.flare.agent_error_quota
import dev.dimension.flare.agent_error_rate_limited
import dev.dimension.flare.agent_error_retry
import dev.dimension.flare.agent_error_service
import dev.dimension.flare.agent_error_title
import dev.dimension.flare.agent_error_unknown
import dev.dimension.flare.feature.agent.common.AgentFailure
import dev.dimension.flare.feature.agent.common.AgentFailureKind
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.InfoBar
import io.github.composefluent.component.InfoBarSeverity
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AgentErrorCard(
    failure: AgentFailure,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    historyError: Boolean = false,
) {
    var detailsVisible by remember(failure) { mutableStateOf(false) }
    val message = if (historyError) stringResource(Res.string.agent_error_history) else failure.displayMessage()
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Text(message, modifier = Modifier.heightIn(max = 144.dp).verticalScroll(rememberScrollState()))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) {
                    AccentButton(onClick = onRetry) { Text(stringResource(Res.string.agent_error_retry)) }
                }
                if (failure.message.isNotBlank() && (historyError || failure.kind != AgentFailureKind.Unknown)) {
                    SubtleButton(onClick = { detailsVisible = !detailsVisible }) {
                        Text(stringResource(Res.string.agent_error_details))
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
    InfoBar(
        title = { Text(stringResource(Res.string.agent_error_title)) },
        message = content,
        severity = InfoBarSeverity.Critical,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AgentFailure.displayMessage(): String =
    if (kind == AgentFailureKind.Unknown && message.isNotBlank()) {
        message
    } else {
        stringResource(
            when (kind) {
                AgentFailureKind.Network -> Res.string.agent_error_network
                AgentFailureKind.RateLimited -> Res.string.agent_error_rate_limited
                AgentFailureKind.Service -> Res.string.agent_error_service
                AgentFailureKind.Configuration -> Res.string.agent_error_configuration
                AgentFailureKind.Authentication -> Res.string.agent_error_authentication
                AgentFailureKind.AccessDenied -> Res.string.agent_error_access_denied
                AgentFailureKind.Quota -> Res.string.agent_error_quota
                AgentFailureKind.Interrupted -> Res.string.agent_error_interrupted
                AgentFailureKind.Unknown -> Res.string.agent_error_unknown
            },
        )
    }
