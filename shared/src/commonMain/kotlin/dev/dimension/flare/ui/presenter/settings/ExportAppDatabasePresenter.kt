package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ExportAppDatabasePresenter :
    PresenterBase<UiState<String>>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    @Composable
    override fun body(): UiState<String> {
        var state by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                state = UiState.Success(export())
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }

    public suspend fun export(): String {
        val export =
            AppDatabaseExport(
                accounts = appDatabase.accountDao().allAccounts().first(),
                applications = appDatabase.applicationDao().allApplication().first(),
                keywordFilters = appDatabase.keywordFilterDao().selectAll().first(),
                searchHistories = appDatabase.searchHistoryDao().select().first(),
                rssSources = appDatabase.rssSourceDao().getAll().first(),
            )
        return export.encodeJson()
    }
}
