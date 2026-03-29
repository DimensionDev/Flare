package dev.dimension.flare.data.translation

import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.statusTranslationEntityKey
import dev.dimension.flare.data.database.cache.model.translationEntityKey
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.applyTranslationDocument
import dev.dimension.flare.ui.render.toTranslationDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal interface PreTranslationService {
    fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean = false,
    )

    fun enqueueProfile(user: DbUser)

    fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    )

    fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    )
}

internal data object NoopPreTranslationService : PreTranslationService {
    override fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean,
    ) = Unit

    override fun enqueueProfile(user: DbUser) = Unit

    override fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    ) = Unit

    override fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ) = Unit
}

internal class AiPreTranslationService(
    private val database: CacheDatabase,
    private val appDataStore: AppDataStore,
    private val aiCompletionService: AiCompletionService,
    private val coroutineScope: CoroutineScope,
) : PreTranslationService {
    private val semaphore = Semaphore(permits = 1)

    init {
        coroutineScope.launch {
            cleanupStaleInFlightTranslations()
        }
    }

    override fun enqueueStatuses(
        statuses: List<DbStatus>,
        allowLongText: Boolean,
    ) {
        val snapshot = statuses.distinctBy { it.id }
        if (snapshot.isEmpty()) {
            return
        }
        coroutineScope.launch {
            val settings = activeTranslationSettings(requirePreTranslation = true) ?: return@launch
            val candidates =
                prepareStatusCandidates(
                    statuses = snapshot,
                    targetLanguage = settings.targetLanguage,
                    allowLongText = allowLongText,
                )
            if (candidates.isEmpty()) {
                return@launch
            }
            markPending(candidates)
            semaphore.withPermit {
                translatePreparedCandidates(
                    settings = settings.aiConfig,
                    targetLanguage = settings.targetLanguage,
                    candidates = candidates,
                )
            }
        }
    }

    override fun enqueueProfile(user: DbUser) {
        coroutineScope.launch {
            val settings = activeTranslationSettings(requirePreTranslation = true) ?: return@launch
            val candidate =
                prepareProfileCandidate(
                    user = user,
                    targetLanguage = settings.targetLanguage,
                ) ?: return@launch
            markPending(listOf(candidate))
            semaphore.withPermit {
                translatePreparedCandidates(
                    settings = settings.aiConfig,
                    targetLanguage = settings.targetLanguage,
                    candidates = listOf(candidate),
                )
            }
        }
    }

    override fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    ) {
        coroutineScope.launch {
            val settings = activeTranslationSettings(requirePreTranslation = false) ?: return@launch
            setStatusDisplayMode(
                accountType = accountType,
                statusKey = statusKey,
                mode = TranslationDisplayMode.Translated,
            )
            val dbAccountType = accountType as? dev.dimension.flare.model.DbAccountType ?: return@launch
            val status =
                database
                    .statusDao()
                    .getWithReferencesSync(
                        statusKey = statusKey,
                        accountType = dbAccountType,
                    ) ?: return@launch
            val candidates =
                prepareStatusCandidates(
                    statuses = listOfNotNull(status.status.data) + status.references.mapNotNull { it.status?.data },
                    targetLanguage = settings.targetLanguage,
                    allowLongText = true,
                    preferredDisplayMode = TranslationDisplayMode.Translated,
                )
            if (candidates.isEmpty()) {
                return@launch
            }
            markPending(candidates)
            semaphore.withPermit {
                translatePreparedCandidates(
                    settings = settings.aiConfig,
                    targetLanguage = settings.targetLanguage,
                    candidates = candidates,
                )
            }
        }
    }

    override fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ) {
        coroutineScope.launch {
            val targetLanguage = currentTargetLanguage()
            database
                .translationDao()
                .updateDisplayMode(
                    entityType = TranslationEntityType.Status,
                    entityKey = statusTranslationEntityKey(accountType, statusKey),
                    targetLanguage = targetLanguage,
                    displayMode = mode,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                )
        }
    }

    private suspend fun activeTranslationSettings(requirePreTranslation: Boolean): ActivePreTranslationSettings? {
        val appSettings = appDataStore.appSettingsStore.data.first()
        val targetLanguage = currentTargetLanguage()
        val aiConfig = appSettings.aiConfig
        if (!aiConfig.translation || (requirePreTranslation && !aiConfig.preTranslation) || targetLanguage.isBlank()) {
            return null
        }
        return ActivePreTranslationSettings(
            targetLanguage = targetLanguage,
            aiConfig = aiConfig,
        )
    }

    private fun currentTargetLanguage(): String = Locale.language

    private suspend fun cleanupStaleInFlightTranslations() {
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().markStaleInFlightAsFailed(
            staleBefore = updatedAt - STALE_TRANSLATION_TIMEOUT.inWholeMilliseconds,
            statusReason = FAILED_STALE_IN_FLIGHT_REASON,
            updatedAt = updatedAt,
        )
    }

    private suspend fun prepareStatusCandidates(
        statuses: List<DbStatus>,
        targetLanguage: String,
        allowLongText: Boolean,
        preferredDisplayMode: TranslationDisplayMode? = null,
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
                    sourceLanguages = status.content.translationSourceLanguages(),
                    existing = existingByKey[status.translationEntityKey()],
                    targetLanguage = targetLanguage,
                    now = now,
                    allowLongText = allowLongText,
                    preferredDisplayMode = preferredDisplayMode,
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
            sourceLanguages = user.content.sourceLanguages,
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
            allowLongText = true,
        )

    private suspend fun prepareCandidate(
        entityType: TranslationEntityType,
        entityKey: String,
        payload: TranslationPayload?,
        sourceLanguages: List<String>,
        existing: DbTranslation?,
        targetLanguage: String,
        now: Long,
        allowLongText: Boolean,
        preferredDisplayMode: TranslationDisplayMode? = null,
    ): PreparedTranslationCandidate? {
        if (payload == null) {
            return null
        }
        if (!allowLongText && payload.content?.isLongText == true) {
            return null
        }
        val sourceHash = payload.sourceHash()
        val displayMode = preferredDisplayMode ?: existing?.displayMode ?: TranslationDisplayMode.Auto
        if (payload.isNonTranslatableOnly()) {
            if (
                existing == null ||
                existing.sourceHash != sourceHash ||
                existing.status != TranslationStatus.Skipped ||
                existing.statusReason != SKIPPED_NON_TRANSLATABLE_ONLY_REASON
            ) {
                database.translationDao().insert(
                    DbTranslation(
                        entityType = entityType,
                        entityKey = entityKey,
                        targetLanguage = targetLanguage,
                        sourceHash = sourceHash,
                        status = TranslationStatus.Skipped,
                        displayMode = displayMode,
                        payload = null,
                        statusReason = SKIPPED_NON_TRANSLATABLE_ONLY_REASON,
                        attemptCount = existing?.attemptCount ?: 0,
                        updatedAt = now,
                    ),
                )
            }
            return null
        }
        val sourceDocument = payload.toBatchPayload(targetLanguage)
        if (shouldSkipForMatchingSourceLanguage(sourceLanguages = sourceLanguages, targetLanguage = targetLanguage)) {
            if (
                existing == null ||
                existing.sourceHash != sourceHash ||
                existing.status != TranslationStatus.Skipped ||
                existing.statusReason != SKIPPED_SAME_LANGUAGE_REASON
            ) {
                database.translationDao().insert(
                    DbTranslation(
                        entityType = entityType,
                        entityKey = entityKey,
                        targetLanguage = targetLanguage,
                        sourceHash = sourceHash,
                        status = TranslationStatus.Skipped,
                        displayMode = displayMode,
                        payload = null,
                        statusReason = SKIPPED_SAME_LANGUAGE_REASON,
                        attemptCount = existing?.attemptCount ?: 0,
                        updatedAt = now,
                    ),
                )
            }
            return null
        }
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
                        displayMode = displayMode,
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
            displayMode = displayMode,
        )
    }

    private suspend fun markPending(candidates: List<PreparedTranslationCandidate>) {
        if (candidates.isEmpty()) {
            return
        }
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().insertAll(
            candidates.map { candidate ->
                DbTranslation(
                    entityType = candidate.entityType,
                    entityKey = candidate.entityKey,
                    targetLanguage = candidate.targetLanguage,
                    sourceHash = candidate.sourceHash,
                    status = TranslationStatus.Pending,
                    displayMode = candidate.displayMode,
                    payload = null,
                    statusReason = null,
                    attemptCount = candidate.attemptCount,
                    updatedAt = updatedAt,
                )
            },
        )
    }

    private suspend fun translatePreparedCandidates(
        settings: AppSettings.AiConfig,
        targetLanguage: String,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        candidates.chunkedForBatching().forEach { batch ->
            translateBatch(
                settings = settings,
                targetLanguage = targetLanguage,
                candidates = batch,
            )
        }
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
                    displayMode = candidate.displayMode,
                    payload = null,
                    statusReason = null,
                    attemptCount = candidate.attemptCount,
                    updatedAt = startedAt,
                )
            },
        )

        runBatchTranslationWithRetry(
            settings = settings,
            targetLanguage = targetLanguage,
            candidates = candidates,
        ).getOrElse { throwable ->
            val failedAt = Clock.System.now().toEpochMilliseconds()
            database.translationDao().insertAll(
                candidates.map { candidate ->
                    DbTranslation(
                        entityType = candidate.entityType,
                        entityKey = candidate.entityKey,
                        targetLanguage = candidate.targetLanguage,
                        sourceHash = candidate.sourceHash,
                        status = TranslationStatus.Failed,
                        displayMode = candidate.displayMode,
                        payload = null,
                        statusReason = throwable.message,
                        attemptCount = candidate.attemptCount,
                        updatedAt = failedAt,
                    )
                },
            )
        }
    }

    private suspend fun runBatchTranslationWithRetry(
        settings: AppSettings.AiConfig,
        targetLanguage: String,
        candidates: List<PreparedTranslationCandidate>,
    ): Result<Unit> {
        var lastFailure: Throwable? = null
        repeat(PRE_TRANSLATION_BATCH_MAX_ATTEMPTS) { attempt ->
            val result =
                tryRun {
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
                }
            result
                .onSuccess {
                    return Result.success(Unit)
                }.onFailure { throwable ->
                    lastFailure = throwable
                    if (attempt < PRE_TRANSLATION_BATCH_MAX_ATTEMPTS - 1) {
                        delay(PRE_TRANSLATION_BATCH_RETRY_DELAY)
                    }
                }
        }

        return Result.failure(requireNotNull(lastFailure))
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
                tryRun {
                    val translatedItem =
                        translatedItems[candidate.entityKey]
                            ?: throw IllegalArgumentException("Missing translated item for ${candidate.entityKey}")
                    when (translatedItem.status) {
                        PreTranslationBatchItemStatus.Completed -> {
                            val translatedPayload =
                                candidate.sourcePayload.applyBatchPayload(
                                    sourceDocument = candidate.sourceDocument,
                                    translatedDocument =
                                        translatedItem.payload ?: throw IllegalArgumentException("Missing translated payload"),
                                )
                            if (translatedPayload == candidate.sourcePayload) {
                                DbTranslation(
                                    entityType = candidate.entityType,
                                    entityKey = candidate.entityKey,
                                    targetLanguage = candidate.targetLanguage,
                                    sourceHash = candidate.sourceHash,
                                    status = TranslationStatus.Skipped,
                                    displayMode = candidate.displayMode,
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
                                    displayMode = candidate.displayMode,
                                    payload = translatedPayload,
                                    statusReason = null,
                                    attemptCount = candidate.attemptCount,
                                    updatedAt = updatedAt,
                                )
                            }
                        }

                        PreTranslationBatchItemStatus.Skipped ->
                            DbTranslation(
                                entityType = candidate.entityType,
                                entityKey = candidate.entityKey,
                                targetLanguage = candidate.targetLanguage,
                                sourceHash = candidate.sourceHash,
                                status = TranslationStatus.Skipped,
                                displayMode = candidate.displayMode,
                                payload = null,
                                statusReason = translatedItem.reason ?: SKIPPED_AI_SAME_LANGUAGE_REASON,
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
                        displayMode = candidate.displayMode,
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
            -> false
        }
    }
}

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
    val displayMode: TranslationDisplayMode,
)

