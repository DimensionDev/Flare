package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.ui.render.TranslationDocument
import kotlinx.serialization.Serializable

@Serializable
internal data class PreTranslationBatchDocument(
    val version: Int = 1,
    val targetLanguage: String = "",
    val items: List<PreTranslationBatchItem>,
)

@Serializable
internal data class PreTranslationBatchItem(
    val entityKey: String,
    val status: PreTranslationBatchItemStatus = PreTranslationBatchItemStatus.Completed,
    val payload: PreTranslationBatchPayload? = null,
    val reason: String? = null,
)

@Serializable
internal enum class PreTranslationBatchItemStatus {
    Completed,
    Skipped,
}

@Serializable
internal data class PreTranslationBatchPayload(
    val content: TranslationDocument? = null,
    val contentWarning: TranslationDocument? = null,
    val title: TranslationDocument? = null,
    val description: TranslationDocument? = null,
)

internal data class ActivePreTranslationSettings(
    val targetLanguage: String,
    val appSettings: AppSettings,
)

internal data class PreparedTranslationCandidate(
    val entityType: TranslationEntityType,
    val entityKey: String,
    val targetLanguage: String,
    val sourceHash: String,
    val sourcePayload: TranslationPayload,
    val sourceDocument: PreTranslationBatchPayload,
    val attemptCount: Int,
    val displayMode: TranslationDisplayMode,
)
