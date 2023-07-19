package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import dev.dimension.flare.data.database.cache.model.DbUser

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(user: List<DbUser>)
}