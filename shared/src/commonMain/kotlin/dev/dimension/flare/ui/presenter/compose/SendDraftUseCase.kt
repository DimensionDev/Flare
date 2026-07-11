package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftTarget
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

internal class SendDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
    private val findAccount: suspend (MicroBlogKey) -> UiAccount?,
    private val prepareData: suspend (UiAccount, ComposeData) -> ComposeData = { _, data -> data },
    private val composeDraft: suspend (UiAccount, ComposeData, () -> Unit) -> Unit,
    private val resolveReferencePost: suspend (MicroBlogKey, MicroBlogKey) -> ResolvedReferencePost = { _, _ ->
        error("Referenced post resolver is unavailable.")
    },
) {
    constructor(
        draftRepository: DraftRepository,
        accountRepository: AccountRepository,
        draftMediaStore: DraftMediaStore,
    ) : this(
        draftRepository = draftRepository,
        draftMediaStore = draftMediaStore,
        findAccount = { accountRepository.find(it) },
        prepareData = { account, data ->
            val dataSource =
                accountRepository.getOrCreateDataSource(account) as? ComposeDataSource
                    ?: error("Account does not support compose: ${account.accountKey}")
            data.forTarget(
                account = account,
                newPostConfig = dataSource.composeConfig(ComposeType.New),
            )
        },
        composeDraft = { account, data, progress ->
            val dataSource =
                accountRepository.getOrCreateDataSource(account) as? ComposeDataSource
                    ?: error("Account does not support compose: ${account.accountKey}")
            dataSource.compose(data = data, progress = progress)
        },
        resolveReferencePost = { sourceAccountKey, statusKey ->
            val sourceAccount =
                requireNotNull(accountRepository.find(sourceAccountKey)) {
                    "Referenced post source account is unavailable."
                }
            val dataSource =
                accountRepository.getOrCreateDataSource(sourceAccount) as? PostDataSource
                    ?: error("Referenced post source account cannot load posts.")
            val state =
                dataSource
                    .postHandler
                    .post(statusKey)
                    .toUi()
                    .first { it !is UiState.Loading }
            val post =
                when (state) {
                    is UiState.Success -> state.data
                    is UiState.Error -> throw state.throwable
                    is UiState.Loading -> error("Referenced post is unavailable.")
                }
            val contentPost =
                requireNotNull(post.contentPostOrNull()) {
                    "Referenced post is unavailable."
                }
            ResolvedReferencePost(
                sourcePlatform = contentPost.platformType,
                shareUrl = contentPost.shareUrl,
                renderShareMedia = { renderer -> renderer.renderAndAwait(post) },
            )
        },
    )

    suspend operator fun invoke(
        bundle: ComposeDraftBundle,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        val shareMedia = bundle.template.referenceStatus?.shareMedia
        val mediasToPersist = bundle.template.medias + listOfNotNull(shareMedia)
        val shareImageMediaIndex = shareMedia?.let { bundle.template.medias.size }
        val persistedMedia = draftMediaStore.persist(bundle.groupId, mediasToPersist)
        val savedGroupId =
            draftRepository.saveDraft(
                input =
                    SaveDraftInput(
                        groupId = bundle.groupId,
                        content = bundle.template.toDraftContent(shareImageMediaIndex = shareImageMediaIndex),
                        targets =
                            bundle.accounts.map {
                                SaveDraftTarget(
                                    accountKey = it.accountKey,
                                    status = DraftTargetStatus.SENDING,
                                    attemptCount = 1,
                                    lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
                                )
                            },
                        medias = persistedMedia,
                    ),
            )
        val failures =
            sendDatas(
                targets = bundle.accounts.map { ComposeTargetData(account = it, data = bundle.template) },
                groupId = savedGroupId,
                progress = progress,
            )
        progress(failures.toProgressState())
    }

    suspend operator fun invoke(
        groupId: String,
        referenceShareImageRenderer: ReferenceShareImageRenderer? = null,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return
        val eligibleDraftTargets =
            draft.targets.filter {
                it.status != DraftTargetStatus.PREPARING &&
                    it.status != DraftTargetStatus.SENDING
            }
        if (eligibleDraftTargets.isEmpty()) {
            return
        }
        val medias = draftMediaStore.restore(draft.medias)
        val targets =
            eligibleDraftTargets.mapNotNull { target ->
                findAccount(target.accountKey)?.let { account ->
                    ComposeTargetData(account = account, data = draft.content.toComposeData(medias = medias))
                }
            }
        if (!draftRepository.claimTargetsForSending(groupId)) {
            return
        }
        var data = draft.content.toComposeData(medias = medias)
        val failures = mutableListOf<Throwable>()
        val availableAccountKeys = targets.map { it.account.accountKey }.toSet()
        val missingAccountTargets =
            eligibleDraftTargets.filterNot { target -> availableAccountKeys.contains(target.accountKey) }
        if (missingAccountTargets.isNotEmpty()) {
            val failure = IllegalStateException("Draft target account is unavailable.")
            failures += failure
            missingAccountTargets.forEach { target ->
                draftRepository.updateTargetStatus(
                    groupId = groupId,
                    accountKey = target.accountKey,
                    status = DraftTargetStatus.FAILED,
                    errorMessage = failure.message,
                    attemptCount = target.attemptCount + 1,
                    lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
                )
            }
        }

        val referenceMetadataFailure =
            try {
                val reference = data.referenceStatus
                if (
                    reference != null &&
                    (reference.sourceAccountKey == null || reference.sourcePlatform == null)
                ) {
                    val sourceMetadata = resolveReferenceSourceMetadata(reference, targets.map { it.account })
                    data =
                        data.copy(
                            referenceStatus =
                                reference.copy(
                                    sourceAccountKey = sourceMetadata.accountKey,
                                    sourcePlatform = sourceMetadata.platformType,
                                ),
                        )
                    draftRepository.updateDraftContent(
                        groupId = groupId,
                        content =
                            data.toDraftContent(
                                shareImageMediaIndex = draft.content.reference?.shareImageMediaIndex,
                            ),
                    )
                }
                null
            } catch (throwable: Exception) {
                throwable
            }
        if (referenceMetadataFailure != null) {
            markTargetsFailed(groupId, targets, referenceMetadataFailure)
            progress(
                ComposeProgressState.Progress(
                    current = 0,
                    max = targets.sumOf { it.data.medias.size + 1 },
                ),
            )
            progress(ComposeProgressState.Error(ComposeDraftFailedException(listOf(referenceMetadataFailure))))
            return
        }

        val reference = data.referenceStatus
        val shareImageTargets =
            if (reference == null) {
                emptyList()
            } else {
                targets.filter { target ->
                    reference.sourcePlatform == null ||
                        requiresReferenceShareImage(
                            sourcePlatform = reference.sourcePlatform,
                            sourceAccountKey = reference.sourceAccountKey,
                            targetAccount = target.account,
                        )
                }
            }
        var unavailableShareImageTargets = emptySet<MicroBlogKey>()
        if (shareImageTargets.isNotEmpty() && reference?.shareMedia == null) {
            val referenceToRender = requireNotNull(reference)
            val renderFailure =
                try {
                    val renderer =
                        requireNotNull(referenceShareImageRenderer) {
                            "Cross-platform reference image renderer is unavailable."
                        }
                    val sourceAccountKey =
                        requireNotNull(referenceToRender.sourceAccountKey) {
                            "Referenced post source account is unavailable."
                        }
                    val resolved = resolveReferencePost(sourceAccountKey, referenceToRender.composeStatus.statusKey)
                    val shareMedia = resolved.renderShareMedia(renderer)
                    data =
                        data.copy(
                            referenceStatus =
                                referenceToRender.copy(
                                    sourcePlatform = referenceToRender.sourcePlatform ?: resolved.sourcePlatform,
                                    shareUrl = referenceToRender.shareUrl ?: resolved.shareUrl,
                                    shareMedia = shareMedia,
                                ),
                        )
                    val persistedMedias = draftMediaStore.persist(groupId, data.medias + shareMedia)
                    draftRepository.updateDraftContentAndMedias(
                        groupId = groupId,
                        content = data.toDraftContent(shareImageMediaIndex = data.medias.size),
                        medias = persistedMedias,
                    )
                    null
                } catch (throwable: Exception) {
                    throwable
                }
            if (renderFailure != null) {
                failures += renderFailure
                unavailableShareImageTargets = shareImageTargets.map { it.account.accountKey }.toSet()
                markTargetsFailed(groupId, shareImageTargets, renderFailure)
            }
        }

        failures +=
            sendDatas(
                targets =
                    targets
                        .filterNot { unavailableShareImageTargets.contains(it.account.accountKey) }
                        .map { it.copy(data = data) },
                groupId = groupId,
                progress = progress,
            )
        progress(failures.toProgressState())
    }

    private suspend fun resolveReferenceSourceMetadata(
        reference: ComposeData.ReferenceStatus,
        targets: List<UiAccount>,
    ): ResolvedReferenceMetadata {
        val sourceAccountKey = reference.sourceAccountKey
        val sourcePlatform = reference.sourcePlatform
        if (sourceAccountKey != null && sourcePlatform != null) {
            return ResolvedReferenceMetadata(
                accountKey = sourceAccountKey,
                platformType = sourcePlatform,
            )
        }
        if (sourceAccountKey != null) {
            val sourceAccount = findAccount(sourceAccountKey)
            if (sourceAccount != null) {
                return ResolvedReferenceMetadata(
                    accountKey = sourceAccountKey,
                    platformType = sourceAccount.platformType,
                )
            }
            val matchingPlatforms =
                targets
                    .filter { account ->
                        account.accountKey.host.equals(sourceAccountKey.host, ignoreCase = true) ||
                            account.accountKey.host.equals(reference.composeStatus.statusKey.host, ignoreCase = true)
                    }.map { it.platformType }
                    .distinct()
            val inferredPlatform =
                requireNotNull(matchingPlatforms.singleOrNull()) {
                    "Referenced post source platform is unavailable."
                }
            return ResolvedReferenceMetadata(
                accountKey = sourceAccountKey,
                platformType = inferredPlatform,
            )
        }
        val statusHost = reference.composeStatus.statusKey.host
        val sourceAccount =
            when (sourcePlatform) {
                PlatformType.Mastodon,
                PlatformType.Misskey,
                -> {
                    targets.firstOrNull { account ->
                        account.platformType == sourcePlatform &&
                            account.accountKey.host.equals(statusHost, ignoreCase = true)
                    }
                }

                null -> {
                    targets.firstOrNull { account ->
                        account.accountKey.host.equals(statusHost, ignoreCase = true)
                    }
                }

                else -> {
                    targets.firstOrNull { account -> account.platformType == sourcePlatform }
                }
            }
        return requireNotNull(sourceAccount) {
            "Referenced post source account is unavailable."
        }.let { account ->
            ResolvedReferenceMetadata(
                accountKey = account.accountKey,
                platformType = sourcePlatform ?: account.platformType,
            )
        }
    }

    private suspend fun markTargetsFailed(
        groupId: String,
        targets: List<ComposeTargetData>,
        throwable: Throwable,
    ) {
        targets.forEach { target ->
            draftRepository.updateTargetStatus(
                groupId = groupId,
                accountKey = target.account.accountKey,
                status = DraftTargetStatus.FAILED,
                errorMessage = throwable.message,
                lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    suspend fun sendPersistedTargets(
        groupId: String,
        accounts: List<UiAccount>,
        data: ComposeData,
        progress: suspend (ComposeProgressState) -> Unit,
    ): List<Throwable> =
        sendDatas(
            targets = accounts.map { ComposeTargetData(account = it, data = data) },
            groupId = groupId,
            progress = progress,
        )

    private suspend fun sendDatas(
        targets: List<ComposeTargetData>,
        groupId: String,
        progress: suspend (ComposeProgressState) -> Unit,
    ): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        val preparedTargets =
            targets.mapNotNull { target ->
                try {
                    target.copy(data = prepareData(target.account, target.data))
                } catch (throwable: Exception) {
                    draftRepository.updateTargetStatus(
                        groupId = groupId,
                        accountKey = target.account.accountKey,
                        status = DraftTargetStatus.FAILED,
                        errorMessage = throwable.message,
                        attemptCount = 1,
                        lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
                    )
                    failures += throwable
                    null
                }
            }
        val progressTracker = ComposeProgressTracker(preparedTargets)
        progress(progressTracker.state())
        preparedTargets.forEach { target ->
            draftRepository.updateTargetStatus(
                groupId = groupId,
                accountKey = target.account.accountKey,
                status = DraftTargetStatus.SENDING,
                attemptCount = 1,
                lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
            )
            var pendingProgressTicks = 0
            try {
                composeDraft(target.account, target.data) {
                    pendingProgressTicks++
                }
                repeat(pendingProgressTicks) {
                    progressTracker.onComposeProgress(target.account.accountKey)
                    progress(progressTracker.state())
                }
                progressTracker.onComposeSuccess(target.account.accountKey)
                progress(progressTracker.state())
                draftRepository.deleteTarget(groupId, target.account.accountKey)
            } catch (throwable: Exception) {
                repeat(pendingProgressTicks) {
                    progressTracker.onComposeProgress(target.account.accountKey)
                    progress(progressTracker.state())
                }
                draftRepository.updateTargetStatus(
                    groupId = groupId,
                    accountKey = target.account.accountKey,
                    status = DraftTargetStatus.FAILED,
                    errorMessage = throwable.message,
                    attemptCount = 1,
                    lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
                )
                failures += throwable
            }
        }
        return failures
    }
}

private fun List<Throwable>.toProgressState(): ComposeProgressState =
    if (isEmpty()) {
        ComposeProgressState.Success
    } else {
        ComposeProgressState.Error(ComposeDraftFailedException(this))
    }

internal fun ComposeData.forTarget(
    account: UiAccount,
    newPostConfig: dev.dimension.flare.data.datasource.microblog.ComposeConfig,
): ComposeData {
    val reference = referenceStatus ?: return this
    if (
        reference.sourcePlatform != null &&
        !requiresReferenceShareImage(
            sourcePlatform = reference.sourcePlatform,
            sourceAccountKey = reference.sourceAccountKey,
            targetAccount = account,
        )
    ) {
        return this
    }

    val shareMedia =
        requireNotNull(reference.shareMedia) {
            "Cross-platform reference image is unavailable."
        }
    val mediaConfig =
        requireNotNull(newPostConfig.media) {
            "Target account does not support image posts."
        }
    require(poll == null) {
        "Cross-platform references cannot be combined with a poll."
    }
    require(medias.size + 1 <= mediaConfig.maxCount) {
        "Target account has no remaining media slot for the reference image."
    }

    val targetContent = appendShareUrlIfAllowed(content, reference.shareUrl, newPostConfig.text?.maxLength)
    require(targetContent.isNotBlank() || mediaConfig.allowMediaOnly) {
        "Target account does not support media-only posts."
    }
    return copy(
        content = targetContent,
        medias = medias + shareMedia,
        referenceStatus = null,
    )
}

internal fun appendShareUrlIfAllowed(
    content: String,
    shareUrl: String?,
    maxLength: Int?,
): String {
    val url = shareUrl?.takeIf { it.isNotBlank() } ?: return content
    if (content.contains(url)) return content
    val candidate =
        if (content.isBlank()) {
            url
        } else {
            "${content.trimEnd()}\n\n$url"
        }
    return if (maxLength != null && candidate.length <= maxLength) candidate else content
}

private class ComposeProgressTracker(
    targets: List<ComposeTargetData>,
) {
    private val mediaStepLimitsByAccount =
        targets.associate { it.account.accountKey to it.data.medias.size }
    private val completedMediaStepsByAccount = mutableMapOf<MicroBlogKey, Int>()
    private val completedSendAccounts = mutableSetOf<MicroBlogKey>()
    private val maxSteps = targets.sumOf { it.data.medias.size + 1 }
    private var completedSteps = 0

    fun onComposeProgress(accountKey: MicroBlogKey) {
        val mediaLimit = mediaStepLimitsByAccount.getValue(accountKey)
        val currentMediaSteps = completedMediaStepsByAccount[accountKey] ?: 0
        if (currentMediaSteps >= mediaLimit) {
            return
        }
        completedMediaStepsByAccount[accountKey] = currentMediaSteps + 1
        completedSteps++
    }

    fun onComposeSuccess(accountKey: MicroBlogKey) {
        if (completedSendAccounts.add(accountKey)) {
            completedSteps++
        }
    }

    fun state(): ComposeProgressState.Progress =
        ComposeProgressState.Progress(
            current = completedSteps,
            max = maxSteps,
        )
}

private data class ComposeTargetData(
    val account: UiAccount,
    val data: ComposeData,
)

internal class ResolvedReferencePost(
    val sourcePlatform: PlatformType,
    val shareUrl: String?,
    val renderShareMedia: suspend (ReferenceShareImageRenderer) -> ComposeData.Media,
)

private data class ResolvedReferenceMetadata(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
)

internal class ComposeDraftFailedException(
    val failures: List<Throwable>,
) : Exception(
        failures.firstOrNull()?.message ?: "Compose failed.",
    )
