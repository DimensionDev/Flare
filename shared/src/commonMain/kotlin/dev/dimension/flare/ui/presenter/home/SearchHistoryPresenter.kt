package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.SearchHistoryRepository
import dev.dimension.flare.ui.model.UiSearchHistory
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import org.koin.compose.koinInject

class SearchHistoryPresenter : PresenterBase<SearchHistoryState>() {
    @Composable
    override fun body(): SearchHistoryState {
        val repository: SearchHistoryRepository = koinInject()
        val searchHistories by remember {
            repository.allSearchHistory
        }.collectAsUiState()

        return object : SearchHistoryState {
            override val searchHistories = searchHistories

            override fun addSearchHistory(keyword: String) {
                repository.addSearchHistory(keyword)
            }

            override fun deleteSearchHistory(keyword: String) {
                repository.deleteSearchHistory(keyword)
            }
        }
    }
}

@Immutable
interface SearchHistoryState {
    val searchHistories: UiState<ImmutableListWrapper<UiSearchHistory>>

    fun addSearchHistory(keyword: String)

    fun deleteSearchHistory(keyword: String)
}
