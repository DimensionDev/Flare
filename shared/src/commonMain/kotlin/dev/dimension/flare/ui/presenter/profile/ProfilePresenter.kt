package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceFlow
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
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.status.LogUserHistoryPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class ProfilePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val userStateFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            val userId =
                userKey?.id
                    ?: if (service is AuthenticatedMicroblogDataSource) {
                        service.accountKey.id
                    } else {
                        throw NoActiveAccountException
                    }
            service.userById(userId).toUi()
        }
    }

    private val relationStateFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            require(service is AuthenticatedMicroblogDataSource)
            val actualUserKey = userKey ?: service.accountKey
            service.relation(actualUserKey)
        }
    }

    private val isMeFlow by lazy {
        accountServiceFlow(accountType, accountRepository).map { service ->
            if (service is AuthenticatedMicroblogDataSource) {
                service.accountKey == userKey || userKey == null
            } else {
                false
            }
        }
    }

    private val profileActionsFlow by lazy {
        accountServiceFlow(accountType, accountRepository).map { service ->
            require(service is AuthenticatedMicroblogDataSource)
            service.profileActions().toImmutableList().toImmutableListWrapper()
        }
    }

    private val canSendMessageFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            if (service is DirectMessageDataSource && userKey != null) {
                flow<Boolean> {
                    runCatching {
                        service.canSendDirectMessage(userKey)
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
    }

    private val myAccountKeyFlow by lazy {
        accountServiceFlow(accountType, accountRepository).map { service ->
            if (service is AuthenticatedMicroblogDataSource) {
                service.accountKey
            } else {
                throw NoActiveAccountException
            }
        }
    }

    private val tabsFlow by lazy {
        accountServiceFlow(accountType, accountRepository).map { service ->
            val actualUserKey =
                userKey
                    ?: if (service is AuthenticatedMicroblogDataSource) {
                        service.accountKey
                    } else {
                        null
                    } ?: throw NoActiveAccountException

            service.profileTabs(actualUserKey)
        }
    }

    @Composable
    override fun body(): ProfileState {
        val scope = rememberCoroutineScope()
        val accountServiceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val userState by userStateFlow.collectAsState(UiState.Loading())
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
        val relationState by relationStateFlow.collectAsState(UiState.Loading())
        val isMe by isMeFlow.collectAsUiState()
        val actions by profileActionsFlow.collectAsUiState()
        val canSendMessage by canSendMessageFlow.collectAsUiState()
        val myAccountKey by myAccountKeyFlow.collectAsUiState()

        val tabs =
            tabsFlow.collectAsUiState().value.map {
                it
                    .map {
                        when (it) {
                            is ProfileTab.Media ->
                                ProfileState.Tab.Media(
                                    data = mediaState,
                                )
                            is ProfileTab.Timeline -> {
                                ProfileState.Tab.Timeline(
                                    type = it.type,
                                    data =
                                        remember(it.loader) {
                                            object : TimelinePresenter() {
                                                override val loader: Flow<BaseTimelineLoader>
                                                    get() = flowOf(it.loader)
                                            }
                                        }.body().listState,
                                )
                            }
                        }
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }

        return object : ProfileState(
            userState = userState,
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
                tabs.onSuccess {
                    it.toImmutableList().forEach {
                        when (it) {
                            is ProfileState.Tab.Media -> {
                                it.data.refreshSuspend()
                            }
                            is ProfileState.Tab.Timeline -> {
                                it.data.refreshSuspend()
                            }
                        }
                    }
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
