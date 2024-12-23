package dev.dimension.flare.data.database.cache.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson

@Entity
internal data class DbEmoji(
    @PrimaryKey
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "content")
    val content: EmojiContent,
)

internal class EmojiContentConverter {
    @TypeConverter
    fun fromEmojiContent(emojiContent: EmojiContent): String = emojiContent.encodeJson()

    @TypeConverter
    fun toEmojiContent(data: String): EmojiContent = data.decodeJson()
}
