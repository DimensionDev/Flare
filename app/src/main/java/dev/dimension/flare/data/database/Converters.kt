package dev.dimension.flare.data.database

import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

class Converters {
    @TypeConverter
    fun toPlatformType(value: String?): PlatformType? {
        return value?.let { PlatformType.valueOf(it) }
    }

    @TypeConverter
    fun fromPlatformType(value: PlatformType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMicroblogKey(value: String?): MicroBlogKey? {
        return value?.let { MicroBlogKey.valueOf(it) }
    }

    @TypeConverter
    fun fromMicroblogKey(value: MicroBlogKey?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun fromUserContent(value: UserContent?): String? {
        return value?.encodeJson()
    }

    @TypeConverter
    fun toUserContent(value: String?): UserContent? {
        return value?.decodeJson()
    }

    @TypeConverter
    fun fromStatusContent(value: StatusContent?): String? {
        return value?.encodeJson()
    }

    @TypeConverter
    fun toStatusContent(value: String?): StatusContent? {
        return value?.decodeJson()
    }
}
