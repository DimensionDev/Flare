package dev.dimension.flare.data.database.adapter

import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

public class AccountTypeConverter {
    @TypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @TypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @TypeConverter
    fun fromUiProfile(value: UiProfile): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiProfile(value: ByteArray): UiProfile = value.decodeProtobuf()

    @TypeConverter
    fun fromUiTimelineV2(value: UiTimelineV2): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiTimelineV2(value: ByteArray): UiTimelineV2 = value.decodeProtobuf()

    @TypeConverter
    fun fromUiRelation(value: UiRelation): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiRelation(value: ByteArray): UiRelation = value.decodeProtobuf()
}
