package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.flatMap
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map

class ProfileMediaPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<ProfileMediaState>() {
    @Composable
    override fun body(): ProfileMediaState {
        val scope = rememberCoroutineScope()
        val accountServiceState = accountServiceProvider(accountType = accountType)
        val mediaState =
            accountServiceState
                .map { service ->
                    val actualUserKey =
                        userKey
                            ?: if (service is AuthenticatedMicroblogDataSource) {
                                service.accountKey
                            } else {
                                null
                            } ?: throw NoActiveAccountException
                    remember(service, userKey) {
                        service
                            .userTimeline(
                                actualUserKey,
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
        return object : ProfileMediaState {
            override val mediaState = mediaState
        }
    }
}

interface ProfileMediaState {
    val mediaState: PagingState<ProfileMedia>
}

data class ProfileMedia(
    val media: UiMedia,
    val status: UiTimeline,
    val index: Int,
)
