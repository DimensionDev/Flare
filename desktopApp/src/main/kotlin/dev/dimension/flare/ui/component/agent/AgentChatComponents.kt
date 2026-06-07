package dev.dimension.flare.ui.component.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_thinking
import dev.dimension.flare.status_insight_error
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun <Message : Any> AgentChatScaffold(
    messages: List<Message>,
    input: String,
    isRunning: Boolean,
    canSend: Boolean,
    error: Throwable?,
    runningTrace: String,
    inputPlaceholder: String,
    sendContentDescription: String,
    messageText: (Message) -> String,
    isUserMessage: (Message) -> Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContentItemCount: Int = 0,
    leadingContent: LazyListScope.() -> Unit = {},
) {
    val textState = rememberTextFieldState(input)
    LaunchedEffect(input) {
        if (textState.text.toString() != input) {
            textState.setTextAndPlaceCursorAtEnd(input)
        }
    }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect(onInputChange)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FluentTheme.colors.background.solid.base),
    ) {
        AgentChatMessageList(
            messages = messages,
            isRunning = isRunning,
            error = error,
            runningTrace = runningTrace,
            messageText = messageText,
            isUserMessage = isUserMessage,
            leadingContentItemCount = leadingContentItemCount,
            leadingContent = leadingContent,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        AgentChatInput(
            state = textState,
            enabled = !isRunning,
            canSend = canSend,
            placeholder = inputPlaceholder,
            sendContentDescription = sendContentDescription,
            onSend = {
                onSend()
                textState.clearText()
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        )
    }
}

@Composable
private fun <Message : Any> AgentChatMessageList(
    messages: List<Message>,
    isRunning: Boolean,
    error: Throwable?,
    runningTrace: String,
    messageText: (Message) -> String,
    isUserMessage: (Message) -> Boolean,
    leadingContentItemCount: Int,
    leadingContent: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + leadingContentItemCount + (if (isRunning) 1 else 0) + (if (error != null) 1 else 0)

    if (listState.firstVisibleItemIndex == 0) {
        LaunchedEffect(itemCount) {
            if (itemCount > 0) {
                listState.scrollToItem(0)
            }
        }
    }

    FlareScrollBar(
        state = listState,
        reverseLayout = true,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            error?.let { throwable ->
                item {
                    Text(
                        text = throwable.message ?: stringResource(Res.string.status_insight_error),
                        color = FluentTheme.colors.system.critical,
                    )
                }
            }

            if (isRunning) {
                item {
                    AgentChatCurrentTrace(trace = runningTrace.ifBlank { stringResource(Res.string.agent_chat_thinking) })
                }
            }

            items(messages.asReversed()) { message ->
                AgentChatMessageBubble(
                    text = messageText(message),
                    isUser = isUserMessage(message),
                )
            }

            leadingContent()
        }
    }
}

@Composable
private fun AgentChatMessageBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.82f)
                    .background(
                        color =
                            if (isUser) {
                                FluentTheme.colors.fillAccent.default
                            } else {
                                FluentTheme.colors.background.layer.default
                            },
                        shape = RoundedCornerShape(8.dp),
                    ).padding(12.dp),
        ) {
            Text(
                text = text,
                color =
                    if (isUser) {
                        FluentTheme.colors.text.onAccent.primary
                    } else {
                        FluentTheme.colors.text.text.primary
                    },
            )
        }
    }
}

@Composable
private fun AgentChatInput(
    state: TextFieldState,
    enabled: Boolean,
    canSend: Boolean,
    placeholder: String,
    sendContentDescription: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        enabled = enabled,
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    if (canSend) {
                        onSend()
                    }
                    true
                } else {
                    false
                }
            },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
        trailing = {
            SubtleButton(
                onClick = onSend,
                disabled = !canSend,
                iconOnly = true,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.PaperPlane,
                    contentDescription = sendContentDescription,
                )
            }
        },
        placeholder = {
            Text(text = placeholder)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        onKeyboardAction = {
            if (canSend) {
                onSend()
            }
        },
    )
}

@Composable
private fun AgentChatCurrentTrace(trace: String) {
    val transition = rememberInfiniteTransition()
    val shimmerOffset by transition.animateFloat(
        initialValue = -240f,
        targetValue = 480f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )
    val color = LocalContentColor.current
    val shimmerBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    color.copy(alpha = 0.35f),
                    color,
                    color.copy(alpha = 0.35f),
                ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 180f, 0f),
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.Robot,
            contentDescription = null,
        )
        androidx.compose.foundation.text.BasicText(
            text = trace,
            style =
                LocalTextStyle.current.merge(
                    TextStyle(brush = shimmerBrush),
                ),
        )
    }
}
