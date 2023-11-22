package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.ComposeData
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
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
                    is MastodonDataSource.MastodonComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )

                    is MisskeyDataSource.MisskeyComposeData ->
                        data.account.dataSource.compose(
                            data = data,
                            progress = {
                                progress.invoke(ComposeProgressState.Progress(it.progress, it.total))
                            },
                        )

                    is BlueskyDataSource.BlueskyComposeData ->
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
