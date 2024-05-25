package dev.dimension.flare.ui.screen.compose

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import dev.dimension.flare.R
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.BlueskyComposeData
import dev.dimension.flare.data.datasource.microblog.MastodonComposeData
import dev.dimension.flare.data.datasource.microblog.MisskeyComposeData
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.datasource.microblog.VVOComposeData
import dev.dimension.flare.data.datasource.microblog.XQTComposeData
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.TextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.component.status.mastodon.VisibilityIcon
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.compose.ComposePresenter
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.presenter.compose.MastodonVisibilityState
import dev.dimension.flare.ui.presenter.compose.MisskeyVisibilityState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
fun ReplyRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    replyTo: MicroBlogKey,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        status = ComposeStatus.Reply(replyTo),
        accountType = accountType,
    )
}

@Destination<RootGraph>(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
fun Quote(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    quoted: MicroBlogKey,
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        },
        status = ComposeStatus.Quote(quoted),
        accountType = accountType,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
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
                state.mediaState.addMedia(uris)
            },
        )
    val focusRequester = remember { FocusRequester() }
    val contentWarningFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.contentWarningState) {
        state.contentWarningState.onSuccess { contentWarningState ->
            if (contentWarningState.enabled) {
                contentWarningFocusRequester.requestFocus()
            } else {
                focusRequester.requestFocus()
            }
        }.onError {
            focusRequester.requestFocus()
        }
    }

