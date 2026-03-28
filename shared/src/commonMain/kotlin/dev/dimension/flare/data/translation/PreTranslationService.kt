package dev.dimension.flare.data.translation

import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.applyTranslationDocument
import dev.dimension.flare.ui.render.toTranslationDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

internal interface PreTranslationService {
    fun enqueueStatuses(statuses: List<DbStatus>)

    fun enqueueProfile(user: DbUser)
}

internal data object NoopPreTranslationService : PreTranslationService {
    override fun enqueueStatuses(statuses: List<DbStatus>) = Unit

    override fun enqueueProfile(user: DbUser) = Unit
}

internal class AiPreTranslationService(
    private val database: CacheDatabase,
    private val appDataStore: AppDataStore,
    private val aiCompletionService: AiCompletionService,
    private val coroutineScope: CoroutineScope,
) : PreTranslationService {
    private val semaphore = Semaphore(permits = 1)

    override fun enqueueStatuses(statuses: List<DbStatus>) {
        val snapshot = statuses.distinctBy { it.id }
        if (snapshot.isEmpty()) {
            return
        }
        coroutineScope.launch {
            semaphore.withPermit {
                processStatusSnapshot(snapshot)
            }
        }
    }

    override fun enqueueProfile(user: DbUser) {
        coroutineScope.launch {
            semaphore.withPermit {
                processProfile(user)
            }
        }
    }

    private suspend fun processStatusSnapshot(statuses: List<DbStatus>) {
        val settings = preTranslationSettings() ?: return
        val candidates =
            prepareStatusCandidates(
                statuses = statuses,
                targetLanguage = settings.targetLanguage,
            )
        if (candidates.isEmpty()) {
            return
        }
        candidates.chunkedForBatching().forEach { batch ->
            translateBatch(
                settings = settings.aiConfig,
                targetLanguage = settings.targetLanguage,
                candidates = batch,
            )
        }
    }

    private suspend fun processProfile(user: DbUser) {
        val settings = preTranslationSettings() ?: return
        val candidate =
            prepareProfileCandidate(
                user = user,
                targetLanguage = settings.targetLanguage,
            ) ?: return
        translateBatch(
            settings = settings.aiConfig,
            targetLanguage = settings.targetLanguage,
            candidates = listOf(candidate),
        )
    }

    private suspend fun preTranslationSettings(): ActivePreTranslationSettings? {
        val appSettings = appDataStore.appSettingsStore.data.first()
        val targetLanguage = appSettings.language.ifBlank { Locale.language }
        val aiConfig = appSettings.aiConfig
        if (!aiConfig.translation || !aiConfig.preTranslation || targetLanguage.isBlank()) {
            return null
        }
        return ActivePreTranslationSettings(
            targetLanguage = targetLanguage,
            aiConfig = aiConfig,
        )
    }

    private suspend fun prepareStatusCandidates(
        statuses: List<DbStatus>,
        targetLanguage: String,
    ): List<PreparedTranslationCandidate> {
        val deduplicated = statuses.distinctBy { it.translationEntityKey() }
        if (deduplicated.isEmpty()) {
            return emptyList()
        }
        val existingByKey =
            database
                .translationDao()
                .getByEntityKeys(
                    entityType = TranslationEntityType.Status,
                    entityKeys = deduplicated.map { it.translationEntityKey() },
                    targetLanguage = targetLanguage,
                ).associateBy { it.entityKey }
        val now = Clock.System.now().toEpochMilliseconds()
        return buildList {
            deduplicated.forEach { status ->
                prepareCandidate(
                    entityType = TranslationEntityType.Status,
                    entityKey = status.translationEntityKey(),
                    payload = status.content.translationPayload(),
                    existing = existingByKey[status.translationEntityKey()],
                    targetLanguage = targetLanguage,
                    now = now,
                )?.let(::add)
            }
        }
    }

    private suspend fun prepareProfileCandidate(
        user: DbUser,
        targetLanguage: String,
    ): PreparedTranslationCandidate? =
        prepareCandidate(
            entityType = TranslationEntityType.Profile,
            entityKey = user.translationEntityKey(),
            payload = user.content.translationPayload(),
            existing =
                database
                    .translationDao()
                    .get(
                        entityType = TranslationEntityType.Profile,
                        entityKey = user.translationEntityKey(),
                        targetLanguage = targetLanguage,
                    ),
            targetLanguage = targetLanguage,
            now = Clock.System.now().toEpochMilliseconds(),
        )

    private suspend fun prepareCandidate(
        entityType: TranslationEntityType,
        entityKey: String,
        payload: TranslationPayload?,
        existing: DbTranslation?,
        targetLanguage: String,
        now: Long,
    ): PreparedTranslationCandidate? {
        if (payload == null) {
            return null
        }
        val sourceDocument = payload.toBatchPayload(targetLanguage)
        val sourceHash = payload.sourceHash()
        if (sourceDocument.isEmpty()) {
            if (
                existing == null ||
                existing.sourceHash != sourceHash ||
                existing.status != TranslationStatus.Skipped ||
                existing.statusReason != SKIPPED_EMPTY_REASON
            ) {
                database.translationDao().insert(
                    DbTranslation(
                        entityType = entityType,
                        entityKey = entityKey,
                        targetLanguage = targetLanguage,
                        sourceHash = sourceHash,
                        status = TranslationStatus.Skipped,
                        payload = null,
                        statusReason = SKIPPED_EMPTY_REASON,
                        attemptCount = existing?.attemptCount ?: 0,
                        updatedAt = now,
                    ),
                )
            }
            return null
        }
        if (!shouldTranslate(existing = existing, sourceHash = sourceHash, now = now)) {
            return null
        }
        return PreparedTranslationCandidate(
            entityType = entityType,
            entityKey = entityKey,
            targetLanguage = targetLanguage,
            sourceHash = sourceHash,
            sourcePayload = payload,
            sourceDocument = sourceDocument,
            attemptCount = (existing?.attemptCount ?: 0) + 1,
        )
    }

    private suspend fun translateBatch(
        settings: AppSettings.AiConfig,
        targetLanguage: String,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        if (candidates.isEmpty()) {
            return
        }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().insertAll(
            candidates.map { candidate ->
                DbTranslation(
                    entityType = candidate.entityType,
                    entityKey = candidate.entityKey,
                    targetLanguage = candidate.targetLanguage,
                    sourceHash = candidate.sourceHash,
                    status = TranslationStatus.Translating,
                    payload = null,
                    statusReason = null,
                    attemptCount = candidate.attemptCount,
                    updatedAt = startedAt,
                )
            },
        )

        runCatching {
            val sourceDocument =
                PreTranslationBatchDocument(
                    targetLanguage = targetLanguage,
                    items =
                        candidates.map { candidate ->
                            PreTranslationBatchItem(
                                entityKey = candidate.entityKey,
                                payload = candidate.sourceDocument,
                            )
                        },
                )
            val sourceJson = sourceDocument.encodeJson(PreTranslationBatchDocument.serializer())
            val prompt = buildTranslatePrompt(settings.translatePrompt, targetLanguage, sourceJson)
            val translatedJson =
                aiCompletionService.translate(
                    config = settings,
                    source = sourceJson,
                    targetLanguage = targetLanguage,
                    prompt = prompt,
                ) ?: error("Pre-translation returned empty response")

            applyBatchResult(
                translatedJson = translatedJson,
                candidates = candidates,
            )
        }.getOrElse { throwable ->
            val failedAt = Clock.System.now().toEpochMilliseconds()
            database.translationDao().insertAll(
                candidates.map { candidate ->
                    DbTranslation(
                        entityType = candidate.entityType,
                        entityKey = candidate.entityKey,
                        targetLanguage = candidate.targetLanguage,
                        sourceHash = candidate.sourceHash,
                        status = TranslationStatus.Failed,
                        payload = null,
                        statusReason = throwable.message,
                        attemptCount = candidate.attemptCount,
                        updatedAt = failedAt,
                    )
                },
            )
        }
    }

    private suspend fun applyBatchResult(
        translatedJson: String,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        val translatedDocument =
            cleanJsonResponse(translatedJson).decodeJson(PreTranslationBatchDocument.serializer())
        val translatedItems = translatedDocument.items.associateBy { it.entityKey }
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().insertAll(
            candidates.map { candidate ->
                runCatching {
                    val translatedItem =
                        translatedItems[candidate.entityKey]
                            ?: throw IllegalArgumentException("Missing translated item for ${candidate.entityKey}")
                    val translatedPayload =
                        candidate.sourcePayload.applyBatchPayload(
                            sourceDocument = candidate.sourceDocument,
                            translatedDocument = translatedItem.payload,
                        )
                    if (translatedPayload == candidate.sourcePayload) {
                        DbTranslation(
                            entityType = candidate.entityType,
                            entityKey = candidate.entityKey,
                            targetLanguage = candidate.targetLanguage,
                            sourceHash = candidate.sourceHash,
                            status = TranslationStatus.Skipped,
                            payload = null,
                            statusReason = SKIPPED_UNCHANGED_REASON,
                            attemptCount = candidate.attemptCount,
                            updatedAt = updatedAt,
                        )
                    } else {
                        DbTranslation(
                            entityType = candidate.entityType,
                            entityKey = candidate.entityKey,
                            targetLanguage = candidate.targetLanguage,
                            sourceHash = candidate.sourceHash,
                            status = TranslationStatus.Completed,
                            payload = translatedPayload,
                            statusReason = null,
                            attemptCount = candidate.attemptCount,
                            updatedAt = updatedAt,
                        )
                    }
                }.getOrElse { throwable ->
                    DbTranslation(
                        entityType = candidate.entityType,
                        entityKey = candidate.entityKey,
                        targetLanguage = candidate.targetLanguage,
                        sourceHash = candidate.sourceHash,
                        status = TranslationStatus.Failed,
                        payload = null,
                        statusReason = throwable.message,
                        attemptCount = candidate.attemptCount,
                        updatedAt = updatedAt,
                    )
                }
            },
        )
    }

    private fun buildTranslatePrompt(
        configuredPrompt: String,
        targetLanguage: String,
        sourceJson: String,
    ): String {
        val template =
            configuredPrompt.ifBlank {
                AiPromptDefaults.TRANSLATE_PROMPT
            }
        return template
            .replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceJson)
            .replace("{source_json}", sourceJson)
            .replace("{source_html}", sourceJson)
            .replace("{source_xml}", sourceJson)
            .replace("{source_markup}", sourceJson)
    }

    private fun cleanJsonResponse(content: String): String =
        content
            .removePrefix("```json")
            .removePrefix("```html")
            .removePrefix("```xml")
            .removePrefix("```markup")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    private fun shouldTranslate(
        existing: DbTranslation?,
        sourceHash: String,
        now: Long,
    ): Boolean {
        if (existing == null || existing.sourceHash != sourceHash) {
            return true
        }
        return when (existing.status) {
            TranslationStatus.Completed,
            TranslationStatus.Skipped,
            -> false

            TranslationStatus.Failed -> true
            TranslationStatus.Pending,
            TranslationStatus.Translating,
            -> now - existing.updatedAt >= STALE_TRANSLATION_TIMEOUT.inWholeMilliseconds
        }
    }
}

