package dev.dimension.flare.ui.component.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextThemeProvider
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_thinking
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentInputRequestOptionButtonType
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AgentChatScaffold(
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
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
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
            errorMessage = errorMessage,
            runningTrace = runningTrace,
            onInputRequestOptionSelected = onInputRequestOptionSelected,
            onPostClick = onPostClick,
            onUserClick = onUserClick,
            leadingContentItemCount = leadingContentItemCount,
            leadingContent = leadingContent,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        AgentChatInput(
            state = textState,
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
private fun AgentChatMessageList(
    messages: List<AgentChatHistoryMessage>,
    isRunning: Boolean,
    errorMessage: String?,
    runningTrace: String,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit,
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
    leadingContentItemCount: Int,
    leadingContent: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount =
        messages.size +
            leadingContentItemCount +
            (if (isRunning) 1 else 0) +
            (if (errorMessage != null) 1 else 0)

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
            errorMessage?.let { text ->
                item(
                    key = "agent-chat-error",
                    contentType = "agent-chat-error",
                ) {
                    Text(
                        text = text,
                        color = FluentTheme.colors.system.critical,
                    )
                }
            }

            if (isRunning) {
                item(
                    key = "agent-chat-current-trace",
                    contentType = "agent-chat-current-trace",
                ) {
                    AgentChatCurrentTrace(trace = runningTrace.ifBlank { stringResource(Res.string.agent_chat_thinking) })
                }
            }

            items(
                items = messages.asReversed(),
                key = { message -> "agent-chat-message:${message.id}" },
                contentType = { message ->
                    if (message.isUser) {
                        "agent-chat-user-message"
                    } else {
                        "agent-chat-assistant-message"
                    }
                },
            ) { message ->
                AgentChatMessageBubble(
                    parts = message.parts,
                    isUser = message.isUser,
                    onInputRequestOptionSelected = onInputRequestOptionSelected,
                    onPostClick = onPostClick,
                    onUserClick = onUserClick,
                )
            }

            leadingContent()
        }
    }
}

@Composable
private fun AgentChatMessageBubble(
    parts: List<AgentMessagePart>,
    isUser: Boolean,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewOnlyUserMessage = isUser && parts.isPreviewOnly()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (previewOnlyUserMessage) {
            Column(
                modifier = Modifier.fillMaxWidth(0.82f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AgentChatMessageParts(
                    parts = parts,
                    isUser = isUser,
                    onPostClick = onPostClick,
                    onUserClick = onUserClick,
                    onInputRequestOptionSelected = onInputRequestOptionSelected,
                )
            }
        } else {
            Column(
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AgentChatMessageParts(
                    parts = parts,
                    isUser = isUser,
                    onPostClick = onPostClick,
                    onUserClick = onUserClick,
                    onInputRequestOptionSelected = onInputRequestOptionSelected,
                )
            }
        }
    }
}

