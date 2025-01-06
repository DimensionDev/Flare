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
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.supportedGuestPlatforms
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class GuestConfigPresenter :
    PresenterBase<GuestConfigPresenter.State>(),
    KoinComponent {
    private val accountRepository by inject<AccountRepository>()
    private val appDataStore by inject<AppDataStore>()
    private val coroutineScope by inject<CoroutineScope>()

    @Immutable
    public interface State {
        public val data: UiState<String>
        public val platformType: UiState<PlatformType>
        public val supportedPlatforms: ImmutableList<PlatformType>

        public fun setHost(value: String)

        public val canSave: Boolean

        public fun save(
            host: String,
            platformType: PlatformType,
        )
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val guestData by appDataStore.guestDataStore.data.collectAsUiState()
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
        val detectedPlatformType by remember<Flow<UiState<PlatformType>>>(hostFlow) {
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
                detectedPlatformType.takeSuccess() in supportedGuestPlatforms
        return object : State {
            override val data = guestData.map { it.host }
            override val platformType = detectedPlatformType
            override val supportedPlatforms: ImmutableList<PlatformType>
                get() = supportedGuestPlatforms.toImmutableList()

            override fun setHost(value: String) {
                host = value
            }

            override fun save(
                host: String,
                platformType: PlatformType,
            ) {
                coroutineScope.launch {
                    appDataStore.guestDataStore.updateData {
                        it.copy(
                            host = host,
                            platformType = platformType,
                        )
                    }
                }
            }

            override val canSave = canSave
        }
    }
}