private const val DEFAULT_PRE_TRANSLATION_MAX_ITEMS = 8
private const val DEFAULT_PRE_TRANSLATION_MAX_INPUT_TOKENS = 6000
private const val PRE_TRANSLATION_BATCH_MAX_ATTEMPTS = 2
private const val FAILED_STALE_IN_FLIGHT_REASON = "stale_in_flight"
private const val SKIPPED_AI_SAME_LANGUAGE_REASON = "ai_same_language"
private const val SKIPPED_EMPTY_REASON = "empty"
private const val SKIPPED_NON_TRANSLATABLE_ONLY_REASON = "non_translatable_only"
private const val SKIPPED_SAME_LANGUAGE_REASON = "source_language_matches_target"
private const val SKIPPED_UNCHANGED_REASON = "unchanged"
private val PRE_TRANSLATION_BATCH_RETRY_DELAY = 500.milliseconds
private val STALE_TRANSLATION_TIMEOUT = 10.minutes
private val protectedTranslationPattern =
    Regex("""https?://\S+|@[A-Za-z0-9._-]+(?:@[A-Za-z0-9.-]+)?|#[\p{L}\p{N}_]+""")

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

private fun dev.dimension.flare.ui.model.UiTimelineV2.translationSourceLanguages(): List<String> =
    when (this) {
        is dev.dimension.flare.ui.model.UiTimelineV2.Feed -> sourceLanguages
        is dev.dimension.flare.ui.model.UiTimelineV2.Post -> sourceLanguages
        is dev.dimension.flare.ui.model.UiTimelineV2.Message -> emptyList()
        is dev.dimension.flare.ui.model.UiTimelineV2.User -> emptyList()
        is dev.dimension.flare.ui.model.UiTimelineV2.UserList -> emptyList()
    }

