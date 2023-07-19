package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import dev.dimension.flare.data.database.cache.model.DbStatus

@Dao
interface StatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(status: List<DbStatus>)

    @Delete
    suspend fun delete(status: List<DbStatus>)
}