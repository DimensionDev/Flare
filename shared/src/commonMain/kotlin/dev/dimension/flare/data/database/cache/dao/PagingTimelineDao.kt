package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RewriteQueriesToDropUnusedColumns
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

internal data class DbTimelinePageIdentity(
    val statusId: String,
    val sortId: Long,
    val rootRenderHash: Int,
    val rootTranslationSignature: String,
    val referenceCount: Long,
    val referenceSignature: String,
)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
internal interface PagingTimelineDao {
    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey AND DbStatus.accountType = :accountType " +
            "ORDER BY DbPagingTimeline.sortId",
    )
    fun getPagingSource(
        pagingKey: String,
        accountType: DbAccountType,
    ): PagingSource<Int, DbStatusWithReference>

    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId",
    )
    fun getPagingSource(pagingKey: String): PagingSource<Int, DbStatusWithReference>

    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun getTimelinePage(
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbStatusWithReference>

    @Query(
        "WITH page AS (" +
            "SELECT " +
            "DbStatus.id AS statusId, " +
            "DbPagingTimeline.sortId AS sortId, " +
            "DbStatus.renderHash AS rootRenderHash " +
            "FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId " +
            "LIMIT :limit OFFSET :offset" +
            "), translation_rows AS (" +
            "SELECT " +
            "DbTranslation.entityKey AS statusId, " +
            "DbTranslation.id || ':' || DbTranslation.status || ':' || DbTranslation.displayMode || ':' || " +
            "DbTranslation.updatedAt || ':' || DbTranslation.sourceHash AS signature " +
            "FROM DbTranslation " +
            "WHERE DbTranslation.entityType = 'Status' " +
            "AND DbTranslation.entityKey IN (" +
            "SELECT statusId FROM page " +
            "UNION " +
            "SELECT status_reference.referenceStatusId FROM status_reference " +
            "WHERE status_reference.statusId IN (SELECT statusId FROM page)" +
            ") " +
            "ORDER BY DbTranslation.id" +
            "), translation_stats AS (" +
            "SELECT " +
            "statusId AS statusId, " +
            "COALESCE(GROUP_CONCAT(signature, '|'), '') AS signature " +
            "FROM translation_rows " +
            "GROUP BY statusId" +
            "), reference_rows AS (" +
            "SELECT " +
            "status_reference.statusId AS statusId, " +
            "status_reference.referenceOrder || ':' || status_reference.referenceType || ':' || " +
            "status_reference.referenceStatusId || ':' || ReferenceStatus.renderHash || ':' || " +
            "COALESCE(translation_stats.signature, '') AS signature " +
            "FROM status_reference " +
            "INNER JOIN DbStatus AS ReferenceStatus ON status_reference.referenceStatusId = ReferenceStatus.id " +
            "LEFT JOIN translation_stats ON status_reference.referenceStatusId = translation_stats.statusId " +
            "WHERE status_reference.statusId IN (SELECT statusId FROM page) " +
            "ORDER BY status_reference.statusId, status_reference.referenceOrder, " +
            "status_reference.referenceType, status_reference.referenceStatusId" +
            "), reference_stats AS (" +
            "SELECT " +
            "reference_rows.statusId AS statusId, " +
            "COUNT(*) AS referenceCount, " +
            "COALESCE(GROUP_CONCAT(reference_rows.signature, '|'), '') AS referenceSignature " +
            "FROM reference_rows " +
            "GROUP BY reference_rows.statusId" +
            ") " +
            "SELECT " +
            "page.statusId AS statusId, " +
            "page.sortId AS sortId, " +
            "page.rootRenderHash AS rootRenderHash, " +
            "COALESCE(root_translation_stats.signature, '') AS rootTranslationSignature, " +
            "COALESCE(reference_stats.referenceCount, 0) AS referenceCount, " +
            "COALESCE(reference_stats.referenceSignature, '') AS referenceSignature " +
            "FROM page " +
            "LEFT JOIN reference_stats ON page.statusId = reference_stats.statusId " +
            "LEFT JOIN translation_stats AS root_translation_stats ON page.statusId = root_translation_stats.statusId " +
            "ORDER BY page.sortId",
    )
    suspend fun getTimelinePageIdentities(
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbTimelinePageIdentity>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM DbStatus " +
            "WHERE DbStatus.text like :query",
    )
    fun searchHistoryPagingSource(query: String): PagingSource<Int, DbStatusWithReference>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM DbStatus " +
            "WHERE DbStatus.text LIKE :query ESCAPE '\\' " +
            "LIMIT :limit",
    )
    suspend fun searchCachedStatuses(
        query: String,
        limit: Int,
    ): List<DbStatusWithReference>

    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "WHERE DbStatus.accountType = :accountType " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbPagingTimeline " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "AND DbPagingTimeline.statusId = DbStatus.id" +
            ") " +
            "LIMIT 1",
    )
    fun get(
        pagingKey: String,
        accountType: DbAccountType,
    ): Flow<DbStatusWithReference?>

    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId DESC",
    )
    fun getStatusHistoryPagingSource(pagingKey: String): PagingSource<Int, DbStatusWithReference>

    @Transaction
    @Query(
        "SELECT DbStatus.* FROM DbStatus " +
            "INNER JOIN DbPagingTimeline ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId DESC " +
            "LIMIT :limit",
    )
    suspend fun getStatusHistoryPage(
        pagingKey: String,
        limit: Int,
    ): List<DbStatusWithReference>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds)",
    )
    suspend fun getByPagingKeyAndStatusIds(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbPagingTimeline>

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey ORDER BY sortId",
    )
    suspend fun getByPagingKey(pagingKey: String): List<DbPagingTimeline>

    @Transaction
    @Query(
        "SELECT DbPagingTimeline.* FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbStatus.accountType = :accountType",
    )
    suspend fun getByAccountTypeWithStatus(accountType: DbAccountType): List<DbPagingTimelineWithStatus>

    @Delete
    suspend fun delete(timeline: List<DbPagingTimeline>)

    @Query(
        "DELETE FROM DbPagingTimeline WHERE pagingKey = :pagingKey " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.id = DbPagingTimeline.statusId " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun delete(
        pagingKey: String,
        accountType: DbAccountType,
    )

    suspend fun delete(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ) {
        delete(pagingKey, AccountType.Specific(accountKey))
    }

    /**
     * Should be used to delete a specific paging timeline by its key.
     */
    @Query("DELETE FROM DbPagingTimeline WHERE pagingKey = :pagingKey")
    suspend fun delete(pagingKey: String)

    @Query(
        "DELETE FROM DbPagingTimeline " +
            "WHERE EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.id = DbPagingTimeline.statusId " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query(
        "DELETE FROM DbPagingTimeline " +
            "WHERE statusId = :statusId " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.id = DbPagingTimeline.statusId " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun deleteStatus(
        accountType: DbAccountType,
        statusId: String,
    )

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM DbPagingTimeline " +
            "WHERE pagingKey = :paging_key " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.id = DbPagingTimeline.statusId " +
            "AND DbStatus.accountType = :accountType" +
            ")" +
            ")",
    )
    suspend fun existsPaging(
        accountType: DbAccountType,
        paging_key: String,
    ): Boolean

    suspend fun existsPaging(
        account_key: MicroBlogKey,
        paging_key: String,
    ): Boolean =
        existsPaging(
            accountType = AccountType.Specific(account_key),
            paging_key = paging_key,
        )

    @Query("DELETE FROM DbPagingTimeline")
    suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM DbPagingTimeline WHERE pagingKey = :pagingKey)")
    suspend fun anyPaging(pagingKey: String): Boolean

    @Query("SELECT * FROM DbPagingKey WHERE pagingKey = :pagingKey LIMIT 1")
    suspend fun getPagingKey(pagingKey: String): DbPagingKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPagingKey(pagingKey: DbPagingKey)

    @Query("DELETE FROM DbPagingKey WHERE pagingKey = :pagingKey")
    suspend fun deletePagingKey(pagingKey: String)

    @Query("UPDATE DbPagingKey SET nextKey = :nextKey WHERE pagingKey = :pagingKey")
    suspend fun updatePagingKeyNextKey(
        pagingKey: String,
        nextKey: String,
    )

    @Query("UPDATE DbPagingKey SET prevKey = :prevKey WHERE pagingKey = :pagingKey")
    suspend fun updatePagingKeyPrevKey(
        pagingKey: String,
        prevKey: String,
    )
}
