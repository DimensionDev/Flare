package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@Entity(
    indices = [Index(value = ["statusKey", "accountType"], unique = true)],
)
public data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountType: DbAccountType,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiTimelineV2,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = createId(accountType = accountType, statusKey = statusKey),
) {
    public companion object {
        public fun createId(
            accountType: DbAccountType,
            statusKey: MicroBlogKey,
        ): String = "${accountType}_$statusKey"
    }
}
