package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

public class DMConversationPresenter(
    private val accountType: AccountType,
    private val roomKey: MicroBlogKey,
) : PresenterBase<DMConversationState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DMConversationState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val scope = rememberCoroutineScope()
        val items =
            serviceState
                .map { service ->
                    val directMessage = requireNotNull(service.capabilities.directMessage)
                    remember(service, roomKey) {
                        directMessage.directMessageConversation(roomKey, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val users =
            serviceState
                .flatMap { service ->
                    val directMessage = requireNotNull(service.capabilities.directMessage)
                    remember(service, roomKey) {
                        directMessage.getDirectMessageConversationInfo(roomKey)
                    }.collectAsState().toUi()
                }.map {
                    it.users
                }
        serviceState.onSuccess {
            val directMessage = requireNotNull(it.capabilities.directMessage)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(10.seconds)
                    runCatching {
                        directMessage.fetchNewDirectMessageForConversation(roomKey)
                    }
                }
            }
        }
        return object : DMConversationState {
            override val items = items

            override val users = users

            override fun send(message: String) {
                serviceState.onSuccess {
                    requireNotNull(it.capabilities.directMessage).sendDirectMessage(roomKey, message)
                }
            }

            override fun retry(key: MicroBlogKey) {
                serviceState.onSuccess {
                    requireNotNull(it.capabilities.directMessage).retrySendDirectMessage(key)
                }
            }

            override fun leave() {
                serviceState.onSuccess {
                    requireNotNull(it.capabilities.directMessage).leaveDirectMessage(roomKey)
                }
            }
        }
    }
}

@Immutable
public interface DMConversationState {
    public val items: PagingState<UiDMItem>
    public val users: UiState<ImmutableList<UiProfile>>

    public fun send(message: String)

    public fun retry(key: MicroBlogKey)

    public fun leave()
}
