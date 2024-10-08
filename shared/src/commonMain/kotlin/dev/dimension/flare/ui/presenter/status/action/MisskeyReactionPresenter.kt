package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MisskeyReactionPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<MisskeyReactionState>() {
    @Composable
    override fun body(): MisskeyReactionState {
        val service =
            accountServiceProvider(accountType = accountType).map { service ->
                service as MisskeyDataSource
            }
        val data =
            service.flatMap {
                remember(it) {
                    it.emoji()
                }.collectAsState().toUi().map {
                    it.toImmutableListWrapper()
                }
            }

        val status =
            remember(statusKey, accountType) {
                StatusPresenter(accountType = accountType, statusKey = statusKey)
            }.body().status
        // using io scope because it's a long-running operation
        val scope = koinInject<CoroutineScope>()
        return object : MisskeyReactionState {
            override val emojis = data

            override fun select(emoji: UiEmoji) {
                service.onSuccess { dataSource ->
                    status.onSuccess { status ->
                        scope.launch {
                            val content = status.content
                            if (content is UiTimeline.ItemContent.Status) {
                                val bottomContent = content.bottomContent
                                if (bottomContent is UiTimeline.ItemContent.Status.BottomContent.Reaction) {
                                    dataSource.react(
                                        statusKey = statusKey,
                                        hasReacted = bottomContent.myReaction != null,
                                        reaction = emoji.shortcode,
                                    )
                                }
                            }
//                            dataSource.react(status as UiStatus.Misskey, ":${emoji.shortcode}:")
                        }
                    }
                }
            }
        }
    }
}

interface MisskeyReactionState {
    val emojis: UiState<ImmutableListWrapper<UiEmoji>>

    fun select(emoji: UiEmoji)
}
