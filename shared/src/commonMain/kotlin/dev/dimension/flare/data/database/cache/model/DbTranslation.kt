package dev.dimension.flare.data.database.cache.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.serialization.Serializable

@Entity(
    indices = [
        Index(value = ["entityType", "entityKey", "targetLanguage"], unique = true),
        Index(value = ["entityType", "entityKey"]),
        Index(value = ["status"]),
        Index(value = ["targetLanguage"]),
    ],
)
internal data class DbTranslation(
    val entityType: TranslationEntityType,
    val entityKey: String,
    val targetLanguage: String,
    val sourceHash: String,
    val status: TranslationStatus,
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val payload: TranslationPayload? = null,
    val statusReason: String? = null,
    val attemptCount: Int = 0,
    val updatedAt: Long,
    @PrimaryKey
    val id: String = "${entityType.name}:$entityKey:$targetLanguage",
)

@Serializable
internal enum class TranslationEntityType {
    Status,
    Profile,
}

@Serializable
internal enum class TranslationStatus {
    Pending,
    Translating,
    Completed,
    Failed,
    Skipped,
}

@Serializable
internal data class TranslationPayload(
    val content: UiRichText? = null,
    val contentWarning: UiRichText? = null,
    val title: UiRichText? = null,
    val description: UiRichText? = null,
)

internal class TranslationConverters {
    @TypeConverter
    fun fromEntityType(value: TranslationEntityType): String = value.name

    @TypeConverter
    fun toEntityType(value: String): TranslationEntityType = TranslationEntityType.valueOf(value)

    @TypeConverter
    fun fromStatus(value: TranslationStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): TranslationStatus = TranslationStatus.valueOf(value)

    @TypeConverter
    fun fromPayload(value: TranslationPayload?): String? = value?.encodeJson(TranslationPayload.serializer())

    @TypeConverter
    fun toPayload(value: String?): TranslationPayload? = value?.decodeJson(TranslationPayload.serializer())
}

internal fun DbStatus.translationEntityKey(): String = id

internal fun DbUser.translationEntityKey(): String = profileTranslationEntityKey(userKey)

internal fun UiProfile.translationEntityKey(): String = profileTranslationEntityKey(key)

internal fun profileTranslationEntityKey(userKey: MicroBlogKey): String = "profile:$userKey"
