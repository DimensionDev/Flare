package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.datasource.microblog.applyPostActionLayout
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.data.translation.legacyPlatformSourceHash
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TranslationDisplayTest {
    @Test
    fun legacyPlatformCacheRemainsUsableOnlyForItsOriginalLanguageAndPreference() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                platformId = "xQt",
                content = UiTranslatableText("original".toUiPlainText()),
            )
        val source = requireNotNull(post.translationPayload())
        val translated = TranslationPayload(content = "legacy translation".toUiPlainText())
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = source.legacyPlatformSourceHash("xQt", translated),
                status = TranslationStatus.Completed,
                payload = translated,
                updatedAt = 1,
            )
        val options = TranslationDisplayOptions(true, true, "ai", preferPlatformTranslation = true)
        assertNotEquals(source.sourceHash(post.effectiveTranslationCacheKey("ai", true)), translation.sourceHash)
        for (mode in listOf(TranslationDisplayMode.Auto, TranslationDisplayMode.Original)) {
            val expected =
                if (mode ==
                    TranslationDisplayMode.Original
                ) {
                    TranslationDisplayState.Hidden
                } else {
                    TranslationDisplayState.Translated
                }
            for (snapshot in listOf(post, post.copy(content = post.content.copy(translation = "new platform result".toUiPlainText())))) {
                val displayed = snapshot.applyTranslation(options, listOf(translation.copy(displayMode = mode))) as UiTimelineV2.Post
                assertEquals(expected, displayed.translationDisplayState)
                assertEquals("legacy translation", displayed.content.translation?.raw)
            }
        }
        val invalidated =
            listOf(
                post.copy(content = UiTranslatableText("edited original".toUiPlainText())).applyTranslation(options, listOf(translation)),
                post
                    .copy(
                        contentWarning = UiTranslatableText("new warning".toUiPlainText()),
                    ).applyTranslation(options, listOf(translation)),
                post.applyTranslation(options, listOf(translation.copy(targetLanguage = "different-language"))),
                post.applyTranslation(options.copy(preferPlatformTranslation = false), listOf(translation)),
            )
        invalidated.forEach {
            assertEquals(TranslationDisplayState.Hidden, (it as UiTimelineV2.Post).translationDisplayState)
        }
    }

    @Test
    fun stablePlatformCacheRejectsChangedOriginalAndTargetLanguage() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                platformId = "xQt",
                content = UiTranslatableText("original".toUiPlainText()),
            )
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = requireNotNull(post.translationPayload()).sourceHash("platform:xQt"),
                status = TranslationStatus.Completed,
                payload = TranslationPayload(content = "translation".toUiPlainText()),
                updatedAt = 1,
            )
        val options = TranslationDisplayOptions(true, true, "ai", preferPlatformTranslation = true)
        assertEquals(
            TranslationDisplayState.Translated,
            (post.applyTranslation(options, listOf(translation)) as UiTimelineV2.Post).translationDisplayState,
        )
        for (changed in listOf(
            post.copy(content = UiTranslatableText("edited".toUiPlainText())),
            post.copy(platformId = "another-platform"),
        )) {
            assertEquals(
                TranslationDisplayState.Hidden,
                (changed.applyTranslation(options, listOf(translation)) as UiTimelineV2.Post).translationDisplayState,
            )
        }
        assertEquals(
            TranslationDisplayState.Hidden,
            (
                post.applyTranslation(
                    options,
                    listOf(translation.copy(targetLanguage = "different-language")),
                ) as UiTimelineV2.Post
            ).translationDisplayState,
        )
    }

    @Test
    fun changedProviderDoesNotReuseThePreviousProvidersTranslation() {
        val post = createSampleStatus(createSampleUser()).copy(content = UiTranslatableText("original".toUiPlainText()))
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = requireNotNull(post.translationPayload()).sourceHash("ai"),
                status = TranslationStatus.Completed,
                payload = TranslationPayload(content = "old provider".toUiPlainText()),
                updatedAt = 1,
            )
        for (preferPlatform in listOf(false, true)) {
            val displayed =
                post.applyTranslation(
                    TranslationDisplayOptions(true, true, "google-web", preferPlatformTranslation = preferPlatform),
                    listOf(translation),
                ) as UiTimelineV2.Post
            assertEquals(TranslationDisplayState.Hidden, displayed.translationDisplayState)
        }
    }

    @Test
    fun cachedPlatformTranslationSurvivesMissingAndChangedPlatformPayload() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                platformId = "xQt",
                content = UiTranslatableText("original".toUiPlainText(), "platform A".toUiPlainText()),
            )
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = requireNotNull(post.translationPayload()).sourceHash(post.effectiveTranslationCacheKey("ai", true)),
                status = TranslationStatus.Completed,
                payload = TranslationPayload(content = post.content.translation),
                updatedAt = 1,
            )
        val options = TranslationDisplayOptions(true, true, "ai", preferPlatformTranslation = true)
        val snapshots =
            listOf(
                post,
                post.copy(content = post.content.copy(translation = null)),
                post.copy(content = post.content.copy(translation = "platform B".toUiPlainText())),
            )

        snapshots.forEach { snapshot ->
            val displayed = snapshot.applyTranslation(options, listOf(translation)) as UiTimelineV2.Post
            assertEquals(TranslationDisplayState.Translated, displayed.translationDisplayState)
            assertEquals("platform A", displayed.content.translation?.raw)
        }
    }

    @Test
    fun completedProviderTranslationStaysVisibleWhilePlatformTranslationIsImported() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                platformId = "xQt",
                content = UiTranslatableText("original".toUiPlainText(), "platform".toUiPlainText()),
            )
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = requireNotNull(post.translationPayload()).sourceHash("ai"),
                status = TranslationStatus.Completed,
                payload = TranslationPayload(content = "provider".toUiPlainText()),
                updatedAt = 1,
            )

        val displayed =
            post.applyTranslation(
                TranslationDisplayOptions(true, true, "ai", preferPlatformTranslation = true),
                listOf(translation),
            ) as UiTimelineV2.Post

        assertEquals(TranslationDisplayState.Translated, displayed.translationDisplayState)
        assertEquals("provider", displayed.content.translation?.raw)
    }

    @Test
    fun skippedEmptyPostOmitsUnavailableTranslateAction() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                content = UiTranslatableText("".toUiPlainText()),
                actions =
                    persistentListOf(
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = UiIcon.More,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                ),
                            actions = persistentListOf(ActionMenu.Item(icon = UiIcon.Share, actionFamily = PostActionFamily.Share)),
                        ),
                    ),
            )
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = requireNotNull(post.translationPayload()).sourceHash("ai"),
                status = TranslationStatus.Skipped,
                statusReason = PreTranslationStoreSupport.SKIPPED_EMPTY_REASON,
                updatedAt = 1,
            )
        val displayed =
            post.applyTranslation(
                TranslationDisplayOptions(
                    translationEnabled = true,
                    autoDisplayEnabled = true,
                    providerCacheKey = "ai",
                ),
                listOf(translation),
            ) as UiTimelineV2.Post

        val actions =
            displayed.actions.applyPostActionLayout(
                PostActionLayoutConfig(
                    enabled = true,
                    primary = persistentListOf(PostActionFamily.Translate),
                ),
            )

        assertEquals(post.actions, actions)
        assertEquals(post.actions, displayed.actions)
    }

    @Test
    fun selectedTranslationPopulatesWrapperWithoutChangingOriginal() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                platformId = "xQt",
                content =
                    UiTranslatableText(
                        original = "original".toUiPlainText(),
                        translation = "platform translation".toUiPlainText(listOf(Locale.language)),
                    ),
                contentWarning = UiTranslatableText("original cw".toUiPlainText()),
            )
        val sourcePayload = requireNotNull(post.translationPayload())
        val cacheKey = post.effectiveTranslationCacheKey("ai", preferPlatformTranslation = true)
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = sourcePayload.sourceHash(cacheKey),
                status = TranslationStatus.Completed,
                payload = TranslationPayload(content = post.content.translation),
                updatedAt = 1,
            )

        val displayed =
            post.applyTranslation(
                options =
                    TranslationDisplayOptions(
                        translationEnabled = true,
                        autoDisplayEnabled = true,
                        providerCacheKey = "ai",
                        preferPlatformTranslation = true,
                    ),
                translations = listOf(translation),
            ) as dev.dimension.flare.ui.model.UiTimelineV2.Post

        assertEquals("original", displayed.content.original.raw)
        assertEquals("platform translation", displayed.content.translation?.raw)
        assertEquals("original cw", displayed.contentWarning?.original?.raw)
        assertNull(displayed.contentWarning?.translation)
        assertEquals(TranslationDisplayState.Translated, displayed.translationDisplayState)
    }

    @Test
    fun originalModeKeepsTranslationAvailableButDoesNotDisplayIt() {
        val post =
            createSampleStatus(createSampleUser()).copy(
                content = UiTranslatableText("original".toUiPlainText()),
            )
        val payload = requireNotNull(post.translationPayload())
        val translation =
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = "post",
                targetLanguage = Locale.language,
                sourceHash = payload.sourceHash("ai"),
                status = TranslationStatus.Completed,
                displayMode = TranslationDisplayMode.Original,
                payload = TranslationPayload(content = "translated".toUiPlainText()),
                updatedAt = 1,
            )

        val displayed =
            post.applyTranslation(
                TranslationDisplayOptions(
                    translationEnabled = true,
                    autoDisplayEnabled = true,
                    providerCacheKey = "ai",
                ),
                listOf(translation),
            ) as dev.dimension.flare.ui.model.UiTimelineV2.Post

        assertEquals("original", displayed.content.original.raw)
        assertEquals("translated", displayed.content.translation?.raw)
        assertEquals(TranslationDisplayState.Hidden, displayed.translationDisplayState)
    }
}
