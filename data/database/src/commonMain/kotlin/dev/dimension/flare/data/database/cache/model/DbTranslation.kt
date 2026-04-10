package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.AccountType
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
public data class DbTranslation(
    val entityType: TranslationEntityType,
    val entityKey: String,
    val targetLanguage: String,
    val sourceHash: String,
    val status: TranslationStatus,
    val displayMode: TranslationDisplayMode = TranslationDisplayMode.Auto,
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val payload: TranslationPayload? = null,
    val statusReason: String? = null,
    val attemptCount: Int = 0,
    val updatedAt: Long,
    @PrimaryKey
    val id: String = "${entityType.name}:$entityKey:$targetLanguage",
)

@Serializable
public enum class TranslationEntityType {
    Status,
    Profile,
}

@Serializable
public enum class TranslationStatus {
    Pending,
    Translating,
    Completed,
    Failed,
    Skipped,
}

@Serializable
public enum class TranslationDisplayMode {
    Auto,
    Original,
    Translated,
}

@Serializable
public data class TranslationPayload(
    val content: UiRichText? = null,
    val contentWarning: UiRichText? = null,
    val title: UiRichText? = null,
    val description: UiRichText? = null,
)

public class TranslationConverters {
    @TypeConverter
    fun fromEntityType(value: TranslationEntityType): String = value.name

    @TypeConverter
    fun toEntityType(value: String): TranslationEntityType = TranslationEntityType.valueOf(value)

    @TypeConverter
    fun fromStatus(value: TranslationStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): TranslationStatus = TranslationStatus.valueOf(value)

    @TypeConverter
    fun fromDisplayMode(value: TranslationDisplayMode): String = value.name

    @TypeConverter
    fun toDisplayMode(value: String): TranslationDisplayMode = TranslationDisplayMode.valueOf(value)

    @TypeConverter
    fun fromPayload(value: TranslationPayload?): String? = value?.encodeJson(TranslationPayload.serializer())

    @TypeConverter
    fun toPayload(value: String?): TranslationPayload? = value?.decodeJson(TranslationPayload.serializer())
}

public fun DbStatus.translationEntityKey(): String = id

public fun statusTranslationEntityKey(
    accountType: AccountType,
    statusKey: MicroBlogKey,
): String = "${accountType}_$statusKey"

public fun DbUser.translationEntityKey(): String = profileTranslationEntityKey(userKey)

public fun UiProfile.translationEntityKey(): String = profileTranslationEntityKey(key)

public fun profileTranslationEntityKey(userKey: MicroBlogKey): String = "profile:$userKey"
