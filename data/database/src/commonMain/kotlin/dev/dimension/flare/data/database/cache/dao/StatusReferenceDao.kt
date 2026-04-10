package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.model.ReferenceType

@Dao
public interface StatusReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbStatusReference>)

    @Query("DELETE FROM status_reference WHERE statusId = :statusId")
    suspend fun delete(statusId: String)

    @Query("DELETE FROM status_reference WHERE statusId in (:statusIds)")
    suspend fun delete(statusIds: List<String>)

    @Query("DELETE FROM status_reference WHERE statusId in (:statusIds) AND referenceType in (:types)")
    suspend fun delete(
        statusIds: List<String>,
        types: List<ReferenceType>,
    )

    @Query("SELECT * FROM status_reference WHERE statusId = :statusId")
    suspend fun getByStatusId(statusId: String): List<DbStatusReference>
}
