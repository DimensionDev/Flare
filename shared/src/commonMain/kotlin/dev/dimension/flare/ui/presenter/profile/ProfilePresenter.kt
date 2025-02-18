package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.flatten
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
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
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.status.LogUserHistoryPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ProfilePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): ProfileState {
        val scope = rememberCoroutineScope()
        val accountServiceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val userState =
            accountServiceState.map { service ->
                val userId =
                    userKey?.id
                        ?: if (service is AuthenticatedMicroblogDataSource) {
                            service.accountKey.id
                        } else {
                            null
                        }
                if (userId == null) {
                    throw NoActiveAccountException
                } else {
                    remember(service, userKey) {
                        service.userById(userId)
                    }.collectAsState()
                }
            }
        accountServiceState.onSuccess {
            val userKey = userKey ?: if (it is AuthenticatedMicroblogDataSource) it.accountKey else null
            if (userKey != null) {
                remember { LogUserHistoryPresenter(accountType, userKey) }.body()
            }
        }

        val mediaState =
            remember {
                ProfileMediaPresenter(accountType = accountType, userKey = userKey)
            }.body().mediaState
        val relationState =
            accountServiceState.flatMap { service ->
                require(service is AuthenticatedMicroblogDataSource)
                val actualUserKey = userKey ?: service.accountKey
                remember(service, userKey) {
                    service.relation(actualUserKey)
                }.collectAsUiState().value.flatMap { it }
            }

        val isMe =
            accountServiceState.map {
                if (it is AuthenticatedMicroblogDataSource) {
                    it.accountKey == userKey || userKey == null
                } else {
                    false
                }
            }
        val actions =
            accountServiceState.map { service ->
                require(service is AuthenticatedMicroblogDataSource)
                service.profileActions().toImmutableList().toImmutableListWrapper()
            }
        val myAccountKey =
            accountServiceState.mapNotNull {
                if (it is AuthenticatedMicroblogDataSource) {
                    it.accountKey
                } else {
                    null
                }
            }
        val canSendMessage =
            remember(accountServiceState, userKey) {
                accountServiceState.map {
                    if (it is DirectMessageDataSource && userKey != null) {
                        flow<Boolean> {
                            runCatching {
                                it.canSendDirectMessage(userKey)
                            }.getOrElse {
                                false
                            }.let {
                                emit(it)
                            }
                        }
                    } else {
                        flow<Boolean> { emit(false) }
                    }
                }
            }.flatMap { it.collectAsUiState().value }

        val tabs =
            accountServiceState.map { service ->
                val actualUserKey =
                    userKey
                        ?: if (service is AuthenticatedMicroblogDataSource) {
                            service.accountKey
                        } else {
                            null
                        } ?: throw NoActiveAccountException
                remember(service, userKey) {
                    service.profileTabs(actualUserKey, scope)
                }.map {
                    when (it) {
                        is ProfileTab.Media ->
                            ProfileState.Tab.Media(
                                data = mediaState,
                            )
                        is ProfileTab.Timeline -> {
                            ProfileState.Tab.Timeline(
                                type = it.type,
                                data = it.flow.collectAsLazyPagingItems().toPagingState(),
                            )
                        }
                    }
                }.let {
                    remember(it) {
                        it.toImmutableList().toImmutableListWrapper()
                    }
                }
            }

        val listState =
            remember(tabs) {
                tabs
                    .map {
                        it
                            .toImmutableList()
                            .filterIsInstance<ProfileState.Tab.Timeline>()
                            .first()
                            .data
                    }.flatten()
            }
        return object : ProfileState(
            userState = userState.flatMap { it.toUi() },
            listState = listState,
            mediaState = mediaState,
            relationState = relationState,
            isMe = isMe,
            actions = actions,
            isGuestMode = accountType == AccountType.Guest,
            isListDataSource =
                accountServiceState.map {
                    it is ListDataSource
                },
            myAccountKey = myAccountKey,
            canSendMessage = canSendMessage,
            tabs = tabs,
        ) {
            override suspend fun refresh() {
                userState.onSuccess {
                    it.refresh()
                }
                listState.onSuccess {
                    refreshSuspend()
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
                        if (service is AuthenticatedMicroblogDataSource) {
                            service.follow(userKey, data)
                        }
                    }
                }
            }

            override fun report(userKey: MicroBlogKey) {
            }
        }
    }
}

@Immutable
public abstract class ProfileState(
    public val userState: UiState<UiProfile>,
    public val listState: PagingState<UiTimeline>,
    public val mediaState: PagingState<ProfileMedia>,
    public val relationState: UiState<UiRelation>,
    public val isMe: UiState<Boolean>,
    public val actions: UiState<ImmutableListWrapper<ProfileAction>>,
    public val isGuestMode: Boolean,
    public val isListDataSource: UiState<Boolean>,
    public val myAccountKey: UiState<MicroBlogKey>,
    public val canSendMessage: UiState<Boolean>,
    public val tabs: UiState<ImmutableListWrapper<Tab>>,
) {
    public abstract suspend fun refresh()

    public abstract fun follow(
        userKey: MicroBlogKey,
        data: UiRelation,
    )

    public abstract fun onProfileActionClick(
        userKey: MicroBlogKey,
        relation: UiRelation,
        action: ProfileAction,
    )

    public abstract fun report(userKey: MicroBlogKey)

    @Immutable
    public sealed interface Tab {
        @Immutable
        public data class Timeline internal constructor(
            val type: ProfileTab.Timeline.Type,
            val data: PagingState<UiTimeline>,
        ) : Tab

        @Immutable
        public data class Media internal constructor(
            val data: PagingState<ProfileMedia>,
        ) : Tab
    }
}

public class ProfileWithUserNameAndHostPresenter(
    private val userName: String,
    private val host: String,
    private val accountType: AccountType,
) : PresenterBase<UserState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): UserState {
        val userState =
            accountServiceProvider(accountType = accountType, repository = accountRepository).flatMap { service ->
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
