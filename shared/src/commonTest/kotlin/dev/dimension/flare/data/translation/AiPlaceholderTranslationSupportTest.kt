package dev.dimension.flare.data.translation

import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiPlaceholderTranslationSupportTest {
    @Test
    fun buildPromptTemplate_outputsPlainTemplateWithoutJsonWrapper() {
        val template = AiPlaceholderTranslationSupport.buildPromptTemplate(sourceDocument())

        assertTrue(template.startsWith("<<<B0>>>"))
        assertTrue(template.contains("{{T0}}Hello "))
        assertTrue(template.contains("{{L1}}"))
        assertTrue(template.contains("<<<E0>>>"))
    }

    @Test
    fun reconstructDocument_restoresLockedTokensFromPlainTemplateResponse() {
        val translated =
            AiPlaceholderTranslationSupport.reconstructDocument(
                sourceDocument = sourceDocument(),
                translatedTemplate =
                    """
                    <<<B0>>>
                    {{T0}}你好 {{L1}}{{T2}}来自东京
                    <<<E0>>>
                    """.trimIndent(),
                targetLanguage = "zh-CN",
            )

        assertEquals("zh-CN", translated.targetLanguage)
        assertEquals(
            listOf("你好 ", " @alice https://x.com ", "来自东京"),
            translated.blocks
                .single()
                .tokens
                .map { it.text },
        )
    }

    @Test
    fun reconstructDocument_rejectsLegacyJsonResponse() {
        assertFailsWith<IllegalArgumentException> {
            AiPlaceholderTranslationSupport.reconstructDocument(
                sourceDocument = sourceDocument(),
                translatedTemplate = """{"blocks":["{{T0}}你好 {{L1}}{{T2}}来自东京"]}""",
                targetLanguage = "zh-CN",
            )
        }
    }

    @Test
    fun reconstructBatchDocument_restoresCompletedPayloadsAndSkippedItems() {
        val translated =
            AiPlaceholderTranslationSupport.reconstructBatchDocument(
                sourceDocument = sourceBatchDocument(),
                translatedTemplate =
                    """
                    <<<I status:1 C>>>
                    <<<F content>>>
                    <<<B0>>>
                    {{T0}}你好 {{L1}}{{T2}}来自东京
                    <<<E0>>>
                    <<<I profile:1 S same_language>>>
                    """.trimIndent(),
                targetLanguage = "zh-CN",
            )

        val statusItem = translated.items.first { it.entityKey == "status:1" }
        assertEquals(PreTranslationBatchItemStatus.Completed, statusItem.status)
        assertEquals(
            listOf("你好 ", " @alice https://x.com ", "来自东京"),
            requireNotNull(requireNotNull(statusItem.payload).content)
                .blocks
                .single()
                .tokens
                .map { it.text },
        )
        val profileItem = translated.items.first { it.entityKey == "profile:1" }
        assertEquals(PreTranslationBatchItemStatus.Skipped, profileItem.status)
        assertEquals("same_language", profileItem.reason)
        assertEquals(null, profileItem.payload)
    }

    private fun sourceDocument(): TranslationDocument =
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

    private fun sourceBatchDocument(): PreTranslationBatchDocument =
        PreTranslationBatchDocument(
            items =
                listOf(
                    PreTranslationBatchItem(
                        entityKey = "status:1",
                        payload = PreTranslationBatchPayload(content = sourceDocument()),
                    ),
                    PreTranslationBatchItem(
                        entityKey = "profile:1",
                        payload =
                            PreTranslationBatchPayload(
                                description =
                                    TranslationDocument(
                                        blocks =
                                            listOf(
                                                TranslationBlock(
                                                    id = 0,
                                                    tokens =
                                                        listOf(
                                                            TranslationToken(0, TranslationTokenKind.Translatable, "Profile bio"),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ),
                    ),
                ),
        )
}
