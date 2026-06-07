package dev.dimension.flare.ui.component.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDividerDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <Message : Any> AgentChatContent(
    title: String,
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
    showHeader: Boolean = true,
    leadingContentItemCount: Int = 0,
    leadingContent: LazyListScope.() -> Unit = {},
) {
    AgentChatScaffold(
        messages = messages,
        input = input,
        isRunning = isRunning,
        canSend = canSend,
        error = error,
        runningTrace = runningTrace,
        inputPlaceholder = inputPlaceholder,
        sendContentDescription = sendContentDescription,
        messageText = messageText,
        isUserMessage = isUserMessage,
        onInputChange = onInputChange,
        onSend = onSend,
        modifier = modifier,
        topBar =
            if (showHeader) {
                {
                    AgentChatHeader(
                        title = title,
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding)
                                .padding(vertical = 12.dp),
                    )
                }
            } else {
                {}
            },
        leadingContentItemCount = leadingContentItemCount,
        leadingContent = leadingContent,
    )
}

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
    topBar: @Composable () -> Unit = {},
    reserveBottomBarHeight: Boolean = true,
    leadingContentItemCount: Int = 0,
    leadingContent: LazyListScope.() -> Unit = {},
) {
    val bottomBarHeight =
        if (reserveBottomBarHeight) {
            LocalBottomBarHeight.current
        } else {
            0.dp
        }

    FlareScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = {
            Surface {
                Box {
                    HorizontalDivider(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                        color = FlareDividerDefaults.color,
                        thickness = FlareDividerDefaults.thickness,
                    )
                    AgentChatInput(
                        value = input,
                        enabled = !isRunning,
                        canSend = canSend,
                        placeholder = inputPlaceholder,
                        sendContentDescription = sendContentDescription,
                        onValueChange = onInputChange,
                        onSend = onSend,
                        modifier =
                            Modifier
                                .padding(bottom = bottomBarHeight)
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Horizontal,
                                    ),
                                ).consumeWindowInsets(
                                    PaddingValues(
                                        bottom = bottomBarHeight,
                                    ),
                                ).imePadding()
                                .fillMaxWidth()
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                    vertical = 8.dp,
                                ),
                    )
                }
            }
        },
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.add(
                WindowInsets(bottom = bottomBarHeight),
            ),
    ) { contentPadding ->
        AgentChatMessageList(
            messages = messages,
            isRunning = isRunning,
            error = error,
            runningTrace = runningTrace,
            messageText = messageText,
            isUserMessage = isUserMessage,
            modifier =
                Modifier
                    .consumeWindowInsets(contentPadding)
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            contentPadding = contentPadding,
            leadingContentItemCount = leadingContentItemCount,
            leadingContent = leadingContent,
        )
    }
}

@Composable
internal fun AgentChatHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.Robot,
            contentDescription = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <Message : Any> AgentChatMessageList(
    messages: List<Message>,
    isRunning: Boolean,
    error: Throwable?,
    runningTrace: String,
    messageText: (Message) -> String,
    isUserMessage: (Message) -> Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    leadingContentItemCount: Int = 0,
    leadingContent: LazyListScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    val itemCount =
        messages.size +
            leadingContentItemCount +
            (if (isRunning) 1 else 0) +
            (if (error != null) 1 else 0)

    if (listState.firstVisibleItemIndex == 0) {
        LaunchedEffect(itemCount) {
            if (itemCount > 0) {
                listState.scrollToItem(0)
            }
        }
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .imeNestedScroll(),
        state = listState,
        reverseLayout = true,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        error?.let { throwable ->
            item {
                AgentChatError(
                    text = throwable.message ?: stringResource(id = R.string.status_insight_error),
                )
            }
        }

        if (isRunning) {
            item {
                AgentChatCurrentTrace(trace = runningTrace)
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

@Composable
internal fun AgentChatMessageBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color =
                    if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

@Composable
internal fun AgentChatInput(
    value: String,
    enabled: Boolean,
    canSend: Boolean,
    placeholder: String,
    sendContentDescription: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        minLines = 1,
        maxLines = 4,
        placeholder = {
            Text(text = placeholder)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions =
            KeyboardActions(
                onSend = {
                    if (canSend) {
                        onSend()
                    }
                },
            ),
        trailingIcon = {
            IconButton(
                onClick = onSend,
                enabled = canSend,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.PaperPlane,
                    contentDescription = sendContentDescription,
                )
            }
        },
    )
}

@Composable
internal fun AgentChatError(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun AgentChatCurrentTrace(
    trace: String,
    modifier: Modifier = Modifier,
) {
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
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val shimmerBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    color.copy(alpha = 0.35f),
                    color,
                    color.copy(alpha = 0.35f),
                ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 220f, 0f),
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.Robot,
            contentDescription = null,
        )
        Text(
            text = trace,
            style = MaterialTheme.typography.bodyMedium.copy(brush = shimmerBrush),
        )
    }
}
