package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentLocalizedText
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <Message : Any> AgentChatSheetScaffold(
    messages: List<Message>,
    input: String,
    isRunning: Boolean,
    canSend: Boolean,
    error: Throwable?,
    runningTrace: String,
    errorText: String,
    inputRequest: AgentInputRequest?,
    inputPlaceholder: String,
    sendContentDescription: String,
    messageText: (Message) -> String,
    messageParts: (Message) -> List<AgentMessagePart>,
    isUserMessage: (Message) -> Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    messageInputRequest: (Message) -> AgentInputRequest? = { null },
    messageInputRequestSelected: (Message) -> Boolean = { false },
    messageInputRequestSelectedOptionId: (Message) -> String? = { null },
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
    leadingContentItemCount: Int = 0,
    leadingContent: LazyListScope.() -> Unit = {},
) {
    val textState = rememberTextFieldState(input)
    val currentOnInputChange by rememberUpdatedState(onInputChange)

    LaunchedEffect(input) {
        if (textState.text.toString() != input) {
            textState.setTextAndPlaceCursorAtEnd(input)
        }
    }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect(currentOnInputChange)
    }

    val listState = rememberLazyListState()
    val itemCount =
        messages.size +
            leadingContentItemCount +
            (if (isRunning) 1 else 0) +
            (if (error != null) 1 else 0)

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .imeNestedScroll()
                    .padding(horizontal = screenHorizontalPadding),
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingContent()

            items(messages) { message ->
                val localizedText = messageLocalizedText(message)?.resolveAgentLocalizedText()
                AgentChatMessageBubble(
                    text = localizedText ?: messageText(message),
                    parts = localizedText?.let { listOf(AgentMessagePart.Text(it)) } ?: messageParts(message),
                    inputRequest = messageInputRequest(message),
                    inputRequestSelected = messageInputRequestSelected(message),
                    inputRequestSelectedOptionId = messageInputRequestSelectedOptionId(message),
                    isUser = isUserMessage(message),
                    onInputRequestOptionSelected = onInputRequestOptionSelected,
                    onPostClick = onPostClick,
                    onUserClick = onUserClick,
                )
            }

            if (isRunning) {
                item {
                    AgentChatCurrentTrace(trace = runningTrace)
                }
            }

            error?.let { throwable ->
                item {
                    AgentChatError(
                        text = throwable.message ?: errorText,
                    )
                }
            }
        }
        AgentChatInput(
            state = textState,
            canSend = canSend,
            inputRequest = inputRequest,
            placeholder = inputPlaceholder,
            sendContentDescription = sendContentDescription,
            onSend = onSend,
            modifier =
                Modifier
                    .imePadding()
                    .padding(
                        horizontal = screenHorizontalPadding,
                        vertical = 8.dp,
                    ),
        )
    }
}
