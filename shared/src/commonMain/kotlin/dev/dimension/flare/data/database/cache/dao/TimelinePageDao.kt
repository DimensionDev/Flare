package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationEntityType

@Dao
internal interface TimelinePageDao {
    @Query(
        "SELECT " +
            "DbPagingTimeline.pagingKey, " +
            "DbPagingTimeline.statusId, " +
            "DbPagingTimeline.sortId, " +
            "DbPagingTimeline.message, " +
            "DbPagingTimeline.messageRenderHash, " +
            "DbPagingTimeline.statusReferenceHash, " +
            "DbPagingTimeline.presentationReferenceHash, " +
            "DbPagingTimeline.dependencyRevision, " +
            "DbPagingTimeline._id " +
            "FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "ORDER BY DbPagingTimeline.sortId " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun getTimelineRows(
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimeline>

    @Query(
        "SELECT " +
            "DbPagingTimeline.pagingKey, " +
            "DbPagingTimeline.statusId, " +
            "DbPagingTimeline.sortId, " +
            "DbPagingTimeline.message, " +
            "DbPagingTimeline.messageRenderHash, " +
            "DbPagingTimeline.statusReferenceHash, " +
            "DbPagingTimeline.presentationReferenceHash, " +
            "DbPagingTimeline.dependencyRevision, " +
            "DbPagingTimeline._id " +
            "FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.id = DbPagingTimeline.statusId " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey " +
            "AND DbPagingTimeline.statusId IN (:statusIds)",
    )
    suspend fun getTimelineRowsByStatusIds(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbPagingTimeline>

    @Query(
        "SELECT * FROM status_reference " +
            "WHERE statusId IN (:statusIds) " +
            "ORDER BY statusId, referenceOrder",
    )
    suspend fun getStatusReferences(statusIds: List<String>): List<DbStatusReference>

    @Query(
        "SELECT * FROM timeline_item_presentation_reference " +
            "WHERE pagingKey = :pagingKey AND statusId IN (:statusIds) " +
            "ORDER BY statusId, referenceOrder",
    )
    suspend fun getPresentationReferences(
        pagingKey: String,
        statusIds: List<String>,
    ): List<DbTimelineItemPresentationReference>

    @Query("SELECT * FROM DbStatus WHERE id IN (:statusIds)")
    suspend fun getStatuses(statusIds: List<String>): List<DbStatus>

    @Query("SELECT id, contentHash, renderHash FROM DbStatus WHERE id IN (:statusIds)")
    suspend fun getStatusFingerprints(statusIds: List<String>): List<DbStatusFingerprint>

    @Query(
        "SELECT * FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey IN (:entityKeys)",
    )
    suspend fun getTranslations(
        entityType: TranslationEntityType,
        entityKeys: List<String>,
    ): List<DbTranslation>
}
