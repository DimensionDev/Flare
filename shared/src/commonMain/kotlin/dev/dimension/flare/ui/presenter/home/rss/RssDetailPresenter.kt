package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
        remember {
            LogStatusHistoryPresenter(
                accountType = AccountType.Guest,
                statusKey = MicroBlogKey.fromRss(url),
            )
        }.body()
        val data by remember(url) {
            readability
                .parse(url)
                .map {
                    it.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) },
                    )
                }.onStart {
                    emit(UiState.Loading())
                }
        }.collectAsState(UiState.Loading())
        return object : State {
            override val data = data
        }
    }
}
