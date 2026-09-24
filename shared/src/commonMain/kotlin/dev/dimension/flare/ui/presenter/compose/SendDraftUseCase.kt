package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftTarget
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Clock

internal class SendDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
    private val findAccount: suspend (MicroBlogKey) -> UiAccount?,
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
        composeDraft = { account, data, progress ->
            val dataSource =
                accountRepository.getOrCreateDataSource(account) as? ComposeDataSource
                    ?: error("Account does not support compose: ${account.accountKey}")
            dataSource.compose(data = data, progress = progress)
        },
    )

    suspend operator fun invoke(
        bundle: ComposeDraftBundle,
        onPrepared: suspend () -> Unit = {},
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        withCancellationRecovery(bundle.groupId, bundle.accounts.map { it.accountKey }) { pendingTargets ->
            val persistedMedia = draftMediaStore.persist(bundle.groupId, bundle.template.medias)
            val savedGroupId =
                draftRepository.saveDraft(
                    input =
                        SaveDraftInput(
                            groupId = bundle.groupId,
                            content = bundle.template.toDraftContent(),
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
            val data =
                bundle.template.copy(
                    medias =
                        draftMediaStore.restore(
                            checkNotNull(draftRepository.draft(savedGroupId).firstOrNull()).medias,
                        ),
                )
            onPrepared()
            sendDatas(
                targets = bundle.accounts.map { ComposeTargetData(account = it, data = data) },
                groupId = savedGroupId,
                pendingTargets = pendingTargets,
                progress = progress,
            )
        }
    }

    suspend operator fun invoke(
        groupId: String,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return
        val medias = draftMediaStore.restore(draft.medias)
        val datas =
            draft.targets
                .filter { it.status != DraftTargetStatus.SENDING }
                .mapNotNull { target ->
                    findAccount(target.accountKey)?.let { account ->
                        ComposeTargetData(
                            account = account,
                            data = draft.content.toComposeData(medias = medias),
                        )
                    }
                }
        withCancellationRecovery(groupId, datas.map { it.account.accountKey }) { pendingTargets ->
            sendDatas(
                targets = datas,
                groupId = groupId,
                pendingTargets = pendingTargets,
                progress = progress,
            )
        }
    }

    private suspend fun withCancellationRecovery(
        groupId: String,
        accountKeys: List<MicroBlogKey>,
        send: suspend (MutableSet<MicroBlogKey>) -> Unit,
    ) {
        val pendingTargets = accountKeys.toMutableSet()
        var cancelled = false
        try {
            send(pendingTargets)
        } catch (cancellation: CancellationException) {
            cancelled = true
            throw cancellation
        } finally {
            if (cancelled || !currentCoroutineContext().isActive) {
                // Cover preparation, progress callbacks, and database writes as well as the upload.
                withContext(NonCancellable) {
                    pendingTargets.forEach { accountKey ->
                        draftRepository.updateTargetStatus(
                            groupId = groupId,
                            accountKey = accountKey,
                            status = DraftTargetStatus.FAILED,
                            errorMessage = "Upload cancelled",
                        )
                    }
                }
            }
        }
    }

    private suspend fun sendDatas(
        targets: List<ComposeTargetData>,
        groupId: String,
        pendingTargets: MutableSet<MicroBlogKey>,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        val progressTracker = ComposeProgressTracker(targets)
        progress(progressTracker.state())
        val failures = mutableListOf<Throwable>()
        targets.forEach { target ->
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
                // Publishing succeeded: retire this target before progress reporting can be cancelled.
                withContext(NonCancellable) {
                    draftRepository.deleteTarget(groupId, target.account.accountKey)
                    pendingTargets.remove(target.account.accountKey)
                }
                repeat(pendingProgressTicks) {
                    progressTracker.onComposeProgress(target.account.accountKey)
                    progress(progressTracker.state())
                }
                progressTracker.onComposeSuccess(target.account.accountKey)
                progress(progressTracker.state())
            } catch (throwable: Exception) {
                if (!currentCoroutineContext().isActive) {
                    throw throwable
                }
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
                pendingTargets.remove(target.account.accountKey)
                failures += throwable
            }
        }
        if (failures.isEmpty()) {
            progress(ComposeProgressState.Success)
        } else {
            progress(ComposeProgressState.Error(ComposeDraftFailedException(failures)))
        }
    }
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
