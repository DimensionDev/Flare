package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow

@Dao
public interface StatusDao {
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

    @Query("SELECT * FROM DbStatus WHERE accountType = :accountType AND statusKey IN (:statusKeys)")
    suspend fun getByKeys(
        statusKeys: List<MicroBlogKey>,
        accountType: DbAccountType,
    ): List<DbStatus>

    @Query("UPDATE DbStatus SET content = :content WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun update(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: UiTimelineV2,
    )

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
