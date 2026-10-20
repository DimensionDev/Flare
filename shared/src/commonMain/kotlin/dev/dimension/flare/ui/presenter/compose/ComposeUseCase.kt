package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.data.repository.newDraftGroupId
import dev.dimension.flare.data.repository.toComposeDraftBundle
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.CancellationException
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
) {
    operator fun invoke(
        accounts: List<UiAccount>,
        data: ComposeData,
        groupId: String,
        onPrepared: suspend () -> Unit = {},
        onPreparationFailed: suspend () -> Unit = {},
    ) {
        invoke(
            accounts = accounts,
            data = data,
            groupId = groupId,
            onPrepared = onPrepared,
            onPreparationFailed = onPreparationFailed,
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
        onPrepared: suspend () -> Unit = {},
        onPreparationFailed: suspend () -> Unit = {},
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        scope.launch {
            var prepared = false
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
                sendDraftUseCase(
                    bundle = data.toComposeDraftBundle(accounts = accounts, groupId = groupId),
                    onPrepared = {
                        prepared = true
                        onPrepared()
                    },
                    progress = progress,
                )
            }.onFailure { error ->
                if (!prepared) onPreparationFailed()
                if (error is CancellationException) throw error
                progress(ComposeProgressState.Error(error))
            }
        }
    }

    fun saveDraft(
        accounts: List<UiAccount>,
        data: ComposeData,
        groupId: String = newDraftGroupId(),
        onSaved: suspend () -> Unit = {},
        onFailure: suspend () -> Unit = {},
    ) {
        scope.launch {
            tryRun {
                saveDraftUseCase(data.toComposeDraftBundle(accounts = accounts, groupId = groupId))
                onSaved()
            }.onFailure { error ->
                onFailure()
                if (error is CancellationException) throw error
                withContext(Dispatchers.Main) { inAppNotification.onError(Message.Compose, error) }
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
