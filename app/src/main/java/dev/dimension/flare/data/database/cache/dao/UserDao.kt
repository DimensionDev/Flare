package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(user: List<DbUser>)

    @Query("SELECT * FROM user WHERE userKey = :userKey")
    fun getUser(userKey: MicroBlogKey): Flow<DbUser?>
}