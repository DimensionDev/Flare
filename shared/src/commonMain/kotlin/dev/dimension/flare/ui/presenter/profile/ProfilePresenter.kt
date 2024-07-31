package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

class ProfilePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileState>() {
    @Composable
    override fun body(): ProfileState {
        val scope = rememberCoroutineScope()
        val accountServiceState = accountServiceProvider(accountType = accountType)
        val userState =
            accountServiceState.map { service ->
                remember(service, userKey) {
                    service.userById(userKey?.id ?: service.account.accountKey.id)
                }.collectAsState()
            }

        val listState =
            accountServiceState.map { service ->
                remember(service, userKey) {
                    service.userTimeline(
                        userKey ?: service.account.accountKey,
                        scope = scope,
                    )
                }.collectAsLazyPagingItems()
            }
        val mediaState =
            remember {
                ProfileMediaPresenter(accountType = accountType, userKey = userKey)
            }.body().mediaState
        val relationState =
            accountServiceState.flatMap { service ->
                remember(service, userKey) {
                    service.relation(userKey ?: service.account.accountKey)
                }.collectAsUiState().value.flatMap { it }
            }

//        val scope = koinInject<CoroutineScope>()
        val isMe =
            accountServiceState.map {
                it.account.accountKey == userKey || userKey == null
            }
        val actions =
            accountServiceState.map { service ->
                service.profileActions().toImmutableList().toImmutableListWrapper()
            }
        return object : ProfileState(
            userState = userState.flatMap { it.toUi() },
            listState = listState,
            mediaState = mediaState,
            relationState = relationState,
            isMe = isMe,
            actions = actions,
        ) {
            override suspend fun refresh() {
                userState.onSuccess {
                    it.refresh()
                }
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }

            override fun onProfileActionClick(
                userKey: MicroBlogKey,
                relation: UiRelation,
                action: ProfileAction,
            ) {
                scope.launch {
                    action.invoke(userKey, relation)
                }
            }

            override fun follow(
                userKey: MicroBlogKey,
                data: UiRelation,
            ) {
                scope.launch {
                    accountServiceState.onSuccess { service ->
                        service.follow(userKey, data)
                    }
                }
            }
//
//            override fun block(
//                user: UiUser,
//                data: UiRelation,
//            ) {
//                scope.launch {
//                    accountServiceState.onSuccess { service ->
//                        service.block(user.userKey, data)
//                    }
//                }
//            }
//
//            override fun mute(
//                user: UiUser,
//                data: UiRelation,
//            ) {
//                scope.launch {
//                    accountServiceState.onSuccess { service ->
//                        service.mute(user.userKey, data)
//                    }
//                }
//            }

            override fun report(userKey: MicroBlogKey) {
            }
        }
    }
}

abstract class ProfileState(
    val userState: UiState<UiProfile>,
    val listState: UiState<LazyPagingItems<UiTimeline>>,
    val mediaState: UiState<LazyPagingItems<ProfileMedia>>,
    val relationState: UiState<UiRelation>,
    val isMe: UiState<Boolean>,
    val actions: UiState<ImmutableListWrapper<ProfileAction>>,
) {
    abstract suspend fun refresh()

    abstract fun follow(
        userKey: MicroBlogKey,
        data: UiRelation,
    )
//
//    abstract fun block(
//        user: UiUser,
//        data: UiRelation,
//    )
//
//    abstract fun mute(
//        user: UiUser,
//        data: UiRelation,
//    )

    abstract fun onProfileActionClick(
        userKey: MicroBlogKey,
        relation: UiRelation,
        action: ProfileAction,
    )

    abstract fun report(userKey: MicroBlogKey)
}

class ProfileWithUserNameAndHostPresenter(
    private val userName: String,
    private val host: String,
    private val accountType: AccountType,
) : PresenterBase<UserState>() {
    @Composable
    override fun body(): UserState {
        val userState =
            accountServiceProvider(accountType = accountType).flatMap { service ->
                remember(service) {
                    service.userByAcct("$userName@$host")
                }.collectAsState().toUi()
            }
        return object : UserState {
            override val user: UiState<UiUserV2>
                get() = userState
        }
    }
}
