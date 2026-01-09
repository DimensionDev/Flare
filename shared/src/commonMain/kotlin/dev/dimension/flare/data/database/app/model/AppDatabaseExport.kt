package dev.dimension.flare.data.database.app.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.Serializable

@Serializable
internal data class AppDatabaseExport(
    val accounts: List<DbAccountExport> = emptyList(),
    val applications: List<DbApplicationExport> = emptyList(),
    val keywordFilters: List<DbKeywordFilter> = emptyList(),
    val searchHistories: List<DbSearchHistory> = emptyList(),
    val rssSources: List<DbRssSources> = emptyList(),
)

/**
 * Sanitized version of DbAccount for export that excludes sensitive credential data.
 * This ensures that access tokens and other authentication secrets are not exposed
 * in the exported file.
 */
@Serializable
internal data class DbAccountExport(
    val account_key: MicroBlogKey,
    val platform_type: PlatformType,
    val last_active: Long,
)

/**
 * Sanitized version of DbApplication for export that excludes sensitive credential data.
 * This ensures that OAuth client secrets and other authentication credentials are not
 * exposed in the exported file.
 */
@Serializable
internal data class DbApplicationExport(
    val host: String,
    val platform_type: PlatformType,
    val has_pending_oauth_request: Int = 0,
)

/**
 * Convert DbAccount to sanitized export format by excluding credential_json
 */
internal fun DbAccount.toExport(): DbAccountExport =
    DbAccountExport(
        account_key = account_key,
        platform_type = platform_type,
        last_active = last_active,
    )

/**
 * Convert DbApplication to sanitized export format by excluding credential_json
 */
internal fun DbApplication.toExport(): DbApplicationExport =
    DbApplicationExport(
        host = host,
        platform_type = platform_type,
        has_pending_oauth_request = has_pending_oauth_request,
    )
