package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class DMConversationPresenter(
    private val accountType: AccountType,
    private val roomKey: MicroBlogKey,
) : PresenterBase<DMConversationState>() {
    @Composable
    override fun body(): DMConversationState {
        val serviceState = accountServiceProvider(accountType = accountType)
        val scope = rememberCoroutineScope()
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, roomKey) {
                        service.directMessageConversation(roomKey, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val users =
            serviceState
                .flatMap { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, roomKey) {
                        service.getDirectMessageConversationInfo(roomKey)
                    }.collectAsState().toUi()
                }.map {
                    it.users
                }
        serviceState.onSuccess {
            require(it is DirectMessageDataSource)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(5.seconds)
                    runCatching {
                        it.fetchNewDirectMessageForConversation(roomKey)
                    }
                }
            }
        }
        return object : DMConversationState {
            override val items = items

            override val users = users

            override fun send(message: String) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.sendDirectMessage(roomKey, message)
                }
            }

            override fun retry(key: MicroBlogKey) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.retrySendDirectMessage(key)
                }
            }

            override fun leave() {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.leaveDirectMessage(roomKey)
                }
            }
        }
    }
}

interface DMConversationState {
    val items: PagingState<UiDMItem>
    val users: UiState<ImmutableList<UiUserV2>>

    fun send(message: String)

    fun retry(key: MicroBlogKey)

    fun leave()
}
