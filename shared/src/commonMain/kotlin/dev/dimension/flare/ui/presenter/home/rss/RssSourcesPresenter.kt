package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class RssSourcesPresenter :
    PresenterBase<RssSourcesPresenter.State>(),
    KoinComponent {
    private val appDatabase by inject<AppDatabase>()

    public interface State : CheckRssSourcePresenter.State {
        public val sources: PagingState<UiRssSource>

        public fun add(
            url: String,
            title: String,
        )

        public fun delete(url: String)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val sources by remember {
            appDatabase.rssSourceDao().getAll().map {
                it.map {
                    it.render()
                }
            }
        }.collectPagingState()
        val checkRssSourcePresenterState = remember { CheckRssSourcePresenter() }.body()
        return object : State, CheckRssSourcePresenter.State by checkRssSourcePresenterState {
            override val sources: PagingState<UiRssSource> = sources

            override fun add(
                url: String,
                title: String,
            ) {
                scope.launch {
                    appDatabase.rssSourceDao().insert(DbRssSources(url = url, title = title, lastUpdate = 0))
                }
            }

            override fun delete(url: String) {
                scope.launch {
                    appDatabase.rssSourceDao().delete(url)
                }
            }
        }
    }
}
