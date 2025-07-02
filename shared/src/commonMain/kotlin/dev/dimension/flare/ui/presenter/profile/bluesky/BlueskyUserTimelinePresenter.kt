package dev.dimension.flare.ui.presenter.profile.bluesky

import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class BlueskyUserTimelinePresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
    private val type: ProfileTab.Timeline.Type,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<BaseTimelineLoader>
        get() =
            accountServiceFlow(
                accountType,
                accountRepository,
            ).map {
                val key =
                    userKey ?: if (it is AuthenticatedMicroblogDataSource) {
                        it.accountKey
                    } else {
                        throw NoActiveAccountException
                    }
                it
                    .profileTabs(key)
                    .filterIsInstance<ProfileTab.Timeline>()
                    .first { it.type == type }
                    .loader
            }
}
