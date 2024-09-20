package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: DbUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<DbUser>)

    @Query("UPDATE DbUser SET content = :content WHERE userKey = :userKey")
    suspend fun update(
        userKey: MicroBlogKey,
        content: UserContent,
    )

    @Query("SELECT * FROM DbUser WHERE userKey IN (:userKeys)")
    fun findByKeys(userKeys: List<MicroBlogKey>): Flow<List<DbUser>>

    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    fun findByKey(userKey: MicroBlogKey): Flow<DbUser?>

    @Query("SELECT * FROM DbUser WHERE handle = :handle AND host = :host AND platformType = :platformType")
    fun findByHandleAndHost(
        handle: String,
        host: String,
        platformType: PlatformType,
    ): Flow<DbUser?>

    @Query("SELECT COUNT(*) FROM DbUser")
    fun count(): Flow<Long>

    @Query("DELETE FROM DbUser")
    suspend fun clear()
}
