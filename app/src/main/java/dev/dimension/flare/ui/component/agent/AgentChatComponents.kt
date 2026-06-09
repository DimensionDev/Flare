package dev.dimension.flare.ui.component.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.R
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentLocalizedText
import dev.dimension.flare.feature.agent.common.AgentLocalizedTextKey
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentToolKey
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDividerDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.flow.distinctUntilChanged
import com.halilibo.richtext.ui.material3.RichText as ComposeRichText

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
    messageParts: (Message) -> List<AgentMessagePart>,
    isUserMessage: (Message) -> Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    inputRequest: AgentInputRequest? = null,
    messageInputRequest: (Message) -> AgentInputRequest? = { null },
    messageInputRequestSelected: (Message) -> Boolean = { false },
    messageInputRequestSelectedOptionId: (Message) -> String? = { null },
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
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
        inputRequest = inputRequest,
        inputPlaceholder = inputPlaceholder,
        sendContentDescription = sendContentDescription,
        messageText = messageText,
        messageParts = messageParts,
        messageInputRequest = messageInputRequest,
        messageInputRequestSelected = messageInputRequestSelected,
        messageInputRequestSelectedOptionId = messageInputRequestSelectedOptionId,
        isUserMessage = isUserMessage,
        onInputChange = onInputChange,
        onSend = onSend,
        messageLocalizedText = messageLocalizedText,
        onInputRequestOptionSelected = onInputRequestOptionSelected,
        onPostClick = onPostClick,
        onUserClick = onUserClick,
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
    messageParts: (Message) -> List<AgentMessagePart>,
    isUserMessage: (Message) -> Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    inputRequest: AgentInputRequest? = null,
    messageInputRequest: (Message) -> AgentInputRequest? = { null },
    messageInputRequestSelected: (Message) -> Boolean = { false },
    messageInputRequestSelectedOptionId: (Message) -> String? = { null },
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
    topBar: @Composable () -> Unit = {},
    reserveBottomBarHeight: Boolean = true,
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
                        state = textState,
                        canSend = canSend,
                        inputRequest = inputRequest,
                        placeholder = inputPlaceholder,
                        sendContentDescription = sendContentDescription,
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
        val layoutDirection = LocalLayoutDirection.current
        val messageListContentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 12.dp,
            )
        AgentChatMessageList(
            messages = messages,
            isRunning = isRunning,
            error = error,
            runningTrace = runningTrace,
            messageText = messageText,
            messageParts = messageParts,
            messageInputRequest = messageInputRequest,
            messageInputRequestSelected = messageInputRequestSelected,
            messageInputRequestSelectedOptionId = messageInputRequestSelectedOptionId,
            isUserMessage = isUserMessage,
            onInputRequestOptionSelected = onInputRequestOptionSelected,
            messageLocalizedText = messageLocalizedText,
            onPostClick = onPostClick,
            onUserClick = onUserClick,
            modifier =
                Modifier
                    .consumeWindowInsets(contentPadding)
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            contentPadding = messageListContentPadding,
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun <Message : Any> AgentChatMessageList(
    messages: List<Message>,
    isRunning: Boolean,
    error: Throwable?,
    runningTrace: String,
    messageText: (Message) -> String,
    messageParts: (Message) -> List<AgentMessagePart>,
    messageInputRequest: (Message) -> AgentInputRequest?,
    messageInputRequestSelected: (Message) -> Boolean,
    messageInputRequestSelectedOptionId: (Message) -> String?,
    isUserMessage: (Message) -> Boolean,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit,
    modifier: Modifier = Modifier,
    messageLocalizedText: (Message) -> AgentLocalizedText? = { null },
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
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

@Composable
internal fun AgentChatMessageBubble(
    text: String,
    parts: List<AgentMessagePart>,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    inputRequest: AgentInputRequest? = null,
    inputRequestSelected: Boolean = false,
    inputRequestSelectedOptionId: String? = null,
    onInputRequestOptionSelected: (AgentInputRequest.Option) -> Unit = {},
    onPostClick: (UiTimelineV2.Post) -> Unit = {},
    onUserClick: (UiProfile) -> Unit = {},
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
            Column(
                modifier = Modifier.padding(12.dp),
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
}

@Composable
private fun AgentChatMessageParts(
    text: String,
    parts: List<AgentMessagePart>,
    isUser: Boolean,
    onPostClick: (UiTimelineV2.Post) -> Unit,
    onUserClick: (UiProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayParts = parts.takeIf { it.isNotEmpty() } ?: listOf(AgentMessagePart.Text(text))
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        displayParts.forEach { part ->
            when (part) {
                is AgentMessagePart.Text -> {
                    AgentMarkdownText(
                        markdown = part.markdown,
                        color =
                            if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
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
    CompositionLocalProvider(LocalContentColor provides color) {
        SelectionContainer {
            ComposeRichText(modifier = modifier) {
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
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
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
                modifier =
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
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
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        UserCompat(
            user = user,
            modifier = Modifier.padding(10.dp),
            onUserClick = { onClick?.invoke() },
        )
    }
}

@Composable
internal fun AgentChatInput(
    state: TextFieldState,
    canSend: Boolean,
    placeholder: String,
    sendContentDescription: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    inputRequest: AgentInputRequest? = null,
) {
    fun sendIfEnabled() {
        if (canSend) {
            onSend()
        }
    }

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
        placeholder = {
            Text(text = inputRequest?.localizedFreeTextPlaceholder?.resolveAgentLocalizedText() ?: placeholder)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        onKeyboardAction = {
            sendIfEnabled()
        },
        trailingIcon = {
            IconButton(
                onClick = { sendIfEnabled() },
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
private fun AgentInputRequestOptionsContent(
    request: AgentInputRequest,
    enabled: Boolean,
    selectedOptionId: String?,
    onOptionSelected: (AgentInputRequest.Option) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    OutlinedButton(
                        onClick = { onOptionSelected(option) },
                        enabled = enabled,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                request.localizedPrompt
                    .resolveAgentLocalizedText()
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .ifBlank { stringResource(id = R.string.agent_compose_confirmation_prompt) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
                Button(
                    onClick = { onOptionSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = option.localizedLabel.resolveAgentLocalizedText())
                }
            } else {
                OutlinedButton(
                    onClick = { onOptionSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = option.localizedLabel.resolveAgentLocalizedText())
                }
            }
        }
    }
}

@Composable
internal fun AgentLocalizedText.resolveAgentLocalizedText(): String {
    fun arg(index: Int): String = args.getOrNull(index).orEmpty()
    return when (key) {
        AgentLocalizedTextKey.DynamicText -> {
            arg(0)
        }

        AgentLocalizedTextKey.Cancel -> {
            stringResource(id = R.string.agent_ui_cancel)
        }

        AgentLocalizedTextKey.ConfirmExecute -> {
            stringResource(id = R.string.agent_ui_confirm_execute)
        }

        AgentLocalizedTextKey.ConfirmSaveSubscription -> {
            stringResource(id = R.string.agent_ui_confirm_save_subscription)
        }

        AgentLocalizedTextKey.CancelSaveSubscription -> {
            stringResource(id = R.string.agent_ui_cancel_save_subscription)
        }

        AgentLocalizedTextKey.ConfirmDeleteSubscription -> {
            stringResource(id = R.string.agent_ui_confirm_delete_subscription)
        }

        AgentLocalizedTextKey.CancelDeleteSubscription -> {
            stringResource(id = R.string.agent_ui_cancel_delete_subscription)
        }

        AgentLocalizedTextKey.ConfirmSendPost -> {
            stringResource(id = R.string.agent_ui_confirm_send_post)
        }

        AgentLocalizedTextKey.CancelSendPost -> {
            stringResource(id = R.string.agent_ui_cancel_send_post)
        }

        AgentLocalizedTextKey.SelectLoadSubscriptionSource -> {
            stringResource(id = R.string.agent_ui_select_load_subscription_source)
        }

        AgentLocalizedTextKey.SelectDeleteSubscriptionSource -> {
            stringResource(id = R.string.agent_ui_select_delete_subscription_source)
        }

        AgentLocalizedTextKey.SelectSaveSubscriptionSource -> {
            stringResource(id = R.string.agent_ui_select_save_subscription_source)
        }

        AgentLocalizedTextKey.SubscriptionSourcePlaceholder -> {
            stringResource(id = R.string.agent_ui_subscription_source_placeholder)
        }

        AgentLocalizedTextKey.SubscriptionSaveSelectionPlaceholder -> {
            stringResource(id = R.string.agent_ui_subscription_save_selection_placeholder)
        }

        AgentLocalizedTextKey.SubscriptionSaveConfirmationPlaceholder -> {
            stringResource(id = R.string.agent_ui_subscription_save_confirmation_placeholder)
        }

        AgentLocalizedTextKey.SubscriptionDeleteConfirmationPlaceholder -> {
            stringResource(id = R.string.agent_ui_subscription_delete_confirmation_placeholder)
        }

        AgentLocalizedTextKey.SubscriptionSaveConfirmationMessage -> {
            stringResource(id = R.string.agent_ui_subscription_save_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4), arg(5))
        }

        AgentLocalizedTextKey.SubscriptionDeleteConfirmationMessage -> {
            stringResource(id = R.string.agent_ui_subscription_delete_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4))
        }

        AgentLocalizedTextKey.SelectComposeTargetPost -> {
            stringResource(id = R.string.agent_ui_select_compose_target_post, arg(0))
        }

        AgentLocalizedTextKey.SelectComposeAccount -> {
            stringResource(id = R.string.agent_ui_select_compose_account, arg(0))
        }

        AgentLocalizedTextKey.SelectComposePlatform -> {
            stringResource(id = R.string.agent_ui_select_compose_platform, arg(0))
        }

        AgentLocalizedTextKey.ComposeTargetPostPlaceholder -> {
            stringResource(id = R.string.agent_ui_compose_target_post_placeholder)
        }

        AgentLocalizedTextKey.ComposeAccountPlaceholder -> {
            stringResource(id = R.string.agent_ui_compose_account_placeholder)
        }

        AgentLocalizedTextKey.ComposePlatformPlaceholder -> {
            stringResource(id = R.string.agent_ui_compose_platform_placeholder)
        }

        AgentLocalizedTextKey.ComposeConfirmationPlaceholder -> {
            stringResource(id = R.string.agent_ui_compose_confirmation_placeholder)
        }

        AgentLocalizedTextKey.ComposeSendConfirmationTitle -> {
            stringResource(id = R.string.agent_ui_compose_send_confirmation_title)
        }

        AgentLocalizedTextKey.ComposeReplyConfirmationTitle -> {
            stringResource(id = R.string.agent_ui_compose_reply_confirmation_title)
        }

        AgentLocalizedTextKey.ComposeQuoteConfirmationTitle -> {
            stringResource(id = R.string.agent_ui_compose_quote_confirmation_title)
        }

        AgentLocalizedTextKey.ComposeConfirmationMessage -> {
            resolveComposeConfirmationText()
        }

        AgentLocalizedTextKey.SelectPostActionPost -> {
            stringResource(id = R.string.agent_ui_select_post_action_post)
        }

        AgentLocalizedTextKey.SelectPostAction -> {
            stringResource(id = R.string.agent_ui_select_post_action)
        }

        AgentLocalizedTextKey.PostActionTargetPostPlaceholder -> {
            stringResource(id = R.string.agent_ui_post_action_target_post_placeholder)
        }

        AgentLocalizedTextKey.PostActionPlaceholder -> {
            stringResource(id = R.string.agent_ui_post_action_placeholder)
        }

        AgentLocalizedTextKey.PostActionConfirmationPlaceholder -> {
            stringResource(id = R.string.agent_ui_post_action_confirmation_placeholder)
        }

        AgentLocalizedTextKey.PostActionConfirmationMessage -> {
            stringResource(id = R.string.agent_ui_post_action_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4))
        }

        AgentLocalizedTextKey.SelectRelationStateUser -> {
            stringResource(id = R.string.agent_ui_select_relation_state_user)
        }

        AgentLocalizedTextKey.SelectRelationUser -> {
            stringResource(id = R.string.agent_ui_select_relation_user)
        }

        AgentLocalizedTextKey.SelectRelationAction -> {
            stringResource(id = R.string.agent_ui_select_relation_action)
        }

        AgentLocalizedTextKey.SelectRelationAccount -> {
            stringResource(id = R.string.agent_ui_select_relation_account)
        }

        AgentLocalizedTextKey.RelationUserPlaceholder -> {
            stringResource(id = R.string.agent_ui_relation_user_placeholder)
        }

        AgentLocalizedTextKey.RelationActionPlaceholder -> {
            stringResource(id = R.string.agent_ui_relation_action_placeholder)
        }

        AgentLocalizedTextKey.RelationAccountPlaceholder -> {
            stringResource(id = R.string.agent_ui_relation_account_placeholder)
        }

        AgentLocalizedTextKey.RelationConfirmationPlaceholder -> {
            stringResource(id = R.string.agent_ui_relation_confirmation_placeholder)
        }

        AgentLocalizedTextKey.RelationConfirmationMessage -> {
            stringResource(id = R.string.agent_ui_relation_confirmation_message, arg(0), arg(1), arg(2), arg(3), arg(4), arg(5))
        }

        AgentLocalizedTextKey.SelectRecentPostsUser -> {
            stringResource(id = R.string.agent_ui_select_recent_posts_user)
        }

        AgentLocalizedTextKey.SelectMatchedUser -> {
            stringResource(id = R.string.agent_ui_select_matched_user)
        }

        AgentLocalizedTextKey.SelectProfileUser -> {
            stringResource(id = R.string.agent_ui_select_profile_user)
        }

        AgentLocalizedTextKey.SelectFollowingUser -> {
            stringResource(id = R.string.agent_ui_select_following_user)
        }

        AgentLocalizedTextKey.SelectFollowersUser -> {
            stringResource(id = R.string.agent_ui_select_followers_user)
        }

        AgentLocalizedTextKey.SelectProfileTabsUser -> {
            stringResource(id = R.string.agent_ui_select_profile_tabs_user)
        }

        AgentLocalizedTextKey.StatusInsightUserPlaceholder -> {
            stringResource(id = R.string.agent_ui_status_insight_user_placeholder)
        }
    }
}

@Composable
private fun AgentLocalizedText.resolveComposeConfirmationText(): String {
    val title =
        AgentLocalizedText(
            key =
                runCatching {
                    AgentLocalizedTextKey.valueOf(args.getOrNull(0).orEmpty())
                }.getOrDefault(AgentLocalizedTextKey.ComposeSendConfirmationTitle),
        ).resolveAgentLocalizedText()
    val account = args.getOrNull(1).orEmpty()
    val platform = args.getOrNull(4).orEmpty()
    val content = args.getOrNull(12).orEmpty()
    return stringResource(id = R.string.agent_ui_compose_confirmation_message, title, account, platform, content)
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.Robot,
            contentDescription = null,
        )
        AgentChatCurrentTraceText(trace = trace)
    }
}

@Composable
private fun AgentChatCurrentTraceText(trace: String) {
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
    Text(
        text = trace,
        style = MaterialTheme.typography.bodyMedium.copy(brush = shimmerBrush),
    )
}

@Composable
internal fun AgentTrace.label(): String =
    toolKey?.label()
        ?: when (phase) {
            AgentPhase.LoadingPostContext -> {
                stringResource(id = R.string.status_insight_trace_loading_post_context)
            }

            AgentPhase.PostContextLoaded -> {
                stringResource(id = R.string.status_insight_trace_post_context_loaded)
            }

            AgentPhase.PreparingImages -> {
                stringResource(id = R.string.status_insight_trace_preparing_images)
            }

            AgentPhase.ImagesUnsupportedFallback -> {
                stringResource(id = R.string.status_insight_trace_images_unsupported_fallback)
            }

            AgentPhase.AgentStarted -> {
                stringResource(id = R.string.status_insight_trace_agent_started)
            }

            AgentPhase.StrategyStarted -> {
                stringResource(id = R.string.status_insight_trace_strategy_started)
            }

            AgentPhase.StrategyCompleted -> {
                stringResource(id = R.string.status_insight_trace_strategy_completed)
            }

            AgentPhase.SubgraphStarted -> {
                stringResource(id = R.string.status_insight_trace_subgraph_started)
            }

            AgentPhase.SubgraphCompleted -> {
                stringResource(id = R.string.status_insight_trace_subgraph_completed)
            }

            AgentPhase.SubgraphFailed -> {
                stringResource(id = R.string.status_insight_trace_subgraph_failed)
            }

            AgentPhase.AskingModel -> {
                stringResource(
                    id = R.string.status_insight_trace_asking_model,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ModelResponseReceived -> {
                stringResource(id = R.string.status_insight_trace_model_response_received)
            }

            AgentPhase.StreamingStarted -> {
                stringResource(
                    id = R.string.status_insight_trace_streaming_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.StreamingResponse -> {
                stringResource(id = R.string.status_insight_trace_streaming_response)
            }

            AgentPhase.StreamingCompleted -> {
                stringResource(id = R.string.status_insight_trace_streaming_completed)
            }

            AgentPhase.StreamingFailed -> {
                stringResource(id = R.string.status_insight_trace_streaming_failed)
            }

            AgentPhase.RunningStep -> {
                stringResource(id = R.string.status_insight_trace_running_step)
            }

            AgentPhase.StepCompleted -> {
                stringResource(id = R.string.status_insight_trace_step_completed)
            }

            AgentPhase.StepFailed -> {
                stringResource(id = R.string.status_insight_trace_step_failed)
            }

            AgentPhase.ToolCallStarted -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallCompleted -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_completed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolValidationFailed -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_validation_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallFailed -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.AgentCompleted -> {
                stringResource(id = R.string.status_insight_trace_agent_completed)
            }

            AgentPhase.AgentFailed -> {
                stringResource(id = R.string.status_insight_trace_agent_failed)
            }

            AgentPhase.AgentClosing -> {
                stringResource(id = R.string.status_insight_trace_agent_closing)
            }
        }

@Composable
private fun AgentToolKey.label(): String =
    when (this) {
        AgentToolKey.LoadStatusContextStarted -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_started)
        }

        AgentToolKey.LoadStatusContextCompleted -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_completed)
        }

        AgentToolKey.LoadStatusContextValidationFailed -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_validation_failed)
        }

        AgentToolKey.LoadStatusContextFailed -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_failed)
        }

        AgentToolKey.SearchPostsStarted,
        AgentToolKey.SearchUsersStarted,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_started)
        }

        AgentToolKey.SearchPostsCompleted,
        AgentToolKey.SearchUsersCompleted,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_completed)
        }

        AgentToolKey.SearchPostsValidationFailed,
        AgentToolKey.SearchUsersValidationFailed,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_validation_failed)
        }

        AgentToolKey.SearchPostsFailed,
        AgentToolKey.SearchUsersFailed,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_failed)
        }
    }
