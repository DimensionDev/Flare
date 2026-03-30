package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.data.translation.TranslationPromptFormatter
import dev.dimension.flare.data.translation.TranslationProvider
import dev.dimension.flare.data.translation.TranslationResponseSanitizer
import dev.dimension.flare.data.translation.translationProviderCacheKey
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.applyTranslationJson
import dev.dimension.flare.ui.render.toTranslatableText
import dev.dimension.flare.ui.render.toTranslationJson
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class TranslatePresenter(
    private val source: UiRichText,
    private val targetLanguage: String = Locale.language,
    private val cacheTarget: TranslateCacheTarget? = null,
) : PresenterBase<UiState<UiRichText>>(),
    KoinComponent {
    private val aiCompletionService by inject<AiCompletionService>()
    private val appDataStore: AppDataStore by inject()
    private val database: CacheDatabase by inject()
    private val sourceText: String by lazy { source.toTranslatableText() }
    private val sourceJson: String by lazy { source.toTranslationJson(targetLanguage) }

    @Composable
    override fun body(): UiState<UiRichText> {
        return produceState<UiState<UiRichText>>(initialValue = UiState.Loading()) {
            value =
                tryRun {
                    val settings =
                        appDataStore.appSettingsStore.data
                            .first()
                    val providerCacheKey = settings.translationProviderCacheKey()
                    cachedTranslation(providerCacheKey)?.let {
                        return@tryRun it
                    }
                    val prompt =
                        TranslationPromptFormatter.buildTranslatePrompt(
                            settings = settings,
                            targetLanguage = targetLanguage,
                            sourceText = sourceText,
                            sourceJson = sourceJson,
                        )
                    val translatedContent =
                        TranslationProvider.translateDocumentJson(
                            settings = settings,
                            aiCompletionService = aiCompletionService,
                            sourceText = sourceText,
                            sourceJson = sourceJson,
                            targetLanguage = targetLanguage,
                            prompt = prompt,
                        )
                    if (translatedContent != null) {
                        val translated = toUiRichText(translatedContent)
                        cacheTranslation(translated, providerCacheKey)
                        return@tryRun translated
                    }
                    error("Translation returned empty response")
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private fun toUiRichText(translatedContent: String): UiRichText =
        TranslationResponseSanitizer
            .clean(translatedContent)
            .let { cleaned ->
                tryRun {
                    source.applyTranslationJson(cleaned)
                }.getOrElse {
                    cleaned.toUiPlainText()
                }
            }

    private suspend fun cachedTranslation(providerCacheKey: String): UiRichText? {
        val target = cacheTarget ?: return null
        val translation =
            database
                .translationDao()
                .get(
                    entityType = TranslationEntityType.Status,
                    entityKey = target.entityKey(),
                    targetLanguage = targetLanguage,
                ) ?: return null
        if (translation.sourceHash != target.sourcePayload().sourceHash(providerCacheKey)) {
            return null
        }
        return when (translation.status) {
            TranslationStatus.Completed -> target.readField(translation.payload)
            TranslationStatus.Skipped -> source
            TranslationStatus.Pending,
            TranslationStatus.Translating,
            TranslationStatus.Failed,
            -> null
        }
    }

    private suspend fun cacheTranslation(
        translated: UiRichText,
        providerCacheKey: String,
    ) {
        val target = cacheTarget ?: return
        val sourcePayload = target.sourcePayload()
        val existing =
            database
                .translationDao()
                .get(
                    entityType = TranslationEntityType.Status,
                    entityKey = target.entityKey(),
                    targetLanguage = targetLanguage,
                )
        val mergedPayload =
            target.mergePayload(
                existing = existing?.takeIf { it.sourceHash == sourcePayload.sourceHash(providerCacheKey) }?.payload,
                translated = translated,
            )
        database.translationDao().insert(
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = target.entityKey(),
                targetLanguage = targetLanguage,
                sourceHash = sourcePayload.sourceHash(providerCacheKey),
                status = TranslationStatus.Completed,
                displayMode = TranslationDisplayMode.Translated,
                payload = mergedPayload,
                statusReason = null,
                attemptCount = existing?.attemptCount ?: 0,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}

public data class TranslateCacheTarget(
    val accountType: AccountType,
    val statusKey: MicroBlogKey,
    val payload: StatusTranslationPayload,
    val field: Field,
) {
    public enum class Field {
        Content,
        ContentWarning,
    }
}

public data class StatusTranslationPayload(
    val content: UiRichText,
    val contentWarning: UiRichText?,
)

private fun TranslateCacheTarget.entityKey(): String = "${accountType}_$statusKey"

private fun TranslateCacheTarget.sourcePayload(): TranslationPayload =
    TranslationPayload(
        content = payload.content,
        contentWarning = payload.contentWarning,
    )

private fun TranslateCacheTarget.readField(payload: TranslationPayload?): UiRichText? =
    when (field) {
        TranslateCacheTarget.Field.Content -> payload?.content
        TranslateCacheTarget.Field.ContentWarning -> payload?.contentWarning
    }

private fun TranslateCacheTarget.mergePayload(
    existing: TranslationPayload?,
    translated: UiRichText,
): TranslationPayload =
    when (field) {
        TranslateCacheTarget.Field.Content ->
            TranslationPayload(
                content = translated,
                contentWarning = existing?.contentWarning,
            )

        TranslateCacheTarget.Field.ContentWarning ->
            TranslationPayload(
                content = existing?.content,
                contentWarning = translated,
            )
    }
