package dev.dimension.flare.ui.screen.dm

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ProfileRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.List
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.presenter.dm.DMListPresenter
import dev.dimension.flare.ui.presenter.dm.DMListState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.NavigationState
import dev.dimension.flare.ui.screen.list.ItemPlaceHolder
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun DMScreenRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    navigationState: NavigationState,
    initialUserKey: MicroBlogKey? = null,
) {
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator<DMPaneNavArgs>()
    if (initialUserKey != null) {
        LaunchedEffect(initialUserKey) {
            scaffoldNavigator.navigateTo(
                ListDetailPaneScaffoldRole.Detail,
                DMPaneNavArgs(initialUserKey.toString(), isUserKey = true),
            )
        }
    } else {
        BackHandler(
            scaffoldNavigator.canNavigateBack(),
        ) {
            scaffoldNavigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                DMListScreen(
                    accountType = accountType,
                    onItemClicked = { key ->
                        scaffoldNavigator.navigateTo(
                            ListDetailPaneScaffoldRole.Detail,
                            DMPaneNavArgs(key.toString(), isUserKey = false),
                        )
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.content?.let { args ->
                    if (args.isUserKey) {
                        UserDMConversationScreen(
                            accountType = accountType,
                            userKey = MicroBlogKey.valueOf(args.key),
                            onBack = scaffoldNavigator::navigateBack,
                            navigationState = navigationState,
                            toProfile = {
                                navigator.navigate(ProfileRouteDestination(userKey = it, accountType = accountType))
                            },
                        )
                    } else {
                        DMConversationScreen(
                            accountType = accountType,
                            roomKey = MicroBlogKey.valueOf(args.key),
                            onBack = scaffoldNavigator::navigateBack,
                            navigationState = navigationState,
                            toProfile = {
                                navigator.navigate(ProfileRouteDestination(userKey = it, accountType = accountType))
                            },
                        )
                    }
                }
            }
        },
    )
}

@Parcelize
private data class DMPaneNavArgs(
    val key: String,
    val isUserKey: Boolean,
) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DMListScreen(
    accountType: AccountType,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("dm_list_$accountType") {
        presenter(accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.dm_list_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    items(
                        state.items,
                        emptyContent = {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.List,
                                        contentDescription = stringResource(id = R.string.dm_list_empty),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = stringResource(id = R.string.dm_list_empty),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                        loadingContent = {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ItemPlaceHolder()
                                HorizontalDivider()
                            }
                        },
                        errorContent = {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                                        contentDescription = stringResource(id = R.string.dm_list_error),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = stringResource(id = R.string.dm_list_error),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    Text(
                                        text = it.message.orEmpty(),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                        itemContent = { item ->
                            Column(
                                modifier =
                                    Modifier
                                        .clickable {
                                            onItemClicked.invoke(item.key)
                                        },
                            ) {
                                ListComponent(
                                    headlineContent = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val user = item.user
                                            if (user != null) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    HtmlText(
                                                        element = user.name.data,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        text = user.handle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier =
                                                            Modifier
                                                                .alpha(MediumAlpha),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            val lastMessage = item.lastMessage
                                            if (lastMessage != null) {
                                                Text(
                                                    text = lastMessage.timestamp.shortTime,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier =
                                                        Modifier
                                                            .alpha(MediumAlpha),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingContent = {
                                        val avatar = item.user?.avatar
                                        if (avatar.isNullOrEmpty()) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.CircleUser,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier
                                                        .size(AvatarComponentDefaults.size),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            AvatarComponent(avatar)
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            text = item.lastMessageText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        if (item.unreadCount > 0) {
                                            Badge {
                                                Text(
                                                    text = item.unreadCount.toString(),
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .padding(
                                                horizontal = screenHorizontalPadding,
                                                vertical = 8.dp,
                                            ),
                                )
                                HorizontalDivider()
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType) {
                DMListPresenter(accountType)
            }.invoke()
        object : DMListState by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
