package dev.dimension.flare.ui.screen.compose

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FaceSmile
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.SquarePollHorizontal
import compose.icons.fontawesomeicons.solid.TriangleExclamation
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.EmojiPicker
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusVisibilityComponent
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.compose.ComposePresenter
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun ShortcutComposeRoute(
    onBack: () -> Unit,
    initialText: String = "",
    initialMedias: ImmutableList<Uri> = persistentListOf(),
) {
    val activeAccountState by producePresenter(key = "shortcut_compose_active_account") {
        activeAccountPresenter()
    }
    val accountType =
        activeAccountState.user
            .takeSuccess()
            ?.let { AccountType.Specific(it.key) }
            ?: AccountType.Guest
    FlareTheme {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            ComposeScreen(
                onBack = onBack,
                accountType = accountType,
                initialText = initialText,
                initialMedias = initialMedias,
            )
        }
    }
}

@Composable
private fun activeAccountPresenter() =
    run {
        remember { ActiveAccountPresenter() }.invoke()
    }

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun ComposeScreen(
    onBack: () -> Unit,
    accountType: AccountType?,
    modifier: Modifier = Modifier,
    status: ComposeStatus? = null,
    draftGroupId: String? = null,
    initialText: String = "",
    initialMedias: ImmutableList<Uri> = persistentListOf(),
    onOpenDraftBox: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by producePresenter(key = "compose_${accountType}_${status}_$draftGroupId") {
        composePresenter(
            context = context,
            accountType = accountType,
            status = status,
            draftGroupId = draftGroupId,
            initialText = initialText,
            initialMedias = initialMedias,
        )
    }
    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(4),
            onResult = { uris ->
                state.mediaState.onSuccess {
                    it.addMedia(uris)
                }
            },
        )
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
    Column(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape = MaterialTheme.shapes.large,
                ).clip(MaterialTheme.shapes.large),
    ) {
        FlareTopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
                when (state.state.composeStatus) {
                    is ComposeStatus.VVOComment ->
                        Text(text = stringResource(id = R.string.compose_vvo_comment_title))
                    is ComposeStatus.Quote ->
                        Text(text = stringResource(id = R.string.compose_quote_title))
                    is ComposeStatus.Reply ->
                        Text(text = stringResource(id = R.string.compose_reply_title))
                    null ->
                        Text(text = stringResource(id = R.string.compose_title))
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (state.hasTextContent) {
                            showCloseConfirmDialog = true
                        } else {
                            onBack()
                        }
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            },
            actions = {
                if (onOpenDraftBox != null && state.state.showDraft) {
                    TextButton(
                        onClick = onOpenDraftBox,
                    ) {
                        Text(text = stringResource(id = R.string.draft_box_title))
                    }
                }
                IconButton(
                    onClick = {
                        state.send {
                            onBack.invoke()
                        }
                    },
                    enabled = state.canSend,
                ) {
                    FAIcon(imageVector = FontAwesomeIcons.Solid.PaperPlane, contentDescription = null)
                }
            },
        )
        if (showCloseConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCloseConfirmDialog = false
                },
                title = {
                    Text(text = stringResource(id = R.string.compose_close_confirm_title))
                },
                text = {
                    Text(text = stringResource(id = R.string.compose_close_confirm_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.saveDraft { dispatched ->
                                if (dispatched) {
                                    showCloseConfirmDialog = false
                                    onBack()
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(id = R.string.compose_save_draft))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCloseConfirmDialog = false
                            onBack()
                        },
                    ) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                },
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
                    selectedUsers.forEach { userState ->
                        userState.onSuccess { user ->
                            AssistChip(
                                onClick = {
                                    state.state.selectAccount(user.key)
                                },
                                label = {
                                    Text(user.handle.canonical)
                                },
                                leadingIcon = {
                                    AvatarComponent(user.avatar, size = 24.dp)
                                },
                                shape = RoundedCornerShape(100),
                            )
                        }
                    }
                    state.state.otherUsers.onSuccess { others ->
                        if (others.isNotEmpty()) {
                            AssistChip(
                                shape = CircleShape,
                                onClick = {
                                    state.setShowAccountSelectMenu(true)
                                },
                                label = {
                                    FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = null)
                                    FlareDropdownMenu(
                                        expanded = state.showAccountSelectMenu,
                                        onDismissRequest = {
                                            state.setShowAccountSelectMenu(false)
                                        },
                                        properties = PopupProperties(focusable = false),
                                    ) {
                                        others.forEach { userState ->
                                            userState.onSuccess { user ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(text = user.handle.canonical)
                                                    },
                                                    onClick = {
                                                        state.state.selectAccount(user.key)
                                                    },
                                                    leadingIcon = {
                                                        AvatarComponent(
                                                            user.avatar,
                                                            size = 24.dp,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            state.contentWarningState.onSuccess {
                AnimatedVisibility(it.enabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            state = it.textFieldState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(
                                        focusRequester = contentWarningFocusRequester,
                                    ),
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            placeholder = {
                                Text(text = stringResource(id = R.string.compose_content_warning_hint))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                TextField(
                    state = state.textFieldState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(
                                focusRequester = focusRequester,
                            ),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    placeholder = {
                        Text(text = stringResource(id = R.string.compose_hint))
                    },
                )

                state.remainingLength.onSuccess {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = screenHorizontalPadding),
                    )
                }
            }
            state.mediaState.onSuccess { mediaState ->
                AnimatedVisibility(mediaState.medias.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = screenHorizontalPadding)
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            mediaState.medias.forEach { media ->
                                Box {
                                    NetworkImage(
                                        model = media.url,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(128.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Row(
                                        modifier =
                                            Modifier
                                                .matchParentSize(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        if (mediaState.enableAltText) {
                                            Box {
                                                var showEditDialog by remember {
                                                    mutableStateOf(false)
                                                }
                                                TextButton(
                                                    onClick = {
                                                        showEditDialog = true
                                                    },
                                                    colors =
                                                        ButtonDefaults.textButtonColors(
                                                            containerColor = Color.Black.copy(alpha = 0.8f),
                                                            contentColor = Color.White,
                                                        ),
                                                ) {
                                                    Text("ALT")
                                                }
                                                if (showEditDialog) {
                                                    AlertDialog(
                                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                                        onDismissRequest = {
                                                            showEditDialog = false
                                                        },
                                                        confirmButton = {
                                                            TextButton(
                                                                onClick = {
                                                                    showEditDialog = false
                                                                },
                                                            ) {
                                                                Text(stringResource(android.R.string.ok))
                                                            }
                                                        },
                                                        icon = {
                                                            NetworkImage(
                                                                model = media.url,
                                                                contentDescription = null,
                                                                modifier =
                                                                    Modifier
                                                                        .size(128.dp)
                                                                        .clip(RoundedCornerShape(8.dp)),
                                                            )
                                                        },
                                                        title = {
                                                            Text(text = stringResource(id = R.string.media_alt_text))
                                                        },
                                                        text = {
                                                            OutlinedTextField(
                                                                media.textState,
                                                                trailingIcon = {
                                                                    val remainingLength =
                                                                        mediaState.altTextMaxLength - media.textState.text.length
                                                                    Text(
                                                                        remainingLength.toString(),
                                                                        color =
                                                                            if (remainingLength > 10) {
                                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                                            } else {
                                                                                MaterialTheme.colorScheme.error
                                                                            },
                                                                    )
                                                                },
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                mediaState.removeMedia(media.uri)
                                            },
                                            colors =
                                                IconButtonDefaults.iconButtonColors(
                                                    containerColor = Color.Black.copy(alpha = 0.8f),
                                                    contentColor = Color.White,
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
                                Checkbox(
                                    checked = mediaState.isMediaSensitive,
                                    onCheckedChange = { mediaState.setMediaSensitive(it) },
                                    interactionSource = sensitiveInteractionSource,
                                )
                                Text(text = stringResource(id = R.string.compose_media_sensitive))
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
                                    true to stringResource(id = R.string.compose_poll_single_choice),
                                    false to stringResource(id = R.string.compose_poll_multiple_choice),
                                )
                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items.forEach { (singleChoice, label) ->
                                    FilterChip(
                                        selected = pollState.pollSingleChoice == singleChoice,
                                        onClick = {
                                            pollState.setPollSingleChoice(singleChoice)
                                        },
                                        label = {
                                            Text(label)
                                        },
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    pollState.addPollOption()
                                },
                                enabled = pollState.canAddPollOption,
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
                        FilledTonalButton(
                            onClick = {
                                pollState.setShowExpirationMenu(true)
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.End),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.compose_poll_expiration_at,
                                        stringResource(id = pollState.expiredAt.textId),
                                    ),
                            )
                            FlareDropdownMenu(
                                expanded = pollState.showExpirationMenu,
                                onDismissRequest = {
                                    pollState.setShowExpirationMenu(false)
                                },
                                properties = PopupProperties(focusable = false),
                            ) {
                                PollExpiration.entries.forEach { expiration ->
                                    DropdownMenuItem(
                                        onClick = {
                                            pollState.setExpiredAt(expiration)
                                            pollState.setShowExpirationMenu(false)
                                        },
                                        text = {
                                            Text(text = stringResource(id = expiration.textId))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.state.replyState?.let { replyState ->
                replyState.onSuccess { state ->
                    val content = state as? UiTimelineV2.Post
                    if (content is UiTimelineV2.Post) {
                        Card {
                            CompositionLocalProvider(
                                LocalComponentAppearance provides
                                    LocalComponentAppearance.current.copy(
                                        showMedia = false,
                                        expandMediaSize = false,
                                        showLinkPreview = false,
                                        postActionStyle = PostActionStyle.Hidden,
                                    ),
                            ) {
                                CommonStatusComponent(
                                    item = content,
                                    modifier =
                                        Modifier
                                            .padding(
                                                horizontal = screenHorizontalPadding,
                                                vertical = 8.dp,
                                            ).fillMaxWidth(),
                                    isQuote = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier =
                Modifier
                    .heightIn(min = 16.dp)
                    .fillMaxWidth(),
            tonalElevation = 8.dp,
        ) {
            Row {
                state.mediaState.onSuccess {
                    if (it.enabled) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                    ),
                                )
                            },
                            enabled = state.canMedia,
                        ) {
                            FAIcon(imageVector = FontAwesomeIcons.Solid.Image, contentDescription = null)
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
                    IconButton(
                        onClick = {
                            visibilityState.showVisibilityMenu()
                        },
                    ) {
                        StatusVisibilityComponent(visibility = visibilityState.visibility)
                        FlareDropdownMenu(
                            expanded = visibilityState.showVisibilityMenu,
                            onDismissRequest = {
                                visibilityState.hideVisibilityMenu()
                            },
                            properties = PopupProperties(focusable = false),
                        ) {
                            visibilityState.allVisibilities.forEach { visibility ->
                                DropdownMenuItem(
                                    onClick = {
                                        visibilityState.setVisibility(visibility)
                                        visibilityState.hideVisibilityMenu()
                                    },
                                    text = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.Start,
                                        ) {
                                            Text(
                                                text = stringResource(id = visibility.localName),
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                text = stringResource(id = visibility.localDescription),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        StatusVisibilityComponent(visibility = visibility)
                                    },
                                    contentPadding =
                                        PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                )
                            }
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
                        IconButton(
                            onClick = {
                                state.setShowEmojiMenu(!state.showEmojiMenu)
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.FaceSmile,
                                contentDescription = null,
                            )
                            if (state.showEmojiMenu) {
                                Popup(
                                    alignment = Alignment.BottomStart,
                                    onDismissRequest = {
                                        state.setShowEmojiMenu(false)
                                    },
                                    offset =
                                        IntOffset(
                                            x = 0,
                                            y =
                                                with(LocalDensity.current) {
                                                    -48.dp.roundToPx()
                                                },
                                        ),
                                    properties =
                                        PopupProperties(
                                            usePlatformDefaultWidth = true,
                                            focusable = true,
                                        ),
                                ) {
                                    Card(
                                        modifier =
                                            Modifier
                                                .sizeIn(
                                                    maxHeight = 256.dp,
                                                    maxWidth = 384.dp,
                                                ),
                                        elevation =
                                            CardDefaults.elevatedCardElevation(
                                                defaultElevation = 3.dp,
                                            ),
                                    ) {
                                        EmojiPicker(
                                            data = emojis.data,
                                            onEmojiSelected = state::selectEmoji,
                                            accountType = emojis.accountType,
                                            modifier =
                                                Modifier
                                                    .padding(8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                state.languageState.onSuccess { languageState ->
                    Box {
                        TextButton(
                            onClick = {
                                languageState.setShowLanguagePicker(true)
                            },
                        ) {
                            Text(languageState.selectedLanguageName)
                        }
                        if (languageState.showLanguagePicker) {
                            AlertDialog(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                onDismissRequest = {
                                    languageState.setShowLanguagePicker(false)
                                },
                                text = {
                                    LazyColumn {
                                        items(languageState.allLanguage) { (locale, tag) ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(locale.displayName)
                                                },
                                                trailingContent = {
                                                    if (languageState.allowMultiple) {
                                                        Checkbox(
                                                            checked = languageState.selectedLanguage.contains(tag),
                                                            onCheckedChange = {
                                                                languageState.selectLanguage(tag)
                                                            },
                                                            enabled =
                                                                languageState.canSelect || languageState.selectedLanguage.contains(tag),
                                                        )
                                                    } else {
                                                        RadioButton(
                                                            selected = languageState.selectedLanguage.contains(tag),
                                                            onClick = {
                                                                languageState.selectLanguage(tag)
                                                            },
                                                        )
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .clickable {
                                                            languageState.selectLanguage(tag)
                                                        },
                                                colors =
                                                    ListItemDefaults.colors(
                                                        containerColor = Color.Transparent,
                                                    ),
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            languageState.setShowLanguagePicker(false)
                                        },
                                    ) {
                                        Text(stringResource(android.R.string.ok))
                                    }
                                },
                            )
                        }
                    }
                }
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
    OutlinedTextField(
        state = textFieldState,
        modifier =
            modifier
                .fillMaxWidth(),
        placeholder = {
            Text(text = stringResource(id = R.string.compose_poll_option_hint, index + 1))
        },
        trailingIcon = {
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
    context: Context,
    accountType: AccountType?,
    status: ComposeStatus? = null,
    draftGroupId: String? = null,
    initialText: String = "",
    initialMedias: ImmutableList<Uri> = persistentListOf(),
) = run {
    val state =
        remember(status, accountType, draftGroupId) {
            ComposePresenter(
                accountType = accountType,
                status = status,
                draftGroupId = draftGroupId,
            )
        }.invoke()
    val textFieldState by remember {
        mutableStateOf(TextFieldState(initialText))
    }

    LaunchedEffect(textFieldState.text) {
        state.setText(textFieldState.text.toString())
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
                mediaPresenter(it, initialMedias)
            }

    mediaState.onSuccess {
        LaunchedEffect(it.medias.size) {
            state.setMediaSize(it.medias.size)
        }
    }

    val contentWarningState =
        state.composeConfig
            .mapNotNull {
                it.contentWarning
            }.map {
                contentWarningPresenter()
            }

    val languageState =
        state.composeConfig
            .mapNotNull {
                it.language
            }.map {
                languageState(it)
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
    state.loadedDraftState?.onSuccess { draft ->
        val composeConfig = state.composeConfig.takeSuccess()
        val canApplyDraft =
            (composeConfig?.visibility == null || state.visibilityState.takeSuccess() != null) &&
                (composeConfig?.media == null || mediaState is UiState.Success) &&
                (composeConfig?.poll == null || pollState is UiState.Success) &&
                (composeConfig?.contentWarning == null || contentWarningState is UiState.Success) &&
                (composeConfig?.language == null || languageState is UiState.Success)
        LaunchedEffect(draft.groupId, draft.updatedAt, canApplyDraft) {
            if (!canApplyDraft) {
                return@LaunchedEffect
            }
            textFieldState.edit {
                delete(0, length)
                append(draft.data.content)
                selection = TextRange(length)
            }
            mediaState.takeSuccess()?.replaceMedias(draft.medias)
            mediaState.takeSuccess()?.setMediaSensitive(draft.data.sensitive)
            pollState.takeSuccess()?.setPoll(draft.data.poll)
            contentWarningState.takeSuccess()?.setText(draft.data.spoilerText)
            state.visibilityState.takeSuccess()?.setVisibility(draft.data.visibility)
            languageState.takeSuccess()?.setSelectedLanguages(draft.data.language)
            state.consumeLoadedDraft()
        }
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
    var showEmojiMenu by remember { mutableStateOf(false) }
    var showAccountSelectMenu by remember { mutableStateOf(false) }
    object {
        val remainingLength =
            remainingLength.map {
                it.toString()
            }
        val textFieldState = textFieldState
        val canSend = state.canSend
        val canPoll = canPoll
        val canMedia = canMedia
        val pollState = pollState
        val mediaState = mediaState
        val contentWarningState = contentWarningState
        val state = state
        val showAccountSelectMenu = showAccountSelectMenu
        val languageState = languageState
        val hasTextContent = textFieldState.text.toString().isNotBlank()

        fun selectEmoji(emoji: UiEmoji) {
            textFieldState.edit {
                insert(textFieldState.selection.start, emoji.insertText)
            }
        }

        val showEmojiMenu = showEmojiMenu

        fun setShowEmojiMenu(value: Boolean) {
            showEmojiMenu = value
        }

        fun setShowAccountSelectMenu(value: Boolean) {
            showAccountSelectMenu = value
        }

        fun buildComposeData() =
            ComposeData(
                content = textFieldState.text.toString(),
                medias =
                    mediaState.takeSuccess()?.medias.orEmpty().map {
                        ComposeData.Media(
                            file = FileItem(context, it.uri),
                            altText =
                                it.textState.text
                                    .toString()
                                    .takeIf { it.isNotEmpty() },
                        )
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
                        ?: UiTimelineV2.Post.Visibility.Public,
                referenceStatus =
                    state.composeStatus?.let {
                        ComposeData.ReferenceStatus(
                            composeStatus = it,
                        )
                    },
                language = languageState.takeSuccess()?.selectedLanguage.orEmpty(),
            )

        fun send(onDispatched: () -> Unit = {}) {
            val data = buildComposeData()
            state.send(data) { dispatched ->
                if (dispatched) {
                    onDispatched()
                }
            }
        }

        fun saveDraft(onDispatched: (Boolean) -> Unit) {
            state.saveDraft(buildComposeData(), onDispatched)
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

            fun setText(value: String?) {
                enabled = !value.isNullOrEmpty()
                textFieldState.edit {
                    delete(0, length)
                    value?.let(::append)
                }
            }

            fun clear() {
                enabled = false
                textFieldState.edit {
                    delete(0, length)
                }
            }
        }
    }

@Composable
private fun mediaPresenter(
    config: ComposeConfig.Media,
    initialMedias: ImmutableList<Uri> = persistentListOf(),
) = run {
    var medias by remember {
        mutableStateOf(
            initialMedias.map {
                MediaData(it)
            },
        )
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
        val enableAltText = config.altTextMaxLength > 0
        val altTextMaxLength = config.altTextMaxLength

        fun addMedia(uris: List<Uri>) {
            medias =
                (
                    medias +
                        uris.map {
                            MediaData(it)
                        }
                ).distinctBy {
                    it.uri
                }.takeLast(config.maxCount)
        }

        fun replaceMedias(items: List<dev.dimension.flare.ui.model.UiDraftMedia>) {
            medias =
                items
                    .map { item ->
                        MediaData(
                            uri = Uri.parse(item.cachePath),
                            textState = TextFieldState(item.altText.orEmpty()),
                        )
                    }.distinctBy {
                        it.uri
                    }.takeLast(config.maxCount)
        }

        fun removeMedia(uri: Uri) {
            medias = medias.filterNot { it.uri == uri }
            if (medias.isEmpty()) {
                isMediaSensitive = false
            }
        }

        fun setMediaSensitive(value: Boolean) {
            isMediaSensitive = value
        }

        fun clear() {
            medias = emptyList()
            isMediaSensitive = false
        }
    }
}

@Immutable
private data class MediaData(
    val uri: Uri,
    val textState: TextFieldState = TextFieldState(),
) {
    val url = uri.toString()
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
        var showExpirationMenu by remember {
            mutableStateOf(false)
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
            val showExpirationMenu = showExpirationMenu

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

            fun setShowExpirationMenu(value: Boolean) {
                showExpirationMenu = value
            }

            fun setPoll(value: ComposeData.Poll?) {
                if (value == null) {
                    clear()
                    return
                }
                enabled = true
                options =
                    value.options
                        .ifEmpty { listOf("", "") }
                        .map(::TextFieldState)
                        .let { current ->
                            if (current.size == 1) {
                                current + TextFieldState()
                            } else {
                                current
                            }
                        }.take(config.maxOptions)
                pollSingleChoice = !value.multiple
                expiredAt = PollExpiration.fromDuration(value.expiredAfter)
            }

            fun clear() {
                enabled = false
                options = listOf(TextFieldState(), TextFieldState())
                pollSingleChoice = true
                expiredAt = PollExpiration.Minutes5
                showExpirationMenu = false
            }
        }
    }

@Composable
private fun languageState(config: ComposeConfig.Language) =
    run {
        val allLanguage =
            remember {
                config.sortedIsoCodes
                    .map {
                        @Suppress("DEPRECATION")
                        Locale(it) to it
                    }.toImmutableList()
            }
        var selectedLanguage by remember {
            mutableStateOf(
                listOfNotNull(
                    allLanguage
                        .firstOrNull {
                            it.first.isO3Language == Locale.getDefault().isO3Language
                        }?.second,
                ).toImmutableList(),
            )
        }
        var showLanguagePicker by remember {
            mutableStateOf(false)
        }

        object {
            val allLanguage = allLanguage
            val selectedLanguage = selectedLanguage
            val allowMultiple = config.maxCount > 1
            val canSelect = selectedLanguage.size < config.maxCount

            val selectedLanguageName =
                selectedLanguage.joinToString(" ") { tag ->
                    @Suppress("DEPRECATION")
                    Locale(tag).displayName
                }
            val showLanguagePicker = showLanguagePicker

            fun setShowLanguagePicker(value: Boolean) {
                showLanguagePicker = value
            }

            fun selectLanguage(tag: String) {
                if (config.maxCount == 1) {
                    selectedLanguage = persistentListOf(tag)
                } else if (selectedLanguage.contains(tag) && selectedLanguage.size > 1) {
                    selectedLanguage = (selectedLanguage - tag).toImmutableList()
                } else if (!selectedLanguage.contains(tag) && selectedLanguage.size < config.maxCount) {
                    selectedLanguage = (selectedLanguage + tag).toImmutableList()
                }
            }

            fun setSelectedLanguages(tags: List<String>) {
                selectedLanguage =
                    tags
                        .filter { tag ->
                            allLanguage.any { it.second == tag }
                        }.take(config.maxCount)
                        .toImmutableList()
            }
        }
    }

internal enum class PollExpiration(
    val textId: Int,
    val duration: Duration,
) {
    Minutes5(R.string.compose_poll_expiration_5_minutes, 5.minutes),
    Minutes30(R.string.compose_poll_expiration_30_minutes, 30.minutes),
    Hours1(R.string.compose_poll_expiration_1_hour, 1.hours),
    Hours6(R.string.compose_poll_expiration_6_hours, 6.hours),
    Hours12(R.string.compose_poll_expiration_12_hours, 12.hours),
    Days1(R.string.compose_poll_expiration_1_day, 1.days),
    Days3(R.string.compose_poll_expiration_3_days, 3.days),
    Days7(R.string.compose_poll_expiration_7_days, 7.days),

    ;

    companion object {
        fun fromDuration(durationMillis: Long): PollExpiration =
            entries.minByOrNull { item ->
                kotlin.math.abs(item.duration.inWholeMilliseconds - durationMillis)
            } ?: Minutes5
    }
}

internal val UiTimelineV2.Post.Visibility.localName: Int
    get() =
        when (this) {
            UiTimelineV2.Post.Visibility.Public ->
                R.string.misskey_visibility_public

            UiTimelineV2.Post.Visibility.Home ->
                R.string.misskey_visibility_home

            UiTimelineV2.Post.Visibility.Followers ->
                R.string.misskey_visibility_followers

            UiTimelineV2.Post.Visibility.Specified ->
                R.string.misskey_visibility_specified

            UiTimelineV2.Post.Visibility.Channel ->
                R.string.misskey_visibility_public
        }

internal val UiTimelineV2.Post.Visibility.localDescription: Int
    get() =
        when (this) {
            UiTimelineV2.Post.Visibility.Public ->
                R.string.misskey_visibility_public_description

            UiTimelineV2.Post.Visibility.Home ->
                R.string.misskey_visibility_home_description

            UiTimelineV2.Post.Visibility.Followers ->
                R.string.misskey_visibility_followers_description

            UiTimelineV2.Post.Visibility.Specified ->
                R.string.misskey_visibility_specified_description

            UiTimelineV2.Post.Visibility.Channel ->
                R.string.misskey_visibility_public_description
        }