private fun shouldSkipForMatchingSourceLanguage(
    sourceLanguages: List<String>,
    targetLanguage: String,
): Boolean {
    val canonicalTargetLanguage = canonicalTranslationLanguage(targetLanguage) ?: return false
    return sourceLanguages
        .asSequence()
        .mapNotNull(::canonicalTranslationLanguage)
        .any { it == canonicalTargetLanguage }
}

private fun canonicalTranslationLanguage(language: String): String? {
    val normalized = language.trim().lowercase().replace('_', '-')
    if (normalized.isBlank()) {
        return null
    }
    val parts = normalized.split('-').filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        return null
    }
    val primary = parts.first()
    if (primary != "zh") {
        return primary
    }
    val regionOrScript = parts.drop(1)
    return when {
        regionOrScript.any { it == "hant" } || regionOrScript.any { it in setOf("tw", "hk", "mo") } -> "zh-hant"
        regionOrScript.any { it == "hans" } || regionOrScript.any { it in setOf("cn", "sg") } -> "zh-hans"
        else -> "zh"
    }
}

private fun TranslationPayload.isNonTranslatableOnly(): Boolean {
    val fields = listOfNotNull(content, contentWarning, title, description)
    return fields.isNotEmpty() && fields.all { it.isNonTranslatableOnly() }
}

