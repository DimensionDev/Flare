package dev.dimension.flare.data.database.app.model

import kotlinx.serialization.Serializable

@Serializable
internal data class AppDatabaseExport(
    val accounts: List<DbAccount> = emptyList(),
    val applications: List<DbApplication> = emptyList(),
    val keywordFilters: List<DbKeywordFilter> = emptyList(),
    val searchHistories: List<DbSearchHistory> = emptyList(),
    val rssSources: List<DbRssSources> = emptyList(),
)
