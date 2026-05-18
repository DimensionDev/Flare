package dev.dimension.flare.data.translation

import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationProviderAiPlaceholderTest {
    @Test
    fun translateDocumentJson_usesPlainTemplatePromptAndReconstructsResponse() =
        runTest {
            val sourceDocument =
                TranslationDocument(
                    blocks =
                        listOf(
                            TranslationBlock(
                                id = 0,
                                tokens =
                                    listOf(
                                        TranslationToken(0, TranslationTokenKind.Translatable, "Hello "),
                                        TranslationToken(1, TranslationTokenKind.Locked, " @alice https://x.com "),
                                        TranslationToken(2, TranslationTokenKind.Translatable, "from Tokyo"),
                                    ),
                            ),
                        ),
                )
            val promptTemplate = AiPlaceholderTranslationSupport.buildPromptTemplate(sourceDocument)
            val prompt =
                TranslationPromptFormatter.buildTranslatePrompt(
                    settings = aiSettings(),
                    targetLanguage = "zh-CN",
                    sourceTemplate = promptTemplate,
                )
            val onDeviceAI =
                RecordingOnDeviceAI(
                    response =
                        """
                        <<<B0>>>
                        {{T0}}你好 {{L1}}{{T2}}来自东京
                        <<<E0>>>
                        """.trimIndent(),
                )

            val translatedJson =
                TranslationProvider.translateDocumentJson(
                    settings = aiSettings(),
                    aiCompletionService = AiCompletionService(OpenAIService(), onDeviceAI),
                    sourceTemplate = promptTemplate,
                    sourceJson = sourceDocument.encodeJson(TranslationDocument.serializer()),
                    targetLanguage = "zh-CN",
                    prompt = prompt,
                )

            val translatedDocument =
                assertNotNull(translatedJson).decodeJson(TranslationDocument.serializer())
            assertEquals(
                listOf("你好 ", " @alice https://x.com ", "来自东京"),
                translatedDocument.blocks
                    .single()
                    .tokens
                    .map { it.text },
            )
            assertTrue(assertNotNull(onDeviceAI.prompt).contains("<<<B0>>>"))
            assertFalse(assertNotNull(onDeviceAI.prompt).contains("Translate the following JSON"))
            assertFalse(assertNotNull(onDeviceAI.prompt).contains("\"blocks\""))
        }

    private fun aiSettings(): AppSettings =
        AppSettings(
            version = "",
            aiConfig =
                AppSettings.AiConfig(
                    type = AppSettings.AiConfig.Type.OnDevice,
                    translatePrompt = AiPromptDefaults.TRANSLATE_PROMPT,
                ),
            translateConfig =
                AppSettings.TranslateConfig(
                    provider = AppSettings.TranslateConfig.Provider.AI,
                ),
        )

    private class RecordingOnDeviceAI(
        private val response: String,
    ) : OnDeviceAI {
        var prompt: String? = null
            private set

        override suspend fun isAvailable(): Boolean = true

        override suspend fun translate(
            source: String,
            targetLanguage: String,
            prompt: String,
        ): String {
            this.prompt = prompt
            return response
        }

        override suspend fun tldr(
            source: String,
            targetLanguage: String,
            prompt: String,
        ): String? = null
    }
}
