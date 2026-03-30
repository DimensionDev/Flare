package dev.dimension.flare.data.translation

import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleWebTranslationDocumentSupportTest {
    @Test
    fun collectTranslatableTexts_deduplicatesAcrossBatchPayloads() {
        val document =
            TranslationDocument(
                blocks =
                    listOf(
                        TranslationBlock(
                            id = 0,
                            tokens =
                                listOf(
                                    TranslationToken(0, TranslationTokenKind.Translatable, "Hello"),
                                    TranslationToken(1, TranslationTokenKind.Locked, " @alice "),
                                    TranslationToken(2, TranslationTokenKind.Translatable, "world"),
                                ),
                        ),
                    ),
            )
        val batch =
            PreTranslationBatchDocument(
                items =
                    listOf(
                        PreTranslationBatchItem(
                            entityKey = "status:1",
                            payload =
                                PreTranslationBatchPayload(
                                    content = document,
                                    title =
                                        TranslationDocument(
                                            blocks =
                                                listOf(
                                                    TranslationBlock(
                                                        id = 0,
                                                        tokens =
                                                            listOf(
                                                                TranslationToken(0, TranslationTokenKind.Translatable, "Hello"),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                ),
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
                                                                TranslationToken(0, TranslationTokenKind.Translatable, "Bio"),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
            )

        assertEquals(
            listOf("Hello", "world", "Bio"),
            GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(batch),
        )
    }

    @Test
    fun applyTranslations_updatesOnlyTranslatableTokens() {
        val document =
            TranslationDocument(
                blocks =
                    listOf(
                        TranslationBlock(
                            id = 0,
                            tokens =
                                listOf(
                                    TranslationToken(0, TranslationTokenKind.Translatable, "Hello"),
                                    TranslationToken(1, TranslationTokenKind.Locked, " @alice "),
                                    TranslationToken(2, TranslationTokenKind.Translatable, "world"),
                                ),
                        ),
                    ),
            )

        val translated =
            GoogleWebTranslationDocumentSupport.applyTranslations(
                document = document,
                targetLanguage = "zh-CN",
                translatedTexts =
                    mapOf(
                        "Hello" to "你好",
                        "world" to "世界",
                    ),
            )

        assertEquals("zh-CN", translated.targetLanguage)
        assertEquals(
            listOf("你好", " @alice ", "世界"),
            translated.blocks
                .single()
                .tokens
                .map { it.text },
        )
    }

    @Test
    fun applyTranslations_requiresTranslationsForEveryTranslatableToken() {
        val document =
            TranslationDocument(
                blocks =
                    listOf(
                        TranslationBlock(
                            id = 0,
                            tokens =
                                listOf(
                                    TranslationToken(0, TranslationTokenKind.Translatable, "Hello"),
                                ),
                        ),
                    ),
            )

        assertFailsWith<IllegalStateException> {
            GoogleWebTranslationDocumentSupport.applyTranslations(
                document = document,
                targetLanguage = "zh-CN",
                translatedTexts = emptyMap(),
            )
        }
    }
}
