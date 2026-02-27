package dev.dimension.flare.data.database.adapter

import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class AccountTypeConverter {
    @TypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @TypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @TypeConverter
    fun fromUiProfile(value: UiProfile): String = value.encodeJson()

    @TypeConverter
    fun toUiProfile(value: String): UiProfile = value.decodeJson()

    @TypeConverter
    fun fromUiTimelineV2(value: UiTimelineV2): String = value.encodeJson()

    @TypeConverter
    fun toUiTimelineV2(value: String): UiTimelineV2 = value.decodeJson()

    @TypeConverter
    fun fromUiRelation(value: UiRelation): String = value.encodeJson()

    @TypeConverter
    fun toUiRelation(value: String): UiRelation = value.decodeJson()
}