@Composable
private fun AgentChatMessageParts(
    parts: List<AgentMessagePart>,
    isUser: Boolean,
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        parts.forEach { part ->
            when (part) {
                is AgentMessagePart.Text -> {
                    AgentMarkdownText(
                        markdown = part.markdown,
                        color =
                            if (isUser) {
                                FluentTheme.colors.text.onAccent.primary
                            } else {
                                FluentTheme.colors.text.text.primary
                            },
                    )
                }

                is AgentMessagePart.PostCard -> {
                    AgentPostCard(
                        post = part.post,
                        onClick = { onPostClick(part.post) },
                    )
                }

                is AgentMessagePart.UserCard -> {
                    AgentUserCard(
                        user = part.user,
                        onClick = { onUserClick(part.user) },
                    )
                }

                is AgentMessagePart.Actions -> {
                    if (!isUser) {
                        AgentInputRequestOptionsContent(
                            request = part.request,
                            enabled = !part.selected,
                            selectedOptionId = part.selectedOptionId,
                            onOptionSelected = onInputRequestOptionSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    RichTextThemeProvider(
        textStyleProvider = { FluentTheme.typography.body },
        contentColorProvider = { color },
        textStyleBackProvider = { _, content -> content() },
        contentColorBackProvider = { _, content -> content() },
    ) {
        SelectionContainer {
            BasicRichText(modifier = modifier) {
                Markdown(content = markdown)
            }
        }
    }
}

@Composable
private fun AgentPostCard(
    post: UiTimelineV2.Post,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).border(
                    width = 1.dp,
                    color = FluentTheme.colors.stroke.card.default,
                    shape = RoundedCornerShape(8.dp),
                ).background(
                    color = FluentTheme.colors.background.layer.default,
                    shape = RoundedCornerShape(8.dp),
                ).padding(8.dp),
    ) {
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                LocalTimelineAppearance.current.copy(
                    showMedia = false,
                    expandMediaSize = false,
                    showLinkPreview = false,
                    postActionStyle = PostActionStyle.Hidden,
                ),
        ) {
            CommonStatusComponent(
                item = post,
                modifier = Modifier.fillMaxWidth(),
                isQuote = true,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun AgentUserCard(
    user: UiProfile,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).border(
                    width = 1.dp,
                    color = FluentTheme.colors.stroke.card.default,
                    shape = RoundedCornerShape(8.dp),
                ).background(
                    color = FluentTheme.colors.background.layer.default,
                    shape = RoundedCornerShape(8.dp),
                ).padding(10.dp),
    ) {
        UserCompat(
            user = user,
            onUserClick = { onClick?.invoke() },
        )
    }
}

@Composable
private fun AgentChatInput(
    state: TextFieldState,
    canSend: Boolean,
    placeholder: String,
    sendContentDescription: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun sendIfEnabled() {
        if (canSend) {
            onSend()
        }
    }

    TextField(
        state = state,
        modifier =
            modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                        sendIfEnabled()
                        true
                    } else {
                        false
                    }
                },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
        trailing = {
            SubtleButton(
                onClick = { sendIfEnabled() },
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
            sendIfEnabled()
        },
    )
}

@Composable
private fun AgentInputRequestOptionsContent(
    request: AgentInputRequest,
    enabled: Boolean,
    selectedOptionId: String?,
    onOptionSelected: (AgentInputRequest.Option) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val visibleOptions =
            selectedOptionId?.let { optionId ->
                request.options.filter { it.id == optionId }
            } ?: request.options
        val actionOptions = visibleOptions.filter { it.postPreview == null && it.userPreview == null }
        val postOptions = visibleOptions.filter { it.postPreview != null }
        val userOptions = visibleOptions.filter { it.userPreview != null }
        postOptions.forEach { option ->
            val post = option.postPreview ?: return@forEach
            AgentPostCard(
                post = post,
                onClick =
                    if (enabled) {
                        { onOptionSelected(option) }
                    } else {
                        null
                    },
            )
        }
        userOptions.forEach { option ->
            val user = option.userPreview ?: return@forEach
            AgentUserCard(
                user = user,
                onClick =
                    if (enabled) {
                        { onOptionSelected(option) }
                    } else {
                        null
                    },
            )
        }
        request.postPreview?.let { post ->
            AgentPostCard(
                post = post,
                onClick = null,
            )
        }
        request.userPreview?.let { user ->
            AgentUserCard(
                user = user,
                onClick = null,
            )
        }
        AgentRequestActionButtons(
            actionOptions = actionOptions,
            enabled = enabled,
            onOptionSelected = onOptionSelected,
        )
    }
}

@Composable
private fun AgentRequestActionButtons(
    actionOptions: List<AgentInputRequest.Option>,
    enabled: Boolean,
    onOptionSelected: (AgentInputRequest.Option) -> Unit,
) {
    if (actionOptions.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actionOptions.forEach { option ->
            if (option.buttonType == AgentInputRequestOptionButtonType.Primary) {
                AccentButton(
                    onClick = {
                        if (enabled) {
                            onOptionSelected(option)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    AgentOptionButtonContent(
                        option = option,
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (enabled) {
                            onOptionSelected(option)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    AgentOptionButtonContent(
                        option = option,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentOptionButtonContent(option: AgentInputRequest.Option) {
    Text(text = option.label)
}

private fun List<AgentMessagePart>.isPreviewOnly(): Boolean =
    isNotEmpty() &&
        all { part ->
            part is AgentMessagePart.PostCard || part is AgentMessagePart.UserCard
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
