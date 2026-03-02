package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class OpenAIModelsPresenter :
    PresenterBase<OpenAIModelsPresenter.State>(),
    KoinComponent {
    private val openAIService by inject<OpenAIService>()

    @Immutable
    public interface State {
        public val models: UiState<List<String>>

        public fun check(
            serverUrl: String,
            apiKey: String,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        var currentServerUrl by remember { mutableStateOf("") }
        var currentApiKey by remember { mutableStateOf("") }
        val inputFlow =
            remember {
                snapshotFlow { currentServerUrl to currentApiKey }
                    .distinctUntilChanged()
            }
        val models by remember(inputFlow) {
            inputFlow.flatMapLatest { (url, key) ->
                flow {
                    if (url.isBlank() || key.isBlank()) {
                        emit(UiState.Success(emptyList()))
                        return@flow
                    }
                    tryRun {
                        emit(UiState.Loading())
                        emit(
                            UiState.Success(
                                openAIService.models(
                                    serverUrl = url,
                                    apiKey = key,
                                ),
                            ),
                        )
                    }.onFailure {
                        emit(UiState.Error(it))
                    }
                }
            }
        }.collectAsState(UiState.Success(emptyList()))

        return object : State {
            override val models: UiState<List<String>> = models

            override fun check(
                serverUrl: String,
                apiKey: String,
            ) {
                currentServerUrl = serverUrl.trim()
                currentApiKey = apiKey.trim()
            }
        }
    }
}
