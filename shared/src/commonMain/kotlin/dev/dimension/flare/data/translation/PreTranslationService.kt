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
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.tryRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val batchTranslator: suspend (
        AppSettings,
        AiCompletionService,
        String,
        PreTranslationBatchDocument,
        String,
        String,
    ) -> String? = TranslationProvider::translateBatchDocumentJson,
) : PreTranslationService {
    private val sessionMutex = Mutex()
    private val executionSession =
        MutableStateFlow(
            PreTranslationExecutionSession(
                generation = 0L,
                providerCacheKey = "",
                scope = createExecutionScope(),
            ),
        )
    private var nextGeneration: Long = 0L

    init {
        coroutineScope.launch {
            cleanupStaleInFlightTranslations()
        }
        coroutineScope.launch {
            appDataStore.appSettingsStore.data
                .map { it.translationProviderCacheKey() }
                .distinctUntilChanged()
                .collect { providerCacheKey ->
                    rotateExecutionSession(providerCacheKey)
                }
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
        launchPreparedCandidates(requirePreTranslation = true) { settings ->
            prepareStatusCandidates(
                statuses = snapshot,
                targetLanguage = settings.targetLanguage,
                autoTranslateExcludedLanguages = settings.autoTranslateExcludedLanguages,
                providerCacheKey = settings.providerCacheKey,
                allowLongText = allowLongText,
            )
        }
    }

    override fun enqueueProfile(user: DbUser) {
        launchPreparedCandidates(requirePreTranslation = true) { settings ->
            listOfNotNull(
                prepareProfileCandidate(
                    user = user,
                    targetLanguage = settings.targetLanguage,
                    autoTranslateExcludedLanguages = settings.autoTranslateExcludedLanguages,
                    providerCacheKey = settings.providerCacheKey,
                ),
            )
        }
    }

    override fun retryStatus(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
    ) {
        launchPreparedCandidates(requirePreTranslation = false) { settings ->
            updateStatusDisplayMode(
                accountType = accountType,
                statusKey = statusKey,
                mode = TranslationDisplayMode.Translated,
            )
            prepareRetryCandidates(
                accountType = accountType,
                statusKey = statusKey,
                targetLanguage = settings.targetLanguage,
                providerCacheKey = settings.providerCacheKey,
            )
        }
    }

    override fun setStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ) {
        coroutineScope.launch {
            updateStatusDisplayMode(
                accountType = accountType,
                statusKey = statusKey,
                mode = mode,
            )
        }
    }

    private fun launchPreparedCandidates(
        requirePreTranslation: Boolean,
        prepareCandidates: suspend (ActivePreTranslationSettings) -> List<PreparedTranslationCandidate>,
    ) {
        coroutineScope.launch {
            val settings = activeTranslationSettings(requirePreTranslation = requirePreTranslation) ?: return@launch
            val session = executionSessionFor(settings) ?: return@launch
            session.scope.launch {
                val candidates = prepareCandidates(settings)
                processPreparedCandidates(
                    session = session,
                    settings = settings,
                    candidates = candidates,
                )
            }
        }
    }

    private suspend fun updateStatusDisplayMode(
        accountType: dev.dimension.flare.model.AccountType,
        statusKey: dev.dimension.flare.model.MicroBlogKey,
        mode: TranslationDisplayMode,
    ) {
        database.translationDao().updateDisplayMode(
            entityType = TranslationEntityType.Status,
            entityKey = statusTranslationEntityKey(accountType, statusKey),
            targetLanguage = currentTargetLanguage(),
            displayMode = mode,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private suspend fun rotateExecutionSession(providerCacheKey: String): PreTranslationExecutionSession =
        sessionMutex.withLock {
            val current = executionSession.value
            if (current.providerCacheKey == providerCacheKey) {
                return@withLock current
            }
            current.scope.coroutineContext[Job]?.cancel()
            if (current.providerCacheKey.isNotEmpty()) {
                database.translationDao().deleteInFlight()
            }
            createExecutionSession(providerCacheKey).also {
                executionSession.value = it
            }
        }

    private suspend fun executionSessionFor(settings: ActivePreTranslationSettings): PreTranslationExecutionSession? {
        executionSession.value
            .takeIf { it.providerCacheKey == settings.providerCacheKey }
            ?.let {
                return it
            }
        val latestProviderCacheKey =
            appDataStore.appSettingsStore.data
                .first()
                .translationProviderCacheKey()
        if (latestProviderCacheKey != settings.providerCacheKey) {
            return null
        }
        return rotateExecutionSession(latestProviderCacheKey)
    }

    private fun createExecutionSession(providerCacheKey: String): PreTranslationExecutionSession {
        nextGeneration += 1
        return PreTranslationExecutionSession(
            generation = nextGeneration,
            providerCacheKey = providerCacheKey,
            scope = createExecutionScope(),
        )
    }

    private fun createExecutionScope(): CoroutineScope =
        CoroutineScope(
            coroutineScope.coroutineContext +
                SupervisorJob(coroutineScope.coroutineContext[Job]),
        )

    private suspend fun ensureCurrentExecutionSession(session: PreTranslationExecutionSession) {
        currentCoroutineContext().ensureActive()
        if (
            executionSession.value.generation != session.generation ||
            executionSession.value.providerCacheKey != session.providerCacheKey
        ) {
            throw CancellationException("Pre-translation session rotated")
        }
    }

    private suspend fun processPreparedCandidates(
        session: PreTranslationExecutionSession,
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        if (candidates.isEmpty()) {
            return
        }
        ensureCurrentExecutionSession(session)
        markPending(candidates)
        session.semaphore.withPermit {
            ensureCurrentExecutionSession(session)
            translatePreparedCandidates(
                session = session,
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
            autoTranslateExcludedLanguages = appSettings.translateConfig.autoTranslateExcludedLanguages,
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
            autoTranslateExcludedLanguages = emptyList(),
            providerCacheKey = providerCacheKey,
            allowLongText = true,
            preferredDisplayMode = TranslationDisplayMode.Translated,
        )
    }

    private suspend fun prepareStatusCandidates(
        statuses: List<DbStatus>,
        targetLanguage: String,
        autoTranslateExcludedLanguages: List<String>,
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
                    autoTranslateExcludedLanguages = autoTranslateExcludedLanguages,
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
        autoTranslateExcludedLanguages: List<String>,
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
            autoTranslateExcludedLanguages = autoTranslateExcludedLanguages,
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
        autoTranslateExcludedLanguages: List<String>,
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

                PreTranslationContentRules.shouldSkipForExcludedSourceLanguage(
                    sourceLanguages = sourceLanguages,
                    excludedLanguages = autoTranslateExcludedLanguages,
                ) -> PreTranslationStoreSupport.SKIPPED_EXCLUDED_LANGUAGE_REASON

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
        session: PreTranslationExecutionSession,
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        PreTranslationStoreSupport
            .chunkCandidatesForBatching(candidates)
            .forEach { batch ->
                translateBatch(
                    session = session,
                    settings = settings,
                    candidates = batch,
                )
            }
    }

    private suspend fun translateBatch(
        session: PreTranslationExecutionSession,
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        if (candidates.isEmpty()) {
            return
        }
        ensureCurrentExecutionSession(session)
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

        val failure =
            runBatchTranslationWithRetry(
                session = session,
                settings = settings,
                candidates = candidates,
            ).exceptionOrNull()
        if (failure != null) {
            ensureCurrentExecutionSession(session)
            val failedAt = Clock.System.now().toEpochMilliseconds()
            database.translationDao().insertAll(
                candidates.map { candidate ->
                    PreTranslationStoreSupport.toDbTranslation(
                        candidate = candidate,
                        status = TranslationStatus.Failed,
                        updatedAt = failedAt,
                        statusReason = failure.message,
                    )
                },
            )
        }
    }

    private suspend fun runBatchTranslationWithRetry(
        session: PreTranslationExecutionSession,
        settings: ActivePreTranslationSettings,
        candidates: List<PreparedTranslationCandidate>,
    ): Result<Unit> {
        var lastFailure: Throwable? = null
        repeat(PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_MAX_ATTEMPTS) { attempt ->
            try {
                ensureCurrentExecutionSession(session)
                val sourceDocument =
                    PreTranslationBatchDocument(
                        targetLanguage = settings.targetLanguage,
                        items = candidates.map(PreTranslationStoreSupport::toBatchItem),
                    )
                val sourceJson =
                    sourceDocument.encodeJson(
                        PreTranslationBatchDocument.serializer(),
                    )
                val promptTemplate = AiPlaceholderTranslationSupport.buildPromptTemplate(sourceDocument)
                val prompt =
                    TranslationPromptFormatter.buildTranslatePrompt(
                        settings = settings.appSettings,
                        targetLanguage = settings.targetLanguage,
                        sourceTemplate = promptTemplate,
                    )
                val translatedJson =
                    batchTranslator(
                        settings.appSettings,
                        aiCompletionService,
                        sourceJson,
                        sourceDocument,
                        settings.targetLanguage,
                        prompt,
                    ) ?: error("Pre-translation returned empty response")
                ensureCurrentExecutionSession(session)
                applyBatchResult(
                    session = session,
                    translatedJson = translatedJson,
                    candidates = candidates,
                )
                return Result.success(Unit)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                lastFailure = throwable
                if (attempt < PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_MAX_ATTEMPTS - 1) {
                    delay(PreTranslationStoreSupport.PRE_TRANSLATION_BATCH_RETRY_DELAY)
                }
            }
        }
        return Result.failure(requireNotNull(lastFailure))
    }

    private suspend fun applyBatchResult(
        session: PreTranslationExecutionSession,
        translatedJson: String,
        candidates: List<PreparedTranslationCandidate>,
    ) {
        ensureCurrentExecutionSession(session)
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

private data class PreTranslationExecutionSession(
    val generation: Long,
    val providerCacheKey: String,
    val scope: CoroutineScope,
    val semaphore: Semaphore = Semaphore(permits = 1),
)
