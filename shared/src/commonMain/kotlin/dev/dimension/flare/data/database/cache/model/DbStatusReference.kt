package dev.dimension.flare.data.database.cache.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.ReferenceType

@Entity(
    tableName = "status_reference",
    indices = [
        Index(
            value = [
                "statusId",
            ],
        ),
        Index(
            value = [
                "referenceType",
                "statusId",
                "referenceStatusId",
            ],
            unique = true,
        ),
        Index(value = ["referenceStatusId"]),
    ],
)
internal data class DbStatusReference(
    /**
     * Id that being used in the database
     */
    @PrimaryKey
    val _id: String,
    val referenceType: ReferenceType,
    val statusId: String,
    val referenceStatusId: String,
    val referenceOrder: Int = 0,
) {
    companion object {
        fun createId(
            referenceType: ReferenceType,
            statusId: String,
            referenceStatusId: String,
        ): String =
            stableDatabaseId(
                "status-reference",
                statusId,
                referenceType.name,
                referenceStatusId,
            )
    }
}
