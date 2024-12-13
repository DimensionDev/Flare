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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
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
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.TextField2
import dev.dimension.flare.ui.component.ThemeWrapper
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
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
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
    FlareTheme {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            ComposeScreen(
                onBack = onBack,
                accountType = AccountType.Active,
                initialText = initialText,
                initialMedias = initialMedias,
            )
        }
    }
}

@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.VVO.ReplyToComment.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
fun VVoReplyCommentRoute(
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey,
    replyTo: MicroBlogKey,
    rootId: String,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        accountType = AccountType.Specific(accountKey = accountKey),
        status = ComposeStatus.VVOComment(replyTo, rootId),
    )
}

@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    wrappers = [ThemeWrapper::class],
)
@Composable
fun ComposeRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        accountType = accountType,
    )
}

@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Compose.Reply.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
fun ReplyRoute(
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        status = ComposeStatus.Reply(statusKey),
        accountType = AccountType.Specific(accountKey = accountKey),
    )
}

@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Compose.Quote.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
fun Quote(
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        status = ComposeStatus.Quote(statusKey),
        accountType = AccountType.Specific(accountKey = accountKey),
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
private fun ComposeScreen(
    onBack: () -> Unit,
    accountType: AccountType,
    modifier: Modifier = Modifier,
    status: ComposeStatus? = null,
    initialText: String = "",
    initialMedias: ImmutableList<Uri> = persistentListOf(),
) {
    val context = LocalContext.current
    val state by producePresenter(key = "compose_$accountType") {
        composePresenter(
            context = context,
            accountType = accountType,
            status = status,
            initialText = initialText,
            initialMedias = initialMedias,
        )
    }
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

    LaunchedEffect(state.contentWarningState) {
        state.contentWarningState
            .onSuccess { contentWarningState ->
                if (contentWarningState.enabled) {
                    contentWarningFocusRequester.requestFocus()
                } else {
                    focusRequester.requestFocus()
                }
            }.onError {
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
                Text(text = stringResource(id = R.string.compose_title))
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        state.send()
                        onBack.invoke()
                    },
                    enabled = state.canSend,
                ) {
                    FAIcon(imageVector = FontAwesomeIcons.Solid.PaperPlane, contentDescription = null)
                }
            },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.state.enableCrossPost.onSuccess { enableCrossPost ->
                if (enableCrossPost) {
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
                                    AssistChip(
                                        onClick = {
                                            state.state.selectAccount(account)
                                        },
                                        label = {
                                            Text(it.handle)
                                        },
                                        leadingIcon = {
                                            AvatarComponent(it.avatar, size = 24.dp)
                                        },
                                        shape = RoundedCornerShape(100),
                                    )
                                }
                            }
                            state.state.otherAccounts.onSuccess { others ->
                                if (others.size > 0) {
                                    AssistChip(
                                        shape = CircleShape,
                                        onClick = {
                                            state.setShowAccountSelectMenu(true)
                                        },
                                        label = {
                                            FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = null)
                                            DropdownMenu(
                                                expanded = state.showAccountSelectMenu,
                                                onDismissRequest = {
                                                    state.setShowAccountSelectMenu(false)
                                                },
                                                properties = PopupProperties(focusable = true),
                                            ) {
                                                for (i in 0 until others.size) {
                                                    val (user, account) = others[i]
                                                    user.onSuccess { data ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(text = data.handle)
                                                            },
                                                            onClick = {
                                                                state.state.selectAccount(account)
                                                            },
                                                            leadingIcon = {
                                                                AvatarComponent(
                                                                    data.avatar,
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
                }
            }
            state.contentWarningState.onSuccess {
                AnimatedVisibility(it.enabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField2(
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
                TextField2(
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
                                        model = uri,
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
                            SingleChoiceSegmentedButtonRow(
                                modifier =
                                    Modifier
                                        .weight(1f),
                            ) {
                                SegmentedButton(
                                    selected = pollState.pollSingleChoice,
                                    onClick = {
                                        pollState.setPollSingleChoice(true)
                                    },
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 0,
                                            count = 2,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.compose_poll_single_choice))
                                }
                                SegmentedButton(
                                    selected = !pollState.pollSingleChoice,
                                    onClick = {
                                        pollState.setPollSingleChoice(false)
                                    },
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 1,
                                            count = 2,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.compose_poll_multiple_choice))
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
                            DropdownMenu(
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
                    val content = state.content
                    if (content is UiTimeline.ItemContent.Status) {
                        Card {
                            QuotedStatus(
                                data = content,
                                modifier =
                                    Modifier
                                        .padding(horizontal = screenHorizontalPadding)
                                        .fillMaxWidth(),
                            )
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
                        DropdownMenu(
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
                                    onDismissRequest = {
                                        state.setShowEmojiMenu(false)
                                    },
                                    offset =
                                        IntOffset(
                                            x = 0,
                                            y =
                                                with(LocalDensity.current) {
                                                    48.dp.roundToPx()
                                                },
                                        ),
                                    properties = PopupProperties(usePlatformDefaultWidth = true),
                                ) {
                                    Card(
                                        modifier =
                                            Modifier.sizeIn(
                                                maxHeight = 256.dp,
                                                maxWidth = 384.dp,
                                            ),
                                        elevation =
                                            CardDefaults.elevatedCardElevation(
                                                defaultElevation = 3.dp,
                                            ),
                                    ) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(36.dp),
                                            contentPadding = PaddingValues(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(emojis.size) { index ->
                                                val emoji = emojis[index]
                                                NetworkImage(
                                                    model = emoji.url,
                                                    contentDescription = emoji.shortcode,
                                                    contentScale = ContentScale.Fit,
                                                    modifier =
                                                        Modifier
                                                            .size(36.dp)
                                                            .clickable {
                                                                state.selectEmoji(emoji)
                                                            },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
    OutlinedTextField2(
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
    accountType: AccountType,
    status: ComposeStatus? = null,
    initialText: String = "",
    initialMedias: ImmutableList<Uri> = persistentListOf(),
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
                mediaPresenter(it, initialMedias)
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
    var showEmojiMenu by remember { mutableStateOf(false) }
    var showAccountSelectMenu by remember { mutableStateOf(false) }
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
        val showAccountSelectMenu = showAccountSelectMenu

        fun selectEmoji(emoji: UiEmoji) {
            textFieldState.edit {
                append(" :${emoji.shortcode}: ")
            }
        }

        val showEmojiMenu = showEmojiMenu

        fun setShowEmojiMenu(value: Boolean) {
            showEmojiMenu = value
        }

        fun setShowAccountSelectMenu(value: Boolean) {
            showAccountSelectMenu = value
        }

        fun send() {
            state.selectedAccounts.forEach {
                val data =
                    ComposeData(
                        content = textFieldState.text.toString(),
                        medias =
                            mediaState.takeSuccess()?.medias.orEmpty().map {
                                FileItem(context, it)
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
private fun mediaPresenter(
    config: ComposeConfig.Media,
    initialMedias: ImmutableList<Uri> = persistentListOf(),
) = run {
    var medias by remember {
        mutableStateOf(initialMedias.toList())
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

        fun addMedia(uris: List<Uri>) {
            medias = (medias + uris).distinct().takeLast(config.maxCount)
        }

        fun removeMedia(uri: Uri) {
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
}

internal val UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.localName: Int
    get() =
        when (this) {
            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
                R.string.misskey_visibility_public

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
                R.string.misskey_visibility_home

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
                R.string.misskey_visibility_followers

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
                R.string.misskey_visibility_specified
        }

internal val UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.localDescription: Int
    get() =
        when (this) {
            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
                R.string.misskey_visibility_public_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
                R.string.misskey_visibility_home_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
                R.string.misskey_visibility_followers_description

            UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
                R.string.misskey_visibility_specified_description
        }
