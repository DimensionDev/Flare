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
    indices = [
        Index(value = ["handle", "host", "platformType"], unique = true),
    ],
)
internal data class DbUser(
    @PrimaryKey
    val userKey: MicroBlogKey,
    val platformType: PlatformType,
    val name: String,
    val handle: String,
    val host: String,
    val content: UserContent,
)

internal class UserContentConverters {
    @TypeConverter
    fun fromUserContent(content: UserContent): String = content.encodeJson()

    @TypeConverter
    fun toUserContent(value: String): UserContent = value.decodeJson()
}
