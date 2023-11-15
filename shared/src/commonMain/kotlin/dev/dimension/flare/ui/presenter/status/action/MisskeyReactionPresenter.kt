package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.rememberKoinInject

class MisskeyReactionPresenter(
    private val statusKey: MicroBlogKey,
) : PresenterBase<MisskeyReactionState>() {
    @Composable
    override fun body(): MisskeyReactionState {
        val service =
            activeAccountServicePresenter().map { (service, _) ->
                service as MisskeyDataSource
            }
        val data =
            service.flatMap {
                it.emoji().collectAsState().toUi()
            }

        val status =
            activeAccountServicePresenter().map { (service, account) ->
                remember(account.accountKey, statusKey) {
                    service.status(statusKey)
                }.collectAsLazyPagingItems()
            }.flatMap {
                if (it.itemCount == 0) {
                    UiState.Loading()
                } else {
                    val item = it[0]
                    if (item == null || item !is UiStatus.Misskey) {
                        UiState.Loading()
                    } else {
                        UiState.Success(item)
                    }
                }
            }
        // using io scope because it's a long-running operation
        val scope = rememberKoinInject<CoroutineScope>()
        return object : MisskeyReactionState {
            override val emojis = data

            override fun select(emoji: UiEmoji) {
                service.onSuccess { dataSource ->
                    status.onSuccess { status ->
                        scope.launch {
                            dataSource.react(status, ":${emoji.shortcode}:")
                        }
                    }
                }
            }
        }
    }
}

interface MisskeyReactionState {
    val emojis: UiState<ImmutableList<UiEmoji>>

    fun select(emoji: UiEmoji)
}
