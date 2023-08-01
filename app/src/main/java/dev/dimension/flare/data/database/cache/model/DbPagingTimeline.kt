package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dimension.flare.model.MicroBlogKey

@Entity(
    tableName = "paging_timeline",
    indices = [
        Index(
            value = ["accountKey", "statusKey", "pagingKey"],
            unique = true
        )
    ]
)
data class DbPagingTimeline(
    @PrimaryKey
    val _id: String,
    val accountKey: MicroBlogKey,
    val pagingKey: String,
    val statusKey: MicroBlogKey,
    val sortId: Long
)

data class DbPagingTimelineWithStatus(
    @Embedded
    val timeline: DbPagingTimeline,

    @Relation(
        parentColumn = "statusKey",
        entityColumn = "statusKey",
        entity = DbStatus::class
    )
    val status: DbStatusWithReference
)
