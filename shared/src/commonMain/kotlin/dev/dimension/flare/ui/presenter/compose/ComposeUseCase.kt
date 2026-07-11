package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.newDraftGroupId
import dev.dimension.flare.data.repository.toComposeDraftBundle
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
internal class ComposeUseCase(
    private val scope: CoroutineScope,
    @Provided private val inAppNotification: InAppNotification,
    private val appDataStore: AppDataStore,
    private val saveDraftUseCase: SaveDraftUseCase,
    private val sendDraftUseCase: SendDraftUseCase,
    private val draftRepository: DraftRepository,
) {
    operator fun invoke(
        accounts: List<UiAccount>,
        data: ComposeData,
        groupId: String,
        referencePost: UiTimelineV2? = null,
        referenceShareImageRenderer: ReferenceShareImageRenderer? = null,
    ) {
        invoke(
            accounts = accounts,
            data = data,
            groupId = groupId,
            referencePost = referencePost,
            referenceShareImageRenderer = referenceShareImageRenderer,
        ) {
            if (it is ComposeProgressState.Error) {
                DebugRepository.error(it.throwable)
            }
            withContext(Dispatchers.Main) {
                when (it) {
                    is ComposeProgressState.Error -> {
                        inAppNotification.onError(Message.Compose, it.throwable)
                    }

                    is ComposeProgressState.Progress -> {
                        inAppNotification.onProgress(Message.Compose, it.current, it.max)
                    }

                    ComposeProgressState.Success -> {
                        inAppNotification.onSuccess(Message.Compose)
                    }
                }
            }
        }
    }

    operator fun invoke(
        accounts: List<UiAccount>,
        data: ComposeData,
        groupId: String,
        referencePost: UiTimelineV2? = null,
        referenceShareImageRenderer: ReferenceShareImageRenderer? = null,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        scope.launch {
            val result =
                tryRun {
                    progress.invoke(ComposeProgressState.Progress(0, 1))
                    appDataStore.composeConfigData.updateData {
                        it.copy(
                            visibility = data.visibility,
                            lastAccounts =
                                if (data.referenceStatus == null) {
                                    accounts.map { account -> account.accountKey }
                                } else {
                                    it.lastAccounts
                                },
                        )
                    }
                    val bundle = data.toComposeDraftBundle(accounts = accounts, groupId = groupId)
                    val referenceStatus = data.referenceStatus
                    val crossPlatformAccounts =
                        if (referenceStatus == null) {
                            emptyList()
                        } else {
                            accounts.filter { account ->
                                referenceStatus.sourcePlatform == null ||
                                    requiresReferenceShareImage(
                                        sourcePlatform = referenceStatus.sourcePlatform,
                                        sourceAccountKey = referenceStatus.sourceAccountKey,
                                        targetAccount = account,
                                    )
                            }
                        }
                    if (crossPlatformAccounts.isNotEmpty()) {
                        saveDraftUseCase(bundle, targetStatus = DraftTargetStatus.PREPARING)
                        val renderFailure =
                            try {
                                val renderer =
                                    requireNotNull(referenceShareImageRenderer) {
                                        "Cross-platform reference image renderer is unavailable."
                                    }
                                val post =
                                    requireNotNull(referencePost) {
                                        "Referenced post is unavailable."
                                    }
                                val shareMedia = renderer.renderAndAwait(post)
                                sendDraftUseCase(
                                    bundle =
                                        bundle.copy(
                                            template =
                                                data.copy(
                                                    referenceStatus =
                                                        requireNotNull(referenceStatus).copy(shareMedia = shareMedia),
                                                ),
                                        ),
                                    progress = progress,
                                )
                                null
                            } catch (throwable: Exception) {
                                throwable
                            }
                        if (renderFailure != null) {
                            crossPlatformAccounts.forEach { account ->
                                draftRepository.updateTargetStatus(
                                    groupId = groupId,
                                    accountKey = account.accountKey,
                                    status = DraftTargetStatus.FAILED,
                                    errorMessage = renderFailure.message,
                                )
                            }
                            val nativeAccounts = accounts - crossPlatformAccounts.toSet()
                            val nativeFailures =
                                if (nativeAccounts.isNotEmpty()) {
                                    sendDraftUseCase.sendPersistedTargets(
                                        groupId = groupId,
                                        accounts = nativeAccounts,
                                        data = data,
                                        progress = progress,
                                    )
                                } else {
                                    emptyList()
                                }
                            progress(
                                ComposeProgressState.Error(
                                    ComposeDraftFailedException(listOf(renderFailure) + nativeFailures),
                                ),
                            )
                        }
                    } else {
                        sendDraftUseCase(
                            bundle = bundle,
                            progress = progress,
                        )
                    }
                }
            result.exceptionOrNull()?.let { throwable ->
                accounts.forEach { account ->
                    runCatching {
                        draftRepository.updateTargetStatus(
                            groupId = groupId,
                            accountKey = account.accountKey,
                            status = DraftTargetStatus.FAILED,
                            errorMessage = throwable.message,
                        )
                    }
                }
                progress(ComposeProgressState.Error(throwable))
            }
        }
    }

    fun saveDraft(
        accounts: List<UiAccount>,
        data: ComposeData,
        groupId: String = newDraftGroupId(),
    ) {
        scope.launch {
            tryRun {
                saveDraftUseCase(data.toComposeDraftBundle(accounts = accounts, groupId = groupId))
            }
        }
    }
}

@Immutable
internal sealed interface ComposeProgressState {
    @Immutable
    data object Success : ComposeProgressState

    @Immutable
    data class Progress(
        val current: Int,
        val max: Int,
    ) : ComposeProgressState

    @Immutable
    data class Error(
        val throwable: Throwable,
    ) : ComposeProgressState
}
