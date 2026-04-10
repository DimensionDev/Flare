package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.model.PlatformType

public class PlatformTypeConverter {
    @androidx.room3.TypeConverter
    fun fromString(value: String): PlatformType = PlatformType.valueOf(value)

    @androidx.room3.TypeConverter
    fun fromEnum(value: PlatformType): String = value.name
}
