package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.datasource.microblog.ComposeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ComposeUseCase(
    private val scope: CoroutineScope,
    private val inAppNotification: InAppNotification,
) {
    operator fun invoke(data: ComposeData) {
        invoke(data) {
            withContext(Dispatchers.Main) {
                when (it) {
                    is ComposeProgressState.Error ->
                        inAppNotification.onError(Message.Compose, it.throwable)
                    is ComposeProgressState.Progress ->
                        inAppNotification.onProgress(Message.Compose, it.current, it.max)
                    ComposeProgressState.Success ->
                        inAppNotification.onSuccess(Message.Compose)
                }
            }
        }
    }

    operator fun invoke(
        data: ComposeData,
        progress: suspend (ComposeProgressState) -> Unit,
    ) {
        scope.launch {
            runCatching {
                progress.invoke(ComposeProgressState.Progress(0, 1))
                data.account.dataSource.compose(
                    data = data,
                    progress = {
                        scope.launch {
                            progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                        }
                    },
                )
            }.onSuccess {
                progress.invoke(ComposeProgressState.Success)
            }.onFailure {
                progress.invoke(ComposeProgressState.Error(it))
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
