package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class RssDetailPresenter(
    private val url: String,
) : PresenterBase<RssDetailPresenter.State>(),
    KoinComponent {
    private val readability: Readability by inject()

    public interface State {
        public val data: UiState<DocumentData>
    }

    @Composable
    override fun body(): State {
        val data by produceState(UiState.Loading()) {
            value =
                runCatching {
                    readability.parse(url)
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
        return object : State {
            override val data = data
        }
    }
}
