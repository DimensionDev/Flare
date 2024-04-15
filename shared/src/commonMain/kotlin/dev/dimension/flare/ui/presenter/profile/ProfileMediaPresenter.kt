package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.flatMap
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.medias
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
            accountServiceState.map { service ->
                remember(service, userKey) {
                    service.userTimeline(
                        userKey ?: service.account.accountKey,
                        scope = scope,
                        mediaOnly = true,
                    ).map { data ->
                        data.flatMap { status ->
                            status.medias.map {
                                ProfileMedia(
                                    it,
                                    status,
                                    status.medias.indexOf(it),
                                )
                            }
                        }
                    }
                }.collectPagingProxy()
            }
        return object : ProfileMediaState {
            override val mediaState = mediaState
        }
    }
}

interface ProfileMediaState {
    val mediaState: UiState<LazyPagingItemsProxy<ProfileMedia>>
}

data class ProfileMedia(
    val media: UiMedia,
    val status: UiStatus,
    val index: Int,
)
