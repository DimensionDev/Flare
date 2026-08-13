package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.accountKeyOrNull
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.createEmojiData
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

public class AddReactionPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<AddReactionState>() {
    // using io scope because it's a long-running operation
    private val scope by koinInject<CoroutineScope>()
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): AddReactionState {
        val service =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map { service ->
                requireNotNull(service.capabilities.compose)
                service
            }
        val data =
            service
                .flatMap {
                    val compose = requireNotNull(it.capabilities.compose)
                    val emoji = remember(it) { compose.composeConfig(ComposeType.Reply).emoji?.emoji }
                    if (emoji != null) {
                        emoji.collectAsState().toUi()
                    } else {
                        UiState.Success(persistentMapOf())
                    }
                }.map {
                    remember(it) {
                        createEmojiData(it, accountType)
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
                    val postDataSource = dataSource.capabilities.post ?: return@onSuccess
                    val accountKey = dataSource.accountKeyOrNull ?: return@onSuccess
                    status.onSuccess { status ->
                        scope.launch {
                            val post = status.contentPostOrNull()
                            if (post != null) {
                                val hasReacted = post.emojiReactions.any { it.me && it.name == emoji.shortcode }
                                val count =
                                    post.emojiReactions.sumOf { it.count.value }
                                postDataSource.postEventHandler.handleEvent(
                                    PostEvent.Misskey.React(
                                        postKey = statusKey,
                                        hasReacted = hasReacted,
                                        reaction = emoji.shortcode,
                                        count = count,
                                        accountKey = accountKey,
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
