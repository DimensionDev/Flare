package dev.dimension.flare.ui.presenter.profile.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlueskyUserTimelinePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
    private val type: ProfileTab.Timeline.Type
) : TimelinePresenter(), KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun listState(): PagingState<UiTimeline> {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        return serviceState
            .map { service ->
                require(service is BlueskyDataSource)
                val actualUserKey = userKey ?: service.accountKey ?: throw NoActiveAccountException
                remember(service, userKey, type) {
                    when (type) {
                        ProfileTab.Timeline.Type.Status -> {
                            service.userTimeline(
                                userKey = actualUserKey,
                                scope = scope,
                                mediaOnly = false,
                                pagingKey = "user_timeline_${actualUserKey}_status"
                            )
                        }
                        ProfileTab.Timeline.Type.StatusWithReplies -> {
                            service.userTimeline(
                                userKey = actualUserKey,
                                scope = scope,
                                mediaOnly = false,
                                pagingKey = "user_timeline_${actualUserKey}_replies"
                            )
                        }
                        ProfileTab.Timeline.Type.Likes -> {
                            service.userTimeline(
                                userKey = actualUserKey,
                                scope = scope,
                                mediaOnly = false,
                                pagingKey = "user_timeline_${actualUserKey}_likes"
                            )
                        }
                    }
                }.collectAsLazyPagingItems()
            }.toPagingState()
    }
} 