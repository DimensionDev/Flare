package dev.dimension.flare.data.database.adapter

import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.DbAccountType

internal class AccountTypeConverter {
    @TypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @TypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()
}
