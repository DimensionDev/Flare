package dev.dimension.flare.data.database.adapter

import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.AccountType

internal class AccountTypeConverter {
    @TypeConverter
    fun fromString(value: String): AccountType = value.decodeJson()

    @TypeConverter
    fun fromEnum(value: AccountType): String = value.encodeJson()
}
