package dev.dimension.flare.data.database.cache.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType

@Entity
internal data class DbEmoji(
    @PrimaryKey
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB)
    val content: EmojiContent,
)

internal class EmojiContentConverter {
    @TypeConverter
    fun fromEmojiContent(emojiContent: EmojiContent): ByteArray = emojiContent.encodeProtobuf()

    @TypeConverter
    fun toEmojiContent(data: ByteArray): EmojiContent =
        if (data.isEmpty()) {
            EmojiContent()
        } else {
            data.decodeProtobuf()
        }
}

@Entity
internal data class DbEmojiHistory(
    val accountType: DbAccountType,
    val shortCode: String,
    val lastUse: Long,
    @PrimaryKey
    val _id: String = "$accountType-$shortCode",
)
