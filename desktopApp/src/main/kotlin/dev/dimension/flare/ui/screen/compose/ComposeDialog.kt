package dev.dimension.flare.ui.screen.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FaceSmile
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.SquarePollHorizontal
import compose.icons.fontawesomeicons.solid.TriangleExclamation
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.Res
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.compose_content_warning_hint
import dev.dimension.flare.compose_hint
import dev.dimension.flare.compose_media_sensitive
import dev.dimension.flare.compose_poll_expiration_12_hours
import dev.dimension.flare.compose_poll_expiration_1_day
import dev.dimension.flare.compose_poll_expiration_1_hour
import dev.dimension.flare.compose_poll_expiration_30_minutes
import dev.dimension.flare.compose_poll_expiration_3_days
import dev.dimension.flare.compose_poll_expiration_5_minutes
import dev.dimension.flare.compose_poll_expiration_6_hours
import dev.dimension.flare.compose_poll_expiration_7_days
import dev.dimension.flare.compose_poll_expiration_at
import dev.dimension.flare.compose_poll_multiple_choice
import dev.dimension.flare.compose_poll_option_hint
import dev.dimension.flare.compose_poll_single_choice
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.misskey_visibility_followers
import dev.dimension.flare.misskey_visibility_followers_description
import dev.dimension.flare.misskey_visibility_home
import dev.dimension.flare.misskey_visibility_home_description
import dev.dimension.flare.misskey_visibility_public
import dev.dimension.flare.misskey_visibility_public_description
import dev.dimension.flare.misskey_visibility_specified
import dev.dimension.flare.misskey_visibility_specified_description
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.navigate_back
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.EmojiPicker
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.QuotedStatus
import dev.dimension.flare.ui.component.status.StatusVisibilityComponent
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.compose.ComposePresenter
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.FlyoutContainer
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import io.github.composefluent.component.TextFieldDefaults
import io.github.composefluent.surface.Card
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun ComposeDialog(
    onBack: () -> Unit,
    accountType: AccountType,
    modifier: Modifier = Modifier,
    status: ComposeStatus? = null,
    initialText: String = "",
) {
    val state by producePresenter(key = "compose_$accountType") {
        composePresenter(
            accountType = accountType,
            status = status,
            initialText = initialText,
        )
    }

    val focusRequester = remember { FocusRequester() }
    val contentWarningFocusRequester = remember { FocusRequester() }
    state.contentWarningState
        .onSuccess {
            LaunchedEffect(it.enabled) {
                if (it.enabled) {
                    contentWarningFocusRequester.requestFocus()
                } else {
                    focusRequester.requestFocus()
                }
            }
        }.onError {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    Column {
        SubtleButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.Start)
                    .padding(8.dp),
            iconOnly = true,
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Xmark,
                contentDescription = stringResource(Res.string.navigate_back),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.state.selectedUsers.onSuccess { selectedUsers ->
                    for (i in 0 until selectedUsers.size) {
                        val (user, account) = selectedUsers[i]
                        user.onSuccess {
                            PillButton(
//                                onClick = {
//                                    state.state.selectAccount(account)
//                                },
                                selected = false,
                                onSelectedChanged = {
                                    state.state.selectAccount(account)
                                },
                                content = {
                                    AvatarComponent(it.avatar, size = 24.dp)
                                    Text(it.handle)
                                },
                            )
                        }
                    }
                    state.state.otherAccounts.onSuccess { others ->
                        if (others.size > 0) {
                            MenuFlyoutContainer(
                                flyout = {
                                    for (i in 0 until others.size) {
                                        val (user, account) = others[i]
                                        user.onSuccess { data ->
                                            MenuFlyoutItem(
                                                text = {
                                                    Text(text = data.handle)
                                                },
                                                onClick = {
                                                    state.state.selectAccount(account)
                                                },
                                                icon = {
                                                    AvatarComponent(
                                                        data.avatar,
                                                        size = 24.dp,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                },
                            ) {
                                PillButton(
                                    selected = isFlyoutVisible,
                                    onSelectedChanged = {
                                        isFlyoutVisible = !isFlyoutVisible
                                    },
                                    content = {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Plus,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            state.contentWarningState.onSuccess {
                AnimatedVisibility(it.enabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier =
                                Modifier
                                    .padding(
                                        horizontal = screenHorizontalPadding,
                                    ),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                it.textFieldState.text.isEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(
                                    text = stringResource(Res.string.compose_content_warning_hint),
                                    color = TextFieldDefaults.defaultTextFieldColors().default.placeholderColor,
                                )
                            }
                            BasicTextField(
                                state = it.textFieldState,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(
                                            focusRequester = contentWarningFocusRequester,
                                        ),
//                            placeholder = {
//                                Text(text = stringResource(Res.string.compose_content_warning_hint))
//                            },
                                textStyle = LocalTextStyle.current.copy(color = FluentTheme.colors.text.text.primary),
                                cursorBrush = SolidColor(FluentTheme.colors.text.text.primary),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .height(1.dp)
                                    .fillMaxWidth()
                                    .background(FluentTheme.colors.stroke.divider.default),
                        )
//                        HorizontalDivider()
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .padding(
                            horizontal = screenHorizontalPadding,
                        ).fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    state.textFieldState.text.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = stringResource(Res.string.compose_hint),
                        color = TextFieldDefaults.defaultTextFieldColors().default.placeholderColor,
                    )
                }
                BasicTextField(
                    state = state.textFieldState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .focusRequester(
                                focusRequester = focusRequester,
                            ),
                    textStyle = LocalTextStyle.current.copy(color = FluentTheme.colors.text.text.primary),
                    cursorBrush = SolidColor(FluentTheme.colors.text.text.primary),
//                    placeholder = {
//                        Text(text = stringResource(Res.string.compose_hint))
//                    },
                )
            }
            state.mediaState.onSuccess { mediaState ->
                AnimatedVisibility(mediaState.medias.isNotEmpty()) {
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = screenHorizontalPadding)
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            mediaState.medias.forEach { uri ->
                                Box {
                                    NetworkImage(
                                        model = uri.absolutePath,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(128.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                    )
                                    IconButton(
                                        onClick = {
                                            mediaState.removeMedia(uri)
                                        },
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.3f),
                                                    shape = CircleShape,
                                                ),
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Xmark,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                        if (mediaState.canSensitive) {
                            val sensitiveInteractionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier =
                                    Modifier
                                        .padding(horizontal = screenHorizontalPadding)
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = sensitiveInteractionSource,
                                            indication = null,
                                        ) {
                                            mediaState.setMediaSensitive(!mediaState.isMediaSensitive)
                                        },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckBox(
                                    checked = mediaState.isMediaSensitive,
                                    onCheckStateChange = { mediaState.setMediaSensitive(it) },
//                                    interactionSource = sensitiveInteractionSource,
                                )
                                Text(text = stringResource(Res.string.compose_media_sensitive))
                            }
                        }
                    }
                }
            }
            state.pollState.onSuccess { pollState ->
                AnimatedVisibility(pollState.enabled) {
                    Column(
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding)
                                .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val items =
                                mapOf(
                                    true to stringResource(Res.string.compose_poll_single_choice),
                                    false to stringResource(Res.string.compose_poll_multiple_choice),
                                )
                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f),
                            ) {
                                items.forEach {
                                    RadioButton(
                                        selected = pollState.pollSingleChoice == it.key,
                                        onClick = {
                                            pollState.setPollSingleChoice(it.key)
                                        },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp),
                                        label = it.value,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    pollState.addPollOption()
                                },
                                disabled = !pollState.canAddPollOption,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        pollState.options.forEachIndexed { index, textFieldState ->
                            PollOption(
                                textFieldState = textFieldState,
                                index = index,
                                onRemove = {
                                    pollState.removePollOption(index)
                                },
                            )
                        }
                        MenuFlyoutContainer(
                            flyout = {
                                PollExpiration.entries.forEach { expiration ->
                                    MenuFlyoutItem(
                                        onClick = {
                                            pollState.setExpiredAt(expiration)
                                            isFlyoutVisible = false
                                        },
                                        text = {
                                            Text(text = stringResource(expiration.textRes))
                                        },
                                    )
                                }
                            },
                        ) {
                            Button(
                                onClick = {
                                    isFlyoutVisible = true
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.End),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            Res.string.compose_poll_expiration_at,
                                            stringResource(pollState.expiredAt.textRes),
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            state.state.replyState?.let { replyState ->
                replyState.onSuccess { state ->
                    val content = state.content
                    if (content is UiTimeline.ItemContent.Status) {
                        Card(
                            modifier =
                                Modifier
                                    .padding(horizontal = screenHorizontalPadding)
                                    .sizeIn(maxWidth = 300.dp),
                        ) {
                            QuotedStatus(
                                data = content,
                                modifier =
                                    Modifier
                                        .padding(horizontal = screenHorizontalPadding)
                                        .padding(vertical = 8.dp)
                                        .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier =
                Modifier
                    .background(FluentTheme.colors.control.default)
                    .heightIn(min = 16.dp)
                    .fillMaxWidth(),
//            tonalElevation = 8.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.mediaState.onSuccess {
                    if (it.enabled) {
                        IconButton(
                            onClick = {
//                                photoPickerLauncher.launch(
//                                    PickVisualMediaRequest(
//                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo,
//                                    ),
//                                )
                            },
                            enabled = state.canMedia,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Image,
                                contentDescription = null,
                            )
                        }
                    }
                }
                state.pollState.onSuccess {
                    IconButton(
                        onClick = {
                            it.togglePoll()
                        },
                        enabled = state.canPoll,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.SquarePollHorizontal,
                            contentDescription = null,
                        )
                    }
                }
                state.state.visibilityState.onSuccess { visibilityState ->
                    MenuFlyoutContainer(
                        flyout = {
                            visibilityState.allVisibilities.forEach { visibility ->
                                MenuFlyoutItem(
                                    onClick = {
                                        visibilityState.setVisibility(visibility)
                                        isFlyoutVisible = false
                                    },
                                    text = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.Start,
                                        ) {
                                            Text(
                                                text = stringResource(visibility.localName),
                                                style = FluentTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                text = stringResource(visibility.localDescription),
                                                style = FluentTheme.typography.caption,
                                            )
                                        }
                                    },
                                    icon = {
                                        StatusVisibilityComponent(visibility = visibility)
                                    },
                                )
                            }
                        },
                    ) {
                        IconButton(
                            onClick = {
                                isFlyoutVisible = true
                            },
                        ) {
                            StatusVisibilityComponent(visibility = visibilityState.visibility)
                        }
                    }
                }
                state.contentWarningState.onSuccess {
                    IconButton(
                        onClick = {
                            it.toggle()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.TriangleExclamation,
                            contentDescription = null,
                        )
                    }
                }
                state.state.emojiState.onSuccess { emojis ->
                    AnimatedVisibility(emojis.size > 0) {
                        FlyoutContainer(
                            placement = FlyoutPlacement.Bottom,
                            flyout = {
                                val actualAccountType =
                                    remember(
                                        state.state.selectedAccounts,
                                    ) {
                                        state.state.selectedAccounts
                                            .firstOrNull()
                                            ?.accountKey
                                            ?.let(AccountType::Specific)
                                    }
                                EmojiPicker(
                                    data = emojis.data,
                                    onEmojiSelected = state::selectEmoji,
                                    accountType = actualAccountType ?: accountType,
                                    modifier =
                                        Modifier
                                            .sizeIn(
                                                maxWidth = 300.dp,
                                                maxHeight = 200.dp,
                                            ),
                                )
                            },
                        ) {
                            IconButton(
                                onClick = {
                                    isFlyoutVisible = !isFlyoutVisible
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.FaceSmile,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                state.remainingLength.onSuccess {
                    Text(
                        it,
                        style = FluentTheme.typography.caption,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                AccentButton(
                    onClick = {
                        state.send()
                        onBack.invoke()
                    },
                    disabled = !state.canSend,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.PaperPlane,
                        contentDescription = null,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun PollOption(
    textFieldState: TextFieldState,
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = textFieldState,
        modifier =
            modifier
                .fillMaxWidth(),
        placeholder = {
            Text(text = stringResource(Res.string.compose_poll_option_hint, index + 1))
        },
        trailing = {
            IconButton(
                onClick = onRemove,
                enabled = index > 1,
            ) {
                FAIcon(imageVector = FontAwesomeIcons.Solid.Xmark, contentDescription = null)
            }
        },
    )
}

@Composable
private fun composePresenter(
    accountType: AccountType,
    status: ComposeStatus? = null,
    initialText: String = "",
) = run {
    val state =
        remember(status, accountType) {
            ComposePresenter(accountType = accountType, status)
        }.invoke()
    val textFieldState by remember {
        mutableStateOf(TextFieldState(initialText))
    }

    val remainingLength =
        state.composeConfig
            .mapNotNull {
                it.text
            }.map {
                it.maxLength - textFieldState.text.length
            }

    val pollState =
        state.composeConfig
            .mapNotNull {
                it.poll
            }.map {
                pollPresenter(it)
            }

    val mediaState =
        state.composeConfig
            .mapNotNull {
                it.media
            }.map {
                mediaPresenter(it)
            }

    val contentWarningState =
        state.composeConfig
            .mapNotNull {
                it.contentWarning
            }.map {
                contentWarningPresenter()
            }

    state.initialTextState?.onSuccess {
        LaunchedEffect(it) {
            if (it.text.isNotEmpty()) {
                textFieldState.edit {
                    append(it.text)
                    selection = TextRange(it.cursorPosition)
                }
            }
        }
    }

    val canSend =
        remember(textFieldState.text, state.account) {
            textFieldState.text.isNotBlank() &&
                textFieldState.text.isNotEmpty() &&
                state.account is UiState.Success &&
                remainingLength.takeSuccessOr(0) >= 0
        }
    val canPoll =
        remember(mediaState) {
            mediaState !is UiState.Success || mediaState.data.medias.isEmpty()
        }
    val canMedia =
        remember(mediaState, pollState) {
            (mediaState is UiState.Success && mediaState.data.canAddMedia) &&
                !(pollState is UiState.Success && pollState.data.enabled)
        }
    object {
        val remainingLength =
            remainingLength.map {
                it.toString()
            }
        val textFieldState = textFieldState
        val canSend = canSend
        val canPoll = canPoll
        val canMedia = canMedia
        val pollState = pollState
        val mediaState = mediaState
        val contentWarningState = contentWarningState
        val state = state

        fun selectEmoji(emoji: UiEmoji) {
            textFieldState.edit {
                append(" ${emoji.shortcode} ")
            }
        }

        fun send() {
            state.selectedAccounts.forEach {
                val data =
                    ComposeData(
                        content = textFieldState.text.toString(),
                        medias =
                            mediaState.takeSuccess()?.medias.orEmpty().map {
                                FileItem(it)
                            },
                        poll =
                            pollState.takeSuccess()?.takeIf { it.enabled }?.let {
                                ComposeData.Poll(
                                    multiple = !it.pollSingleChoice,
                                    expiredAfter = it.expiredAt.duration.inWholeMilliseconds,
                                    options =
                                        it.options.map { option ->
                                            option.text.toString()
                                        },
                                )
                            },
                        sensitive = mediaState.takeSuccess()?.isMediaSensitive ?: false,
                        spoilerText =
                            contentWarningState
                                .takeSuccess()
                                ?.textFieldState
                                ?.text
                                ?.toString(),
                        visibility =
                            state.visibilityState.takeSuccess()?.visibility
                                ?: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public,
                        account = it,
                        referenceStatus =
                            status?.let {
                                state.replyState?.takeSuccess()?.let { item ->
                                    ComposeData.ReferenceStatus(
                                        data = item,
                                        composeStatus = status,
                                    )
                                }
                            },
                    )
                state.send(data)
            }
        }
    }
}

@Composable
private fun contentWarningPresenter() =
    run {
        val textFieldState by remember {
            mutableStateOf(TextFieldState(""))
        }
        var enabled by remember {
            mutableStateOf(false)
        }
        object {
            val textFieldState = textFieldState
            val enabled = enabled

            fun toggle() {
                enabled = !enabled
            }
        }
    }

@Composable
private fun mediaPresenter(config: ComposeConfig.Media) =
    run {
        var medias by remember {
            mutableStateOf(listOf<File>())
        }
        var isMediaSensitive by remember {
            mutableStateOf(false)
        }

        object {
            val medias = medias.toImmutableList()
            val isMediaSensitive = isMediaSensitive
            val canAddMedia = medias.size < config.maxCount
            val canSensitive = config.canSensitive
            val enabled = config.maxCount > 0

            fun addMedia(uris: List<File>) {
                medias = (medias + uris).distinct().takeLast(config.maxCount)
            }

            fun removeMedia(uri: File) {
                medias = medias.filterNot { it == uri }
                if (medias.isEmpty()) {
                    isMediaSensitive = false
                }
            }

            fun setMediaSensitive(value: Boolean) {
                isMediaSensitive = value
            }
        }
    }

@Composable
private fun pollPresenter(config: ComposeConfig.Poll) =
    run {
        var enabled by remember {
            mutableStateOf(false)
        }
        var options by remember {
            mutableStateOf(listOf(TextFieldState(), TextFieldState()))
        }
        var pollSingleChoice by remember {
            mutableStateOf(true)
        }
        var expiredAt by remember {
            mutableStateOf(PollExpiration.Minutes5)
        }
        val canAddPollOption =
            remember(options) {
                options.size < config.maxOptions
            }

        object {
            val enabled = enabled
            val options = options.toImmutableList()
            val pollSingleChoice = pollSingleChoice
            val canAddPollOption = canAddPollOption
            val expiredAt = expiredAt

            fun togglePoll() {
                enabled = !enabled
                if (!enabled) {
                    options = listOf(TextFieldState(), TextFieldState())
                    pollSingleChoice = true
                    expiredAt = PollExpiration.Minutes5
                }
            }

            fun addPollOption() {
                options = options + TextFieldState()
            }

            fun removePollOption(index: Int) {
                options = options.filterIndexed { i, _ -> i != index }
            }

            fun setPollSingleChoice(singleChoice: Boolean) {
                pollSingleChoice = singleChoice
            }

            fun setExpiredAt(value: PollExpiration) {
                expiredAt = value
            }
        }
    }

internal enum class PollExpiration(
    val textRes: StringResource,
    val duration: Duration,
) {
    Minutes5(Res.string.compose_poll_expiration_5_minutes, 5.minutes),
    Minutes30(Res.string.compose_poll_expiration_30_minutes, 30.minutes),
    Hours1(Res.string.compose_poll_expiration_1_hour, 1.hours),
    Hours6(Res.string.compose_poll_expiration_6_hours, 6.hours),
    Hours12(Res.string.compose_poll_expiration_12_hours, 12.hours),
    Days1(Res.string.compose_poll_expiration_1_day, 1.days),
    Days3(Res.string.compose_poll_expiration_3_days, 3.days),
    Days7(Res.string.compose_poll_expiration_7_days, 7.days),
}

internal val UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.localName: StringResource
    get() =
        when (this) {
            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
                Res.string.misskey_visibility_public

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
                Res.string.misskey_visibility_home

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
                Res.string.misskey_visibility_followers

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
                Res.string.misskey_visibility_specified
        }

internal val UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.localDescription: StringResource
    get() =
        when (this) {
            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
                Res.string.misskey_visibility_public_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
                Res.string.misskey_visibility_home_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
                Res.string.misskey_visibility_followers_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
                Res.string.misskey_visibility_specified_description
        }
