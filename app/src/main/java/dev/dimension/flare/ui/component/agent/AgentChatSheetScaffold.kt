package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentChatSheetScaffold(
    messages: PagingState<AgentChatHistoryMessage>,
    input: String,
    isRunning: Boolean,
    canSend: Boolean,
    errorMessage: String?,
    runningTrace: String,
    inputPlaceholder: String,
    sendContentDescription: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
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
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        ) {
            itemsIndexed(
                state = messages,
                key = { message -> "agent-chat-message:${message.id}" },
                contentType = { message ->
                    if (message.isUser) {
                        "agent-chat-user-message"
                    } else {
                        "agent-chat-assistant-message"
                    }
                },
                loadingCount = AGENT_CHAT_SHEET_MESSAGE_LOADING_COUNT,
                loadingContent = { index, _ ->
                    AgentChatSheetMessageSkeleton(isUser = index == 0)
                },
                errorContent = { throwable ->
                    AgentChatError(text = throwable.message ?: throwable.toString())
                },
                itemContent = { _, _, message ->
                    AgentChatMessageBubble(
                        parts = message.parts,
                        isUser = message.isUser,
                        onInputRequestOptionSelected = onInputRequestOptionSelected,
                        onPostClick = onPostClick,
                        onUserClick = onUserClick,
                    )
                },
            )
        }
        if (isRunning) {
            AgentChatCurrentTrace(
                trace = runningTrace,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(top = 8.dp),
            )
        }
        errorMessage?.let { text ->
            AgentChatError(
                text = text,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(top = 8.dp),
            )
        }
        AgentChatInput(
            state = textState,
            canSend = canSend,
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

private const val AGENT_CHAT_SHEET_MESSAGE_LOADING_COUNT = 3

@Composable
private fun AgentChatSheetMessageSkeleton(isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.72f),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        content = {},
                    )
                }
                androidx.compose.material3.Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    content = {},
                )
            }
        }
    }
}
