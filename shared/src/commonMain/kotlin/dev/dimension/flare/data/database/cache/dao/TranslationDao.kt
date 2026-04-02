package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: DbTranslation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<DbTranslation>)

    @Query(
        "SELECT * FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey = :entityKey AND targetLanguage = :targetLanguage " +
            "LIMIT 1",
    )
    fun find(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
    ): Flow<DbTranslation?>

    @Query(
        "SELECT * FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey = :entityKey AND targetLanguage = :targetLanguage " +
            "LIMIT 1",
    )
    suspend fun get(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
    ): DbTranslation?

    @Query(
        "SELECT * FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey IN (:entityKeys) AND targetLanguage = :targetLanguage",
    )
    suspend fun getByEntityKeys(
        entityType: TranslationEntityType,
        entityKeys: List<String>,
        targetLanguage: String,
    ): List<DbTranslation>

    @Query(
        "UPDATE DbTranslation SET " +
            "sourceHash = :sourceHash, " +
            "status = :status, " +
            "displayMode = :displayMode, " +
            "payload = :payload, " +
            "statusReason = :statusReason, " +
            "attemptCount = :attemptCount, " +
            "updatedAt = :updatedAt " +
            "WHERE entityType = :entityType AND entityKey = :entityKey AND targetLanguage = :targetLanguage",
    )
    suspend fun update(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
        sourceHash: String,
        status: TranslationStatus,
        displayMode: TranslationDisplayMode,
        payload: TranslationPayload?,
        statusReason: String?,
        attemptCount: Int,
        updatedAt: Long,
    )

    @Query(
        "UPDATE DbTranslation SET " +
            "displayMode = :displayMode, " +
            "updatedAt = :updatedAt " +
            "WHERE entityType = :entityType AND entityKey = :entityKey AND targetLanguage = :targetLanguage",
    )
    suspend fun updateDisplayMode(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
        displayMode: TranslationDisplayMode,
        updatedAt: Long,
    )

    @Query(
        "UPDATE DbTranslation SET " +
            "status = :failedStatus, " +
            "payload = NULL, " +
            "statusReason = :statusReason, " +
            "updatedAt = :updatedAt " +
            "WHERE (status = :pendingStatus OR status = :translatingStatus) AND updatedAt < :staleBefore",
    )
    suspend fun markStaleInFlightAsFailed(
        staleBefore: Long,
        statusReason: String,
        updatedAt: Long,
        failedStatus: TranslationStatus = TranslationStatus.Failed,
        pendingStatus: TranslationStatus = TranslationStatus.Pending,
        translatingStatus: TranslationStatus = TranslationStatus.Translating,
    )

    @Query(
        "DELETE FROM DbTranslation " +
            "WHERE entityType = :entityType AND entityKey = :entityKey AND targetLanguage = :targetLanguage",
    )
    suspend fun delete(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
    )

    @Query("DELETE FROM DbTranslation WHERE targetLanguage = :targetLanguage")
    suspend fun deleteByLanguage(targetLanguage: String)

    @Query("DELETE FROM DbTranslation")
    suspend fun clear()
}
