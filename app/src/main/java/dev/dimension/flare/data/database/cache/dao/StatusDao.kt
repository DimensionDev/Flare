package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Delete
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
    suspend fun insertAll(status: List<DbStatus>)

    @Query("SELECT * FROM status WHERE statusKey = :statusKey AND accountKey = :accountKey")
    suspend fun getStatus(statusKey: MicroBlogKey, accountKey: MicroBlogKey): DbStatus?

    // status flow
    @Query("SELECT * FROM status WHERE statusKey = :statusKey AND accountKey = :accountKey")
    fun getStatusFlow(statusKey: MicroBlogKey, accountKey: MicroBlogKey): Flow<DbStatus?>

    @Query("UPDATE status SET content = :content WHERE statusKey = :statusKey AND accountKey = :accountKey")
    suspend fun updateStatus(statusKey: MicroBlogKey, accountKey: MicroBlogKey, content: StatusContent)

    @Delete
    suspend fun delete(status: List<DbStatus>)
}
