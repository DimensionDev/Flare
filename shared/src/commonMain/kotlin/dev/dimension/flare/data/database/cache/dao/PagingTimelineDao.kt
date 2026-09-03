package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Delete
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RewriteQueriesToDropUnusedColumns
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

internal data class DbTimelinePageIdentity(
    val statusId: String,
    val sortId: Long,
    val messageRenderHash: Int?,
    val contentRevision: Long,
)

internal data class DbPagingTimelineVersion(
    val pagingKey: String,
    val statusId: String,
    val sortId: Long,
    val messageRenderHash: Int?,
    val semanticReferenceSignature: String,
    val presentationReferenceSignature: String,
    val contentRevision: Long,
    val _id: String,
)

internal data class DbTimelineRootRow(
    @Embedded
    val timeline: DbPagingTimeline,
    @Embedded(prefix = "status_")
    val status: DbStatus,
)

private const val QUERY_BATCH_SIZE = 500
private const val TIMELINE_WITH_STATUS_QUERY =
    "SELECT " +
        "DbPagingTimeline.pagingKey AS pagingKey, " +
        "DbPagingTimeline.statusId AS statusId, " +
        "DbPagingTimeline.sortId AS sortId, " +
        "DbPagingTimeline.message AS message, " +
        "DbPagingTimeline.messageRenderHash AS messageRenderHash, " +
        "DbPagingTimeline.semanticReferenceSignature AS semanticReferenceSignature, " +
        "DbPagingTimeline.presentationReferenceSignature AS presentationReferenceSignature, " +
        "DbPagingTimeline.contentRevision AS contentRevision, " +
        "DbPagingTimeline._id AS _id, " +
        "DbStatus.statusKey AS status_statusKey, " +
        "DbStatus.accountType AS status_accountType, " +
        "DbStatus.content AS status_content, " +
        "DbStatus.contentFingerprint AS status_contentFingerprint, " +
        "DbStatus.renderHash AS status_renderHash, " +
        "DbStatus.text AS status_text, " +
        "DbStatus.id AS status_id " +
        "FROM DbPagingTimeline " +
        "INNER JOIN DbStatus ON DbStatus.id = DbPagingTimeline.statusId "

