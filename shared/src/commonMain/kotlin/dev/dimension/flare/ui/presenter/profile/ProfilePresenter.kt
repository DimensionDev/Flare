package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class ProfilePresenter(
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileState>() {
    @Composable
    override fun body(): ProfileState {
        val accountServiceState = activeAccountServicePresenter()
        val userState =
            accountServiceState.map { (service, account) ->
                remember(account.accountKey, userKey) {
                    service.userById(userKey?.id ?: account.accountKey.id)
                }.collectAsState()
            }

        val listState =
            accountServiceState.map { (service, account) ->
                remember(account.accountKey, userKey) {
                    service.userTimeline(userKey ?: account.accountKey)
                }.collectPagingProxy()
            }
        val mediaState =
            remember {
                ProfileMediaPresenter(userKey)
            }.body().mediaState
        val relationState =
            accountServiceState.flatMap { (service, account) ->
                remember(account.accountKey, userKey) {
                    service.relation(userKey ?: account.accountKey)
                }.collectAsUiState().value.flatMap { it }
            }

        val scope = koinInject<CoroutineScope>()
        val isMe =
            accountServiceState.map {
                it.second.accountKey == userKey || userKey == null
            }
        return object : ProfileState(
            userState = userState.flatMap { it.toUi() },
            listState = listState,
            mediaState = mediaState,
            relationState = relationState,
            isMe = isMe,
        ) {
            override suspend fun refresh() {
                userState.onSuccess {
                    it.refresh()
                }
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }

            override fun follow(
                user: UiUser,
                data: UiRelation,
            ) {
                scope.launch {
                    accountServiceState.onSuccess { (service, _) ->
                        when (data) {
                            is UiRelation.Bluesky -> blueskyFollow(service as BlueskyDataSource, user.userKey, data)
                            is UiRelation.Mastodon -> mastodonFollow(service as MastodonDataSource, user.userKey, data)
                            is UiRelation.Misskey -> misskeyFollow(service as MisskeyDataSource, user.userKey, data)
                            is UiRelation.XQT -> xqtFollow(service as XQTDataSource, user.userKey, data)
                        }
                    }
                }
            }

            override fun block(
                user: UiUser,
                data: UiRelation,
            ) {
                scope.launch {
                    accountServiceState.onSuccess { (service, _) ->
                        when (data) {
                            is UiRelation.Bluesky -> {
                                require(service is BlueskyDataSource)
                                if (data.blocking) service.unblock(user.userKey) else service.block(user.userKey)
                            }
                            is UiRelation.Mastodon -> {
                                require(service is MastodonDataSource)
                                if (data.blocking) service.unblock(user.userKey) else service.block(user.userKey)
                            }
                            is UiRelation.Misskey -> {
                                require(service is MisskeyDataSource)
                                if (data.blocking) service.unblock(user.userKey) else service.block(user.userKey)
                            }

                            is UiRelation.XQT -> {
                                require(service is XQTDataSource)
                                if (data.blocking) service.unblock(user.userKey) else service.block(user.userKey)
                            }
                        }
                    }
                }
            }

            override fun mute(
                user: UiUser,
                data: UiRelation,
            ) {
                scope.launch {
                    accountServiceState.onSuccess { (service, _) ->
                        when (data) {
                            is UiRelation.Bluesky -> {
                                require(service is BlueskyDataSource)
                                if (data.muting) service.unmute(user.userKey) else service.mute(user.userKey)
                            }
                            is UiRelation.Mastodon -> {
                                require(service is MastodonDataSource)
                                if (data.muting) service.unmute(user.userKey) else service.mute(user.userKey)
                            }
                            is UiRelation.Misskey -> {
                                require(service is MisskeyDataSource)
                                if (data.muted) service.unmute(user.userKey) else service.mute(user.userKey)
                            }

                            is UiRelation.XQT -> {
                                require(service is XQTDataSource)
                                if (data.muting) service.unmute(user.userKey) else service.mute(user.userKey)
                            }
                        }
                    }
                }
            }

            override fun report(user: UiUser) {
            }
        }
    }

    private suspend fun misskeyFollow(
        misskeyDataSource: MisskeyDataSource,
        userKey: MicroBlogKey,
        data: UiRelation.Misskey,
    ) {
        when {
            data.following -> misskeyDataSource.unfollow(userKey)
            data.blocking -> misskeyDataSource.unblock(userKey)
            data.hasPendingFollowRequestFromYou -> Unit // TODO: cancel follow request
            else -> misskeyDataSource.follow(userKey)
        }
    }

    private suspend fun mastodonFollow(
        mastodonDataSource: MastodonDataSource,
        userKey: MicroBlogKey,
        data: UiRelation.Mastodon,
    ) {
        when {
            data.following -> mastodonDataSource.unfollow(userKey)
            data.blocking -> mastodonDataSource.unblock(userKey)
            data.requested -> Unit // you can't cancel follow request on mastodon
            else -> mastodonDataSource.follow(userKey)
        }
    }

    private suspend fun blueskyFollow(
        service: BlueskyDataSource,
        userKey: MicroBlogKey,
        data: UiRelation.Bluesky,
    ) {
        when {
            data.following -> service.unfollow(userKey)
            data.blocking -> service.unblock(userKey)
            else -> service.follow(userKey)
        }
    }

    private suspend fun xqtFollow(
        service: XQTDataSource,
        userKey: MicroBlogKey,
        data: UiRelation.XQT,
    ) {
        when {
            data.following -> service.unfollow(userKey)
            data.blocking -> service.unblock(userKey)
            else -> service.follow(userKey)
        }
    }
}

abstract class ProfileState(
    val userState: UiState<UiUser>,
    val listState: UiState<LazyPagingItemsProxy<UiStatus>>,
    val mediaState: UiState<LazyPagingItemsProxy<UiMedia>>,
    val relationState: UiState<UiRelation>,
    val isMe: UiState<Boolean>,
) {
    abstract suspend fun refresh()

    abstract fun follow(
        user: UiUser,
        data: UiRelation,
    )

    abstract fun block(
        user: UiUser,
        data: UiRelation,
    )

    abstract fun mute(
        user: UiUser,
        data: UiRelation,
    )

    abstract fun report(user: UiUser)
}

class ProfileWithUserNameAndHostPresenter(
    private val userName: String,
    private val host: String,
) : PresenterBase<UiState<UiUser>>() {
    @Composable
    override fun body(): UiState<UiUser> {
        val userState =
            activeAccountServicePresenter().flatMap { (service, account) ->
                remember(account.accountKey) {
                    service.userByAcct("$userName@$host")
                }.collectAsState().toUi()
            }
        return userState
    }
}
