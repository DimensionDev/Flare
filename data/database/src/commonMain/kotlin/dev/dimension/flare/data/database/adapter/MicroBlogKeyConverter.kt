package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.model.MicroBlogKey

public class MicroBlogKeyConverter {
    @androidx.room3.TypeConverter
    public fun fromString(value: String): MicroBlogKey = MicroBlogKey.valueOf(value)

    @androidx.room3.TypeConverter
    public fun fromEnum(value: MicroBlogKey): String = value.toString()
}
