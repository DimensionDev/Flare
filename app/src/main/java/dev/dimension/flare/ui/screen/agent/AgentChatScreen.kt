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
import dev.dimension.flare.feature.agent.presenter.chat.GenericChatPresenter
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.status.action.StatusInsightPostPreview
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatScreen(
    conversationId: String,
    initialMessage: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedInitialMessage = initialMessage?.trim()?.takeIf { it.isNotEmpty() }
    val state by producePresenter("agent_chat_${conversationId}_${normalizedInitialMessage.orEmpty()}") {
        GenericChatPresenter(
            conversationId = conversationId,
            initialMessage = normalizedInitialMessage,
        ).invoke()
    }
    val fallbackTitle = stringResource(id = R.string.agent_chat_title)
    val title = state.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    AgentChatScaffold(
        messages = state.messages,
        input = state.input,
        isRunning = state.isRunning,
        canSend = state.canSend,
        error = state.error,
        runningTrace = stringResource(id = R.string.agent_chat_thinking),
        inputPlaceholder = stringResource(id = R.string.agent_chat_input_placeholder),
        sendContentDescription = stringResource(id = R.string.agent_chat_send),
        messageText = GenericChatPresenter.Message::text,
        isUserMessage = { it is GenericChatPresenter.Message.User },
        onInputChange = state::setInput,
        onSend = state::sendMessage,
        modifier = modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        leadingContentItemCount = state.statusInsightPosts.size,
        leadingContent = {
            state.statusInsightPosts.forEach { post ->
                item {
                    StatusInsightPostPreview(post = post)
                }
            }
        },
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(
                        text = title,
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
