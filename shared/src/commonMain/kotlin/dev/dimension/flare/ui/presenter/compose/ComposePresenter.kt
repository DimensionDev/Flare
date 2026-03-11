package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class ComposePresenter(
    private val accountType: AccountType?,
    private val status: ComposeStatus? = null,
) : PresenterBase<ComposeState>(),
    KoinComponent {
    private val composeUseCase: ComposeUseCase by inject()
    private val accountRepository: AccountRepository by inject()
    private val appDataStore: AppDataStore by inject()

    private val selectedAccountsKeyFlow by lazy {
        MutableStateFlow<ImmutableList<MicroBlogKey>>(persistentListOf())
    }

    private val enableCrossPostFlow by lazy {
        selectedAccountsKeyFlow.map { accountKeys ->
            accountKeys.size > 1 // && status == null
        }
    }

    private val selectedAccountsFlow by lazy {
        selectedAccountsKeyFlow
            .map { accountKeys ->
                accountKeys.map { key ->
                    accountRepository.getFlow(key)
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull {
                        it.takeSuccess()
                    }.toImmutableList()
            }
    }

    private val selectedAccountServicesFlow by lazy {
        selectedAccountsKeyFlow
            .map { accountKeys ->
                accountKeys.map { accountKey ->
                    accountServiceFlow(
                        accountType = AccountType.Specific(accountKey = accountKey),
                        repository = accountRepository,
                    )
                }
            }.combineLatestFlowLists()
            .map { it.toImmutableList() }
    }

    private val composeConfigFlow by lazy {
        selectedAccountServicesFlow.map { services ->
            services
                .mapNotNull {
                    if (it is AuthenticatedMicroblogDataSource) {
                        it.composeConfig(
                            type =
                                when (status) {
                                    is ComposeStatus.Quote -> ComposeType.Quote
                                    is ComposeStatus.Reply -> ComposeType.Reply
                                    null -> ComposeType.New
                                },
                        )
                    } else {
                        null
                    }
                }.reduceOrNull { acc, config -> acc.merge(config) } ?: ComposeConfig()
        }
    }

    private val selectedUsersFlow by lazy {
        selectedAccountServicesFlow
            .map { services ->
                services
                    .mapNotNull { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler.userById(service.accountKey.id).toUi()
                        } else {
                            null
                        }
                    }
            }.combineLatestFlowLists()
            .map { it.toImmutableList() }
    }

    private val otherAccountsFlow by lazy {
        combine(
            accountRepository.allAccounts,
            selectedAccountsKeyFlow,
            statusFlow ?: flowOf(UiState.Error(Exception("No status for compose"))),
        ) { allAccounts, selectedAccountKeys, status ->
            val statusPlatform =
                status
                    .takeSuccess()
                    ?.let {
                        it as? UiTimelineV2.Post
                    }?.platformType
            allAccounts
                .filterNot { account ->
                    selectedAccountKeys.contains(account.accountKey) ||
                        (statusPlatform != null && account.platformType != statusPlatform)
                }.map {
                    it.accountKey
                }
        }
    }

    private val otherUsersFlow by lazy {
        otherAccountsFlow
            .map { keys ->
                keys.map { key ->
                    accountServiceFlow(
                        accountType = AccountType.Specific(accountKey = key),
                        repository = accountRepository,
                    ).mapNotNull {
                        if (it is UserDataSource && it is AuthenticatedMicroblogDataSource) {
                            it.userHandler.userById(it.accountKey.id).toUi()
                        } else {
                            null
                        }
                    }.flatMapLatest { it }
                }
            }.combineLatestFlowLists()
            .map { it.toImmutableList() }
    }

    private val emojiFlow by lazy {
        composeConfigFlow
            .map { config ->
                config.emoji
            }.flatMapLatest { emojiConfig ->
                emojiConfig?.emoji?.toUi()?.map { emojiState ->
                    emojiState.map { emoji ->
                        EmojiData(
                            data = emoji,
                            accountType = AccountType.Specific(emojiConfig.accountKey),
                        )
                    }
                } ?: flowOf(UiState.Error(Exception("No emoji config")))
            }.distinctUntilChanged()
    }

    private val textFlow by lazy {
        MutableStateFlow("")
    }

    private val mediaSizeFlow by lazy {
        MutableStateFlow(0)
    }

    private val remainingLengthFlow by lazy {
        combine(
            textFlow,
            composeConfigFlow,
        ) { text, config ->
            config.text?.maxLength?.minus(text.length) ?: Int.MAX_VALUE
        }
    }

    private val canSendFlow by lazy {
        combine(
            textFlow,
            mediaSizeFlow,
            remainingLengthFlow,
            selectedAccountsKeyFlow,
            composeConfigFlow,
        ) { text, mediaSize, remainingLength, selectedAccountKeys, composeConfig ->
            (text.isNotBlank() && text.isNotEmpty() && selectedAccountKeys.isNotEmpty() && remainingLength >= 0) ||
                ((text.isEmpty() || text.isBlank()) && composeConfig.media?.allowMediaOnly == true && mediaSize > 0)
        }
    }

    private val statusFlow by lazy {
        if (status != null && accountType != null) {
            accountServiceFlow(
                accountType = accountType,
                repository = accountRepository,
            ).mapNotNull {
                if (it is PostDataSource) {
                    it.postHandler.post(status.statusKey).toUi()
                } else {
                    null
                }
            }.flatMapLatest { it }
        } else {
            null
        }
    }

    private val replyStateFlow by lazy {
        statusFlow
            ?.map { statusState ->
                statusState.map { post ->
                    if (post is UiTimelineV2.Post && post.platformType == PlatformType.VVo) {
                        post.quote.firstOrNull() ?: post
                    } else {
                        post
                    }
                }
            }?.distinctUntilChanged()
    }

    private val initialTextFlow by lazy {
        if (accountType is AccountType.Specific && status != null) {
            statusFlow?.flatMapLatest { statusState ->
                selectedUsersFlow
                    .mapNotNull {
                        it.firstOrNull()?.takeSuccess()
                    }.map { user ->
                        statusState.mapNotNull { post ->
                            if (post is UiTimelineV2.Post) {
                                InitialTextResolver.resolve(
                                    post = post,
                                    composeStatus = status,
                                    currentUserHandle = user.handle,
                                    selectedAccountKey = accountType.accountKey,
                                )
                            } else {
                                null
                            }
                        }
                    }.distinctUntilChanged()
            }
        } else {
            null
        }
    }

    @Composable
    override fun body(): ComposeState {
        val scope = rememberCoroutineScope()
        val selectedUsers by selectedUsersFlow.collectAsUiState()
        val remainingUsers by otherUsersFlow.collectAsUiState()
        val emojiState by emojiFlow.flattenUiState()
        val enableCrossPost by enableCrossPostFlow.collectAsUiState()
        val composeConfig: UiState<ComposeConfig> by composeConfigFlow.collectAsUiState()
        val canSend by canSendFlow.collectAsState(false)
        if (accountType != null && accountType is AccountType.Specific) {
            LaunchedEffect(accountType) {
                selectedAccountsKeyFlow.value = listOf(accountType.accountKey).toImmutableList()
            }
        } else {
            // load active account
            LaunchedEffect(Unit) {
                accountRepository.activeAccount.firstOrNull()?.let { account ->
                    selectedAccountsKeyFlow.value =
                        listOfNotNull(account.takeSuccess()?.accountKey)
                            .toImmutableList()
                }
            }
        }

        val replyState = replyStateFlow?.flattenUiState()?.value
        val initialTextState = initialTextFlow?.flattenUiState()?.value

        val visibilityState =
            composeConfig
                .mapNotNull {
                    it.visibility
                }.map {
                    visibilityPresenter()
                }

        return object : ComposeState(
            canSend = canSend,
            visibilityState = visibilityState,
            replyState = replyState,
            emojiState = emojiState,
            composeConfig = composeConfig,
            enableCrossPost = enableCrossPost,
            selectedUsers = selectedUsers,
            otherUsers = remainingUsers,
            initialTextState = initialTextState,
        ) {
            override fun send(
                data: ComposeData,
                groupId: String,
            ) {
                scope.launch {
                    val selectedAccounts = selectedAccountsFlow.firstOrNull().orEmpty()
                    if (selectedAccounts.isNotEmpty()) {
                        composeUseCase.invoke(
                            accounts = selectedAccounts,
                            data = data,
                            groupId = groupId,
                        )
                    }
                }
            }

            override fun selectAccount(accountKey: MicroBlogKey) {
                if (selectedAccountsKeyFlow.value.contains(accountKey)) {
                    if (selectedAccountsKeyFlow.value.size == 1) {
                        return
                    }
                    selectedAccountsKeyFlow.value =
                        (selectedAccountsKeyFlow.value - accountKey).toImmutableList()
                } else {
                    selectedAccountsKeyFlow.value =
                        (selectedAccountsKeyFlow.value + accountKey).toImmutableList()
                }
            }

            override fun setText(value: String) {
                textFlow.value = value
            }

            override fun setMediaSize(value: Int) {
                mediaSizeFlow.value = value
            }
        }
    }

    @Composable
    private fun visibilityPresenter(): VisibilityState {
        var showVisibilityMenu by remember {
            mutableStateOf(false)
        }
        var visibility by remember {
            mutableStateOf(UiTimelineV2.Post.Visibility.Public)
        }
        LaunchedEffect(appDataStore.composeConfigData.data) {
            appDataStore.composeConfigData.data.collect {
                visibility = it.visibility
            }
        }
        return object : VisibilityState {
            override val visibility = visibility

            override val allVisibilities =
                persistentListOf(
                    UiTimelineV2.Post.Visibility.Public,
                    UiTimelineV2.Post.Visibility.Home,
                    UiTimelineV2.Post.Visibility.Followers,
                    UiTimelineV2.Post.Visibility.Specified,
                )

            override val showVisibilityMenu: Boolean
                get() = showVisibilityMenu

            override fun showVisibilityMenu() {
                showVisibilityMenu = true
            }

            override fun hideVisibilityMenu() {
                showVisibilityMenu = false
            }

            override fun setVisibility(value: UiTimelineV2.Post.Visibility) {
                visibility = value
            }

            override fun clear() {
                visibility = UiTimelineV2.Post.Visibility.Public
                showVisibilityMenu = false
            }
        }
    }
}

