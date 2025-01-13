package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MisskeyReactionPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<MisskeyReactionState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): MisskeyReactionState {
        val service =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map { service ->
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
                                        reaction = ":${emoji.shortcode}:",
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

@Immutable
public interface MisskeyReactionState {
    public val emojis: UiState<ImmutableListWrapper<UiEmoji>>

    public fun select(emoji: UiEmoji)
}
