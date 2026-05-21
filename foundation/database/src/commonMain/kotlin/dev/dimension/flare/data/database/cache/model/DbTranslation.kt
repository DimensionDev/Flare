package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.translation.TRANSLATION_SKIPPED_EXCLUDED_LANGUAGE_REASON
import dev.dimension.flare.data.translation.TranslationDisplayMode
import dev.dimension.flare.data.translation.TranslationEntityType
import dev.dimension.flare.data.translation.TranslationPayload
import dev.dimension.flare.data.translation.TranslationStatus
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile

@Entity(
    indices = [
        Index(value = ["entityType", "entityKey", "targetLanguage"], unique = true),
        Index(value = ["entityType", "entityKey"]),
        Index(value = ["status"]),
        Index(value = ["targetLanguage"]),
    ],
)
public data class DbTranslation(
    public val entityType: TranslationEntityType,
    public val entityKey: String,
    public val targetLanguage: String,
    public val sourceHash: String,
    public val status: TranslationStatus,
    public val displayMode: TranslationDisplayMode = TranslationDisplayMode.Auto,
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    public val payload: TranslationPayload? = null,
    public val statusReason: String? = null,
    public val attemptCount: Int = 0,
    public val updatedAt: Long,
    @PrimaryKey
    public val id: String = "${entityType.name}:$entityKey:$targetLanguage",
)

public fun DbTranslation?.canRetrySkippedManually(): Boolean =
    this?.status == TranslationStatus.Skipped &&
        statusReason == TRANSLATION_SKIPPED_EXCLUDED_LANGUAGE_REASON

public class TranslationConverters {
    @TypeConverter
    public fun fromEntityType(value: TranslationEntityType): String = value.name

    @TypeConverter
    public fun toEntityType(value: String): TranslationEntityType = TranslationEntityType.valueOf(value)

    @TypeConverter
    public fun fromStatus(value: TranslationStatus): String = value.name

    @TypeConverter
    public fun toStatus(value: String): TranslationStatus = TranslationStatus.valueOf(value)

    @TypeConverter
    public fun fromDisplayMode(value: TranslationDisplayMode): String = value.name

    @TypeConverter
    public fun toDisplayMode(value: String): TranslationDisplayMode = TranslationDisplayMode.valueOf(value)

    @TypeConverter
    public fun fromPayload(value: TranslationPayload?): String? = value?.encodeJson(TranslationPayload.serializer())

    @TypeConverter
    public fun toPayload(value: String?): TranslationPayload? = value?.decodeJson(TranslationPayload.serializer())
}

public fun DbStatus.translationEntityKey(): String = id

public fun statusTranslationEntityKey(
    accountType: AccountType,
    statusKey: MicroBlogKey,
): String = "${accountType}_$statusKey"

public fun DbUser.translationEntityKey(): String = profileTranslationEntityKey(userKey)

public fun UiProfile.translationEntityKey(): String = profileTranslationEntityKey(key)

public fun profileTranslationEntityKey(userKey: MicroBlogKey): String = "profile:$userKey"
