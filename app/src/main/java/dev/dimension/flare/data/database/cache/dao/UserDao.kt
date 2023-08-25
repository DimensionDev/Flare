package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(user: List<DbUser>)

    @Query("SELECT * FROM user WHERE userKey IN (:userKeys)")
    suspend fun findByKeys(userKeys: List<MicroBlogKey>): List<DbUser>

    @Query("SELECT * FROM user WHERE userKey = :userKey")
    fun getUser(userKey: MicroBlogKey): Flow<DbUser?>

    @Query("SELECT * FROM user WHERE handle = :handle AND host = :host AND platformType = :platformType")
    fun getUserByHandleAndHost(handle: String, host: String, platformType: PlatformType): Flow<DbUser?>
}
