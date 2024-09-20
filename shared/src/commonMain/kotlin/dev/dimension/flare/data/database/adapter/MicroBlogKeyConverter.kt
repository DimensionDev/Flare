package dev.dimension.flare.data.database.adapter

import dev.dimension.flare.model.MicroBlogKey

class MicroBlogKeyConverter {
    @androidx.room.TypeConverter
    fun fromString(value: String): MicroBlogKey = MicroBlogKey.valueOf(value)

    @androidx.room.TypeConverter
    fun fromEnum(value: MicroBlogKey): String = value.toString()
}
