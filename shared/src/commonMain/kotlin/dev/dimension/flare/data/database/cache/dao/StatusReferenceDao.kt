package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.model.MicroBlogKey

@Dao
internal interface StatusReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbStatusReference>)

    @Query("DELETE FROM status_reference WHERE statusKey = :key")
    suspend fun delete(key: MicroBlogKey)

    @Query("DELETE FROM status_reference WHERE statusKey in (:keys)")
    suspend fun delete(keys: List<MicroBlogKey>)
}
