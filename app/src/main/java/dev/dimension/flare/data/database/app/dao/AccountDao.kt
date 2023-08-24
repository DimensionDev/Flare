package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM DbAccount ORDER BY lastActive DESC LIMIT 1")
    fun getActiveAccount(): Flow<DbAccount?>

    @Query("SELECT * FROM DbAccount")
    fun getAllAccounts(): Flow<List<DbAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAccount(account: DbAccount)

    // set active account
    @Query("UPDATE DbAccount SET lastActive = :lastActive WHERE account_key = :accountKey")
    suspend fun setActiveAccount(accountKey: MicroBlogKey, lastActive: Long)

    @Query("SELECT * FROM DbAccount WHERE account_key = :accountKey")
    suspend fun getAccount(accountKey: MicroBlogKey): DbAccount?

    @Delete
    suspend fun deleteAccount(account: DbAccount)

    // update credential_json
    @Query("UPDATE DbAccount SET credential_json = :credentialJson WHERE account_key = :accountKey")
    suspend fun updateCredentialJson(accountKey: MicroBlogKey, credentialJson: String)
}
