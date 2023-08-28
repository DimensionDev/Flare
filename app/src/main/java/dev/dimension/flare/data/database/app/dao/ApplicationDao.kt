package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.dimension.flare.data.database.app.model.DbApplication
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Query("SELECT * FROM DbApplication")
    fun getApplications(): Flow<List<DbApplication>>

    @Query("SELECT * FROM DbApplication")
    suspend fun getApplicationsSync(): List<DbApplication>

    @Query("SELECT * FROM DbApplication WHERE host = :host")
    suspend fun getApplication(host: String): DbApplication?

    @Query("SELECT * FROM DbApplication WHERE hasPendingOAuth = 1")
    suspend fun getPendingOAuthApplication(): List<DbApplication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addApplication(application: DbApplication)

    @Update
    suspend fun updateApplication(application: DbApplication)

    @Delete
    suspend fun deleteApplication(application: DbApplication)
}
