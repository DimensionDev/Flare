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
    private val enableCrossPlatformReference: Boolean = false,
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

    private val referenceMetadataFlow by lazy {
        MutableStateFlow(
            ReferenceMetadata(
                sourceAccountKey = (accountType as? AccountType.Specific)?.accountKey,
            ),
        )
    }

    private val resolvedReferenceMetadataFlow by lazy {
        combine(referenceMetadataFlow, allAccountsFlow) { metadata, accounts ->
            metadata.copy(
                sourcePlatform =
                    metadata.sourcePlatform
                        ?: metadata.sourceAccountKey
                            ?.let { accounts[it]?.takeSuccess()?.platformType },
            )
        }
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

    private val referenceTargetPlansFlow by lazy {
        combine(
            selectedAccountServicesFlow,
            selectedAccountsFlow,
            activeStatusFlow,
            statusFlow,
            resolvedReferenceMetadataFlow,
        ) { services, accounts, composeStatus, statusState, referenceMetadata ->
            val sourcePlatform =
                statusState.takeSuccess()?.contentPostOrNull()?.platformType
                    ?: referenceMetadata.sourcePlatform
            val accountsByKey = accounts.associateBy { it.accountKey }
            services.mapNotNull { service ->
                if (service !is ComposeDataSource) {
                    return@mapNotNull null
                }
                val account = accountsByKey[service.accountKey] ?: return@mapNotNull null
                val requiresShareImage =
                    enableCrossPlatformReference &&
                        composeStatus != null &&
                        (
                            sourcePlatform == null ||
                                requiresReferenceShareImage(
                                    sourcePlatform = sourcePlatform,
                                    sourceAccountKey = referenceMetadata.sourceAccountKey,
                                    targetAccount = account,
                                )
                        )
                ReferenceTargetPlan(
                    account = account,
                    requiresShareImage = requiresShareImage,
                    composeConfig =
                        service.composeConfig(
                            type =
                                when {
                                    requiresShareImage -> ComposeType.New
                                    composeStatus is ComposeStatus.Quote -> ComposeType.Quote
                                    composeStatus is ComposeStatus.Reply -> ComposeType.Reply
                                    else -> ComposeType.New
                                },
                        ),
                )
            }
        }
    }

    private val composeConfigFlow by lazy {
        referenceTargetPlansFlow.map { targets ->
            when (targets.size) {
                0 -> ComposeConfig()
                1 -> targets.first().composeConfig
                else -> targets.map { it.composeConfig }.reduce { acc, config -> acc.merge(config) }
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
            resolvedReferenceMetadataFlow,
        ) { allAccounts, selectedAccountKeys, status, referenceMetadata ->
            val post = status.takeSuccess()?.contentPostOrNull()
            val statusPlatform = post?.platformType ?: referenceMetadata.sourcePlatform
            val canCrossPlatform =
                enableCrossPlatformReference &&
                    (post?.shareUrl ?: referenceMetadata.shareUrl) != null
            allAccounts
                .values
                .mapNotNull { it.takeSuccess() }
                .filterNot { account ->
                    selectedAccountKeys.contains(account.accountKey) ||
                        (
                            !canCrossPlatform &&
                                requiresReferenceShareImage(
                                    sourcePlatform = statusPlatform,
                                    sourceAccountKey = referenceMetadata.sourceAccountKey,
                                    targetAccount = account,
                                )
                        )
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
            resolvedReferenceMetadataFlow,
        ) { allAccounts, allUsers, selectedAccountKeys, status, referenceMetadata ->
            val post = status.takeSuccess()?.contentPostOrNull()
            val statusPlatform = post?.platformType ?: referenceMetadata.sourcePlatform
            val canCrossPlatform =
                enableCrossPlatformReference &&
                    (post?.shareUrl ?: referenceMetadata.shareUrl) != null
            allAccounts
                .values
                .mapNotNull { it.takeSuccess() }
                .filter { account ->
                    selectedAccountKeys.contains(account.accountKey) ||
                        statusPlatform == null ||
                        canCrossPlatform ||
                        !requiresReferenceShareImage(
                            sourcePlatform = statusPlatform,
                            sourceAccountKey = referenceMetadata.sourceAccountKey,
                            targetAccount = account,
                        )
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

    private val crossPlatformTargetStateFlow by lazy {
        combine(
            referenceTargetPlansFlow,
            statusFlow,
            resolvedReferenceMetadataFlow,
        ) { targets, statusState, referenceMetadata ->
            val post = statusState.takeSuccess()?.contentPostOrNull()
            CrossPlatformTargetState(
                shareUrl = post?.shareUrl ?: referenceMetadata.shareUrl,
                hasCrossTargets = targets.any { it.requiresShareImage },
                allTargetsAreCrossPlatform =
                    targets.isNotEmpty() &&
                        targets.all { it.requiresShareImage },
            )
        }
    }

    private val sendCapabilityFlow by lazy {
        combine(
            composeConfigFlow,
            crossPlatformTargetStateFlow,
            referenceTargetPlansFlow,
        ) { composeConfig, crossPlatformState, targetPlans ->
            SendCapability(
                composeConfig = composeConfig,
                crossPlatformState = crossPlatformState,
                targetPlans = targetPlans,
            )
        }
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
            selectedComposeAccountKeysFlow,
            sendCapabilityFlow,
        ) { text, mediaSize, remainingLength, selectedAccountKeys, sendCapability ->
            val composeConfig = sendCapability.composeConfig
            val crossPlatformState = sendCapability.crossPlatformState
            val mediaConfig = composeConfig.media
            val crossPlatformMediaIsValid =
                sendCapability.targetPlans.hasReferenceShareImageCapacity(mediaSize)
            val canUseAutomaticReferenceContent =
                crossPlatformState.hasCrossTargets &&
                    crossPlatformState.allTargetsAreCrossPlatform &&
                    (
                        crossPlatformState.shareUrl?.let { url ->
                            composeConfig.text?.let { url.length <= it.maxLength }
                        } == true || mediaConfig?.allowMediaOnly == true
                    )
            selectedAccountKeys.isNotEmpty() &&
                remainingLength >= 0 &&
                crossPlatformMediaIsValid &&
                (
                    text.isNotBlank() ||
                        (mediaConfig?.allowMediaOnly == true && mediaSize > 0) ||
                        canUseAutomaticReferenceContent
                )
        }
    }

    private val statusFlow by lazy {
        combine(activeStatusFlow, referenceMetadataFlow) { composeStatus, referenceMetadata ->
            composeStatus to referenceMetadata.sourceAccountKey
        }.flatMapLatest { (composeStatus, sourceAccountKey) ->
            val resolvedAccountType = sourceAccountKey?.let { AccountType.Specific(it) }
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
        val selectedUsers by selectedUsersFlow.collectAsUiState()
        val selectedAccounts by selectedAccountsFlow.collectAsUiState()
        val remainingUsers by otherUsersFlow.collectAsUiState()
        val accountUsers by accountUsersFlow.collectAsUiState()
        val availableAccounts by allAccountsFlow.collectAsState(emptyMap())
        val emojiState by emojiFlow.flattenUiState()
        val enableCrossPost by enableCrossPostFlow.collectAsUiState()
        val composeConfig: UiState<ComposeConfig> by composeConfigFlow.collectAsUiState()
        val referenceMetadata by resolvedReferenceMetadataFlow.collectAsState(ReferenceMetadata())
        val referenceTargetPlans by referenceTargetPlansFlow.collectAsState(emptyList())
        val canSend by canSendFlow.collectAsState(false)
        val loadedDraftState by loadedDraftStateFlow.collectAsState()
        val editingDraftGroupId by editingDraftGroupIdFlow.collectAsState()
        val composeStatus by activeStatusFlow.collectAsState()
        var directSendState by remember {
            mutableStateOf(ComposeDirectSendState.idle())
        }
        var sendEnqueued by remember {
            mutableStateOf(false)
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

        val rawStatusState = statusFlow.flattenUiState().value
        val replyState = replyStateFlow.flattenUiState().value
        val referencePost = rawStatusState.takeSuccess()
        val referenceContentPost = referencePost?.contentPostOrNull()
        val referenceSourcePlatform = referenceContentPost?.platformType ?: referenceMetadata.sourcePlatform
        val referenceShareUrl = referenceContentPost?.shareUrl ?: referenceMetadata.shareUrl
        val effectiveReferenceMetadata =
            referenceMetadata.copy(
                sourcePlatform = referenceSourcePlatform,
                shareUrl = referenceShareUrl,
            )
        LaunchedEffect(referenceContentPost?.platformType, referenceContentPost?.shareUrl) {
            referenceContentPost?.let { post ->
                val current = referenceMetadataFlow.value
                referenceMetadataFlow.value =
                    current.copy(
                        sourcePlatform = post.platformType,
                        shareUrl = post.shareUrl ?: current.shareUrl,
                    )
            }
        }
        val selectedAccountValues = selectedAccounts.takeSuccess().orEmpty()
        val hasCrossPlatformTargets =
            referenceTargetPlans.any { it.requiresShareImage }
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
        val textMaxLength =
            composeConfig
                .takeSuccess()
                ?.text
                ?.maxLength
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
            referencePost = referencePost,
            referenceSourcePlatform = referenceSourcePlatform,
            referenceShareUrl = referenceShareUrl,
            hasCrossPlatformTargets = hasCrossPlatformTargets,
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
            textMaxLength = textMaxLength,
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
                referenceShareImageRenderer: ReferenceShareImageRenderer?,
                onDispatched: (Boolean) -> Unit,
            ) {
                if (sendEnqueued || selectedAccountValues.isEmpty()) {
                    onDispatched(false)
                    return
                }
                if (
                    hasCrossPlatformTargets &&
                    (
                        data.poll != null ||
                            !referenceTargetPlans.hasReferenceShareImageCapacity(data.medias.size)
                    )
                ) {
                    onDispatched(false)
                    return
                }
                sendEnqueued = true
                val enrichedData = data.withReferenceMetadata(effectiveReferenceMetadata)
                composeUseCase.invoke(
                    accounts = selectedAccountValues,
                    data = enrichedData,
                    groupId = editingDraftGroupIdFlow.value ?: newDraftGroupId(),
                    referencePost = referencePost,
                    referenceShareImageRenderer = referenceShareImageRenderer,
                )
                onDispatched(true)
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

            override fun requiresReferenceShareImage(accountKey: MicroBlogKey): Boolean {
                if (!enableCrossPlatformReference) return false
                if (composeStatus != null && referenceSourcePlatform == null) return true
                val targetAccount = availableAccounts[accountKey]?.takeSuccess() ?: return false
                return dev.dimension.flare.ui.presenter.compose.requiresReferenceShareImage(
                    sourcePlatform = referenceSourcePlatform,
                    sourceAccountKey = referenceMetadata.sourceAccountKey,
                    targetAccount = targetAccount,
                )
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
                        val enrichedData = data.withReferenceMetadata(effectiveReferenceMetadata)
                        composeUseCase.saveDraft(
                            accounts = selectedAccounts,
                            data = enrichedData,
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
            referenceMetadataFlow.value =
                ReferenceMetadata(
                    sourceAccountKey =
                        draft.data.referenceStatus?.sourceAccountKey
                            ?: draft.resolveReferenceSourceAccountKey(),
                    sourcePlatform = draft.data.referenceStatus?.sourcePlatform,
                    shareUrl = draft.data.referenceStatus?.shareUrl,
                )
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

private data class CrossPlatformTargetState(
    val shareUrl: String?,
    val hasCrossTargets: Boolean,
    val allTargetsAreCrossPlatform: Boolean,
)

internal data class ReferenceMetadata(
    val sourceAccountKey: MicroBlogKey? = null,
    val sourcePlatform: PlatformType? = null,
    val shareUrl: String? = null,
)

internal fun ComposeData.withReferenceMetadata(metadata: ReferenceMetadata): ComposeData =
    copy(
        referenceStatus =
            referenceStatus?.let { reference ->
                reference.copy(
                    sourceAccountKey = metadata.sourceAccountKey ?: reference.sourceAccountKey,
                    sourcePlatform = metadata.sourcePlatform ?: reference.sourcePlatform,
                    shareUrl = metadata.shareUrl ?: reference.shareUrl,
                )
            },
    )

private fun UiDraft.resolveReferenceSourceAccountKey(): MicroBlogKey? {
    val reference = data.referenceStatus ?: return null
    val statusHost = reference.composeStatus.statusKey.host
    accounts
        .firstOrNull { draftAccount ->
            draftAccount.account.accountKey.host
                .equals(statusHost, ignoreCase = true)
        }?.let { return it.account.accountKey }
    val sourcePlatform = reference.sourcePlatform ?: return null
    return accounts
        .filter { it.account.platformType == sourcePlatform }
        .singleOrNull()
        ?.account
        ?.accountKey
}

private data class SendCapability(
    val composeConfig: ComposeConfig,
    val crossPlatformState: CrossPlatformTargetState,
    val targetPlans: List<ReferenceTargetPlan>,
)

internal data class ReferenceTargetPlan(
    val account: UiAccount,
    val requiresShareImage: Boolean,
    val composeConfig: ComposeConfig,
)

internal fun List<ReferenceTargetPlan>.hasReferenceShareImageCapacity(userMediaCount: Int): Boolean =
    all { target ->
        !target.requiresShareImage ||
            target.composeConfig.media?.let { media ->
                userMediaCount + 1 <= media.maxCount
            } == true
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
    public val referenceSourcePlatform: PlatformType?,
    public val referenceShareUrl: String?,
    public val hasCrossPlatformTargets: Boolean,
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
    public val textMaxLength: Int?,
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
        referenceShareImageRenderer: ReferenceShareImageRenderer? = null,
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

    public abstract fun requiresReferenceShareImage(accountKey: MicroBlogKey): Boolean

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
