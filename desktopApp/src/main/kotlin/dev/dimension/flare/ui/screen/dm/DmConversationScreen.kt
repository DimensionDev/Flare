package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowRightFromBracket
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.PaperPlane
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.dm_conversation
import dev.dimension.flare.dm_leave
import dev.dimension.flare.dm_send_placeholder
import dev.dimension.flare.dm_to_profile
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.more
import dev.dimension.flare.send
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.dm.DMItem
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.dm.DMConversationPresenter
import dev.dimension.flare.ui.presenter.dm.DMConversationState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Scrollbar
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
fun DmConversationScreen(
    accountType: AccountType,
    roomKey: MicroBlogKey,
    onBack: () -> Unit,
    toProfile: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(
        key = "dm_conversation_${accountType}_$roomKey",
    ) {
        presenter(
            accountType = accountType,
            roomKey = roomKey,
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val listState = rememberLazyListState()
    val scrollbarAdapter = rememberScrollbarAdapter(listState)
    state.items.onSuccess {
        if (listState.firstVisibleItemIndex == 0) {
            LaunchedEffect(itemCount) {
                listState.scrollToItem(0)
            }
        }
    }

    Column {
        Row(
            modifier =
                Modifier
                    .background(FluentTheme.colors.background.card.default)
                    .fillMaxWidth()
                    .padding(start = 40.dp)
                    .padding(LocalWindowPadding.current + PaddingValues(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.users
                .onSuccess {
                    if (it.size == 1) {
                        RichText(
                            text = it.first().name,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.dm_conversation),
                        )
                    }
                }.onError {
                    Text(it.message.toString())
                }
            Spacer(modifier = Modifier.weight(1f))
            MenuFlyoutContainer(
                flyout = {
                    state.users.onSuccess {
                        if (it.size == 1) {
                            MenuFlyoutItem(
                                text = {
                                    Text(
                                        text = stringResource(Res.string.dm_to_profile),
                                    )
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.CircleUser,
                                        contentDescription = stringResource(Res.string.dm_to_profile),
                                    )
                                },
                                onClick = {
                                    isFlyoutVisible = false
                                    toProfile.invoke(it.first().key)
                                },
                            )
                        }
                    }

                    MenuFlyoutItem(
                        text = {
                            Text(
                                text = stringResource(Res.string.dm_leave),
                                color = FluentTheme.colors.system.critical,
                            )
                        },
                        icon = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowRightFromBracket,
                                contentDescription = stringResource(Res.string.dm_leave),
                                tint = FluentTheme.colors.system.critical,
                            )
                        },
                        onClick = {
                            isFlyoutVisible = false
                            state.leave()
                            onBack()
                        },
                    )
                },
            ) {
                SubtleButton(
                    onClick = { isFlyoutVisible = true },
                    iconOnly = true,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                        contentDescription = stringResource(Res.string.more),
                    )
                }
            }
        }
        ScrollbarContainer(
            adapter = scrollbarAdapter,
            scrollbar = {
                Scrollbar(true, scrollbarAdapter, reverseLayout = true)
            },
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(top = 8.dp),
                modifier =
                    Modifier
                        .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            ) {
                stickyHeader {
                    TextField(
                        state = state.text,
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        trailing = {
                            SubtleButton(
                                onClick = {
                                    state.send()
                                },
                                disabled = !state.canSend,
                                iconOnly = true,
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.PaperPlane,
                                    contentDescription = stringResource(Res.string.send),
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.dm_send_placeholder),
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Send,
                            ),
                        onKeyboardAction = {
                            if (state.canSend) {
                                state.send()
                            }
                        },
                    )
                }
                items(
                    state.items,
                    key = {
                        get(it)?.id ?: it
                    },
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
}

@Composable
private fun presenter(
    accountType: AccountType,
    roomKey: MicroBlogKey,
) = run {
    val text = rememberTextFieldState()
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

        fun send() {
            send(text.text.toString())
            text.clearText()
        }
    }
}
