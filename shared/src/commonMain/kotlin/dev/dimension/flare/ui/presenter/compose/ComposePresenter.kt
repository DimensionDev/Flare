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
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.data.repository.draftFileItem
import dev.dimension.flare.data.repository.newDraftGroupId
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebIgnore
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalCoroutinesApi::class)
@WebPresenter("compose")
public class ComposePresenter(
    private val accountType: AccountType?,
    private val status: ComposeStatus? = null,
    private val draftGroupId: String? = null,
) : PresenterBase<ComposeState>() {
    private val composeUseCase: ComposeUseCase by koinInject()
    private val accountRepository: AccountRepository by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val restoreDraftUseCase: RestoreDraftUseCase by koinInject()
    private val draftRepository: DraftRepository by koinInject()
    private val ioScope: CoroutineScope by koinInject()
    private val shouldPersistSelectedAccounts: Boolean =
        accountType == null && status == null && draftGroupId == null

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

    private val allAccountsFlow by lazy {
        observeAllComposeAccounts(
            accountFlows =
                allAccountServicesFlow(accountRepository)
                    .map { accounts ->
                        accounts
                            .filterIsInstance<ComposeDataSource>()
                            .map { account ->
                                accountRepository.getFlow(account.accountKey).map {
                                    account.accountKey to it
                                }
                            }
                    },
        )
    }

    private val selectedComposeAccountKeysFlow by lazy {
        observeSelectedComposeAccountKeys(
            allAccountsFlow = allAccountsFlow,
            selectedAccountsKeyFlow = selectedAccountsKeyFlow,
        )
    }

    private val allUsersFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services.mapNotNull { service ->
                    if (service is UserDataSource && service is ComposeDataSource) {
                        service.userHandler.userById(service.accountKey.id).toUi().map {
                            service.accountKey to it
                        }
                    } else {
                        null
                    }
                }
            }.combineLatestFlowLists()
            .map { it.toMap() }
    }

    private val activeStatusFlow by lazy {
        MutableStateFlow(status)
    }

    private val statusFlow by lazy {
        MutableStateFlow<UiState<UiTimelineV2>>(UiState.Loading())
    }

    private val editingDraftGroupIdFlow by lazy {
        MutableStateFlow<String?>(draftGroupId)
    }

    private val loadedDraftStateFlow by lazy {
        MutableStateFlow<UiState<UiDraft>?>(null)
    }

    private val enableCrossPostFlow by lazy {
        selectedComposeAccountKeysFlow.map { accountKeys ->
            accountKeys.size > 1 // && status == null
        }
    }

    private val selectedAccountsFlow: Flow<ImmutableList<UiAccount>> by lazy {
        combine(
            allAccountsFlow,
            selectedComposeAccountKeysFlow,
        ) { allAccounts, selectedKeys ->
            selectedKeys
                .mapNotNull { key ->
                    allAccounts[key]?.takeSuccess()
                }.toImmutableList()
        }
    }

    private val selectedAccountServicesFlow by lazy {
        selectedComposeAccountKeysFlow
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
                    if (it is ComposeDataSource) {
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

    private val selectedUsersFlow: Flow<ImmutableList<UiState<UiProfile>>> by lazy {
        combine(
            selectedComposeAccountKeysFlow,
            allUsersFlow,
        ) { selectedKeys, allUsers ->
            selectedKeys
                .mapNotNull { key ->
                    allUsers[key]
                }.toImmutableList()
        }
    }

    private val otherAccountsFlow by lazy {
        combine(
            allAccountsFlow,
            selectedComposeAccountKeysFlow,
            statusFlow,
        ) { allAccounts, selectedAccountKeys, status ->
            val statusPlatform =
                status
                    .takeSuccess()
                    ?.contentPostOrNull()
                    ?.platformType
            allAccounts
                .values
                .mapNotNull { it.takeSuccess() }
                .filterNot { account ->
                    selectedAccountKeys.contains(account.accountKey) ||
                        (statusPlatform != null && account.platformType != statusPlatform)
                }.map {
                    it.accountKey
                }
        }
    }

    private val otherUsersFlow by lazy {
        combine(
            otherAccountsFlow,
            allUsersFlow,
        ) { otherAccountKeys, allUsers ->
            otherAccountKeys
                .mapNotNull { key ->
                    allUsers[key]
                }.toImmutableList()
        }
    }

    private val accountUsersFlow by lazy {
        combine(
            allAccountsFlow,
            allUsersFlow,
            selectedComposeAccountKeysFlow,
            statusFlow,
        ) { allAccounts, allUsers, selectedAccountKeys, status ->
            val statusPlatform =
                status
                    .takeSuccess()
                    ?.contentPostOrNull()
                    ?.platformType
            allAccounts
                .values
                .mapNotNull { it.takeSuccess() }
                .filter { account ->
                    selectedAccountKeys.contains(account.accountKey) ||
                        statusPlatform == null ||
                        account.platformType == statusPlatform
                }.mapNotNull { account ->
                    allUsers[account.accountKey]
                }.toImmutableList()
        }
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
            text to config.text
        }.flatMapLatest { (text, config) ->
            config?.remainingLength(text)?.map<Int, Int?> { it } ?: flowOf(null)
        }.distinctUntilChanged()
    }

    private val canSendFlow by lazy {
        combine(
            textFlow,
            mediaSizeFlow,
            remainingLengthFlow,
            selectedComposeAccountKeysFlow,
            composeConfigFlow,
        ) {
            text,
            mediaSize,
            remainingLength,
            selectedAccountKeys,
            composeConfig,
            ->
            (
                text.isNotBlank() && text.isNotEmpty() && selectedAccountKeys.isNotEmpty() &&
                    (remainingLength == null || remainingLength >= 0)
            ) ||
                ((text.isEmpty() || text.isBlank()) && composeConfig.media?.allowMediaOnly == true && mediaSize > 0)
        }
    }

    private val statusSourceFlow by lazy {
        observeComposeStatusTarget(
            activeStatusFlow = activeStatusFlow,
            selectedAccountKeysFlow = selectedComposeAccountKeysFlow,
            fallbackAccountType = accountType,
        ).flatMapLatest { (composeStatus, resolvedAccountType) ->
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
                    val timelinePost = post.asTimelinePostItem()
                    if (timelinePost != null && timelinePost.displayPost.platformType == PlatformType.VVo &&
                        status is ComposeStatus.Quote
                    ) {
                        timelinePost.presentation.quotes.firstOrNull() ?: timelinePost.displayPost
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
                            val timelinePost = post.asTimelinePostItem()
                            if (timelinePost != null) {
                                InitialTextResolver.resolve(
                                    post = timelinePost.displayPost,
                                    quotes = timelinePost.presentation.quotes,
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
        val loadedStatus by statusSourceFlow.flattenUiState()
        LaunchedEffect(loadedStatus) {
            statusFlow.value = loadedStatus
        }
        val selectedUsers by selectedUsersFlow.collectAsUiState()
        val remainingUsers by otherUsersFlow.collectAsUiState()
        val accountUsers by accountUsersFlow.collectAsUiState()
        val emojiState by emojiFlow.flattenUiState()
        val enableCrossPost by enableCrossPostFlow.collectAsUiState()
        val composeConfig: UiState<ComposeConfig> by composeConfigFlow.collectAsUiState()
        val remainingLength by remainingLengthFlow.collectAsState(null)
        val canSend by canSendFlow.collectAsState(false)
        val loadedDraftState by loadedDraftStateFlow.collectAsState()
        val editingDraftGroupId by editingDraftGroupIdFlow.collectAsState()
        val composeStatus by activeStatusFlow.collectAsState()
        var directSendState by remember {
            mutableStateOf(ComposeDirectSendState.idle())
        }
        if (draftGroupId != null) {
            LaunchedEffect(Unit) {
                selectedAccountsKeyFlow.value = persistentListOf()
            }
        } else if (accountType != null && accountType is AccountType.Specific) {
            LaunchedEffect(accountType) {
                val composeAccounts = allAccountsFlow.firstOrNull().orEmpty()
                selectedAccountsKeyFlow.value =
                    if (composeAccounts.containsKey(accountType.accountKey)) {
                        listOf(accountType.accountKey).toImmutableList()
                    } else {
                        if (status == null) {
                            composeAccounts
                                .keys
                                .firstOrNull()
                                ?.let { persistentListOf(it) }
                                ?: persistentListOf()
                        } else {
                            persistentListOf()
                        }
                    }
            }
        } else {
            // load last used accounts or active account
            LaunchedEffect(Unit) {
                val composeAccounts = allAccountsFlow.firstOrNull().orEmpty()
                val lastAccounts =
                    appDataStore
                        .composeConfigData
                        .data
                        .firstOrNull()
                        ?.lastAccounts
                        .orEmpty()
                        .filter { accountKey -> composeAccounts.containsKey(accountKey) }
                selectedAccountsKeyFlow.value = lastAccounts
                    .takeIf { it.isNotEmpty() }
                    ?.toImmutableList() ?: composeAccounts
                    .keys
                    .firstOrNull()
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
                if (shouldPersistSelectedAccounts) {
                    ioScope.launch {
                        val composeAccounts = allAccountsFlow.firstOrNull().orEmpty()
                        val accounts =
                            selectedAccountsKeyFlow.value
                                .filter { accountKey -> composeAccounts.containsKey(accountKey) }
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
        val pollMaxOptions =
            composeConfig
                .takeSuccess()
                ?.poll
                ?.maxOptions
        val contentWarningEnabled =
            composeConfig
                .takeSuccess()
                ?.contentWarning != null
        val mediaEnabled =
            composeConfig
                .takeSuccess()
                ?.media
                ?.let { it.maxCount > 0 }
                ?: false
        val mediaCanSensitive =
            composeConfig
                .takeSuccess()
                ?.media
                ?.canSensitive
                ?: false
        val mediaMaxCount =
            composeConfig
                .takeSuccess()
                ?.media
                ?.maxCount
                ?: 0
        val languageCodes =
            composeConfig
                .takeSuccess()
                ?.language
                ?.sortedIsoCodes
                ?.toImmutableList()
                ?: persistentListOf()
        val webVisibility =
            visibilityState
                .takeSuccess()
                ?.visibility
                ?: UiTimelineV2.Post.Visibility.Public
        val webVisibilities =
            visibilityState
                .takeSuccess()
                ?.allVisibilities
                ?: persistentListOf()
        val webEmojis =
            emojiState
                .takeSuccess()
                ?.data
                ?.values
                ?.flatten()
                ?.toImmutableList()
                ?: persistentListOf()
        val webEmojiAccountType =
            emojiState
                .takeSuccess()
                ?.accountType

        val showDraft by showDraftFlow.collectAsState(false)

        return object : ComposeState(
            canSend = canSend,
            visibilityState = visibilityState,
            replyState = replyState,
            referencePost = replyState.takeSuccess(),
            emojiState = emojiState,
            composeConfig = composeConfig,
            enableCrossPost = enableCrossPost,
            selectedUsers = selectedUsers,
            otherUsers = remainingUsers,
            accountUsers = accountUsers,
            initialTextState = initialTextState,
            loadedDraftState = loadedDraftState,
            editingDraftGroupId = editingDraftGroupId,
            composeStatus = composeStatus,
            showDraft = showDraft,
            directSendState = directSendState,
            remainingLength = remainingLength,
            pollMaxOptions = pollMaxOptions,
            contentWarningEnabled = contentWarningEnabled,
            mediaEnabled = mediaEnabled,
            mediaCanSensitive = mediaCanSensitive,
            mediaMaxCount = mediaMaxCount,
            languageCodes = languageCodes,
            emojiAccountType = webEmojiAccountType,
            emojis = webEmojis,
            visibility = webVisibility,
            allVisibilities = webVisibilities,
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
                            onPrepared = {
                                withContext(Dispatchers.Main) {
                                    onDispatched(true)
                                }
                            },
                        )
                    } else {
                        withContext(Dispatchers.Main) {
                            onDispatched(false)
                        }
                    }
                }
            }

            override fun sendDirect(
                content: String,
                accountKeys: List<MicroBlogKey>,
                visibility: UiTimelineV2.Post.Visibility,
                language: String,
                sensitive: Boolean,
                spoilerText: String?,
                localOnly: Boolean,
                pollOptionsText: String,
                pollExpiredAfter: Long,
                pollMultiple: Boolean,
                mediaItemsJson: String,
            ) {
                if (directSendState.phase == ComposeDirectSendPhase.Sending) {
                    return
                }
                scope.launch {
                    val selectedAccounts =
                        if (accountKeys.isNotEmpty()) {
                            accountKeys.mapNotNull { accountRepository.find(it) }
                        } else {
                            selectedAccountsFlow.firstOrNull().orEmpty()
                        }
                    if (selectedAccounts.isEmpty()) {
                        directSendState =
                            ComposeDirectSendState.error(
                                message = "No account selected.",
                            )
                        return@launch
                    }

                    val data =
                        ComposeData(
                            content = content,
                            visibility = visibility,
                            language = language.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: listOf("en"),
                            medias = decodeWebComposeMedia(mediaItemsJson),
                            sensitive = sensitive,
                            spoilerText = spoilerText,
                            localOnly = localOnly,
                            referenceStatus =
                                activeStatusFlow.value?.let {
                                    ComposeData.ReferenceStatus(it)
                                },
                            poll =
                                pollOptionsText
                                    .lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .takeIf { it.isNotEmpty() }
                                    ?.let {
                                        ComposeData.Poll(
                                            options = it,
                                            expiredAfter = pollExpiredAfter,
                                            multiple = pollMultiple,
                                        )
                                    },
                        )

                    val max = selectedAccounts.size
                    var current = 0
                    directSendState = ComposeDirectSendState.sending(current = current, max = max)
                    runCatching {
                        appDataStore.composeConfigData.updateData {
                            it.copy(
                                visibility = data.visibility,
                                lastAccounts =
                                    if (data.referenceStatus == null) {
                                        selectedAccounts.map { account -> account.accountKey }
                                    } else {
                                        it.lastAccounts
                                    },
                            )
                        }
                        selectedAccounts.forEach { account ->
                            val dataSource =
                                accountRepository.getOrCreateDataSource(account)
                                    as? ComposeDataSource
                                    ?: error("Account does not support compose: ${account.accountKey}")
                            dataSource.compose(data = data) {
                                // Media upload progress is not surfaced by this web-only entry yet.
                            }
                            current += 1
                            directSendState = ComposeDirectSendState.sending(current = current, max = max)
                        }
                    }.onSuccess {
                        directSendState = ComposeDirectSendState.success(max = max)
                    }.onFailure { throwable ->
                        directSendState =
                            ComposeDirectSendState.error(
                                message = throwable.message ?: "Send failed.",
                                current = current,
                                max = max,
                            )
                    }
                }
            }

            override fun clearDirectSendState() {
                directSendState = ComposeDirectSendState.idle()
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
                            onSaved = {
                                withContext(Dispatchers.Main) {
                                    if (editingDraftGroupIdFlow.value.isNullOrEmpty() && groupId.isNotEmpty()) {
                                        editingDraftGroupIdFlow.value = groupId
                                    }
                                    onDispatched(true)
                                }
                            },
                        )
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

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeWebComposeMedia(value: String): List<ComposeData.Media> {
        if (value.isBlank()) return emptyList()
        return Json
            .decodeFromString<List<WebComposeMedia>>(value)
            .map { item ->
                ComposeData.Media(
                    file =
                        draftFileItem(
                            path = item.name ?: "web-compose-media",
                            name = item.name,
                            type =
                                when (item.type) {
                                    "Image" -> FileType.Image
                                    "Video" -> FileType.Video
                                    else -> FileType.Other
                                },
                            mimeType = item.mimeType,
                            loader = { Base64.decode(item.bytesBase64) },
                        ),
                    altText = item.altText?.takeIf { it.isNotBlank() },
                )
            }
    }
}

internal fun observeSelectedComposeAccountKeys(
    allAccountsFlow: Flow<Map<MicroBlogKey, UiState<UiAccount>>>,
    selectedAccountsKeyFlow: Flow<ImmutableList<MicroBlogKey>>,
): Flow<ImmutableList<MicroBlogKey>> =
    combine(
        allAccountsFlow,
        selectedAccountsKeyFlow,
    ) { allAccounts, selectedKeys ->
        selectedKeys
            .filter { key -> allAccounts.containsKey(key) }
            .toImmutableList()
    }.distinctUntilChanged()

internal fun observeComposeStatusTarget(
    activeStatusFlow: Flow<ComposeStatus?>,
    selectedAccountKeysFlow: Flow<ImmutableList<MicroBlogKey>>,
    fallbackAccountType: AccountType?,
): Flow<Pair<ComposeStatus?, AccountType.Specific?>> =
    combine(activeStatusFlow, selectedAccountKeysFlow) { composeStatus, accountKeys ->
        val resolvedAccountType =
            accountKeys.firstOrNull()?.let(AccountType::Specific)
                ?: fallbackAccountType as? AccountType.Specific
        composeStatus to resolvedAccountType
    }.distinctUntilChanged()

internal fun observeAllComposeAccounts(
    accountFlows: Flow<List<Flow<Pair<MicroBlogKey, UiState<UiAccount>>>>>,
): Flow<Map<MicroBlogKey, UiState<UiAccount>>> =
    accountFlows
        .combineLatestFlowLists()
        .map { it.toMap() }
        .distinctUntilChanged()

@Serializable
private data class WebComposeMedia(
    val name: String?,
    val mimeType: String?,
    val type: String,
    val bytesBase64: String,
    val altText: String?,
)

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
    @WebIgnore
    public val visibilityState: UiState<VisibilityState>,
    @WebIgnore
    public val replyState: UiState<UiTimelineV2>?,
    public val referencePost: UiTimelineV2?,
    @WebIgnore
    public val initialTextState: UiState<InitialText>?,
    @WebIgnore
    public val emojiState: UiState<EmojiData>,
    @WebIgnore
    public val composeConfig: UiState<ComposeConfig>,
    @WebIgnore
    public val enableCrossPost: UiState<Boolean>,
    public val otherUsers: UiState<ImmutableList<UiState<UiProfile>>>,
    public val selectedUsers: UiState<ImmutableList<UiState<UiProfile>>>,
    @WebIgnore
    public val accountUsers: UiState<ImmutableList<UiState<UiProfile>>>,
    @WebIgnore
    public val loadedDraftState: UiState<UiDraft>?,
    public val editingDraftGroupId: String?,
    public val composeStatus: ComposeStatus?,
    public val showDraft: Boolean,
    public val directSendState: ComposeDirectSendState,
    public val remainingLength: Int?,
    public val pollMaxOptions: Int?,
    public val contentWarningEnabled: Boolean,
    public val mediaEnabled: Boolean,
    public val mediaCanSensitive: Boolean,
    public val mediaMaxCount: Int,
    public val languageCodes: ImmutableList<String>,
    public val emojiAccountType: AccountType?,
    public val emojis: ImmutableList<UiEmoji>,
    public val visibility: UiTimelineV2.Post.Visibility,
    public val allVisibilities: ImmutableList<UiTimelineV2.Post.Visibility>,
) {
    @WebIgnore
    public abstract fun send(
        data: ComposeData,
        onDispatched: (Boolean) -> Unit,
    )

    public abstract fun sendDirect(
        content: String,
        accountKeys: List<MicroBlogKey>,
        visibility: UiTimelineV2.Post.Visibility,
        language: String,
        sensitive: Boolean,
        spoilerText: String?,
        localOnly: Boolean,
        pollOptionsText: String,
        pollExpiredAfter: Long,
        pollMultiple: Boolean,
        mediaItemsJson: String,
    )

    public abstract fun clearDirectSendState()

    public abstract fun selectAccount(accountKey: MicroBlogKey)

    public abstract fun setText(value: String)

    public abstract fun setMediaSize(value: Int)

    public abstract fun loadDraft(groupId: String)

    public abstract fun consumeLoadedDraft()

    @WebIgnore
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

@Immutable
public data class ComposeDirectSendState(
    val phase: ComposeDirectSendPhase,
    val current: Int,
    val max: Int,
    val errorMessage: String?,
) {
    public companion object {
        public fun idle(): ComposeDirectSendState =
            ComposeDirectSendState(
                phase = ComposeDirectSendPhase.Idle,
                current = 0,
                max = 0,
                errorMessage = null,
            )

        public fun sending(
            current: Int,
            max: Int,
        ): ComposeDirectSendState =
            ComposeDirectSendState(
                phase = ComposeDirectSendPhase.Sending,
                current = current,
                max = max,
                errorMessage = null,
            )

        public fun success(max: Int): ComposeDirectSendState =
            ComposeDirectSendState(
                phase = ComposeDirectSendPhase.Success,
                current = max,
                max = max,
                errorMessage = null,
            )

        public fun error(
            message: String,
            current: Int = 0,
            max: Int = 0,
        ): ComposeDirectSendState =
            ComposeDirectSendState(
                phase = ComposeDirectSendPhase.Error,
                current = current,
                max = max,
                errorMessage = message,
            )
    }
}

public enum class ComposeDirectSendPhase {
    Idle,
    Sending,
    Success,
    Error,
}
