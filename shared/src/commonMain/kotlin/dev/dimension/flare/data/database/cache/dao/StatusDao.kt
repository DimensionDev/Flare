package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbTimelineContent
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow

internal data class DbStatusFingerprint(
    val id: String,
    val contentHash: Long,
    val renderHash: Int,
)

internal data class DbStatusId(
    val id: String,
)

@Dao
internal interface StatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: DbStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<DbStatus>)

    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    fun get(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatus?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    fun getWithReferences(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatusWithReference?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun getWithReferencesSync(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): DbStatusWithReference?

    @Query("SELECT id, contentHash, renderHash FROM DbStatus WHERE id IN (:statusIds)")
    suspend fun getFingerprints(statusIds: List<String>): List<DbStatusFingerprint>

    @Query(
        "SELECT timeline.statusId AS id FROM DbPagingTimeline AS timeline " +
            "LEFT JOIN DbStatus AS status ON status.id = timeline.statusId " +
            "WHERE status.id IS NULL " +
            "UNION " +
            "SELECT reference.referenceStatusId AS id FROM status_reference AS reference " +
            "INNER JOIN DbPagingTimeline AS timeline ON timeline.statusId = reference.statusId " +
            "LEFT JOIN DbStatus AS status ON status.id = reference.referenceStatusId " +
            "WHERE status.id IS NULL " +
            "UNION " +
            "SELECT reference.referenceStatusId AS id " +
            "FROM timeline_item_presentation_reference AS reference " +
            "INNER JOIN DbPagingTimeline AS timeline " +
            "ON timeline.pagingKey = reference.pagingKey AND timeline.statusId = reference.statusId " +
            "LEFT JOIN DbStatus AS status ON status.id = reference.referenceStatusId " +
            "WHERE status.id IS NULL",
    )
    suspend fun getMissingTimelineDependencyStatusIds(): List<DbStatusId>

    @Query(
        "UPDATE DbPagingTimeline SET dependencyRevision = dependencyRevision + 1 " +
            "WHERE _id IN (" +
            "SELECT timeline._id FROM DbPagingTimeline AS timeline " +
            "WHERE timeline.statusId IN (:statusIds) " +
            "UNION " +
            "SELECT timeline._id FROM status_reference AS reference " +
            "INNER JOIN DbPagingTimeline AS timeline ON timeline.statusId = reference.statusId " +
            "WHERE reference.referenceStatusId IN (:statusIds) " +
            "UNION " +
            "SELECT timeline._id FROM timeline_item_presentation_reference AS reference " +
            "INNER JOIN DbPagingTimeline AS timeline " +
            "ON timeline.pagingKey = reference.pagingKey AND timeline.statusId = reference.statusId " +
            "WHERE reference.referenceStatusId IN (:statusIds)" +
            ")",
    )
    suspend fun bumpTimelineDependencies(statusIds: List<String>)

    @Query(
        "UPDATE DbStatus SET content = :content, contentHash = :contentHash, renderHash = :renderHash, text = :text " +
            "WHERE statusKey = :statusKey AND accountType = :accountType",
    )
    suspend fun updateStoredContent(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbTimelineContent,
        contentHash: Long,
        renderHash: Int,
        text: String?,
    )

    suspend fun update(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: UiTimelineV2,
        renderHash: Int,
        text: String?,
    ) {
        val storedContent = DbTimelineContent.encode(content)
        updateStoredContent(
            statusKey = statusKey,
            accountType = accountType,
            content = storedContent,
            contentHash = storedContent.contentHash,
            renderHash = renderHash,
            text = text,
        )
    }

    @Query("DELETE FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun delete(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbStatus WHERE accountType = :accountType")
    suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query("SELECT COUNT(*) FROM DbStatus")
    fun count(): Flow<Long>

    @Query("DELETE FROM DbStatus")
    suspend fun clear()
}