@Immutable
public interface VisibilityState {
    public val visibility: UiTimelineV2.Post.Visibility
    public val allVisibilities: ImmutableList<UiTimelineV2.Post.Visibility>
    public val showVisibilityMenu: Boolean

    public fun showVisibilityMenu()

    public fun hideVisibilityMenu()

    public fun setVisibility(value: UiTimelineV2.Post.Visibility)

    public fun clear()
}

@Immutable
public sealed class ComposeStatus {
    public abstract val statusKey: MicroBlogKey

    public data class Quote(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public open class Reply(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public data class VVOComment(
        override val statusKey: MicroBlogKey,
        val rootId: String,
    ) : Reply(statusKey)
}

@Immutable
public abstract class ComposeState(
    public val canSend: Boolean,
    public val visibilityState: UiState<VisibilityState>,
    public val replyState: UiState<UiTimelineV2>?,
    public val initialTextState: UiState<InitialText>?,
    public val emojiState: UiState<EmojiData>,
    public val composeConfig: UiState<ComposeConfig>,
    public val enableCrossPost: UiState<Boolean>,
    public val otherUsers: UiState<ImmutableList<UiState<UiProfile>>>,
    public val selectedUsers: UiState<ImmutableList<UiState<UiProfile>>>,
) {
    public abstract fun send(
        data: ComposeData,
        groupId: String,
    )

    public abstract fun selectAccount(accountKey: MicroBlogKey)

    public abstract fun setText(value: String)

    public abstract fun setMediaSize(value: Int)
}

@Immutable
public data class InitialText internal constructor(
    val text: String,
    val cursorPosition: Int,
)
