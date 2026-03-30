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
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.tryRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

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

internal class OnlinePreTranslationService(
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
            enqueuePreparedCandidates(requirePreTranslation = true) { settings ->
                prepareStatusCandidates(
                    statuses = snapshot,
                    targetLanguage = settings.targetLanguage,
                    providerCacheKey = settings.providerCacheKey,
                    allowLongText = allowLongText,
                )
            }
        }
    }

    override fun enqueueProfile(user: DbUser) {
        coroutineScope.launch {
            enqueuePreparedCandidates(requirePreTranslation = true) { settings ->
                listOfNotNull(
                    prepareProfileCandidate(
                        user = user,
                        targetLanguage = settings.targetLanguage,
                        providerCacheKey = settings.providerCacheKey,
                    ),
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
            val candidates =
                prepareRetryCandidates(
                    accountType = accountType,
                    statusKey = statusKey,
                    targetLanguage = settings.targetLanguage,
                    providerCacheKey = settings.providerCacheKey,
                )
            processPreparedCandidates(
                settings = settings,
                candidates = candidates,
            )
        }
    }

    override fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ) {
        coroutineScope.launch {
            database.translationDao().updateDisplayMode(
                entityType = TranslationEntityType.Status,
                entityKey = statusTranslationEntityKey(accountType, statusKey),
                targetLanguage = currentTargetLanguage(),
                displayMode = mode,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    private suspend fun enqueuePreparedCandidates(
        requirePreTranslation: Boolean,
        prepareCandidates: suspend (ActivePreTranslationSettings) -> List<PreparedTranslationCandidate>,
    ) {
        val settings = activeTranslationSettings(requirePreTranslation = requirePreTranslation) ?: return
        val candidates = prepareCandidates(settings)
        processPreparedCandidates(
            settings = settings,
            candidates = candidates,
        )
    }

    private suspend fun processPreparedCandidates(
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        if (candidates.isEmpty()) {
            return
        }
        markPending(candidates)
        semaphore.withPermit {
            translatePreparedCandidates(
                settings = settings,
                candidates = candidates,
            )
        }
    }

    private suspend fun activeTranslationSettings(requirePreTranslation: Boolean): ActivePreTranslationSettings? {
        val appSettings = appDataStore.appSettingsStore.data.first()
        val targetLanguage = currentTargetLanguage()
        val canTranslate =
            if (requirePreTranslation) {
                appSettings.translateConfig.preTranslate
            } else {
                true
            }
        if (!canTranslate || targetLanguage.isBlank()) {
            return null
        }
        return ActivePreTranslationSettings(
            targetLanguage = targetLanguage,
            appSettings = appSettings,
            providerCacheKey = appSettings.translationProviderCacheKey(),
        )
    }

    private fun currentTargetLanguage(): String = Locale.language

    private suspend fun cleanupStaleInFlightTranslations() {
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().markStaleInFlightAsFailed(
            staleBefore = updatedAt - PreTranslationStoreSupport.STALE_TRANSLATION_TIMEOUT.inWholeMilliseconds,
            statusReason = PreTranslationStoreSupport.FAILED_STALE_IN_FLIGHT_REASON,
            updatedAt = updatedAt,
        )
    }

    private suspend fun prepareRetryCandidates(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        targetLanguage: String,
        providerCacheKey: String,
    ): List<PreparedTranslationCandidate> {
        val dbAccountType = accountType as? dev.dimension.flare.model.DbAccountType ?: return emptyList()
        val status =
            database
                .statusDao()
                .getWithReferencesSync(
                    statusKey = statusKey,
                    accountType = dbAccountType,
                ) ?: return emptyList()
        return prepareStatusCandidates(
            statuses = listOfNotNull(status.status.data) + status.references.mapNotNull { it.status?.data },
            targetLanguage = targetLanguage,
            providerCacheKey = providerCacheKey,
            allowLongText = true,
            preferredDisplayMode = TranslationDisplayMode.Translated,
        )
    }

    private suspend fun prepareStatusCandidates(
        statuses: List<DbStatus>,
        targetLanguage: String,
        providerCacheKey: String,
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
                val entityKey = status.translationEntityKey()
                prepareCandidate(
                    entityType = TranslationEntityType.Status,
                    entityKey = entityKey,
                    payload = status.content.translationPayload(),
                    sourceLanguages = PreTranslationContentRules.sourceLanguages(status.content),
                    existing = existingByKey[entityKey],
                    targetLanguage = targetLanguage,
                    providerCacheKey = providerCacheKey,
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
        providerCacheKey: String,
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
            providerCacheKey = providerCacheKey,
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
        providerCacheKey: String,
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
        val sourceHash = payload.sourceHash(providerCacheKey)
        val displayMode = preferredDisplayMode ?: existing?.displayMode ?: TranslationDisplayMode.Auto
        val sourceDocument = PreTranslationPayloadSupport.toBatchPayload(payload, targetLanguage)
        val skipReason =
            when {
                PreTranslationContentRules.isNonTranslatableOnly(payload) ->
                    PreTranslationStoreSupport.SKIPPED_NON_TRANSLATABLE_ONLY_REASON

                PreTranslationContentRules.shouldSkipForMatchingSourceLanguage(
                    sourceLanguages = sourceLanguages,
                    targetLanguage = targetLanguage,
                ) -> PreTranslationStoreSupport.SKIPPED_SAME_LANGUAGE_REASON

                PreTranslationPayloadSupport.isEmpty(sourceDocument) ->
                    PreTranslationStoreSupport.SKIPPED_EMPTY_REASON

                else -> null
            }
        if (skipReason != null) {
            PreTranslationStoreSupport.persistSkippedTranslationIfNeeded(
                database = database,
                entityType = entityType,
                entityKey = entityKey,
                targetLanguage = targetLanguage,
                sourceHash = sourceHash,
                displayMode = displayMode,
                existing = existing,
                statusReason = skipReason,
                updatedAt = now,
            )
            return null
        }
        if (!PreTranslationStoreSupport.shouldTranslate(existing = existing, sourceHash = sourceHash)) {
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
                PreTranslationStoreSupport.toDbTranslation(
                    candidate = candidate,
                    status = TranslationStatus.Pending,
                    updatedAt = updatedAt,
                )
            },
        )
    }

    private suspend fun translatePreparedCandidates(
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        PreTranslationStoreSupport
            .chunkCandidatesForBatching(candidates)
            .forEach { batch ->
                translateBatch(
                    settings = settings,
                    candidates = batch,
                )
            }
    }

    private suspend fun translateBatch(
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        if (candidates.isEmpty()) {
            return
        }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().insertAll(
            candidates.map { candidate ->
                PreTranslationStoreSupport.toDbTranslation(
                    candidate = candidate,
                    status = TranslationStatus.Translating,
                    updatedAt = startedAt,
                )
            },
        )

        runBatchTranslationWithRetry(
            settings = settings,
            candidates = candidates,
        ).getOrElse { throwable ->
            val failedAt = Clock.System.now().toEpochMilliseconds()
            database.translationDao().insertAll(
                candidates.map { candidate ->
                    PreTranslationStoreSupport.toDbTranslation(
                        candidate = candidate,
                        status = TranslationStatus.Failed,
                        updatedAt = failedAt,
                        statusReason = throwable.message,
                    )
                },
            )
        }
    }

    private suspend fun runBatchTranslationWithRetry(
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ): Result<Unit> {
        var lastFailure: Throwable? = null
        repeat(PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_MAX_ATTEMPTS) { attempt ->
            val result =
                tryRun {
                    val sourceDocument =
                        PreTranslationBatchDocument(
                            targetLanguage = settings.targetLanguage,
                            items = candidates.map(PreTranslationStoreSupport::toBatchItem),
                        )
                    val sourceJson =
                        sourceDocument.encodeJson(
                            PreTranslationBatchDocument.serializer(),
                        )
                    val prompt =
                        TranslationPromptFormatter.buildTranslatePrompt(
                            settings = settings.appSettings,
                            targetLanguage = settings.targetLanguage,
                            sourceText = sourceJson,
                            sourceJson = sourceJson,
                        )
                    val translatedJson =
                        TranslationProvider.translateBatchDocumentJson(
                            settings = settings.appSettings,
                            aiCompletionService = aiCompletionService,
                            sourceJson = sourceJson,
                            sourceDocument = sourceDocument,
                            targetLanguage = settings.targetLanguage,
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
                    if (attempt < PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_MAX_ATTEMPTS - 1) {
                        delay(PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_RETRY_DELAY)
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
            TranslationResponseSanitizer
                .clean(translatedJson)
                .decodeJson(PreTranslationBatchDocument.serializer())
        val translatedItems = translatedDocument.items.associateBy { it.entityKey }
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.translationDao().insertAll(
            candidates.map { candidate ->
                tryRun {
                    val translatedItem =
                        translatedItems[candidate.entityKey]
                            ?: error("Missing translated item for ${candidate.entityKey}")
                    translatedDbTranslation(
                        candidate = candidate,
                        translatedItem = translatedItem,
                        updatedAt = updatedAt,
                    )
                }.getOrElse { throwable ->
                    PreTranslationStoreSupport.toDbTranslation(
                        candidate = candidate,
                        status = TranslationStatus.Failed,
                        updatedAt = updatedAt,
                        statusReason = throwable.message,
                    )
                }
            },
        )
    }

    private fun translatedDbTranslation(
        candidate: PreparedTranslationCandidate,
        translatedItem: PreTranslationBatchItem,
        updatedAt: Long,
    ): DbTranslation =
        when (translatedItem.status) {
            PreTranslationBatchItemStatus.Completed -> {
                val translatedPayload =
                    PreTranslationPayloadSupport.applyBatchPayload(
                        sourcePayload = candidate.sourcePayload,
                        sourceDocument = candidate.sourceDocument,
                        translatedDocument = translatedItem.payload ?: error("Missing translated payload"),
                    )
                if (translatedPayload == candidate.sourcePayload) {
                    PreTranslationStoreSupport.toDbTranslation(
                        candidate = candidate,
                        status = TranslationStatus.Skipped,
                        updatedAt = updatedAt,
                        statusReason = PreTranslationStoreSupport.SKIPPED_UNCHANGED_REASON,
                    )
                } else {
                    PreTranslationStoreSupport.toDbTranslation(
                        candidate = candidate,
                        status = TranslationStatus.Completed,
                        updatedAt = updatedAt,
                        payload = translatedPayload,
                    )
                }
            }

            PreTranslationBatchItemStatus.Skipped ->
                PreTranslationStoreSupport.toDbTranslation(
                    candidate = candidate,
                    status = TranslationStatus.Skipped,
                    updatedAt = updatedAt,
                    statusReason =
                        translatedItem.reason ?: PreTranslationStoreSupport.SKIPPED_AI_SAME_LANGUAGE_REASON,
                )
        }
}
