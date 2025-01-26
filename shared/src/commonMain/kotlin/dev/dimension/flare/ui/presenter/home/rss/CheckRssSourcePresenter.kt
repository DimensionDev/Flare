package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

public class CheckRssSourcePresenter : PresenterBase<CheckRssSourcePresenter.State>() {
    public interface State {
        public val isValid: UiState<Boolean>

        public fun setUrl(value: String)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        var url by remember { mutableStateOf("") }
        val isValid =
            remember {
                snapshotFlow { url }
                    .debounce(500)
                    .flatMapLatest {
                        flow {
                            runCatching {
                                emit(UiState.Loading())
                                RssService.fetch(it)
                            }.onSuccess {
                                emit(UiState.Success(true))
                            }.onFailure {
                                emit(UiState.Success(false))
                            }
                        }
                    }
            }.collectAsUiState().value.flatMap { it }

        return object : State {
            override val isValid = isValid

            override fun setUrl(value: String) {
                url = value
            }
        }
    }
}
