package dev.dimension.flare.ui.presenter.home

import androidx.paging.PagingData
import androidx.paging.filter
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.TimelineFilterRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<BaseTimelineLoader> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map {
            it.homeTimeline()
        }
    }

    private val hideRepostsFlow: Flow<Boolean> by lazy {
        try {
            val timelineFilterRepository: TimelineFilterRepository by inject()
            timelineFilterRepository.hideRepostsFlow
        } catch (_: Throwable) {
            flowOf(false)
        }
    }

    private val hideRepliesFlow: Flow<Boolean> by lazy {
        try {
            val timelineFilterRepository: TimelineFilterRepository by inject()
            timelineFilterRepository.hideRepliesFlow
        } catch (_: Throwable) {
            flowOf(false)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimeline>> =
        super.createPager(scope).flatMapLatest { pager ->
            hideRepostsFlow
                .combine(hideRepliesFlow) { hideReposts, hideReplies ->
                    hideReposts to hideReplies
                }.map { (hideReposts, hideReplies) ->
                    pager.filter { item ->
                        val passesRepostFilter =
                            if (hideReposts) {
                                !isRepost(item)
                            } else {
                                true
                            }
                        val passesReplyFilter =
                            if (hideReplies) {
                                !isReply(item)
                            } else {
                                true
                            }
                        passesRepostFilter && passesReplyFilter
                    }
                }
        }

    private fun isRepost(item: UiTimeline): Boolean =
        item.topMessage?.icon == UiTimeline.TopMessage.Icon.Retweet

    // Check if item is a reply to another user (not self-reply).
    // A post is considered a reply if it has ReplyTo aboveTextContent AND the reply target is not the author.
    private fun isReply(item: UiTimeline): Boolean {
        val content = item.content as? UiTimeline.ItemContent.Status ?: return false
        val replyTo =
            content.aboveTextContent as?
                UiTimeline.ItemContent.Status.AboveTextContent.ReplyTo
                ?: return false
        val user = content.user as? UiProfile ?: return true

        // Use UiProfile.handleWithoutAtAndHost for normalization (UiProfile is the only UiUserV2 implementation)
        val authorNormalized = user.handleWithoutAtAndHost.lowercase()
        val replyToNormalized =
            replyTo.handle
                .removePrefix("@")
                .substringBefore("@")
                .lowercase()

        return authorNormalized != replyToNormalized
    }
}