@Serializable
internal data class PreTranslationBatchDocument(
    val version: Int = 1,
    val targetLanguage: String,
    val items: List<PreTranslationBatchItem>,
)

@Serializable
internal data class PreTranslationBatchItem(
    val entityKey: String,
    val payload: PreTranslationBatchPayload,
)

@Serializable
internal data class PreTranslationBatchPayload(
    val content: TranslationDocument? = null,
    val contentWarning: TranslationDocument? = null,
    val title: TranslationDocument? = null,
    val description: TranslationDocument? = null,
)

private data class ActivePreTranslationSettings(
    val targetLanguage: String,
    val aiConfig: AppSettings.AiConfig,
)

private data class PreparedTranslationCandidate(
    val entityType: TranslationEntityType,
    val entityKey: String,
    val targetLanguage: String,
    val sourceHash: String,
    val sourcePayload: TranslationPayload,
    val sourceDocument: PreTranslationBatchPayload,
    val attemptCount: Int,
)

private const val DEFAULT_PRE_TRANSLATION_MAX_ITEMS = 8
private const val DEFAULT_PRE_TRANSLATION_MAX_INPUT_TOKENS = 6000
private const val SKIPPED_EMPTY_REASON = "empty"
private const val SKIPPED_UNCHANGED_REASON = "unchanged"
private val STALE_TRANSLATION_TIMEOUT = 10.minutes

