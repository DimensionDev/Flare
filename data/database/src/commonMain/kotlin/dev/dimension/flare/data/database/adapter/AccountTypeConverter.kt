package dev.dimension.flare.data.database.adapter

import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

public class AccountTypeConverter {
    @TypeConverter
    public fun fromString(value: String): DbAccountType = value.decodeJson()

    @TypeConverter
    public fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @TypeConverter
    public fun fromUiProfile(value: UiProfile): ByteArray = value.encodeProtobuf()

    @TypeConverter
    public fun toUiProfile(value: ByteArray): UiProfile = value.decodeProtobuf()

    @TypeConverter
    public fun fromUiTimelineV2(value: UiTimelineV2): ByteArray = value.encodeProtobuf()

    @TypeConverter
    public fun toUiTimelineV2(value: ByteArray): UiTimelineV2 = value.decodeProtobuf()

    @TypeConverter
    public fun fromUiRelation(value: UiRelation): ByteArray = value.encodeProtobuf()

    @TypeConverter
    public fun toUiRelation(value: ByteArray): UiRelation = value.decodeProtobuf()

    @TypeConverter
    public fun fromUiDMItem(value: UiDMItem): ByteArray = value.encodeProtobuf()

    @TypeConverter
    public fun toUiDMItem(value: ByteArray): UiDMItem = value.decodeProtobuf()

    @TypeConverter
    public fun fromUiDMRoom(value: UiDMRoom): ByteArray = value.encodeProtobuf()

    @TypeConverter
    public fun toUiDMRoom(value: ByteArray): UiDMRoom = value.decodeProtobuf()
}
