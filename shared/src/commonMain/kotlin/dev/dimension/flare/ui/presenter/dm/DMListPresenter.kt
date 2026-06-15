package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessagePinCodeStatus
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DMListPresenter(
    private val accountType: AccountType,
) : PresenterBase<DMListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DMListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
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
                        remember(service, canLoad) {
                            service.directMessageList(scope = scope)
                        }.collectAsLazyPagingItems()
                    } else {
                        remember(service, canLoad) {
                            flowOf(PagingData.empty<UiDMRoom>())
                        }.collectAsLazyPagingItems()
                    }
                }.toPagingState()
        return object : DMListState {
            override val items = items

            override val pinCodeStatus = pinCodeStatus

            override val pinCodePromptVisible = pinCodeStatus.pinCodePromptVisible

            override val pinCodeVerifying = pinCodeStatus.pinCodeVerifying

            override val pinCodeErrorMessage = pinCodeStatus.pinCodeErrorMessage

            override val isRefreshing = items.isRefreshing

            override fun submitPinCode(pinCode: String) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    scope.launch {
                        it.submitDirectMessagePinCode(pinCode)
                    }
                }
            }

            override suspend fun refreshSuspend() {
                items.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }
}

@Immutable
public interface DMListState {
    public val items: PagingState<UiDMRoom>
    public val pinCodeStatus: UiState<DirectMessagePinCodeStatus>
    public val pinCodePromptVisible: Boolean
    public val pinCodeVerifying: Boolean
    public val pinCodeErrorMessage: String?
    public val isRefreshing: Boolean

    public fun submitPinCode(pinCode: String)

    public suspend fun refreshSuspend()
}

internal val UiState<DirectMessagePinCodeStatus>.canLoadDirectMessage: Boolean
    get() =
        (this as? UiState.Success)
            ?.data
            ?.let {
                it == DirectMessagePinCodeStatus.NotRequired ||
                    it == DirectMessagePinCodeStatus.Verified
            } == true

internal val UiState<DirectMessagePinCodeStatus>.pinCodePromptVisible: Boolean
    get() =
        (this as? UiState.Success)
            ?.data
            ?.let {
                it != DirectMessagePinCodeStatus.NotRequired &&
                    it != DirectMessagePinCodeStatus.Verified
            } == true

internal val UiState<DirectMessagePinCodeStatus>.pinCodeVerifying: Boolean
    get() = (this as? UiState.Success)?.data == DirectMessagePinCodeStatus.Verifying

internal val UiState<DirectMessagePinCodeStatus>.pinCodeErrorMessage: String?
    get() =
        ((this as? UiState.Success)?.data as? DirectMessagePinCodeStatus.Error)
            ?.message
