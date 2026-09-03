package dev.dimension.flare.data.database.cache.model

import androidx.room3.Entity
import androidx.room3.Index
import dev.dimension.flare.model.ReferenceType

@Entity(
    tableName = "status_reference",
    primaryKeys = ["statusId", "referenceType", "referenceStatusId"],
    indices = [
        Index(
            value = [
                "referenceStatusId",
                "statusId",
            ],
        ),
    ],
)
internal data class DbStatusReference(
    val referenceType: ReferenceType,
    val statusId: String,
    val referenceStatusId: String,
    val referenceOrder: Int = 0,
)
