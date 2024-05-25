package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.microblog.BlueskyComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.MastodonComposeData
import dev.dimension.flare.data.datasource.microblog.MisskeyComposeData
import dev.dimension.flare.data.datasource.microblog.VVOComposeData
import dev.dimension.flare.data.datasource.microblog.XQTComposeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ComposeUseCase(
    private val scope: CoroutineScope,
) {
    operator fun invoke(
        data: ComposeData,
        progress: (ComposeProgressState) -> Unit,
    ) {
        scope.launch {
            runCatching {
                when (data) {
                    is MastodonComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )
                    is MisskeyComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )

                    is BlueskyComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )
                    is XQTComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )
                    is VVOComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )
                }
            }.onSuccess {
                progress.invoke(ComposeProgressState.Success)
            }.onFailure {
                it.printStackTrace()
                progress.invoke(ComposeProgressState.Error(it))
            }
        }
    }
}

sealed interface ComposeProgressState {
    data object Success : ComposeProgressState

    data class Progress(val current: Int, val max: Int) : ComposeProgressState

    data class Error(val throwable: Throwable) : ComposeProgressState
}
