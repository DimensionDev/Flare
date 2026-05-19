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
    public val statusKey: MicroBlogKey,
    public val accountType: DbAccountType,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val content: UiTimelineV2,
    public val text: String?, // For Searching
    @PrimaryKey
    public val id: String = createId(accountType = accountType, statusKey = statusKey),
) {
    public companion object {
        public fun createId(
            accountType: DbAccountType,
            statusKey: MicroBlogKey,
        ): String = "${accountType}_$statusKey"
    }
}
