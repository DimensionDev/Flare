package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AddReactionPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<AddReactionState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): AddReactionState {
        val service =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map { service ->
                service as AuthenticatedMicroblogDataSource
            }
        val data =
            service
                .flatMap {
                    val emoji = remember(it) { it.composeConfig(ComposeType.Reply).emoji?.emoji }
                    if (emoji != null) {
                        emoji.collectAsState().toUi()
                    } else {
                        UiState.Success(persistentMapOf())
                    }
                }.map {
                    remember(it) {
                        EmojiData(it, accountType)
                    }
                }

        val status =
            remember(statusKey, accountType) {
                StatusPresenter(accountType = accountType, statusKey = statusKey)
            }.body().status
        return object : AddReactionState {
            override val emojis = data

            override fun select(emoji: UiEmoji) {
                service.onSuccess { dataSource ->
                    val postDataSource = dataSource as? PostDataSource ?: return@onSuccess
                    status.onSuccess { status ->
                        scope.launch {
                            if (status is UiTimelineV2.Post) {
                                val hasReacted = status.emojiReactions.any { it.me && it.name == emoji.shortcode }
                                val count =
                                    status.emojiReactions.sumOf { it.count.value }
                                postDataSource.postEventHandler.handleEvent(
                                    PostEvent.Misskey.React(
                                        postKey = statusKey,
                                        hasReacted = hasReacted,
                                        reaction = emoji.shortcode,
                                        count = count,
                                        accountKey = dataSource.accountKey,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
public interface AddReactionState {
    public val emojis: UiState<EmojiData>

    public fun select(emoji: UiEmoji)
}
