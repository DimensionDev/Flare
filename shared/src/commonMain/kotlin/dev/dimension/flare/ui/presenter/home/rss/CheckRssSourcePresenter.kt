package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

public class CheckRssSourcePresenter : PresenterBase<CheckRssSourcePresenter.State>() {
    public interface State {
        public val isValid: UiState<Boolean>
        public val defaultTitle: UiState<String>

        public fun setUrl(value: String)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        var url by remember { mutableStateOf("") }
        val feedData by
            remember {
                snapshotFlow { url }
                    .debounce(500)
                    .flatMapLatest {
                        flow {
                            runCatching {
                                emit(UiState.Loading())
                                RssService.fetch(it)
                            }.onSuccess {
                                emit(UiState.Success(it))
                            }.onFailure {
                                emit(UiState.Error(it))
                            }
                        }
                    }
            }.flattenUiState()

        val isValid =
            remember(feedData) {
                feedData.map {
                    true
                }
            }

        val defaultTitle =
            remember(feedData) {
                feedData.map {
                    it.title
                }
            }

        return object : State {
            override val isValid = isValid
            override val defaultTitle = defaultTitle

            override fun setUrl(value: String) {
                url = value
            }
        }
    }
}
