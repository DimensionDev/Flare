package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType

@Entity
public data class DbEmoji(
    @PrimaryKey
    @ColumnInfo(name = "host")
    public val host: String,
    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB)
    public val content: EmojiContent,
)

public class EmojiContentConverter {
    @TypeConverter
    public fun fromEmojiContent(emojiContent: EmojiContent): ByteArray = emojiContent.encodeProtobuf()

    @TypeConverter
    public fun toEmojiContent(data: ByteArray): EmojiContent =
        if (data.isEmpty()) {
            EmojiContent()
        } else {
            data.decodeProtobuf()
        }
}

@Entity
public data class DbEmojiHistory(
    public val accountType: DbAccountType,
    public val shortCode: String,
    public val lastUse: Long,
    @PrimaryKey
    public val _id: String = "$accountType-$shortCode",
)
