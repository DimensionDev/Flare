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
    public suspend fun insert(status: DbStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(statuses: List<DbStatus>)

    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    public fun get(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatus?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    public fun getWithReferences(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatusWithReference?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    public suspend fun getWithReferencesSync(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): DbStatusWithReference?

    @Query("SELECT * FROM DbStatus WHERE accountType = :accountType AND statusKey IN (:statusKeys)")
    public suspend fun getByKeys(
        statusKeys: List<MicroBlogKey>,
        accountType: DbAccountType,
    ): List<DbStatus>

    @Query("UPDATE DbStatus SET content = :content WHERE statusKey = :statusKey AND accountType = :accountType")
    public suspend fun update(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: UiTimelineV2,
    )

    @Query("DELETE FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    public suspend fun delete(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbStatus WHERE accountType = :accountType")
    public suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query("SELECT COUNT(*) FROM DbStatus")
    public fun count(): Flow<Long>

    @Query("DELETE FROM DbStatus")
    public suspend fun clear()
}
