package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusContent
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow

internal data class DbStatusVersion(
    val id: String,
    val contentFingerprint: Long,
    val renderHash: Int,
)

@Dao
internal interface StatusDao {
    @Upsert
    suspend fun insert(status: DbStatus)

    @Upsert
    suspend fun insertAll(statuses: List<DbStatus>)

    @Insert
    suspend fun insertNew(statuses: List<DbStatus>)

    @Update
    suspend fun updateExisting(statuses: List<DbStatus>)

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

    @Query("SELECT * FROM DbStatus WHERE accountType = :accountType AND statusKey IN (:statusKeys)")
    suspend fun getByKeys(
        statusKeys: List<MicroBlogKey>,
        accountType: DbAccountType,
    ): List<DbStatus>

    @Query("SELECT id, contentFingerprint, renderHash FROM DbStatus WHERE id IN (:ids)")
    suspend fun getVersions(ids: List<String>): List<DbStatusVersion>

    @Query(
        "UPDATE DbPagingTimeline SET contentRevision = contentRevision + 1 " +
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
        "UPDATE DbStatus SET content = :content, contentFingerprint = :contentFingerprint, " +
            "renderHash = :renderHash, text = :text " +
            "WHERE statusKey = :statusKey AND accountType = :accountType",
    )
    suspend fun updateStoredContent(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbStatusContent,
        contentFingerprint: Long,
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
        val storedContent = DbStatusContent.encode(content)
        updateStoredContent(
            statusKey = statusKey,
            accountType = accountType,
            content = storedContent,
            contentFingerprint = storedContent.fingerprint,
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
