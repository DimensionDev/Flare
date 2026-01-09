package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.ui.presenter.ImportState
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ImportAppDatabasePresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportAppDatabasePresenter.import()
            }
        }

    public suspend fun import() {
        val export = jsonContent.decodeJson<AppDatabaseExport>()
        appDatabase.connect {
            // Note: Accounts and applications are not imported because the export
            // does not contain credentials for security reasons. Users will need to
            // re-authenticate their accounts after importing.

            export.keywordFilters.forEach { appDatabase.keywordFilterDao().insert(it) }

            export.searchHistories.forEach { appDatabase.searchHistoryDao().insert(it) }

            if (export.rssSources.isNotEmpty()) {
                appDatabase.rssSourceDao().insertAll(export.rssSources)
            }
        }
    }
}
