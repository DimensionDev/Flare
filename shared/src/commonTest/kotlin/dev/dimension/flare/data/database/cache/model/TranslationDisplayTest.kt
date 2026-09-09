package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.datasource.microblog.applyPostActionLayout
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.ui.model.ClickEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class TranslationDisplayTest {
    @Test
    fun skippedEmptyPostKeepsTranslateSlotInButtonRow() {
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

        val translate = assertIs<ActionMenu.Item>(actions.first())
        assertEquals(PostActionFamily.Translate, translate.actionFamily)
        assertEquals(UiIcon.Translate, translate.icon)
        assertEquals(ClickEvent.Noop, translate.clickEvent)
        assertFalse(translate.enabled)
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
