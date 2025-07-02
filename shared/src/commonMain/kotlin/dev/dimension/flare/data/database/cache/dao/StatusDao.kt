package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE DbStatus SET content = :content WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun update(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: StatusContent,
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