private fun DbStatus.withTranslations(translationsByStatusId: Map<String, List<DbTranslation>>) =
    DbStatusWithUser(
        data = this,
        translations = translationsByStatusId[id].orEmpty(),
    )

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
internal interface PagingTimelineDao {
    @Transaction
    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey AND DbStatus.accountType = :accountType " +
            "ORDER BY DbPagingTimeline.sortId",
    )
    fun getPagingSource(
        pagingKey: String,
        accountType: DbAccountType,
    ): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId",
    )
    fun getPagingSource(pagingKey: String): PagingSource<Int, DbPagingTimelineWithStatus>

    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun getTimelineRootRows(
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbTimelineRootRow>

    @Query(
        "SELECT * FROM status_reference " +
            "WHERE statusId IN (:statusIds) " +
            "ORDER BY statusId, referenceOrder, referenceType, referenceStatusId",
    )
    suspend fun getPageStatusReferences(statusIds: List<String>): List<DbStatusReference>

    @Query(
        "SELECT * FROM timeline_item_presentation_reference " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds) " +
            "ORDER BY statusId, referenceOrder, presentationType, referenceStatusId",
    )
    suspend fun getPagePresentationReferences(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbTimelineItemPresentationReference>

    @Query("SELECT * FROM DbStatus WHERE id IN (:ids)")
    suspend fun getPageStatuses(ids: List<String>): List<DbStatus>

    @Query(
        "SELECT * FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey IN (:entityKeys)",
    )
    suspend fun getPageTranslations(
        entityKeys: List<String>,
        entityType: TranslationEntityType = TranslationEntityType.Status,
    ): List<DbTranslation>

    @Transaction
    suspend fun getTimelinePage(
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> {
        val roots = getTimelineRootRows(pagingKey, offset, limit)
        if (roots.isEmpty()) {
            return emptyList()
        }
        val rootIds = roots.map { it.status.id }
        val semanticReferences =
            rootIds.chunked(QUERY_BATCH_SIZE).flatMap { getPageStatusReferences(it) }
        val presentationReferences =
            rootIds.chunked(QUERY_BATCH_SIZE).flatMap { getPagePresentationReferences(pagingKey, it) }
        val rootStatuses = roots.associate { it.status.id to it.status }
        val referencedStatusIds =
            buildSet {
                semanticReferences.mapTo(this) { it.referenceStatusId }
                presentationReferences.mapTo(this) { it.referenceStatusId }
            } - rootStatuses.keys
        val statusById =
            rootStatuses +
                referencedStatusIds
                    .chunked(QUERY_BATCH_SIZE)
                    .flatMap { getPageStatuses(it) }
                    .associateBy { it.id }
        val translationsByStatusId =
            statusById.keys
                .chunked(QUERY_BATCH_SIZE)
                .flatMap { getPageTranslations(it) }
                .groupBy { it.entityKey }
        val hydratedStatusById =
            statusById.mapValues { (_, status) -> status.withTranslations(translationsByStatusId) }
        val semanticByRoot = semanticReferences.groupBy { it.statusId }
        val presentationByRoot = presentationReferences.groupBy { it.statusId }
        return roots.map { root ->
            DbPagingTimelineWithStatus(
                timeline = root.timeline,
                status =
                    DbStatusWithReference(
                        status = hydratedStatusById.getValue(root.status.id),
                        references =
                            semanticByRoot[root.status.id].orEmpty().map { reference ->
                                DbStatusReferenceWithStatus(
                                    reference = reference,
                                    status = hydratedStatusById[reference.referenceStatusId],
                                )
                            },
                    ),
                presentationReferences =
                    presentationByRoot[root.status.id].orEmpty().map { reference ->
                        DbTimelineItemPresentationReferenceWithStatus(
                            reference = reference,
                            status = hydratedStatusById[reference.referenceStatusId],
                        )
                    },
            )
        }
    }

    @Query(
        "SELECT " +
            "DbPagingTimeline.statusId AS statusId, " +
            "DbPagingTimeline.sortId AS sortId, " +
            "DbPagingTimeline.messageRenderHash AS messageRenderHash, " +
            "DbPagingTimeline.contentRevision AS contentRevision " +
            "FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId " +
            "LIMIT :limit OFFSET :offset",
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
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "AND DbStatus.accountType = :accountType " +
            "LIMIT 1",
    )
    fun get(
        pagingKey: String,
        accountType: DbAccountType,
    ): Flow<DbPagingTimelineWithStatus?>

    @Transaction
    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId DESC",
    )
    fun getStatusHistoryPagingSource(pagingKey: String): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId DESC " +
            "LIMIT :limit",
    )
    suspend fun getStatusHistoryPage(
        pagingKey: String,
        limit: Int,
    ): List<DbPagingTimelineWithStatus>

    @Upsert
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Insert
    suspend fun insertNew(timeline: List<DbPagingTimeline>)

    @Update
    suspend fun updateExisting(timeline: List<DbPagingTimeline>)

    @Upsert
    suspend fun insertPresentationReferences(items: List<DbTimelineItemPresentationReference>)

    @Insert
    suspend fun insertNewPresentationReferences(items: List<DbTimelineItemPresentationReference>)

    @Update
    suspend fun updateExistingPresentationReferences(items: List<DbTimelineItemPresentationReference>)

    @Delete
    suspend fun deletePresentationReferences(items: List<DbTimelineItemPresentationReference>)

    @Query(
        "DELETE FROM timeline_item_presentation_reference " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds)",
    )
    suspend fun deletePresentationReferences(
        pagingKey: String,
        statusIds: List<String>,
    )

    @Query(
        "SELECT * FROM timeline_item_presentation_reference " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds)",
    )
    suspend fun getPresentationReferences(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbTimelineItemPresentationReference>

    @Query("DELETE FROM timeline_item_presentation_reference WHERE pagingKey = :pagingKey")
    suspend fun deletePresentationReferences(pagingKey: String)

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds)",
    )
    suspend fun getByPagingKeyAndStatusIds(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbPagingTimeline>

    @Query(
        "SELECT pagingKey, statusId, sortId, messageRenderHash, semanticReferenceSignature, " +
            "presentationReferenceSignature, contentRevision, _id FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds)",
    )
    suspend fun getVersionsByPagingKeyAndStatusIds(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbPagingTimelineVersion>

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey ORDER BY sortId",
    )
    suspend fun getByPagingKey(pagingKey: String): List<DbPagingTimeline>

    @Query("SELECT MIN(sortId) FROM DbPagingTimeline WHERE pagingKey = :pagingKey")
    suspend fun getMinSortId(pagingKey: String): Long?

    @Transaction
    @Query(
        TIMELINE_WITH_STATUS_QUERY +
            "WHERE DbStatus.accountType = :accountType " +
            "AND (:afterId IS NULL OR DbPagingTimeline._id > :afterId) " +
            "ORDER BY DbPagingTimeline._id " +
            "LIMIT :limit",
    )
    suspend fun getByAccountTypeWithStatus(
        accountType: DbAccountType,
        afterId: String?,
        limit: Int,
    ): List<DbPagingTimelineWithStatus>

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
        nextKey: String?,
    )

    @Query("UPDATE DbPagingKey SET prevKey = :prevKey WHERE pagingKey = :pagingKey")
    suspend fun updatePagingKeyPrevKey(
        pagingKey: String,
        prevKey: String,
    )
}
