package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType

@Entity(
    tableName = "status_reference",
    indices = [
        Index(
            value = [
                "referenceType",
                "statusKey",
                "referenceStatusKey",
            ],
            unique = true,
        ),
    ],
)
data class DbStatusReference(
    /**
     * Id that being used in the database
     */
    @PrimaryKey
    val _id: String,
    val referenceType: ReferenceType,
    val statusKey: MicroBlogKey,
    val referenceStatusKey: MicroBlogKey,
)

