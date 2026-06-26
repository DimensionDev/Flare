package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.ui.presenter.ExportState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.first
import dev.dimension.flare.di.koinInject

public class ExportAppDatabasePresenter :
    PresenterBase<ExportState>() {
    private val appDatabase: AppDatabase by koinInject()

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportAppDatabasePresenter.export()
        }

    public suspend fun export(): String {
        val export =
            AppDatabaseExport(
                accounts = appDatabase.accountDao().allAccounts().first(),
                keywordFilters = appDatabase.keywordFilterDao().selectAll().first(),
                searchHistories = appDatabase.searchHistoryDao().select().first(),
                rssSources = appDatabase.rssSourceDao().getAll().first(),
            )
        return export.encodeJson()
    }
}
