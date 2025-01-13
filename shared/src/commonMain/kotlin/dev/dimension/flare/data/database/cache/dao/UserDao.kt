package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.DbUserHistory
import dev.dimension.flare.data.database.cache.model.DbUserHistoryWithUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UserDao {
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(data: DbUserHistory)

    @Transaction
    @Query("SELECT * FROM DbUserHistory ORDER BY lastVisit DESC")
    fun getUserHistory(): PagingSource<Int, DbUserHistoryWithUser>

    @Transaction
    @Query(
        "SELECT * FROM DbUser " +
            "WHERE DbUser.name like :query OR DbUser.handle like :query",
    )
    fun searchUser(query: String): PagingSource<Int, DbUser>
}
