package dev.dimension.flare.ui.presenter.profile.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.flatMap
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MisskeyUserMediaPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?
) : PresenterBase<PagingState<ProfileMedia>>(), KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): PagingState<ProfileMedia> {
        val scope = rememberCoroutineScope()
        val accountServiceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        return accountServiceState
            .map { service ->
                require(service is MisskeyDataSource)
                val actualUserKey = userKey ?: if (service is AuthenticatedMicroblogDataSource) {
                    service.accountKey
                } else {
                    null
                } ?: throw NoActiveAccountException
                remember(service, userKey) {
                    service
                        .userTimeline(
                            userKey = actualUserKey,
                            scope = scope,
                            mediaOnly = true,
                        ).map { data ->
                            data.flatMap { status ->
                                val content = status.content
                                if (content is UiTimeline.ItemContent.Status) {
                                    content.images.map {
                                        ProfileMedia(
                                            it,
                                            status,
                                            content.images.indexOf(it),
                                        )
                                    }
                                } else {
                                    emptyList()
                                }
                            }
                        }
                }.collectAsLazyPagingItems()
            }.toPagingState()
    }
} 