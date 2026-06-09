package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_input_placeholder
import dev.dimension.flare.agent_chat_send
import dev.dimension.flare.agent_chat_thinking
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentTarget
import dev.dimension.flare.feature.agent.presenter.history.LocalHistoryAgentPresenter
import dev.dimension.flare.settings_local_history_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.component.agent.label
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LocalHistoryAgentScreen(
    conversationId: String,
    query: String?,
    target: LocalHistoryAgentTarget,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
    val state by producePresenter("local_history_agent_${conversationId}_${normalizedQuery.orEmpty()}_$target") {
        LocalHistoryAgentPresenter(
            conversationId = conversationId,
            query = normalizedQuery,
            target = target,
        ).invoke()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtleButton(
                onClick = onBack,
                iconOnly = true,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                    contentDescription = null,
                )
            }
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Robot,
                contentDescription = null,
            )
            Text(
                text = stringResource(Res.string.settings_local_history_title),
                style = FluentTheme.typography.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AgentChatScaffold(
            messages = state.messages,
            input = state.input,
            isRunning = state.isRunning,
            canSend = state.canSend,
            error = state.error,
            runningTrace = state.currentTrace?.label() ?: stringResource(Res.string.agent_chat_thinking),
            inputRequest = state.inputRequest,
            inputPlaceholder = stringResource(Res.string.agent_chat_input_placeholder),
            sendContentDescription = stringResource(Res.string.agent_chat_send),
            messageText = LocalHistoryAgentPresenter.Message::text,
            messageLocalizedText = LocalHistoryAgentPresenter.Message::localizedText,
            messageParts = LocalHistoryAgentPresenter.Message::parts,
            messageInputRequest = LocalHistoryAgentPresenter.Message::inputRequest,
            messageInputRequestSelected = LocalHistoryAgentPresenter.Message::inputRequestSelected,
            messageInputRequestSelectedOptionId = LocalHistoryAgentPresenter.Message::inputRequestSelectedOptionId,
            isUserMessage = { it is LocalHistoryAgentPresenter.Message.User },
            onInputChange = state::setInput,
            onSend = state::sendMessage,
            onInputRequestOptionSelected = state::selectInputRequestOption,
            onPostClick = { post ->
                navigate(Route.StatusDetail(accountType = post.accountType, statusKey = post.statusKey))
            },
            onUserClick = { user ->
                user.toRoute()?.let(navigate)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun UiProfile.toRoute(): Route? =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> Route.parse(event.url)
        ClickEvent.Noop -> null
    }
