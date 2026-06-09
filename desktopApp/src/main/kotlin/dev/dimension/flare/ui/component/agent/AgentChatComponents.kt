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
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.*
import dev.dimension.flare.agent_chat_thinking
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentLocalizedText
import dev.dimension.flare.feature.agent.common.AgentLocalizedTextKey
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.status_insight_error
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
internal fun <Message : Any> AgentChatScaffold(
    messages: List<Message>,
    input: String,
    isRunning: Boolean,
    canSend: Boolean,
    error: Throwable?,
    runningTrace: String,
    inputRequest: AgentInputRequest? = null,
    inputPlaceholder: String,
    sendContentDescription: String,
    messageText: (Message) -> String,
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    messageParts: (Message) -> List<AgentMessagePart>,
    messageInputRequest: (Message) -> AgentInputRequest? = { null },
    messageInputRequestSelected: (Message) -> Boolean = { false },
    messageInputRequestSelectedOptionId: (Message) -> String? = { null },
    isUserMessage: (Message) -> Boolean,
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
            error = error,
            runningTrace = runningTrace,
            messageText = messageText,
            messageLocalizedText = messageLocalizedText,
            messageParts = messageParts,
            messageInputRequest = messageInputRequest,
            messageInputRequestSelected = messageInputRequestSelected,
            messageInputRequestSelectedOptionId = messageInputRequestSelectedOptionId,
            isUserMessage = isUserMessage,
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
            inputRequest = inputRequest,
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
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    messageParts: (Message) -> List<AgentMessagePart>,
    messageInputRequest: (Message) -> AgentInputRequest?,
    messageInputRequestSelected: (Message) -> Boolean,
    messageInputRequestSelectedOptionId: (Message) -> String?,
    isUserMessage: (Message) -> Boolean,
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
            (if (error != null) 1 else 0)

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
                val localizedText = messageLocalizedText(message)?.resolveAgentLocalizedText()
                val text = localizedText ?: messageText(message)
                AgentChatMessageBubble(
                    text = text,
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

            leadingContent()
        }
    }
}

@Composable
private fun AgentChatMessageBubble(
    text: String,
    parts: List<AgentMessagePart>,
    inputRequest: AgentInputRequest? = null,
    inputRequestSelected: Boolean = false,
    inputRequestSelectedOptionId: String? = null,
    isUser: Boolean,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
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
                text = text,
                parts = parts,
                isUser = isUser,
                onPostClick = onPostClick,
                onUserClick = onUserClick,
            )
            if (!isUser && inputRequest != null) {
                AgentInputRequestOptionsContent(
                    request = inputRequest,
                    enabled = !inputRequestSelected,
                    selectedOptionId = inputRequestSelectedOptionId,
                    onOptionSelected = onInputRequestOptionSelected,
                )
            }
        }
    }
}

