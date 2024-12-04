package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuestConfigPresenter :
    PresenterBase<GuestConfigPresenter.State>(),
    KoinComponent {
    private val accountRepository by inject<AccountRepository>()

    @Immutable
    interface State {
        val data: UiState<String>
        val platformType: UiState<PlatformType>

        fun setHost(value: String)

        val canSave: Boolean

        fun save(
            host: String,
            platformType: PlatformType,
        )
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val guestData by accountRepository.guestData.collectAsUiState()
        var host by remember { mutableStateOf("") }
        val hostFlow =
            remember {
                snapshotFlow { host }
                    .debounce(333L)
            }
        guestData.onSuccess {
            LaunchedEffect(Unit) {
                host = it.host
            }
        }
        val detectedPlatformType by remember(hostFlow) {
            hostFlow.flatMapLatest {
                flow {
                    runCatching {
                        emit(UiState.Loading())
                        NodeInfoService.detectPlatformType(it)
                    }.onSuccess {
                        emit(UiState.Success(it))
                    }.onFailure {
                        emit(UiState.Error(it))
                    }
                }
            }
        }.collectAsState(UiState.Loading())
        val canSave =
            detectedPlatformType is UiState.Success &&
                host.isNotBlank() &&
                detectedPlatformType.takeSuccess() in listOf(PlatformType.Mastodon)
        return object : State {
            override val data = guestData.map { it.host }
            override val platformType = detectedPlatformType

            override fun setHost(value: String) {
                host = value
            }

            override fun save(
                host: String,
                platformType: PlatformType,
            ) {
                accountRepository.setGuestData(
                    host = host,
                    platformType = platformType,
                )
            }

            override val canSave = canSave
        }
    }
}
