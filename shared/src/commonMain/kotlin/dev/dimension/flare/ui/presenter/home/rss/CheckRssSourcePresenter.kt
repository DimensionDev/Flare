package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.data.network.rss.Rss
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map

public class CheckRssSourcePresenter : PresenterBase<CheckRssSourcePresenter.State>() {
    public interface State {
        public val isValid: UiState<Boolean>

        public fun setUrl(value: String)
    }

    @Composable
    override fun body(): State {
        var url by remember { mutableStateOf("") }
        val isValid =
            remember {
                snapshotFlow { url }
                    .map {
                        runCatching {
                            Rss.fetch(it)
                        }.fold(
                            onSuccess = {
                                UiState.Success(true) as UiState<Boolean>
                            },
                            onFailure = {
                                UiState.Error(it)
                            },
                        )
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
