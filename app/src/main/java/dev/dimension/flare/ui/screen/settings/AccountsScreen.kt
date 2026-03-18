package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.FaceSadTear
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.ThemeIconData
import dev.dimension.flare.ui.component.ThemedIcon
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.isError
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsState.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    toLogin: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        accountsPresenter()
    }
    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderableLazyColumnState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            state.moveItem(from.key, to.key)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_accounts_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = {
                            toLogin.invoke()
                        },
                    ) {
                        FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            state = lazyListState,
        ) {
            itemsIndexed(state.currentItems, key = { _, item -> item.account.accountKey.toString() }) { index, (account, data) ->
                val swipeState =
                    rememberSwipeToDismissBoxState()
                val shape = ListItemDefaults.segmentedShapes2(index, state.currentItems.size)
                var showMenu by remember { mutableStateOf(false) }
                val isSwiping =
                    swipeState.dismissDirection != SwipeToDismissBoxValue.Settled
                LaunchedEffect(swipeState.settledValue) {
                    if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
                        delay(AnimationConstants.DefaultDurationMillis.toLong())
                        state.deleteItem(account.accountKey)
                    }
                }
                ReorderableItem(
                    reorderableLazyColumnState,
                    key = account.accountKey.toString(),
                ) { isDragging ->
                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundContent = {
                            if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = shape.draggedShape,
                                            ).padding(16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.settings_accounts_remove),
                                        color = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = data.isSuccess || data.isError,
                    ) {
                        AccountItem(
                            selected = isSwiping || isDragging,
                            userState = data,
                            shapes = shape,
                            onClick = {
                                state.setActiveAccount(it)
                            },
                            onLongClick = {
                                showMenu = true
                            },
                            toLogin = toLogin,
                            trailingContent = { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.activeAccount.onSuccess {
                                        RadioButton(
                                            selected = it.accountKey == user.key,
                                            onClick = {
                                                state.setActiveAccount(user.key)
                                            },
                                        )
                                    }

                                    IconButton(
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.Confirm,
                                                    )
                                                },
                                                onDragStopped = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.Confirm,
                                                    )
                                                },
                                            ),
                                        onClick = {},
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Bars,
                                            contentDescription = stringResource(id = R.string.tab_settings_drag),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            showMenu = true
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.EllipsisVertical,
                                            contentDescription = stringResource(id = R.string.more),
                                        )
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = {
                                                showMenu = false
                                            },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(id = R.string.settings_accounts_remove),
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    state.deleteItem(account.accountKey)
                                                },
                                                leadingIcon = {
                                                    FAIcon(
                                                        imageVector = FontAwesomeIcons.Solid.Trash,
                                                        contentDescription =
                                                            stringResource(
                                                                id = R.string.settings_accounts_remove,
                                                            ),
                                                        tint = MaterialTheme.colorScheme.error,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountItem(
    userState: UiState<UiProfile>,
    onClick: (MicroBlogKey) -> Unit,
    toLogin: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (UiProfile) -> Unit = { },
    headlineContent: @Composable (UiProfile) -> Unit = {
        RichText(text = it.name, maxLines = 1)
    },
    supportingContent: @Composable (UiProfile) -> Unit = {
        Text(text = it.handle.canonical, maxLines = 1)
    },
    avatarSize: Dp = AvatarComponentDefaults.size,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    userState
        .onSuccess { data ->
            SegmentedListItem(
                selected = selected,
                elevation = elevation,
                modifier = modifier,
                onClick = {
                    onClick.invoke(data.key)
                },
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                shapes = shapes,
                content = {
                    headlineContent.invoke(data)
                },
//                modifier =
//                    modifier
//                        .clickable {
//                            onClick.invoke(data.key)
//                        },
                leadingContent = {
                    AvatarComponent(data = data.avatar, size = avatarSize)
                },
                trailingContent = {
                    trailingContent.invoke(data)
                },
                supportingContent = {
                    supportingContent.invoke(data)
                },
                colors = colors,
            )
        }.onLoading {
            SegmentedListItem(
                selected = selected,
                onClick = {},
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                elevation = elevation,
                shapes = shapes,
                content = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
                modifier = modifier,
                leadingContent = {
                    AvatarComponent(
                        data = null,
                        modifier = Modifier.placeholder(true, shape = CircleShape),
                        size = avatarSize,
                    )
                },
                supportingContent = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
                colors = colors,
            )
        }.onError { throwable ->
            SegmentedListItem(
                selected = selected,
                onClick = {},
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                elevation = elevation,
                shapes = shapes,
                content = {
                    if (throwable is LoginExpiredException) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.login_expired,
                                    throwable.accountKey.toString(),
                                ),
                        )
                    } else {
                        Text(text = stringResource(id = R.string.account_item_error_title))
                    }
                },
                modifier = modifier,
                leadingContent = {
                    ThemedIcon(
                        FontAwesomeIcons.Solid.FaceSadTear,
                        contentDescription = stringResource(id = R.string.account_item_error_title),
                        color = ThemeIconData.Color.ImperialMagenta,
                        size = avatarSize,
                    )
                },
                supportingContent = {
                    if (throwable is LoginExpiredException) {
                        Text(text = throwable.accountKey.toString())
                    } else {
                        Text(text = stringResource(id = R.string.account_item_error_message))
                    }
                },
                trailingContent =
                    if (throwable is LoginExpiredException) {
                        {
                            TextButton(onClick = toLogin) {
                                Text(text = stringResource(id = R.string.login_expired_relogin))
                            }
                        }
                    } else {
                        null
                    },
                colors = colors,
            )
        }
}

@Composable
private fun accountsPresenter() =
    run {
        val cacheItems =
            remember {
                mutableStateListOf<AccountItem>()
            }

        val state =
            remember {
                AccountManagementPresenter()
            }.invoke()

        state.accounts.onSuccess {
            LaunchedEffect(it) {
                cacheItems.clear()
                cacheItems.addAll(it)
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { cacheItems.toList() }
                .collect {
                    state.setOrder(cacheItems.map { it.account.accountKey })
                }
        }

        object : AccountManagementPresenter.State by state {
            val currentItems = cacheItems

            fun moveItem(
                from: Any,
                to: Any,
            ) {
                val fromKey = MicroBlogKey.valueOf(from.toString())
                val toKey = MicroBlogKey.valueOf(to.toString())
                val fromIndex = cacheItems.indexOfFirst { it.account.accountKey == fromKey }
                val toIndex = cacheItems.indexOfFirst { it.account.accountKey == toKey }
                if (fromIndex == -1 || toIndex == -1) return
                cacheItems.add(toIndex, cacheItems.removeAt(fromIndex))
            }

            fun deleteItem(accountKey: MicroBlogKey) {
                cacheItems.removeIf { it.account.accountKey == accountKey }
//                state.updateOrder(cacheItems.map { it.account.accountKey })
                state.logout(accountKey)
            }
        }
    }
