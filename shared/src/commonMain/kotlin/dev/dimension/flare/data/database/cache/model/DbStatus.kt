package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@Entity(
    indices = [Index(value = ["statusKey", "accountKey"], unique = true)],
)
data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountKey: MicroBlogKey,
    val userKey: MicroBlogKey?,
    val platformType: PlatformType,
    val content: StatusContent,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = "${accountKey}_$statusKey",
)

class StatusContentConverters {
    @TypeConverter
    fun fromStatusContent(content: StatusContent): String = content.encodeJson()

    @TypeConverter
    fun toStatusContent(value: String): StatusContent = value.decodeJson()
}
