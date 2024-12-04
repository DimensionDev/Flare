package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbGuestData
import kotlinx.coroutines.flow.Flow

@Dao
interface GuestDataDao {
    @Query("SELECT * FROM DbGuestData LIMIT 1")
    fun get(): Flow<DbGuestData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: DbGuestData)
}
