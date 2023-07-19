package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM DbAccount ORDER BY lastActive DESC LIMIT 1")
    fun getActiveAccount(): Flow<DbAccount?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAccount(account: DbAccount)

    @Delete
    suspend fun deleteAccount(account: DbAccount)
}