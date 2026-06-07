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
import dev.dimension.flare.agent_chat_title
import dev.dimension.flare.feature.agent.presenter.chat.GenericChatPresenter
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.status.action.StatusInsightPostPreview
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

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
    val fallbackTitle = stringResource(Res.string.agent_chat_title)
    val title = state.title?.takeIf { it.isNotBlank() } ?: fallbackTitle

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
                text = title,
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
            runningTrace = stringResource(Res.string.agent_chat_thinking),
            inputPlaceholder = stringResource(Res.string.agent_chat_input_placeholder),
            sendContentDescription = stringResource(Res.string.agent_chat_send),
            messageText = GenericChatPresenter.Message::text,
            isUserMessage = { it is GenericChatPresenter.Message.User },
            onInputChange = state::setInput,
            onSend = state::sendMessage,
            leadingContentItemCount = state.statusInsightPosts.size,
            leadingContent = {
                state.statusInsightPosts.forEach { post ->
                    item {
                        StatusInsightPostPreview(post = post)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
