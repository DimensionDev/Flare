package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ApplicationDao {
    @Query("SELECT * FROM DbApplication")
    fun allApplication(): Flow<List<DbApplication>>

    @Query("SELECT * FROM DbApplication WHERE host = :host")
    fun get(host: String): Flow<DbApplication?>

    @Query("SELECT * FROM DbApplication WHERE has_pending_oauth_request = 1")
    fun getPending(): Flow<List<DbApplication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(application: DbApplication)

    @Query("UPDATE DbApplication SET credential_json = :credentialJson, platform_type = :platformType WHERE host = :host")
    suspend fun update(
        host: String,
        credentialJson: String,
        platformType: PlatformType,
    )

    @Query("UPDATE DbApplication SET has_pending_oauth_request = :hasPendingOauthRequest WHERE host = :host")
    suspend fun updatePending(
        host: String,
        hasPendingOauthRequest: Long,
    )

    @Query("DELETE FROM DbApplication WHERE host = :host")
    suspend fun delete(host: String)

    @Query("UPDATE DbApplication SET has_pending_oauth_request = 0 WHERE has_pending_oauth_request = 1")
    suspend fun clearPending()
}
