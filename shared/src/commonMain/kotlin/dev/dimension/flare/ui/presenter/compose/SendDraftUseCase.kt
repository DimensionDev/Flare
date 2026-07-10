package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftTarget
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

internal class SendDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
    private val findAccount: suspend (MicroBlogKey) -> UiAccount?,
    private val prepareData: suspend (UiAccount, ComposeData) -> ComposeData = { _, data -> data },
    private val composeDraft: suspend (UiAccount, ComposeData, () -> Unit) -> Unit,
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
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return
        val medias = draftMediaStore.restore(draft.medias)
        val datas =
            draft.targets
                .filter {
                    it.status != DraftTargetStatus.PREPARING &&
                        it.status != DraftTargetStatus.SENDING
                }.mapNotNull { target ->
                    findAccount(target.accountKey)?.let { account ->
                        ComposeTargetData(
                            account = account,
                            data = draft.content.toComposeData(medias = medias),
                        )
                    }
                }
        val failures =
            sendDatas(
                targets = datas,
                groupId = groupId,
                progress = progress,
            )
        progress(failures.toProgressState())
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
    val sourcePlatform = reference.sourcePlatform ?: return this
    if (account.platformType == sourcePlatform) return this

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

internal class ComposeDraftFailedException(
    val failures: List<Throwable>,
) : Exception(
        failures.firstOrNull()?.message ?: "Compose failed.",
    )