private fun List<PreparedTranslationCandidate>.chunkedForBatching(): List<List<PreparedTranslationCandidate>> {
    val result = mutableListOf<List<PreparedTranslationCandidate>>()
    val current = mutableListOf<PreparedTranslationCandidate>()
    var currentTokenEstimate = 0
    forEach { candidate ->
        val itemTokens = candidate.sourceDocument.estimatedTokens()
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

private fun PreTranslationBatchPayload.estimatedTokens(): Int = this.encodeJson(PreTranslationBatchPayload.serializer()).length / 4 + 1

private fun PreTranslationBatchPayload.isEmpty(): Boolean =
    content == null && contentWarning == null && title == null && description == null

private fun TranslationPayload.toBatchPayload(targetLanguage: String): PreTranslationBatchPayload =
    PreTranslationBatchPayload(
        content = content?.toTranslationDocumentOrNull(targetLanguage),
        contentWarning = contentWarning?.toTranslationDocumentOrNull(targetLanguage),
        title = title?.toTranslationDocumentOrNull(targetLanguage),
        description = description?.toTranslationDocumentOrNull(targetLanguage),
    )

private fun TranslationPayload.applyBatchPayload(
    sourceDocument: PreTranslationBatchPayload,
    translatedDocument: PreTranslationBatchPayload,
): TranslationPayload =
    TranslationPayload(
        content = content.applyTranslatedField(sourceDocument.content, translatedDocument.content),
        contentWarning = contentWarning.applyTranslatedField(sourceDocument.contentWarning, translatedDocument.contentWarning),
        title = title.applyTranslatedField(sourceDocument.title, translatedDocument.title),
        description = description.applyTranslatedField(sourceDocument.description, translatedDocument.description),
    )

private fun dev.dimension.flare.ui.render.UiRichText?.toTranslationDocumentOrNull(targetLanguage: String): TranslationDocument? =
    this?.toTranslationDocument(targetLanguage)?.takeUnless { it.blocks.isEmpty() }

private fun dev.dimension.flare.ui.render.UiRichText?.applyTranslatedField(
    sourceDocument: TranslationDocument?,
    translatedDocument: TranslationDocument?,
): dev.dimension.flare.ui.render.UiRichText? =
    when {
        this == null -> null
        sourceDocument == null -> this
        translatedDocument == null -> throw IllegalArgumentException("Missing translated field")
        else -> applyTranslationDocument(translatedDocument)
    }
