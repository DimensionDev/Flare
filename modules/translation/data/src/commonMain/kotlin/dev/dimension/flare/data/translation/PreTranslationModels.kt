package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.ui.render.TranslationDocument
import kotlinx.serialization.Serializable

@Serializable
public data class PreTranslationBatchDocument(
    public val version: Int = 1,
    public val targetLanguage: String = "",
    public val items: List<PreTranslationBatchItem>,
)

@Serializable
public data class PreTranslationBatchItem(
    public val entityKey: String,
    public val status: PreTranslationBatchItemStatus = PreTranslationBatchItemStatus.Completed,
    public val payload: PreTranslationBatchPayload? = null,
    public val reason: String? = null,
)

@Serializable
public enum class PreTranslationBatchItemStatus {
    Completed,
    Skipped,
}

@Serializable
public data class PreTranslationBatchPayload(
    public val content: TranslationDocument? = null,
    public val contentWarning: TranslationDocument? = null,
    public val title: TranslationDocument? = null,
    public val description: TranslationDocument? = null,
)

public data class ActivePreTranslationSettings(
    public val targetLanguage: String,
    public val autoTranslateExcludedLanguages: List<String>,
    public val appSettings: AppSettings,
    public val providerCacheKey: String,
)

public data class PreparedTranslationCandidate(
    public val entityType: TranslationEntityType,
    public val entityKey: String,
    public val targetLanguage: String,
    public val sourceHash: String,
    public val sourcePayload: TranslationPayload,
    public val sourceDocument: PreTranslationBatchPayload,
    public val attemptCount: Int,
    public val displayMode: TranslationDisplayMode,
)