private fun UiRichText.isNonTranslatableOnly(): Boolean {
    var hasVisibleContent = false
    renderRuns.forEach { content ->
        when (content) {
            is RenderContent.BlockImage -> hasVisibleContent = true
            is RenderContent.Text ->
                content.runs.forEach { run ->
                    when (run) {
                        is RenderRun.Image -> hasVisibleContent = true
                        is RenderRun.Text -> {
                            if (run.text.isBlank()) {
                                return@forEach
                            }
                            hasVisibleContent = true
                            if (!run.text.isNonTranslatableOnlyText(run.style)) {
                                return false
                            }
                        }
                    }
                }
        }
    }
    return hasVisibleContent
}

private fun String.isNonTranslatableOnlyText(style: RenderTextStyle): Boolean {
    if (isBlank()) {
        return false
    }
    if (style.code || style.monospace) {
        return true
    }
    var hasVisibleContent = false
    var cursor = 0
    protectedTranslationPattern.findAll(this).forEach { match ->
        if (match.range.first > cursor) {
            val segment = substring(cursor, match.range.first)
            if (!segment.isBlank()) {
                hasVisibleContent = true
                if (!segment.isEmojiOnlyText()) {
                    return false
                }
            }
        }
        if (match.value.isNotBlank()) {
            hasVisibleContent = true
        }
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        val trailing = substring(cursor)
        if (!trailing.isBlank()) {
            hasVisibleContent = true
            if (!trailing.isEmojiOnlyText()) {
                return false
            }
        }
    }
    return hasVisibleContent
}

private fun String.isEmojiOnlyText(): Boolean {
    if (isBlank()) {
        return false
    }
    var hasEmoji = false
    var index = 0
    while (index < length) {
        val current = this[index]
        when {
            current.isWhitespace() -> index += 1
            current in '\uD83C'..'\uD83E' && index + 1 < length && this[index + 1].isLowSurrogate() -> {
                hasEmoji = true
                index += 2
            }
            current.code == 0x200D ||
                current.code == 0x20E3 ||
                current.code in 0xFE00..0xFE0F ||
                current.code in 0x2600..0x27BF -> {
                hasEmoji = true
                index += 1
            }

            else -> return false
        }
    }
    return hasEmoji
}
