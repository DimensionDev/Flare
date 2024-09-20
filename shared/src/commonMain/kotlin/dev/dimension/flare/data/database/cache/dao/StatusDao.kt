package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: DbStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<DbStatus>)

    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountKey = :accountKey")
    fun get(
        statusKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    ): Flow<DbStatus?>

    @Query("UPDATE DbStatus SET content = :content WHERE statusKey = :statusKey AND accountKey = :accountKey")
    suspend fun update(
        statusKey: MicroBlogKey,
        accountKey: MicroBlogKey,
        content: StatusContent,
    )

    @Query("DELETE FROM DbStatus WHERE statusKey = :statusKey AND accountKey = :accountKey")
    suspend fun delete(
        statusKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    )

    @Query("SELECT COUNT(*) FROM DbStatus")
    fun count(): Flow<Long>

    @Query("DELETE FROM DbStatus")
    suspend fun clear()
}
