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
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentChatSheetScaffold(
    messages: List<AgentChatHistoryMessage>,
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
            (if (errorMessage != null) 1 else 0)

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
                AgentChatMessageBubble(
                    parts = message.parts,
                    isUser = message.isUser,
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

            errorMessage?.let { text ->
                item {
                    AgentChatError(
                        text = text,
                    )
                }
            }
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
