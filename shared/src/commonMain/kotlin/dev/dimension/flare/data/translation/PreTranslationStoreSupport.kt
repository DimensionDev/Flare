package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal object PreTranslationStoreSupport {
    const val DEFAULT_PRE_TRANSLATION_MAX_ITEMS: Int = 8
    const val DEFAULT_PRE_TRANSLATION_MAX_INPUT_TOKENS: Int = 6000
    const val PRE_TRANSLATION_BATCH_MAX_ATTEMPTS: Int = 2
    const val FAILED_STALE_IN_FLIGHT_REASON: String = "stale_in_flight"
    const val SKIPPED_AI_SAME_LANGUAGE_REASON: String = "ai_same_language"
    const val SKIPPED_EMPTY_REASON: String = "empty"
    const val SKIPPED_EXCLUDED_LANGUAGE_REASON: String = "source_language_excluded"
    const val SKIPPED_NON_TRANSLATABLE_ONLY_REASON: String = "non_translatable_only"
    const val SKIPPED_SAME_LANGUAGE_REASON: String = "source_language_matches_target"
    const val SKIPPED_UNCHANGED_REASON: String = "unchanged"
    val PRE_TRANSLATION_BATCH_RETRY_DELAY: Duration = 500.milliseconds
    val STALE_TRANSLATION_TIMEOUT: Duration = 10.minutes

    fun toDbTranslation(
        candidate: PreparedTranslationCandidate,
        status: TranslationStatus,
        updatedAt: Long,
        payload: TranslationPayload? = null,
        statusReason: String? = null,
    ): DbTranslation =
        createTranslationRecord(
            entityType = candidate.entityType,
            entityKey = candidate.entityKey,
            targetLanguage = candidate.targetLanguage,
            sourceHash = candidate.sourceHash,
            status = status,
            displayMode = candidate.displayMode,
            payload = payload,
            statusReason = statusReason,
            attemptCount = candidate.attemptCount,
            updatedAt = updatedAt,
        )

    fun toBatchItem(candidate: PreparedTranslationCandidate): PreTranslationBatchItem =
        PreTranslationBatchItem(
            entityKey = candidate.entityKey,
            payload = candidate.sourceDocument,
        )

    fun chunkCandidatesForBatching(candidates: List<PreparedTranslationCandidate>): List<List<PreparedTranslationCandidate>> {
        val result = mutableListOf<List<PreparedTranslationCandidate>>()
        val current = mutableListOf<PreparedTranslationCandidate>()
        var currentTokenEstimate = 0
        candidates.forEach { candidate ->
            val itemTokens = PreTranslationPayloadSupport.estimatedTokens(candidate.sourceDocument)
            val wouldExceedCount = current.size >= DEFAULT_PRE_TRANSLATION_MAX_ITEMS
            val wouldExceedTokens =
                current.isNotEmpty() &&
                    currentTokenEstimate + itemTokens > DEFAULT_PRE_TRANSLATION_MAX_INPUT_TOKENS
            if (wouldExceedCount || wouldExceedTokens) {
                result += current.toList()
                current.clear()
                currentTokenEstimate = 0
            }
            current += candidate
            currentTokenEstimate += itemTokens
        }
        if (current.isNotEmpty()) {
            result += current.toList()
        }
        return result
    }

    suspend fun persistSkippedTranslationIfNeeded(
        database: CacheDatabase,
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
        sourceHash: String,
        displayMode: TranslationDisplayMode,
        existing: DbTranslation?,
        statusReason: String,
        updatedAt: Long,
    ) {
        if (matchesSkipped(existing, sourceHash, statusReason)) {
            return
        }
        database.translationDao().insert(
            createTranslationRecord(
                entityType = entityType,
                entityKey = entityKey,
                targetLanguage = targetLanguage,
                sourceHash = sourceHash,
                status = TranslationStatus.Skipped,
                displayMode = displayMode,
                payload = null,
                statusReason = statusReason,
                attemptCount = existing?.attemptCount ?: 0,
                updatedAt = updatedAt,
            ),
        )
    }

    fun shouldTranslate(
        existing: DbTranslation?,
        sourceHash: String,
    ): Boolean {
        if (existing == null || existing.sourceHash != sourceHash) {
            return true
        }
        return when (existing.status) {
            TranslationStatus.Completed,
            -> false

            TranslationStatus.Skipped ->
                existing.statusReason == SKIPPED_EXCLUDED_LANGUAGE_REASON

            TranslationStatus.Failed -> true
            TranslationStatus.Pending,
            TranslationStatus.Translating,
            -> false
        }
    }

    fun canRetrySkippedManually(existing: DbTranslation?): Boolean =
        existing?.status == TranslationStatus.Skipped &&
            existing.statusReason == SKIPPED_EXCLUDED_LANGUAGE_REASON

    private fun matchesSkipped(
        existing: DbTranslation?,
        sourceHash: String,
        statusReason: String,
    ): Boolean =
        existing?.sourceHash == sourceHash &&
            existing.status == TranslationStatus.Skipped &&
            existing.statusReason == statusReason

    private fun createTranslationRecord(
        entityType: TranslationEntityType,
        entityKey: String,
        targetLanguage: String,
        sourceHash: String,
        status: TranslationStatus,
        displayMode: TranslationDisplayMode,
        payload: TranslationPayload?,
        statusReason: String?,
        attemptCount: Int,
        updatedAt: Long,
    ): DbTranslation =
        DbTranslation(
            entityType = entityType,
            entityKey = entityKey,
            targetLanguage = targetLanguage,
            sourceHash = sourceHash,
            status = status,
            displayMode = displayMode,
            payload = payload,
            statusReason = statusReason,
            attemptCount = attemptCount,
            updatedAt = updatedAt,
        )
}
