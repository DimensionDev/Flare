package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.model.PlatformType

internal class PlatformTypeConverter {
    @androidx.room3.ColumnTypeConverter
    fun fromString(value: String): PlatformType = PlatformType.valueOf(value)

    @androidx.room3.ColumnTypeConverter
    fun fromEnum(value: PlatformType): String = value.name
}
