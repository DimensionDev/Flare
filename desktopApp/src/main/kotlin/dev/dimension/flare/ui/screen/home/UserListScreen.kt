package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.FansPresenter
import dev.dimension.flare.ui.presenter.profile.FollowingPresenter
import dev.dimension.flare.ui.presenter.profile.UserListPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun FollowingScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
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
        onUserClick = onUserClick,
    )
}

@Composable
internal fun FansScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
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
        onUserClick = onUserClick,
    )
}

@Composable
private fun UserListScreen(
    data: PagingState<UiUserV2>,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    LazyColumn(
        contentPadding = LocalWindowPadding.current,
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            data,
            loadingContent = { index, itemCount ->
                AccountItem(
                    userState = UiState.Loading(),
                    onClick = { onUserClick(it) },
                    toLogin = {},
                )
            },
        ) { index, itemCount, it ->
            AccountItem(
                userState = UiState.Success(it),
                onClick = { onUserClick(it) },
                toLogin = {},
            )
        }
    }
}
