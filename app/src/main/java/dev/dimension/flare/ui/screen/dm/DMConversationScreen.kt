package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowRightFromBracket
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.PaperPlane
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDividerDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.QuotedStatus
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.dm.DMConversationPresenter
import dev.dimension.flare.ui.presenter.dm.DMConversationState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.NavigationState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun DMConversationScreen(
    accountType: AccountType,
    roomKey: MicroBlogKey,
    onBack: () -> Unit,
    navigationState: NavigationState,
    toProfile: (MicroBlogKey) -> Unit,
) {
    DisposableEffect(Unit) {
        navigationState.disableBottomBarAutoHide()
        navigationState.hideBottomBarDivider()
        onDispose {
            navigationState.enableBottomBarAutoHide()
            navigationState.showBottomBarDivider()
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val state by producePresenter(
        key = "dm_conversation_${accountType}_$roomKey",
    ) {
        presenter(
            accountType = accountType,
            roomKey = roomKey,
        )
    }

    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.users
                        .onSuccess {
                            if (it.size == 1) {
                                RichText(
                                    text = it.first().name,
                                    maxLines = 1,
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.dm_conversation),
                                )
                            }
                        }.onError {
                            Text(it.message.toString())
                        }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = {
                            state.setShowDropdown(!state.showDropdown)
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                    DropdownMenu(
                        expanded = state.showDropdown,
                        onDismissRequest = {
                            state.setShowDropdown(false)
                        },
                    ) {
                        state.users.onSuccess {
                            if (it.size == 1) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.dm_to_profile),
                                        )
                                    },
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.CircleUser,
                                            contentDescription = stringResource(R.string.dm_to_profile),
                                        )
                                    },
                                    onClick = {
                                        state.setShowDropdown(false)
                                        toProfile.invoke(it.first().key)
                                    },
                                )
                            }
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.dm_leave),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowRightFromBracket,
                                    contentDescription = stringResource(R.string.dm_leave),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                state.setShowDropdown(false)
                                state.leave()
                                onBack()
                            },
                        )
                    }
                },
            )
        },
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
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .padding(
                                    bottom = LocalBottomBarHeight.current,
                                ).windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ).consumeWindowInsets(
                                    PaddingValues(
                                        bottom = LocalBottomBarHeight.current,
                                    ),
                                ).imePadding()
                                .fillMaxWidth()
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                    vertical = 8.dp,
                                ).focusRequester(
                                    focusRequester = focusRequester,
                                ),
                        state = state.text,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    state.send()
                                },
                                enabled = state.canSend,
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.PaperPlane,
                                    contentDescription = stringResource(id = R.string.send),
                                )
                            }
                        },
                        shape = RoundedCornerShape(100),
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.dm_send_placeholder),
                            )
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        val listState = rememberLazyListState()
        state.items.onSuccess {
            if (listState.firstVisibleItemIndex == 0) {
                LaunchedEffect(itemCount) {
                    listState.scrollToItem(0)
                }
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = contentPadding,
            modifier =
                Modifier
                    .consumeWindowInsets(contentPadding)
                    .fillMaxSize()
                    .imePadding()
                    .imeNestedScroll(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(
                state.items,
                key = {
                    get(it)?.id ?: it
                },
//                emptyContent = {
//
//                },
//                errorContent = {
//
//                },
//                loadingContent = {
//
//                },
                itemContent = { item ->
                    DMItem(
                        item = item,
                        onRetry = {
                            state.retry(item.key)
                        },
                        modifier =
                            Modifier
                                .animateItem()
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                ),
                        onUserClicked = {
                            toProfile.invoke(it.key)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun DMItem(
    item: UiDMItem,
    onRetry: () -> Unit,
    onUserClicked: (UiUserV2) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment =
            if (item.isFromMe) {
                Alignment.End
            } else {
                Alignment.Start
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f),
            contentAlignment =
                if (item.isFromMe) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.showSender) {
                    AvatarComponent(
                        data = item.user.avatar,
                        modifier =
                            Modifier.clickable {
                                onUserClicked.invoke(item.user)
                            },
                    )
                }
                if (item.sendState == UiDMItem.SendState.Failed) {
                    IconButton(
                        onClick = onRetry,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleExclamation,
                            contentDescription = stringResource(id = R.string.send),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                when (val message = item.content) {
                    is UiDMItem.Message.Text ->
                        RichText(
                            text = message.text,
                            modifier =
                                Modifier
                                    .background(
                                        color =
                                            if (item.isFromMe) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceDim
                                            },
                                        shape =
                                            MaterialTheme.shapes.large.let {
                                                if (item.isFromMe) {
                                                    it.copy(
                                                        bottomEnd = CornerSize(0.dp),
                                                    )
                                                } else {
                                                    it.copy(
                                                        bottomStart = CornerSize(0.dp),
                                                    )
                                                }
                                            },
                                    ).padding(
                                        vertical = 8.dp,
                                        horizontal = 16.dp,
                                    ),
                            color =
                                if (item.isFromMe) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )

                    UiDMItem.Message.Deleted ->
                        Text(
                            text = stringResource(id = R.string.dm_deleted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                    is UiDMItem.Message.Media ->
                        MediaItem(
                            media = message.media,
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable {
                                        if (message.media is UiMedia.Image) {
                                            uriHandler.openUri(AppDeepLink.RawImage.invoke(message.media.url))
                                        }
                                    },
                        )

                    is UiDMItem.Message.Status ->
                        QuotedStatus(
                            message.status,
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(
                                        color =
                                            if (item.isFromMe) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceDim
                                            },
                                        shape = MaterialTheme.shapes.large,
                                    ),
                        )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.showSender) {
                Spacer(modifier = Modifier.width(AvatarComponentDefaults.size))
                RichText(
                    text = item.user.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textStyle = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sendState == UiDMItem.SendState.Sending) {
                Text(
                    text = stringResource(id = R.string.dm_sending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (item.sendState == null || item.sendState != UiDMItem.SendState.Failed) {
                Text(
                    item.timestamp.shortTime.localizedShortTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    roomKey: MicroBlogKey,
) = run {
    val text = rememberTextFieldState()
    var showDropdown by remember { mutableStateOf(false) }
    val state =
        remember(
            accountType,
            roomKey,
        ) {
            DMConversationPresenter(
                accountType = accountType,
                roomKey = roomKey,
            )
        }.invoke()

    object : DMConversationState by state {
        val text = text
        val canSend = text.text.isNotEmpty()
        val showDropdown = showDropdown

        fun setShowDropdown(show: Boolean) {
            showDropdown = show
        }

        fun send() {
            send(text.text.toString())
            text.clearText()
        }
    }
}
