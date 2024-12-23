package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.model.PlatformType

internal class PlatformTypeConverter {
    @androidx.room.TypeConverter
    fun fromString(value: String): PlatformType = PlatformType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromEnum(value: PlatformType): String = value.name
}
