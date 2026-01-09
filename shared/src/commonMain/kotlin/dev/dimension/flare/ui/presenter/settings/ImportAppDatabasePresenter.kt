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
        // Parse and validate JSON structure
        val export =
            try {
                jsonContent.decodeJson<AppDatabaseExport>()
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid import file format: ${e.message}", e)
            }

        // Validate imported data
        validateImportData(export)

        // Perform import within a transaction (automatically rolls back on error)
        // Note: DAO insert methods use OnConflictStrategy.REPLACE - existing records with matching primary keys will be replaced
        appDatabase.connect {
            export.accounts.forEach { appDatabase.accountDao().insert(it) }

            export.applications.forEach { appDatabase.applicationDao().insert(it) }

            export.keywordFilters.forEach { appDatabase.keywordFilterDao().insert(it) }

            export.searchHistories.forEach { appDatabase.searchHistoryDao().insert(it) }

            if (export.rssSources.isNotEmpty()) {
                appDatabase.rssSourceDao().insertAll(export.rssSources)
            }
        }
    }

    private fun validateImportData(export: AppDatabaseExport) {
        // Validate account data
        export.accounts.forEach { account ->
            require(account.credential_json.isNotBlank()) {
                "Invalid account data: credential_json cannot be empty"
            }
        }

        // Validate application data
        export.applications.forEach { application ->
            require(application.host.isNotBlank()) {
                "Invalid application data: host cannot be empty"
            }
        }

        // Validate keyword filters
        export.keywordFilters.forEach { filter ->
            require(filter.keyword.isNotBlank()) {
                "Invalid keyword filter: keyword cannot be empty"
            }
        }

        // Validate search histories
        export.searchHistories.forEach { history ->
            require(history.search.isNotBlank()) {
                "Invalid search history: search term cannot be empty"
            }
        }

        // Validate RSS sources
        export.rssSources.forEach { source ->
            require(source.url.isNotBlank()) {
                "Invalid RSS source: URL cannot be empty"
            }
        }
    }
}
