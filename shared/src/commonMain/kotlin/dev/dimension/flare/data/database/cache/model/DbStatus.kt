package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@Entity(
    indices = [Index(value = ["statusKey", "pagingKey"], unique = true)],
)
internal data class DbStatus(
    val statusKey: MicroBlogKey,
    val pagingKey: String,
    val content: UiTimelineV2,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = "${pagingKey}_$statusKey",
)
