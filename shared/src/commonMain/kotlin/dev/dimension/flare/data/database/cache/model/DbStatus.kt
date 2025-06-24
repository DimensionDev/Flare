package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@Entity(
    indices = [Index(value = ["statusKey", "accountType"], unique = true)],
)
internal data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountType: AccountType,
    val userKey: MicroBlogKey?,
    val content: StatusContent,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = "${accountType}_$statusKey",
)

internal class StatusContentConverters {
    @TypeConverter
    fun fromStatusContent(content: StatusContent): String = content.encodeJson()

    @TypeConverter
    fun toStatusContent(value: String): StatusContent = value.decodeJson()
}
