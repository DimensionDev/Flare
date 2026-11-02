package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

public class RssDetailPresenter(
    private val url: String,
    private val descriptionHtml: String? = null,
    private val descriptionTitle: String? = null,
    private val fallbackHtml: String? = null,
) : PresenterBase<RssDetailPresenter.State>() {
    private val readability = Readability()

    @Immutable
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
        val data by remember(url, descriptionHtml, descriptionTitle, fallbackHtml) {
            flow<UiState<DocumentData>> {
                val result =
                    try {
                        UiState.Success(readability.load(url, descriptionHtml, descriptionTitle, fallbackHtml))
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        UiState.Error(exception)
                    }
                emit(result)
            }.onStart { emit(UiState.Loading()) }
        }.collectAsState(UiState.Loading())
        return object : State {
            override val data = data
        }
    }
}
