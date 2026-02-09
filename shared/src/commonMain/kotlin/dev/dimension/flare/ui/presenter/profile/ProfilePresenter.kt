package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.ActionMenu
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
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.status.LogUserHistoryPresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

    private val serviceFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
    }

    private val userStateFlow by lazy {
        serviceFlow.flatMapLatest { service ->
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
        serviceFlow.flatMapLatest { service ->
            require(service is AuthenticatedMicroblogDataSource)
            val actualUserKey = userKey ?: service.accountKey
            service.relation(actualUserKey)
        }
    }

    private val isMeFlow by lazy {
        serviceFlow.map { service ->
            if (service is AuthenticatedMicroblogDataSource) {
                service.accountKey == userKey || userKey == null
            } else {
                false
            }
        }
    }

    private val profileActionsFlow by lazy {
        serviceFlow.map { service ->
            require(service is AuthenticatedMicroblogDataSource)
            service.profileActions().toImmutableList()
        }
    }

    private val canSendMessageFlow by lazy {
        serviceFlow.flatMapLatest { service ->
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
        serviceFlow.map { service ->
            if (service is AuthenticatedMicroblogDataSource) {
                service.accountKey
            } else {
                throw NoActiveAccountException
            }
        }
    }

    private val tabsFlow by lazy {
        serviceFlow.map { service ->
            val actualUserKey =
                userKey
                    ?: if (service is AuthenticatedMicroblogDataSource) {
                        service.accountKey
                    } else {
                        null
                    } ?: throw NoActiveAccountException

            service
                .profileTabs(actualUserKey)
                .map {
                    when (it) {
                        ProfileTab.Media ->
                            ProfileState.Tab.Media(
                                presenter =
                                    ProfileMediaPresenter(
                                        accountType = accountType,
                                        userKey = actualUserKey,
                                    ),
                            )

                        is ProfileTab.Timeline -> {
                            ProfileState.Tab.Timeline(
                                type = it.type,
                                presenter =
                                    object : TimelinePresenter() {
                                        override val loader: Flow<BaseTimelineLoader>
                                            get() = flowOf(it.loader)
                                        override val skipFiltering: Boolean = true
                                    },
                            )
                        }
                    }
                }.toImmutableList()
        }
    }

    private val isListDataSourceFlow by lazy {
        serviceFlow.map { service ->
            service is ListDataSource
        }
    }

    private val isGuestMode by lazy {
        accountType == AccountType.Guest
    }

    @Composable
    override fun body(): ProfileState {
        val scope = rememberCoroutineScope()
        val service by serviceFlow.collectAsUiState()
        val userState by userStateFlow.flattenUiState()
        service.onSuccess {
            val userKey =
                userKey ?: if (it is AuthenticatedMicroblogDataSource) it.accountKey else null
            if (userKey != null) {
                remember { LogUserHistoryPresenter(accountType, userKey) }.body()
            }
        }
        val isListDataSource by isListDataSourceFlow.collectAsUiState()
        val relationState by relationStateFlow.flattenUiState()
        val isMe by isMeFlow.collectAsUiState()
        val profileActions by profileActionsFlow.collectAsUiState()
        val canSendMessage by canSendMessageFlow.collectAsUiState()
        val myAccountKey by myAccountKeyFlow.collectAsUiState()
        val tabs by tabsFlow.collectAsUiState()

        val profileMenus =
            remember(
                isMe,
                canSendMessage,
                relationState,
                service,
                profileActions,
                userState,
                myAccountKey,
            ) {
                val user = userState.takeSuccess()
                val accountKey = myAccountKey.takeSuccess()
                if (isMe.takeSuccessOr(false) || user == null) {
                    emptyList()
                } else {
                    listOfNotNull(
                        if (accountKey != null && userKey != null) {
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.List,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.EditUserList),
                                onClicked = {
                                    launcher.launch(
                                        DeeplinkRoute
                                            .EditUserList(
                                                accountKey = accountKey,
                                                userKey = userKey,
                                            ).toUri(),
                                    )
                                },
                            )
                        } else {
                            null
                        },
                        if (canSendMessage.takeSuccessOr(false) && accountKey != null && userKey != null) {
                            // navigate to send message
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.ChatMessage,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.SendMessage),
                                onClicked = {
                                    launcher.launch(
                                        DeeplinkRoute
                                            .DirectMessage(
                                                accountKey = accountKey,
                                                userKey = userKey,
                                            ).toUri(),
                                    )
                                },
                            )
                        } else {
                            null
                        },
                    ).let { items ->
                        items +
                            listOfNotNull(
                                if (items.isEmpty()) {
                                    null
                                } else {
                                    ActionMenu.Divider
                                },
                            ) +
                            relationState.takeSuccessOr(UiRelation()).let { relation ->
                                profileActions
                                    .takeSuccessOr(
                                        persistentListOf(
                                            object : ProfileAction.Mute {
                                                override suspend fun invoke(
                                                    userKey: MicroBlogKey,
                                                    relation: UiRelation,
                                                ) {
                                                }

                                                override fun relationState(relation: UiRelation): Boolean = false
                                            },
                                            object : ProfileAction.Block {
                                                override suspend fun invoke(
                                                    userKey: MicroBlogKey,
                                                    relation: UiRelation,
                                                ) {
                                                }

                                                override fun relationState(relation: UiRelation): Boolean = false
                                            },
                                        ),
                                    ).map { action ->
                                        when (action) {
                                            is ProfileAction.Block -> {
                                                ActionMenu.Item(
                                                    icon =
                                                        if (action.relationState(relation)) {
                                                            ActionMenu.Item.Icon.UnBlock
                                                        } else {
                                                            ActionMenu.Item.Icon.Block
                                                        },
                                                    text =
                                                        ActionMenu.Item.Text.Localized(
                                                            if (action.relationState(relation)) {
                                                                ActionMenu.Item.Text.Localized.Type.UnBlock
                                                            } else {
                                                                ActionMenu.Item.Text.Localized.Type.Block
                                                            },
                                                        ),
                                                    onClicked = {
                                                        if (userKey != null) {
                                                            if (action.relationState(relation)) {
                                                                scope.launch {
                                                                    action.invoke(userKey, relation)
                                                                }
                                                            } else {
                                                                launcher.launch(
                                                                    DeeplinkRoute
                                                                        .BlockUser(
                                                                            accountKey = accountKey,
                                                                            userKey = userKey,
                                                                        ).toUri(),
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                            }

                                            is ProfileAction.Mute -> {
                                                ActionMenu.Item(
                                                    icon =
                                                        if (action.relationState(relation)) {
                                                            ActionMenu.Item.Icon.UnMute
                                                        } else {
                                                            ActionMenu.Item.Icon.Mute
                                                        },
                                                    text =
                                                        ActionMenu.Item.Text.Localized(
                                                            if (action.relationState(relation)) {
                                                                ActionMenu.Item.Text.Localized.Type.UnMute
                                                            } else {
                                                                ActionMenu.Item.Text.Localized.Type.Mute
                                                            },
                                                        ),
                                                    onClicked = {
                                                        if (userKey != null) {
                                                            if (action.relationState(relation)) {
                                                                scope.launch {
                                                                    action.invoke(userKey, relation)
                                                                }
                                                            } else {
                                                                launcher.launch(
                                                                    DeeplinkRoute
                                                                        .MuteUser(
                                                                            accountKey = accountKey,
                                                                            userKey = userKey,
                                                                        ).toUri(),
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                            } +
                            listOf(
                                ActionMenu.Item(
                                    text =
                                        ActionMenu.Item.Text.Localized(
                                            ActionMenu.Item.Text.Localized.Type.Report,
                                        ),
                                    icon = ActionMenu.Item.Icon.Report,
                                    onClicked = {
                                        if (userKey != null) {
                                            launcher.launch(
                                                DeeplinkRoute
                                                    .ReportUser(
                                                        accountKey = accountKey,
                                                        userKey = userKey,
                                                    ).toUri(),
                                            )
                                        }
                                    },
                                    color = ActionMenu.Item.Color.Red,
                                ),
                            )
                    }
                }.let {
                    if (it.isEmpty()) {
                        it
                    } else {
                        listOfNotNull(
                            ActionMenu.Group(
                                displayItem =
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.MoreVerticel,
                                    ),
                                actions = it.toImmutableList(),
                            ),
                        )
                    }
                }.toImmutableList()
            }

        return object : ProfileState(
            userState = userState,
            relationState = relationState,
            isMe = isMe,
            actions = profileMenus,
            isGuestMode = isGuestMode,
            isListDataSource = isListDataSource,
            myAccountKey = myAccountKey,
            canSendMessage = canSendMessage,
            tabs = tabs,
        ) {
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
                    service.onSuccess { service ->
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
    public val actions: ImmutableList<ActionMenu>,
    public val isGuestMode: Boolean,
    public val isListDataSource: UiState<Boolean>,
    public val myAccountKey: UiState<MicroBlogKey>,
    public val canSendMessage: UiState<Boolean>,
    public val tabs: UiState<ImmutableList<Tab>>,
) {
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
    public sealed class Tab {
        @Immutable
        public data class Timeline internal constructor(
            val type: ProfileTab.Timeline.Type,
            val presenter: TimelinePresenter,
        ) : Tab()

        @Immutable
        public data class Media internal constructor(
            val presenter: ProfileMediaPresenter,
        ) : Tab()
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
            accountServiceProvider(
                accountType = accountType,
                repository = accountRepository,
            ).flatMap { service ->
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
