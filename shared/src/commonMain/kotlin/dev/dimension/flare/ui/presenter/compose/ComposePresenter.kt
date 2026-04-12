package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.newDraftGroupId
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiDraft
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class ComposePresenter(
    private val accountType: AccountType?,
    private val status: ComposeStatus? = null,
    private val draftGroupId: String? = null,
) : PresenterBase<ComposeState>(),
    KoinComponent {
    private val composeUseCase: ComposeUseCase by inject()
    private val accountRepository: AccountRepository by inject()
    private val appDataStore: AppDataStore by inject()
    private val restoreDraftUseCase: RestoreDraftUseCase by inject()
    private val draftRepository: DraftRepository by inject()
    private val ioScope: CoroutineScope by inject()

    private val showDraftFlow by lazy {
        combine(draftRepository.visibleDrafts, draftRepository.sendingDrafts) { visible, sending ->
            visible + sending
        }.map {
            it.isNotEmpty()
        }
    }

    private val selectedAccountsKeyFlow by lazy {
        MutableStateFlow<ImmutableList<MicroBlogKey>>(persistentListOf())
    }

    private val activeStatusFlow by lazy {
        MutableStateFlow(status)
    }

    private val editingDraftGroupIdFlow by lazy {
        MutableStateFlow<String?>(draftGroupId)
    }

    private val loadedDraftStateFlow by lazy {
        MutableStateFlow<UiState<UiDraft>?>(null)
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
        combine(selectedAccountServicesFlow, activeStatusFlow) { services, composeStatus ->
            val configs =
                services.mapNotNull {
                    if (it is AuthenticatedMicroblogDataSource) {
                        it.composeConfig(
                            type =
                                when (composeStatus) {
                                    is ComposeStatus.Quote -> ComposeType.Quote
                                    is ComposeStatus.Reply -> ComposeType.Reply
                                    null -> ComposeType.New
                                },
                        )
                    } else {
                        null
                    }
                }

            when (configs.size) {
                0 -> ComposeConfig()
                1 -> configs.first()
                else -> configs.reduce { acc, config -> acc.merge(config) }
            }
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
            statusFlow,
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
            }
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
        combine(activeStatusFlow, selectedAccountsKeyFlow) { composeStatus, accountKeys ->
            composeStatus to accountKeys.firstOrNull()
        }.flatMapLatest { (composeStatus, selectedAccountKey) ->
            val resolvedAccountType =
                when {
                    selectedAccountKey != null -> AccountType.Specific(selectedAccountKey)
                    accountType is AccountType.Specific -> accountType
                    else -> null
                }
            if (composeStatus != null && resolvedAccountType != null) {
                accountServiceFlow(
                    accountType = resolvedAccountType,
                    repository = accountRepository,
                ).mapNotNull {
                    if (it is PostDataSource) {
                        it.postHandler.post(composeStatus.statusKey).toUi()
                    } else {
                        null
                    }
                }.flatMapLatest { it }
            } else {
                flowOf(UiState.Error(Exception("No status for compose")))
            }
        }
    }

    private val replyStateFlow by lazy {
        statusFlow
            .map { statusState ->
                statusState.map { post ->
                    if (post is UiTimelineV2.Post && post.platformType == PlatformType.VVo) {
                        post.quote.firstOrNull() ?: post
                    } else {
                        post
                    }
                }
            }.distinctUntilChanged()
    }

    private val initialTextFlow by lazy {
        if (accountType is AccountType.Specific && status != null) {
            statusFlow.flatMapLatest { statusState ->
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
        val loadedDraftState by loadedDraftStateFlow.collectAsState()
        val editingDraftGroupId by editingDraftGroupIdFlow.collectAsState()
        val composeStatus by activeStatusFlow.collectAsState()
        if (draftGroupId != null) {
            LaunchedEffect(Unit) {
                selectedAccountsKeyFlow.value = persistentListOf()
            }
        } else if (accountType != null && accountType is AccountType.Specific) {
            LaunchedEffect(accountType) {
                selectedAccountsKeyFlow.value = listOf(accountType.accountKey).toImmutableList()
            }
        } else {
            // load last used accounts or active account
            LaunchedEffect(Unit) {
                selectedAccountsKeyFlow.value = appDataStore
                    .composeConfigData
                    .data
                    .firstOrNull()
                    ?.lastAccounts
                    ?.takeIf { it.isNotEmpty() }
                    ?.toImmutableList() ?: accountRepository
                    .activeAccount
                    .firstOrNull()
                    ?.takeSuccess()
                    ?.accountKey
                    ?.let { persistentListOf(it) }
                    ?: persistentListOf()
            }
        }
        LaunchedEffect(draftGroupId) {
            draftGroupId?.let {
                loadDraftInternal(it)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                ioScope.launch {
                    val accounts = selectedAccountsKeyFlow.value
                    if (accounts.isNotEmpty()) {
                        appDataStore.composeConfigData.updateData {
                            it.copy(
                                lastAccounts = accounts,
                            )
                        }
                    }
                }
            }
        }

        val replyState = replyStateFlow.flattenUiState().value
        val initialTextState =
            if (editingDraftGroupId == null) {
                initialTextFlow?.flattenUiState()?.value
            } else {
                null
            }

        val visibilityState =
            composeConfig
                .mapNotNull {
                    it.visibility
                }.map {
                    visibilityPresenter()
                }

        val showDraft by showDraftFlow.collectAsState(false)

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
            loadedDraftState = loadedDraftState,
            editingDraftGroupId = editingDraftGroupId,
            composeStatus = composeStatus,
            showDraft = showDraft,
        ) {
            override fun send(
                data: ComposeData,
                onDispatched: (Boolean) -> Unit,
            ) {
                scope.launch {
                    val selectedAccounts = selectedAccountsFlow.firstOrNull().orEmpty()
                    if (selectedAccounts.isNotEmpty()) {
                        composeUseCase.invoke(
                            accounts = selectedAccounts,
                            data = data,
                            groupId = editingDraftGroupIdFlow.value ?: newDraftGroupId(),
                        )
                        withContext(Dispatchers.Main) {
                            onDispatched(true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onDispatched(false)
                        }
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

            override fun loadDraft(groupId: String) {
                scope.launch {
                    loadDraftInternal(groupId)
                }
            }

            override fun consumeLoadedDraft() {
                loadedDraftStateFlow.value = null
            }

            override fun saveDraft(
                data: ComposeData,
                onDispatched: (Boolean) -> Unit,
            ) {
                scope.launch {
                    val selectedAccounts = selectedAccountsFlow.firstOrNull().orEmpty()
                    if (selectedAccounts.isNotEmpty()) {
                        val groupId =
                            editingDraftGroupIdFlow.value ?: draftGroupId ?: newDraftGroupId()
                        composeUseCase.saveDraft(
                            accounts = selectedAccounts,
                            data = data,
                            groupId = groupId,
                        )
                        if (editingDraftGroupIdFlow.value.isNullOrEmpty() && groupId.isNotEmpty()) {
                            editingDraftGroupIdFlow.value = groupId
                        }
                        withContext(Dispatchers.Main) {
                            onDispatched(true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onDispatched(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadDraftInternal(groupId: String) {
        loadedDraftStateFlow.value = UiState.Loading()
        runCatching {
            restoreDraftUseCase(groupId)
        }.onSuccess { draft ->
            if (draft == null) {
                loadedDraftStateFlow.value = UiState.Error(IllegalStateException("Draft not found"))
                return
            }
            editingDraftGroupIdFlow.value = draft.groupId
            activeStatusFlow.value = draft.data.referenceStatus?.composeStatus
            selectedAccountsKeyFlow.value =
                draft.accounts.map { it.account.accountKey }.toImmutableList()
            textFlow.value = draft.data.content
            mediaSizeFlow.value = draft.medias.size
            loadedDraftStateFlow.value = UiState.Success(draft)
        }.onFailure {
            loadedDraftStateFlow.value = UiState.Error(it)
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
        var hasExplicitVisibility by remember {
            mutableStateOf(false)
        }
        LaunchedEffect(Unit) {
            appDataStore.composeConfigData.data.firstOrNull()?.let { data ->
                if (!hasExplicitVisibility) {
                    visibility = data.visibility
                }
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
                hasExplicitVisibility = true
                visibility = value
            }

            override fun clear() {
                visibility = UiTimelineV2.Post.Visibility.Public
                showVisibilityMenu = false
                hasExplicitVisibility = true
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
    public val loadedDraftState: UiState<UiDraft>?,
    public val editingDraftGroupId: String?,
    public val composeStatus: ComposeStatus?,
    public val showDraft: Boolean,
) {
    public abstract fun send(
        data: ComposeData,
        onDispatched: (Boolean) -> Unit,
    )

    public abstract fun selectAccount(accountKey: MicroBlogKey)

    public abstract fun setText(value: String)

    public abstract fun setMediaSize(value: Int)

    public abstract fun loadDraft(groupId: String)

    public abstract fun consumeLoadedDraft()

    public abstract fun saveDraft(
        data: ComposeData,
        onDispatched: (Boolean) -> Unit,
    )
}

@Immutable
public data class InitialText internal constructor(
    val text: String,
    val cursorPosition: Int,
)
