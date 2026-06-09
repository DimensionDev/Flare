package dev.dimension.flare.ui.screen.agent

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentTarget
import dev.dimension.flare.feature.agent.presenter.history.LocalHistoryAgentPresenter
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.component.agent.label
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentTrace = state.currentTrace
    val runningTrace =
        if (currentTrace != null) {
            currentTrace.label()
        } else {
            stringResource(id = R.string.agent_chat_thinking)
        }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    AgentChatScaffold(
        messages = state.messages,
        input = state.input,
        isRunning = state.isRunning,
        canSend = state.canSend,
        error = state.error,
        runningTrace = runningTrace,
        inputRequest = state.inputRequest,
        inputPlaceholder = stringResource(id = R.string.agent_chat_input_placeholder),
        sendContentDescription = stringResource(id = R.string.agent_chat_send),
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
            navigate(Route.Status.Detail(statusKey = post.statusKey, accountType = post.accountType))
        },
        onUserClick = { user ->
            user.toRoute()?.let(navigate)
        },
        modifier = modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_local_history_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    )
}

private fun UiProfile.toRoute(): Route? =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> Route.parse(event.url)
        ClickEvent.Noop -> null
    }
