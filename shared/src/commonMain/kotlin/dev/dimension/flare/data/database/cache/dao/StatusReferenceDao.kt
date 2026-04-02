package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType

@Dao
internal interface StatusReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbStatusReference>)

    @Query("DELETE FROM status_reference WHERE statusKey = :key")
    suspend fun delete(key: MicroBlogKey)

    @Query("DELETE FROM status_reference WHERE statusKey in (:keys)")
    suspend fun delete(keys: List<MicroBlogKey>)

    @Query("DELETE FROM status_reference WHERE statusKey in (:keys) AND referenceType in (:types)")
    suspend fun delete(
        keys: List<MicroBlogKey>,
        types: List<ReferenceType>,
    )

    @Query("SELECT * FROM status_reference WHERE statusKey = :key")
    suspend fun getByStatusKey(key: MicroBlogKey): List<DbStatusReference>
}