//    val permissionState =
//        rememberPermissionState(
//            Manifest.permission.POST_NOTIFICATIONS,
//            onPermissionResult = {
//                if (it) {
//                    state.send()
//                    onBack.invoke()
//                }
//            },
//        )
    Column(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape = MaterialTheme.shapes.large,
                )
                .clip(MaterialTheme.shapes.large),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
                Text(text = stringResource(id = R.string.compose_title))
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
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
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            },
        )
        Column(
//            modifier =
//                Modifier
//                    .padding(it),
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
                                            AvatarComponent(it.avatarUrl, size = 24.dp)
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
                                            Icon(Icons.Default.Add, contentDescription = null)
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
                                                                    data.avatarUrl,
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
            AnimatedVisibility(state.mediaState.medias.isNotEmpty()) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding)
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.mediaState.medias.forEach { uri ->
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
                                        state.mediaState.removeMedia(uri)
                                    },
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.3f),
                                                shape = CircleShape,
                                            ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
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
                                    state.mediaState.setMediaSensitive(!state.mediaState.isMediaSensitive)
                                },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.mediaState.isMediaSensitive,
                            onCheckedChange = { state.mediaState.setMediaSensitive(it) },
                            interactionSource = sensitiveInteractionSource,
                        )
                        Text(text = stringResource(id = R.string.compose_media_sensitive))
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
                                Icon(
                                    imageVector = Icons.Default.Add,
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
                    AnimatedVisibility(true) {
                        SharedTransitionLayout {
                            UiStatusQuoted(
                                status = state,
                                onMediaClick = {},
                                colors = CardDefaults.cardColors(),
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
                    .fillMaxWidth(),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier,
                ) {
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
                        Icon(imageVector = Icons.Default.Image, contentDescription = null)
                    }
                    state.pollState.onSuccess {
                        IconButton(
                            onClick = {
                                it.togglePoll()
                            },
                            enabled = state.canPoll,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Poll,
                                contentDescription = null,
                            )
                        }
                    }
                    state.state.visibilityState.onSuccess { visibilityState ->
                        when (visibilityState) {
                            is MastodonVisibilityState ->
                                MastodonVisibilityContent(
                                    visibilityState,
                                )

                            is MisskeyVisibilityState ->
                                MisskeyVisibilityContent(
                                    visibilityState,
                                )
                        }
                    }
                    state.contentWarningState.onSuccess {
                        IconButton(
                            onClick = {
                                it.toggle()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
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
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
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
}

@Composable
private fun MisskeyVisibilityContent(visibilityState: MisskeyVisibilityState) {
    IconButton(
        onClick = {
            visibilityState.showVisibilityMenu()
        },
    ) {
        dev.dimension.flare.ui.component.status.misskey.VisibilityIcon(visibility = visibilityState.visibility)
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
                        dev.dimension.flare.ui.component.status.misskey.VisibilityIcon(visibility = visibility)
                    },
                    contentPadding =
                        PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                )
            }
            DropdownMenuItem(
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = stringResource(id = R.string.misskey_compose_local_only),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(id = R.string.misskey_compose_local_only_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                leadingIcon = {
                    Checkbox(
                        checked = visibilityState.localOnly,
                        onCheckedChange = {
                            visibilityState.setLocalOnly(it)
                        },
                    )
                },
                onClick = {
                    visibilityState.setLocalOnly(!visibilityState.localOnly)
                },
            )
        }
    }
}

@Composable
private fun MastodonVisibilityContent(visibilityState: MastodonVisibilityState) {
    IconButton(
        onClick = {
            visibilityState.showVisibilityMenu()
        },
    ) {
        VisibilityIcon(visibility = visibilityState.visibility)
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
                        VisibilityIcon(visibility = visibility)
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
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
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
    val pollState =
        state.supportedComposeEvent.flatMap {
            if (it.contains(SupportedComposeEvent.Poll)) {
                UiState.Success(pollPresenter())
            } else {
                UiState.Error(IllegalStateException("Poll not supported"))
            }
        }
    val mediaState = mediaPresenter(initialMedias)
    val contentWarningState =
        state.supportedComposeEvent.flatMap {
            if (it.contains(SupportedComposeEvent.ContentWarning)) {
                UiState.Success(contentWarningPresenter())
            } else {
                UiState.Error(IllegalStateException("Content warning not supported"))
            }
        }
    state.replyState?.onSuccess {
        LaunchedEffect(it) {
            if (textFieldState.text.isEmpty()) {
                when (val item = it) {
                    is UiStatus.Mastodon -> {
                        textFieldState.edit {
                            append("${item.user.handle} ")
                        }
                    }

                    is UiStatus.Misskey -> {
//                        textFieldState.edit {
//                            append("${item.user.handle} ")
//                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    val canSend =
        remember(textFieldState.text, state.account) {
            textFieldState.text.isNotBlank() &&
                textFieldState.text.isNotEmpty() &&
                state.account is UiState.Success
        }
    val canPoll =
        remember(mediaState) {
            mediaState.medias.isEmpty()
        }
    val canMedia =
        remember(mediaState, pollState) {
            mediaState.medias.size < 4 && !(pollState is UiState.Success && pollState.data.enabled)
        }
    var showEmojiMenu by remember { mutableStateOf(false) }
    var showAccountSelectMenu by remember { mutableStateOf(false) }
    object {
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

        //        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        fun send() {
            state.selectedAccounts.forEach {
                val data =
                    when (it) {
                        is UiAccount.Mastodon ->
                            MastodonComposeData(
                                content = textFieldState.text.toString(),
                                medias =
                                    mediaState.medias.map {
                                        FileItem(context, it)
                                    },
                                poll =
                                    pollState.takeSuccess()?.takeIf { it.enabled }?.let {
                                        MastodonComposeData.Poll(
                                            multiple = !it.pollSingleChoice,
                                            expiresIn = it.expiredAt.duration.inWholeSeconds,
                                            options =
                                                it.options.map { option ->
                                                    option.text.toString()
                                                },
                                        )
                                    },
                                sensitive = mediaState.isMediaSensitive,
                                spoilerText = contentWarningState.takeSuccess()?.textFieldState?.text?.toString(),
                                visibility =
                                    state.visibilityState
                                        .takeSuccess()
                                        ?.let { it as? MastodonVisibilityState }
                                        ?.visibility
                                        ?: UiStatus.Mastodon.Visibility.Public,
                                inReplyToID = (status as? ComposeStatus.Reply)?.statusKey?.id,
                                account = it,
                            )

                        is UiAccount.Misskey ->
                            MisskeyComposeData(
                                account = it,
                                medias =
                                    mediaState.medias.map {
                                        FileItem(context, it)
                                    },
                                poll =
                                    pollState.takeSuccess()?.takeIf { it.enabled }?.let {
                                        MisskeyComposeData.Poll(
                                            multiple = !it.pollSingleChoice,
                                            expiredAfter = it.expiredAt.duration.inWholeMilliseconds,
                                            options =
                                                it.options.map { option ->
                                                    option.text.toString()
                                                },
                                        )
                                    },
                                sensitive = mediaState.isMediaSensitive,
                                spoilerText = contentWarningState.takeSuccess()?.textFieldState?.text?.toString(),
                                visibility =
                                    state.visibilityState
                                        .takeSuccess()
                                        ?.let { it as? MisskeyVisibilityState }
                                        ?.visibility
                                        ?: UiStatus.Misskey.Visibility.Public,
                                inReplyToID = (status as? ComposeStatus.Reply)?.statusKey?.id,
                                renoteId = (status as? ComposeStatus.Quote)?.statusKey?.id,
                                content = textFieldState.text.toString(),
                                localOnly =
                                    state.visibilityState
                                        .takeSuccess()
                                        ?.let { it as? MisskeyVisibilityState }
                                        ?.localOnly
                                        ?: false,
                            )

                        is UiAccount.Bluesky ->
                            BlueskyComposeData(
                                account = it,
                                medias =
                                    mediaState.medias.map {
                                        FileItem(context, it)
                                    },
                                inReplyToID = (status as? ComposeStatus.Reply)?.statusKey?.id,
                                quoteId = (status as? ComposeStatus.Quote)?.statusKey?.id,
                                content = textFieldState.text.toString(),
                            )

                        is UiAccount.XQT ->
                            XQTComposeData(
                                account = it,
                                medias =
                                    mediaState.medias.map {
                                        FileItem(context, it)
                                    },
                                inReplyToID = (status as? ComposeStatus.Reply)?.statusKey?.id,
                                quoteId = (status as? ComposeStatus.Quote)?.statusKey?.id,
                                quoteUsername =
                                    (status as? ComposeStatus.Quote)?.let {
                                        if (state.replyState is UiState.Success) {
                                            (state.replyState as UiState.Success).data.let {
                                                it as? UiStatus.XQT
                                            }?.user?.rawHandle
                                        } else {
                                            null
                                        }
                                    },
                                content = textFieldState.text.toString(),
                                poll =
                                    pollState.takeSuccess()
                                        ?.takeIf { it.enabled }
                                        ?.let { pollState ->
                                            XQTComposeData.Poll(
                                                multiple = !pollState.pollSingleChoice,
                                                expiredAfter = pollState.expiredAt.duration.inWholeMilliseconds,
                                                options =
                                                    pollState.options.map { option ->
                                                        option.text.toString()
                                                    },
                                            )
                                        },
                                sensitive = mediaState.isMediaSensitive,
                            )

                        is UiAccount.VVo ->
                            VVOComposeData(
                                account = it,
                                medias =
                                    mediaState.medias.map {
                                        FileItem(context, it)
                                    },
                                content = textFieldState.text.toString(),
                            )

                        UiAccount.Guest -> throw IllegalStateException("Guest account cannot compose")
                    }
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
private fun mediaPresenter(initialMedias: ImmutableList<Uri> = persistentListOf()) =
    run {
        var medias by remember {
            mutableStateOf(initialMedias.toList())
        }
        var isMediaSensitive by remember {
            mutableStateOf(false)
        }

        object {
            val medias = medias.toImmutableList()
            val isMediaSensitive = isMediaSensitive

            fun addMedia(uris: List<Uri>) {
                medias = (medias + uris).distinct().takeLast(4)
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
private fun pollPresenter() =
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
                options.size < 4
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

internal enum class PollExpiration(val textId: Int, val duration: Duration) {
    Minutes5(R.string.compose_poll_expiration_5_minutes, 5.minutes),
    Minutes30(R.string.compose_poll_expiration_30_minutes, 30.minutes),
    Hours1(R.string.compose_poll_expiration_1_hour, 1.hours),
    Hours6(R.string.compose_poll_expiration_6_hours, 6.hours),
    Hours12(R.string.compose_poll_expiration_12_hours, 12.hours),
    Days1(R.string.compose_poll_expiration_1_day, 1.days),
    Days3(R.string.compose_poll_expiration_3_days, 3.days),
    Days7(R.string.compose_poll_expiration_7_days, 7.days),
}

internal val UiStatus.Mastodon.Visibility.localName: Int
    get() =
        when (this) {
            UiStatus.Mastodon.Visibility.Public -> dev.dimension.flare.R.string.mastodon_visibility_public
            UiStatus.Mastodon.Visibility.Unlisted -> dev.dimension.flare.R.string.mastodon_visibility_unlisted
            UiStatus.Mastodon.Visibility.Private -> dev.dimension.flare.R.string.mastodon_visibility_private
            UiStatus.Mastodon.Visibility.Direct -> dev.dimension.flare.R.string.mastodon_visibility_direct
        }

internal val UiStatus.Mastodon.Visibility.localDescription: Int
    get() =
        when (this) {
            UiStatus.Mastodon.Visibility.Public -> dev.dimension.flare.R.string.mastodon_visibility_public_description
            UiStatus.Mastodon.Visibility.Unlisted -> dev.dimension.flare.R.string.mastodon_visibility_unlisted_description
            UiStatus.Mastodon.Visibility.Private -> dev.dimension.flare.R.string.mastodon_visibility_private_description
            UiStatus.Mastodon.Visibility.Direct -> dev.dimension.flare.R.string.mastodon_visibility_direct_description
        }

internal val UiStatus.Misskey.Visibility.localName: Int
    get() =
        when (this) {
            UiStatus.Misskey.Visibility.Public -> dev.dimension.flare.R.string.misskey_visibility_public
            UiStatus.Misskey.Visibility.Home -> dev.dimension.flare.R.string.misskey_visibility_home
            UiStatus.Misskey.Visibility.Followers -> dev.dimension.flare.R.string.misskey_visibility_followers
            UiStatus.Misskey.Visibility.Specified -> dev.dimension.flare.R.string.misskey_visibility_specified
        }

internal val UiStatus.Misskey.Visibility.localDescription: Int
    get() =
        when (this) {
            UiStatus.Misskey.Visibility.Public -> dev.dimension.flare.R.string.misskey_visibility_public_description
            UiStatus.Misskey.Visibility.Home -> dev.dimension.flare.R.string.misskey_visibility_home_description
            UiStatus.Misskey.Visibility.Followers -> dev.dimension.flare.R.string.misskey_visibility_followers_description
            UiStatus.Misskey.Visibility.Specified -> dev.dimension.flare.R.string.misskey_visibility_specified_description
        }
