package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
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
    private val id: String,
) : PresenterBase<DMConversationState>() {
    @Composable
    override fun body(): DMConversationState {
        val serviceState = accountServiceProvider(accountType = accountType)
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, id) {
                        service.directMessageConversation(id)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val users =
            serviceState
                .flatMap { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, id) {
                        service.getDirectMessageConversationInfo(id)
                    }.collectAsState().toUi()
                }.map {
                    it.users
                }
        LaunchedEffect(Unit) {
            serviceState.onSuccess {
                require(it is DirectMessageDataSource)
                delay(5.seconds)
                it.fetchNewDirectMessageForConversation(id)
            }
        }
        return object : DMConversationState {
            override val items = items

            override val users = users

            override fun send(message: String) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.sendDirectMessage(id, message)
                }
            }
        }
    }
}

interface DMConversationState {
    val items: PagingState<UiDMItem>
    val users: UiState<ImmutableList<UiUserV2>>

    fun send(message: String)
}
