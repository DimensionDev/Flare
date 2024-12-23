package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AccountDao {
    @Query("SELECT * FROM DbAccount ORDER BY last_active DESC LIMIT 1")
    fun activeAccount(): Flow<DbAccount?>

    @Query("SELECT * FROM DbAccount")
    fun allAccounts(): Flow<List<DbAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: DbAccount)

    @Query("UPDATE DbAccount SET last_active = :lastActive WHERE account_key = :accountKey")
    suspend fun setLastActive(
        accountKey: MicroBlogKey,
        lastActive: Long,
    )

    @Query("SELECT * FROM DbAccount WHERE account_key = :accountKey")
    fun get(accountKey: MicroBlogKey): Flow<DbAccount?>

    @Query("DELETE FROM DbAccount WHERE account_key = :accountKey")
    suspend fun delete(accountKey: MicroBlogKey)

    @Query("UPDATE DbAccount SET credential_json = :credentialJson WHERE account_key = :accountKey")
    suspend fun setCredential(
        accountKey: MicroBlogKey,
        credentialJson: String,
    )
}
