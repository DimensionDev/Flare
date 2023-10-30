package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.paging.LoadState
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

class ProfilePresenter(
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileState>() {
    @Composable
    override fun body(): ProfileState {
        val userState = activeAccountServicePresenter().map { (service, account) ->
            remember(account.accountKey, userKey) {
                service.userById(userKey?.id ?: account.accountKey.id)
            }.collectAsState()
        }

        val listState = activeAccountServicePresenter().map { (service, account) ->
            remember(account.accountKey, userKey) {
                service.userTimeline(userKey ?: account.accountKey)
            }.collectAsLazyPagingItems()
        }
        val relationState = activeAccountServicePresenter().flatMap { (service, account) ->
            remember(account.accountKey, userKey) {
                service.relation(userKey ?: account.accountKey)
            }.collectAsUiState().value.flatMap { it }
        }

        val refreshing = userState is UiState.Loading ||
                userState is UiState.Success && userState.data.refreshState is dev.dimension.flare.common.LoadState.Loading ||
                listState is UiState.Loading ||
                listState is UiState.Success && listState.data.loadState.refresh is LoadStateLoading

        return object : ProfileState(
            refreshing,
            userState.flatMap { it.toUi() },
            listState,
            relationState,
        ) {
            override fun refresh() {
                userState.onSuccess {
                    it.refresh()
                }
                listState.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}

abstract class ProfileState(
    val refreshing: Boolean,
    val userState: UiState<UiUser>,
    val listState: UiState<LazyPagingItems<UiStatus>>,
    val relationState: UiState<UiRelation>,
) {
    abstract fun refresh()
}

class ProfileWithUserNameAndHostPresenter(
    private val userName: String,
    private val host: String,
) : PresenterBase<UiState<UiUser>>() {
    @Composable
    override fun body(): UiState<UiUser> {

        val userState = activeAccountServicePresenter().flatMap { (service, account) ->
            remember(account.accountKey) {
                service.userByAcct("$userName@$host")
            }.collectAsState().toUi()
        }
        return userState
    }
}