@Composable
private fun AgentChatMessageParts(
    text: String,
    parts: List<AgentMessagePart>,
    isUser: Boolean,
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
) {
    val displayParts = parts.takeIf { it.isNotEmpty() } ?: listOf(AgentMessagePart.Text(text))
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        displayParts.forEach { part ->
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
    inputRequest: AgentInputRequest? = null,
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
            Text(text = inputRequest?.localizedFreeTextPlaceholder?.resolveAgentLocalizedText() ?: placeholder)
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
        val confirmOption = actionOptions.firstOrNull { it.id == "confirm" }
        if (confirmOption != null) {
            AgentConfirmationRequest(
                request = request,
                confirmOption = confirmOption,
                actionOptions = actionOptions,
                enabled = enabled,
                onOptionSelected = onOptionSelected,
            )
            return@Column
        }
        Text(
            text = request.localizedPrompt.resolveAgentLocalizedText(),
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
        )
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
        if (actionOptions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actionOptions.forEach { option ->
                    Button(
                        onClick = {
                            if (enabled) {
                                onOptionSelected(option)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = option.localizedLabel.resolveAgentLocalizedText())
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentConfirmationRequest(
    request: AgentInputRequest,
    confirmOption: AgentInputRequest.Option?,
    actionOptions: List<AgentInputRequest.Option>,
    enabled: Boolean,
    onOptionSelected: (AgentInputRequest.Option) -> Unit,
) {
    Text(
        text =
            request.localizedPrompt
                .resolveAgentLocalizedText()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .ifBlank { stringResource(Res.string.agent_compose_confirmation_prompt) },
        style = FluentTheme.typography.body,
        color = FluentTheme.colors.text.text.primary,
    )
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
    AgentConfirmationButtons(
        actionOptions = actionOptions,
        confirmOption = confirmOption,
        enabled = enabled,
        onOptionSelected = onOptionSelected,
    )
}

@Composable
private fun AgentConfirmationButtons(
    actionOptions: List<AgentInputRequest.Option>,
    confirmOption: AgentInputRequest.Option?,
    enabled: Boolean,
    onOptionSelected: (AgentInputRequest.Option) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actionOptions.forEach { option ->
            if (option.id == confirmOption?.id) {
                AccentButton(
                    onClick = {
                        if (enabled) {
                            onOptionSelected(option)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = option.localizedLabel.resolveAgentLocalizedText())
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
                    Text(text = option.localizedLabel.resolveAgentLocalizedText())
                }
            }
        }
    }
}

@Composable
private fun AgentLocalizedText.resolveAgentLocalizedText(): String {
    fun arg(index: Int): String = args.getOrNull(index).orEmpty()
    return when (key) {
        AgentLocalizedTextKey.DynamicText -> arg(0)
        AgentLocalizedTextKey.Cancel -> stringResource(Res.string.agent_ui_cancel)
        AgentLocalizedTextKey.ConfirmExecute -> stringResource(Res.string.agent_ui_confirm_execute)
        AgentLocalizedTextKey.ConfirmSaveSubscription -> stringResource(Res.string.agent_ui_confirm_save_subscription)
        AgentLocalizedTextKey.CancelSaveSubscription -> stringResource(Res.string.agent_ui_cancel_save_subscription)
        AgentLocalizedTextKey.ConfirmDeleteSubscription -> stringResource(Res.string.agent_ui_confirm_delete_subscription)
        AgentLocalizedTextKey.CancelDeleteSubscription -> stringResource(Res.string.agent_ui_cancel_delete_subscription)
        AgentLocalizedTextKey.ConfirmSendPost -> stringResource(Res.string.agent_ui_confirm_send_post)
        AgentLocalizedTextKey.CancelSendPost -> stringResource(Res.string.agent_ui_cancel_send_post)
        AgentLocalizedTextKey.SelectLoadSubscriptionSource -> stringResource(Res.string.agent_ui_select_load_subscription_source)
        AgentLocalizedTextKey.SelectDeleteSubscriptionSource -> stringResource(Res.string.agent_ui_select_delete_subscription_source)
        AgentLocalizedTextKey.SelectSaveSubscriptionSource -> stringResource(Res.string.agent_ui_select_save_subscription_source)
        AgentLocalizedTextKey.SubscriptionSourcePlaceholder -> stringResource(Res.string.agent_ui_subscription_source_placeholder)
        AgentLocalizedTextKey.SubscriptionSaveSelectionPlaceholder -> stringResource(Res.string.agent_ui_subscription_save_selection_placeholder)
        AgentLocalizedTextKey.SubscriptionSaveConfirmationPlaceholder -> stringResource(Res.string.agent_ui_subscription_save_confirmation_placeholder)
        AgentLocalizedTextKey.SubscriptionDeleteConfirmationPlaceholder -> stringResource(Res.string.agent_ui_subscription_delete_confirmation_placeholder)
        AgentLocalizedTextKey.SubscriptionSaveConfirmationMessage ->
            stringResource(Res.string.agent_ui_subscription_save_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4), arg(5))
        AgentLocalizedTextKey.SubscriptionDeleteConfirmationMessage ->
            stringResource(Res.string.agent_ui_subscription_delete_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4))
        AgentLocalizedTextKey.SelectComposeTargetPost -> stringResource(Res.string.agent_ui_select_compose_target_post, arg(0))
        AgentLocalizedTextKey.SelectComposeAccount -> stringResource(Res.string.agent_ui_select_compose_account, arg(0))
        AgentLocalizedTextKey.SelectComposePlatform -> stringResource(Res.string.agent_ui_select_compose_platform, arg(0))
        AgentLocalizedTextKey.ComposeTargetPostPlaceholder -> stringResource(Res.string.agent_ui_compose_target_post_placeholder)
        AgentLocalizedTextKey.ComposeAccountPlaceholder -> stringResource(Res.string.agent_ui_compose_account_placeholder)
        AgentLocalizedTextKey.ComposePlatformPlaceholder -> stringResource(Res.string.agent_ui_compose_platform_placeholder)
        AgentLocalizedTextKey.ComposeConfirmationPlaceholder -> stringResource(Res.string.agent_ui_compose_confirmation_placeholder)
        AgentLocalizedTextKey.ComposeSendConfirmationTitle -> stringResource(Res.string.agent_ui_compose_send_confirmation_title)
        AgentLocalizedTextKey.ComposeReplyConfirmationTitle -> stringResource(Res.string.agent_ui_compose_reply_confirmation_title)
        AgentLocalizedTextKey.ComposeQuoteConfirmationTitle -> stringResource(Res.string.agent_ui_compose_quote_confirmation_title)
        AgentLocalizedTextKey.ComposeConfirmationMessage -> resolveComposeConfirmationText()
        AgentLocalizedTextKey.SelectPostActionPost -> stringResource(Res.string.agent_ui_select_post_action_post)
        AgentLocalizedTextKey.SelectPostAction -> stringResource(Res.string.agent_ui_select_post_action)
        AgentLocalizedTextKey.PostActionTargetPostPlaceholder -> stringResource(Res.string.agent_ui_post_action_target_post_placeholder)
        AgentLocalizedTextKey.PostActionPlaceholder -> stringResource(Res.string.agent_ui_post_action_placeholder)
        AgentLocalizedTextKey.PostActionConfirmationPlaceholder -> stringResource(Res.string.agent_ui_post_action_confirmation_placeholder)
        AgentLocalizedTextKey.PostActionConfirmationMessage ->
            stringResource(Res.string.agent_ui_post_action_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4))
        AgentLocalizedTextKey.SelectRelationStateUser -> stringResource(Res.string.agent_ui_select_relation_state_user)
        AgentLocalizedTextKey.SelectRelationUser -> stringResource(Res.string.agent_ui_select_relation_user)
        AgentLocalizedTextKey.SelectRelationAction -> stringResource(Res.string.agent_ui_select_relation_action)
        AgentLocalizedTextKey.SelectRelationAccount -> stringResource(Res.string.agent_ui_select_relation_account)
        AgentLocalizedTextKey.RelationUserPlaceholder -> stringResource(Res.string.agent_ui_relation_user_placeholder)
        AgentLocalizedTextKey.RelationActionPlaceholder -> stringResource(Res.string.agent_ui_relation_action_placeholder)
        AgentLocalizedTextKey.RelationAccountPlaceholder -> stringResource(Res.string.agent_ui_relation_account_placeholder)
        AgentLocalizedTextKey.RelationConfirmationPlaceholder -> stringResource(Res.string.agent_ui_relation_confirmation_placeholder)
        AgentLocalizedTextKey.RelationConfirmationMessage ->
            stringResource(Res.string.agent_ui_relation_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4), arg(5))
        AgentLocalizedTextKey.SelectRecentPostsUser -> stringResource(Res.string.agent_ui_select_recent_posts_user)
        AgentLocalizedTextKey.SelectMatchedUser -> stringResource(Res.string.agent_ui_select_matched_user)
        AgentLocalizedTextKey.SelectProfileUser -> stringResource(Res.string.agent_ui_select_profile_user)
        AgentLocalizedTextKey.SelectFollowingUser -> stringResource(Res.string.agent_ui_select_following_user)
        AgentLocalizedTextKey.SelectFollowersUser -> stringResource(Res.string.agent_ui_select_followers_user)
        AgentLocalizedTextKey.SelectProfileTabsUser -> stringResource(Res.string.agent_ui_select_profile_tabs_user)
        AgentLocalizedTextKey.StatusInsightUserPlaceholder -> stringResource(Res.string.agent_ui_status_insight_user_placeholder)
    }
}

@Composable
private fun AgentLocalizedText.resolveComposeConfirmationText(): String {
    val title =
        AgentLocalizedText(
            key = runCatching { AgentLocalizedTextKey.valueOf(args.getOrNull(0).orEmpty()) }.getOrDefault(AgentLocalizedTextKey.ComposeSendConfirmationTitle),
        ).resolveAgentLocalizedText()
    val account = args.getOrNull(1).orEmpty()
    val platform = args.getOrNull(4).orEmpty()
    val content = args.getOrNull(12).orEmpty()
    return stringResource(Res.string.agent_ui_compose_confirmation_message, title, account, platform, content)
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
