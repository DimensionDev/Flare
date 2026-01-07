package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ImportAppDatabasePresenter(
    private val jsonContent: String,
) : PresenterBase<UiState<Unit>>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    @Composable
    override fun body(): UiState<Unit> {
        var state by remember { mutableStateOf<UiState<Unit>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                import()
                state = UiState.Success(Unit)
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }

    public suspend fun import() {
        val export = jsonContent.decodeJson<AppDatabaseExport>()

        export.accounts.forEach { appDatabase.accountDao().insert(it) }

        export.applications.forEach { appDatabase.applicationDao().insert(it) }

        export.keywordFilters.forEach { appDatabase.keywordFilterDao().insert(it) }

        export.searchHistories.forEach { appDatabase.searchHistoryDao().insert(it) }

        if (export.rssSources.isNotEmpty()) {
            appDatabase.rssSourceDao().insertAll(export.rssSources)
        }
    }
}
