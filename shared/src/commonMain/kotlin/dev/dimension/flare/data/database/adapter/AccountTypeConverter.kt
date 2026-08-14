package dev.dimension.flare.data.database.adapter

import androidx.room3.ColumnTypeConverter
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

internal class AccountTypeConverter {
    @ColumnTypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @ColumnTypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @ColumnTypeConverter
    fun fromUiProfile(value: UiProfile): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiProfile(value: ByteArray): UiProfile = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiTimelineV2(value: UiTimelineV2): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiTimelineV2(value: ByteArray): UiTimelineV2 = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiDMRoom(value: UiDMRoom): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiDMRoom(value: ByteArray): UiDMRoom = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiDMItem(value: UiDMItem): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiDMItem(value: ByteArray): UiDMItem = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiRelation(value: UiRelation): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiRelation(value: ByteArray): UiRelation = value.decodeProtobuf()
}
