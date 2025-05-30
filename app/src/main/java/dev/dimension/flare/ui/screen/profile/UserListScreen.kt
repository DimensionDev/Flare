package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.FansPresenter
import dev.dimension.flare.ui.presenter.profile.FollowingPresenter
import dev.dimension.flare.ui.presenter.profile.UserListPresenter
import dev.dimension.flare.ui.screen.settings.AccountItem
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun FollowingScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(
        key = "FollowingScreen$userKey",
    ) {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType, userKey) {
                FollowingPresenter(accountType, userKey)
            }.invoke()
        object : UserListPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }

    UserListScreen(
        data = state.listState,
        title = {
            Text(text = stringResource(R.string.following_title))
        },
        onBack = onBack,
        isRefreshing = state.listState.isRefreshing,
        refresh = state::refresh,
        onUserClick = onUserClick,
    )
}

@Composable
internal fun FansScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(
        key = "FansScreen$userKey",
    ) {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType, userKey) {
                FansPresenter(accountType, userKey)
            }.invoke()
        object : UserListPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }

    UserListScreen(
        data = state.listState,
        title = {
            Text(text = stringResource(R.string.fans_title))
        },
        onBack = onBack,
        isRefreshing = state.listState.isRefreshing,
        refresh = state::refresh,
        onUserClick = onUserClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserListScreen(
    data: PagingState<UiUserV2>,
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    isRefreshing: Boolean,
    refresh: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = title,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = isRefreshing,
            onRefresh = refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    items(
                        data,
                        loadingContent = {
                            AccountItem(
                                userState = UiState.Loading(),
                                onClick = { onUserClick(it) },
                                toLogin = {},
                            )
                        },
                    ) {
                        AccountItem(
                            userState = UiState.Success(it),
                            onClick = { onUserClick(it) },
                            toLogin = {},
                        )
                    }
                }
            },
        )
    }
}
