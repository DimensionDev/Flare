package dev.dimension.flare.ui.presenter.profile.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
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

public class MastodonUserTimelinePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
    private val type: ProfileTab.Timeline.Type,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun listState(): PagingState<UiTimeline> {
        val scope = rememberCoroutineScope()
        val serviceState =
            accountServiceProvider(
                accountType = accountType,
                repository = accountRepository,
            )
        return serviceState
            .map { service ->
                require(service is MastodonDataSource)
                val actualUserKey = userKey ?: service.accountKey ?: throw NoActiveAccountException
                remember(service, userKey, type) {
                    when (type) {
                        ProfileTab.Timeline.Type.Status -> {
                            service
                                .profileTabs(
                                    userKey = actualUserKey,
                                    scope = scope,
                                ).filterIsInstance<ProfileTab.Timeline>()
                                .first { it.type == type }
                                .flow
                        }
                        ProfileTab.Timeline.Type.StatusWithReplies -> {
                            service
                                .profileTabs(
                                    userKey = actualUserKey,
                                    scope = scope,
                                ).filterIsInstance<ProfileTab.Timeline>()
                                .first { it.type == type }
                                .flow
                        }
                        ProfileTab.Timeline.Type.Likes -> {
                            service
                                .profileTabs(
                                    userKey = actualUserKey,
                                    scope = scope,
                                ).filterIsInstance<ProfileTab.Timeline>()
                                .first { it.type == type }
                                .flow
                        }
                    }
                }.collectAsLazyPagingItems()
            }.toPagingState()
    }
}
