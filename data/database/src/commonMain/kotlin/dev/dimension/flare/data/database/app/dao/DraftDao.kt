package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import dev.dimension.flare.data.database.app.model.DbDraftGroup
import dev.dimension.flare.data.database.app.model.DbDraftGroupWithRelations
import dev.dimension.flare.data.database.app.model.DbDraftMedia
import dev.dimension.flare.data.database.app.model.DbDraftTarget
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import kotlinx.coroutines.flow.Flow

@Dao
public interface DraftDao {
    @Upsert
    suspend fun upsertGroup(group: DbDraftGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(targets: List<DbDraftTarget>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedias(medias: List<DbDraftMedia>)

    @Query("DELETE FROM DbDraftMedia WHERE group_id = :groupId")
    suspend fun deleteMediasByGroup(groupId: String)

    @Query("DELETE FROM DbDraftTarget WHERE group_id = :groupId")
    suspend fun deleteTargetsByGroup(groupId: String)

    @Query("DELETE FROM DbDraftTarget WHERE target_id = :targetId")
    suspend fun deleteTarget(targetId: String)

    @Query("DELETE FROM DbDraftGroup WHERE group_id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT COUNT(*) FROM DbDraftTarget WHERE group_id = :groupId")
    suspend fun countTargets(groupId: String): Int

    @Transaction
    @Query(
        """
        SELECT * FROM DbDraftGroup
        WHERE EXISTS (
            SELECT 1 FROM DbDraftTarget
            WHERE DbDraftTarget.group_id = DbDraftGroup.group_id
              AND DbDraftTarget.status IN ('DRAFT', 'FAILED')
        )
        ORDER BY updated_at DESC
        """,
    )
    fun visibleDraftGroups(): Flow<List<DbDraftGroupWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM DbDraftGroup
        WHERE group_id = :groupId
        LIMIT 1
        """,
    )
    fun draftGroup(groupId: String): Flow<DbDraftGroupWithRelations?>

    @Transaction
    @Query(
        """
        SELECT * FROM DbDraftGroup
        WHERE group_id = :groupId
        LIMIT 1
        """,
    )
    suspend fun getDraftGroup(groupId: String): DbDraftGroupWithRelations?

    @Query(
        """
        SELECT * FROM DbDraftGroup
        WHERE group_id = :groupId
        LIMIT 1
        """,
    )
    suspend fun getGroup(groupId: String): DbDraftGroup?

    @Transaction
    @Query(
        """
        SELECT * FROM DbDraftGroup
        WHERE EXISTS (
            SELECT 1 FROM DbDraftTarget
            WHERE DbDraftTarget.group_id = DbDraftGroup.group_id
              AND DbDraftTarget.status = 'SENDING'
        )
        ORDER BY updated_at DESC
        """,
    )
    fun sendingDraftGroups(): Flow<List<DbDraftGroupWithRelations>>

    @Query(
        """
        UPDATE DbDraftTarget
        SET status = :status,
            error_message = :errorMessage,
            attempt_count = :attemptCount,
            last_attempt_at = :lastAttemptAt,
            updated_at = :updatedAt
        WHERE target_id = :targetId
        """,
    )
    suspend fun updateTargetStatus(
        targetId: String,
        status: DraftTargetStatus,
        errorMessage: String?,
        attemptCount: Int,
        lastAttemptAt: Long?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE DbDraftGroup
        SET updated_at = :updatedAt
        WHERE group_id = :groupId
        """,
    )
    suspend fun touchGroup(
        groupId: String,
        updatedAt: Long,
    )

    @Query(
        """
        SELECT COUNT(*) FROM DbDraftTarget
        WHERE group_id = :groupId
          AND status = :status
        """,
    )
    suspend fun countTargetsByStatus(
        groupId: String,
        status: DraftTargetStatus,
    ): Int

    @Query(
        """
        UPDATE DbDraftTarget
        SET status = :toStatus,
            error_message = :errorMessage,
            updated_at = :updatedAt
        WHERE status = :fromStatus
          AND (last_attempt_at IS NULL OR last_attempt_at < :expiredBefore)
        """,
    )
    suspend fun resetSendingTargets(
        fromStatus: DraftTargetStatus,
        toStatus: DraftTargetStatus,
        expiredBefore: Long,
        errorMessage: String?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE DbDraftGroup
        SET updated_at = :updatedAt
        WHERE group_id IN (
            SELECT DISTINCT group_id FROM DbDraftTarget
            WHERE status = :fromStatus
              AND (last_attempt_at IS NULL OR last_attempt_at < :expiredBefore)
        )
        """,
    )
    suspend fun touchExpiredSendingGroups(
        fromStatus: DraftTargetStatus,
        expiredBefore: Long,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE DbDraftTarget
        SET status = :toStatus,
            error_message = :errorMessage,
            updated_at = :updatedAt
        WHERE status = :fromStatus
        """,
    )
    suspend fun updateTargetsByStatus(
        fromStatus: DraftTargetStatus,
        toStatus: DraftTargetStatus,
        errorMessage: String?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE DbDraftGroup
        SET updated_at = :updatedAt
        WHERE group_id IN (
            SELECT DISTINCT group_id FROM DbDraftTarget
            WHERE status = :status
        )
        """,
    )
    suspend fun touchGroupsByTargetStatus(
        status: DraftTargetStatus,
        updatedAt: Long,
    )
}
