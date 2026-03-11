package dev.dimension.flare.data.database.cache.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@Entity(
    indices = [Index(value = ["statusKey", "accountType"], unique = true)],
)
internal data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountType: DbAccountType,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiTimelineV2,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = "${accountType}_$statusKey",
)
