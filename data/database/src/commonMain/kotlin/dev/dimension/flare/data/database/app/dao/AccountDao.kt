package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
public interface AccountDao {
    @Query("SELECT * FROM DbAccount ORDER BY last_active DESC LIMIT 1")
    public fun activeAccount(): Flow<DbAccount?>

    @Query("SELECT * FROM DbAccount ORDER BY sort_id")
    public fun sortedAccounts(): Flow<List<DbAccount>>

    @Query("SELECT * FROM DbAccount")
    public fun allAccounts(): Flow<List<DbAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(account: DbAccount)

    @Query("UPDATE DbAccount SET last_active = :lastActive WHERE account_key = :accountKey")
    public suspend fun setLastActive(
        accountKey: MicroBlogKey,
        lastActive: Long,
    )

    @Query("SELECT * FROM DbAccount WHERE account_key = :accountKey")
    public fun get(accountKey: MicroBlogKey): Flow<DbAccount?>

    @Query("SELECT * FROM DbAccount WHERE account_key = :accountKey")
    public suspend fun getAccount(accountKey: MicroBlogKey): DbAccount?

    @Query("DELETE FROM DbAccount WHERE account_key = :accountKey")
    public suspend fun delete(accountKey: MicroBlogKey)

    @Query("UPDATE DbAccount SET credential_json = :credentialJson WHERE account_key = :accountKey")
    public suspend fun setCredential(
        accountKey: MicroBlogKey,
        credentialJson: String,
    )

    @Query("UPDATE DbAccount SET sort_id = :sortId WHERE account_key = :accountKey")
    public suspend fun setSortId(
        accountKey: MicroBlogKey,
        sortId: Long,
    )

    @Query("SELECT MAX(sort_id) FROM DbAccount")
    public suspend fun getMaxSortId(): Long?
}
