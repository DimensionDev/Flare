package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.datasource.flare.FlareDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class FlareServerProviderPresenter :
    PresenterBase<FlareServerProviderPresenter.State>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()
    private val scope by inject<CoroutineScope>()

    public interface State {
        public val currentServer: UiState<String>
        public val serverValidation: UiState<Unit>

        public fun checkServer(value: String)

        public fun confirm()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val current by remember {
            appDataStore.flareDataStore.data.map { it.serverUrl }
        }.collectAsUiState()
        var server by remember { mutableStateOf("") }
        val serverFlow =
            remember {
                snapshotFlow { server }
                    .distinctUntilChanged()
            }

        val serverValidation =
            remember(serverFlow) {
                serverFlow.flatMapLatest {
                    flow {
                        tryRun {
                            emit(UiState.Loading())
                            val dataSource = FlareDataSource(it)
                            dataSource.about()
                        }.onSuccess {
                            emit(UiState.Success(Unit))
                        }.onFailure {
                            emit(UiState.Error(it))
                        }
                    }
                }
            }.collectAsState(UiState.Loading())

        return object : State {
            override val currentServer: UiState<String> = current
            override val serverValidation: UiState<Unit> = serverValidation.value

            override fun checkServer(value: String) {
                server = value
            }

            override fun confirm() {
                scope.launch {
                    appDataStore.flareDataStore.updateData { it.copy(serverUrl = server) }
                }
            }
        }
    }
}
