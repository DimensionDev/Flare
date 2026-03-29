package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.applyTranslationJson
import dev.dimension.flare.ui.render.toTranslatableText
import dev.dimension.flare.ui.render.toTranslationJson
import dev.dimension.flare.ui.render.toUiPlainText
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
                runCatching {
                    val aiConfig =
                        appDataStore.appSettingsStore.data
                            .first()
                            .aiConfig
                    if (!aiConfig.translation) {
                        return@runCatching toUiRichText(legacyGoogleTranslate())
                    }
                    cachedTranslation()?.let {
                        return@runCatching it
                    }
                    val promptTemplate =
                        aiConfig.translatePrompt.ifBlank {
                            AiPromptDefaults.TRANSLATE_PROMPT
                        }
                    val prompt = buildTranslatePrompt(promptTemplate, targetLanguage)
                    val aiTranslation =
                        aiCompletionService.translate(
                            config = aiConfig,
                            source = sourceText,
                            targetLanguage = targetLanguage,
                            prompt = prompt,
                        )
                    if (aiTranslation != null) {
                        val translated = toUiRichText(aiTranslation)
                        cacheTranslation(translated)
                        return@runCatching translated
                    }
                    toUiRichText(legacyGoogleTranslate())
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private suspend fun legacyGoogleTranslate(): String {
        val baseUrl = "https://translate.google.com/translate_a/single"
        val response =
            ktorClient()
                .get {
                    url(baseUrl)
                    parameter("client", "gtx")
                    parameter("sl", "auto")
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", sourceText)
                    parameter("ie", "UTF-8")
                    parameter("oe", "UTF-8")
                }.body<JsonArray>()
        return buildString {
            response.firstOrNull()?.jsonArray?.forEach {
                it.jsonArray.firstOrNull()?.let {
                    val content = it.jsonPrimitive.content
                    if (content.isNotEmpty()) {
                        append(content)
                        append("\n")
                    }
                }
            }
        }
    }

    private fun buildTranslatePrompt(
        template: String,
        targetLanguage: String,
    ): String =
        template
            .replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceText)
            .replace("{source_json}", sourceJson)
            .replace("{source_html}", sourceJson)
            .replace("{source_xml}", sourceJson)
            .replace("{source_markup}", sourceJson)

    private fun toUiRichText(translatedContent: String): UiRichText =
        translatedContent
            .removePrefix("```json")
            .removePrefix("```html")
            .removePrefix("```xml")
            .removePrefix("```markup")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .let { cleaned ->
                runCatching {
                    source.applyTranslationJson(cleaned)
                }.getOrElse {
                    cleaned.toUiPlainText()
                }
            }

    private suspend fun cachedTranslation(): UiRichText? {
        val target = cacheTarget ?: return null
        val translation =
            database
                .translationDao()
                .get(
                    entityType = TranslationEntityType.Status,
                    entityKey = target.entityKey(),
                    targetLanguage = targetLanguage,
                ) ?: return null
        if (translation.sourceHash != target.sourcePayload().sourceHash()) {
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

    private suspend fun cacheTranslation(translated: UiRichText) {
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
                existing = existing?.takeIf { it.sourceHash == sourcePayload.sourceHash() }?.payload,
                translated = translated,
            )
        database.translationDao().insert(
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = target.entityKey(),
                targetLanguage = targetLanguage,
                sourceHash = sourcePayload.sourceHash(),
                status = TranslationStatus.Completed,
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
