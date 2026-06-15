package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessagePinCodeStatus
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

public class DMConversationPresenter(
    private val accountType: AccountType,
    private val roomKey: MicroBlogKey,
) : PresenterBase<DMConversationState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DMConversationState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val scope = rememberCoroutineScope()
        val pinCodeStatus =
            serviceState.flatMap { service ->
                require(service is DirectMessageDataSource)
                val status =
                    remember(service) {
                        service.directMessagePinCodeStatus
                    }.collectAsUiState(
                        initial = UiState.Loading(),
                    )
                status.value
            }
        val canLoad = pinCodeStatus.canLoadDirectMessage
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    if (canLoad) {
                        remember(service, roomKey, canLoad) {
                            service.directMessageConversation(roomKey, scope = scope)
                        }.collectAsLazyPagingItems()
                    } else {
                        remember(service, roomKey, canLoad) {
                            flowOf(PagingData.empty<UiDMItem>())
                        }.collectAsLazyPagingItems()
                    }
                }.toPagingState()
        val users =
            if (canLoad) {
                serviceState
                    .flatMap { service ->
                        require(service is DirectMessageDataSource)
                        remember(service, roomKey) {
                            service.getDirectMessageConversationInfo(roomKey)
                        }.collectAsState().toUi()
                    }.map {
                        it.users
                    }
            } else {
                UiState.Success(persistentListOf())
            }
        if (canLoad) {
            serviceState.onSuccess {
                require(it is DirectMessageDataSource)
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(10.seconds)
                        runCatching {
                            it.fetchNewDirectMessageForConversation(roomKey)
                        }
                    }
                }
            }
        }
        return object : DMConversationState {
            override val items = items

            override val users = users

            override val pinCodeStatus = pinCodeStatus

            override val pinCodePromptVisible = pinCodeStatus.pinCodePromptVisible

            override val pinCodeVerifying = pinCodeStatus.pinCodeVerifying

            override val pinCodeErrorMessage = pinCodeStatus.pinCodeErrorMessage

            override fun submitPinCode(pinCode: String) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    scope.launch {
                        it.submitDirectMessagePinCode(pinCode)
                    }
                }
            }

            override fun send(message: String) {
                if (!canLoad) return
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.sendDirectMessage(roomKey, message)
                }
            }

            override fun retry(key: MicroBlogKey) {
                if (!canLoad) return
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

@Immutable
public interface DMConversationState {
    public val items: PagingState<UiDMItem>
    public val users: UiState<ImmutableList<UiProfile>>
    public val pinCodeStatus: UiState<DirectMessagePinCodeStatus>
    public val pinCodePromptVisible: Boolean
    public val pinCodeVerifying: Boolean
    public val pinCodeErrorMessage: String?

    public fun submitPinCode(pinCode: String)

    public fun send(message: String)

    public fun retry(key: MicroBlogKey)

    public fun leave()
}
