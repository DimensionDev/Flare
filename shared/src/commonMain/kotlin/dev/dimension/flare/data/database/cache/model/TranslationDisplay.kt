package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.DeeplinkEvent
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.toPersistentList

internal data class TranslationDisplayOptions(
    val translationEnabled: Boolean,
    val autoDisplayEnabled: Boolean,
    val providerCacheKey: String,
)

internal fun UiTimelineV2.applyTranslation(
    options: TranslationDisplayOptions,
    translations: List<DbTranslation>,
): UiTimelineV2 {
    if (!options.translationEnabled) {
        return this
    }
    val payload = translationPayload() ?: return this
    val translation =
        translations.firstOrNull {
            it.targetLanguage == Locale.language &&
                it.sourceHash == payload.sourceHash(options.providerCacheKey)
        }

    return when (this) {
        is UiTimelineV2.Feed ->
            copy(
                title =
                    translation
                        .takeIf { it?.status == TranslationStatus.Completed }
                        ?.payload
                        ?.title
                        ?.raw ?: title,
                description =
                    translation
                        .takeIf { it?.status == TranslationStatus.Completed }
                        ?.payload
                        ?.description
                        ?.raw ?: description,
                translationDisplayState = translation.toDisplayState(),
            )

        is UiTimelineV2.Post ->
            run {
                val displayMode = translation?.displayMode ?: TranslationDisplayMode.Auto
                val translatedPayload = translation?.takeIf { it.status == TranslationStatus.Completed }?.payload
                val shouldShowTranslated =
                    translatedPayload != null &&
                        when (displayMode) {
                            TranslationDisplayMode.Translated -> true
                            TranslationDisplayMode.Original -> false
                            TranslationDisplayMode.Auto -> options.autoDisplayEnabled
                        }
                val displayState =
                    when {
                        translation?.status == TranslationStatus.Completed && shouldShowTranslated -> TranslationDisplayState.Translated
                        translation?.status == TranslationStatus.Completed -> TranslationDisplayState.Hidden
                        else -> translation.toDisplayState()
                    }
                val menuAction =
                    when {
                        translation?.status == TranslationStatus.Failed -> TranslationMenuAction.Retry
                        shouldShowTranslated -> TranslationMenuAction.ShowOriginal
                        translation?.status == TranslationStatus.Pending || translation?.status == TranslationStatus.Translating -> null
                        PreTranslationStoreSupport.canRetrySkippedManually(translation) -> TranslationMenuAction.Translate
                        translation?.status == TranslationStatus.Skipped -> null
                        else -> TranslationMenuAction.Translate
                    }
                copy(
                    content = if (shouldShowTranslated) translatedPayload.content ?: content else content,
                    contentWarning = if (shouldShowTranslated) translatedPayload.contentWarning ?: contentWarning else contentWarning,
                    translationDisplayState = displayState,
                    actions = actions.withTranslationMenuAction(menuAction, accountType, statusKey),
                )
            }

        is UiTimelineV2.Message -> this
        is UiTimelineV2.User -> this
        is UiTimelineV2.UserList -> this
    }
}

internal fun UiProfile.applyTranslation(
    options: TranslationDisplayOptions,
    translation: DbTranslation?,
): UiProfile {
    if (!options.autoDisplayEnabled) {
        return this
    }
    val payload = translationPayload()
    val matchedTranslation =
        translation?.takeIf {
            it.targetLanguage == Locale.language &&
                it.sourceHash == payload.sourceHash(options.providerCacheKey)
        }
    val displayState = matchedTranslation.toDisplayState()
    return copy(
        description = matchedTranslation.takeIf { it?.status == TranslationStatus.Completed }?.payload?.description ?: description,
        translationDisplayState = displayState,
    )
}

internal fun UiTimelineV2.translationPayload(): TranslationPayload? =
    when (this) {
        is UiTimelineV2.Feed ->
            TranslationPayload(
                title = title?.toUiPlainText(),
                description = description?.toUiPlainText(),
            )

        is UiTimelineV2.Post ->
            TranslationPayload(
                content = content,
                contentWarning = contentWarning,
            )

        is UiTimelineV2.Message -> null
        is UiTimelineV2.User -> null
        is UiTimelineV2.UserList -> null
    }

internal fun UiProfile.translationPayload(): TranslationPayload =
    TranslationPayload(
        description = description,
    )

internal fun TranslationPayload.sourceHash(providerCacheKey: String): String =
    buildString {
        append(providerCacheKey)
        append('\u0000')
        append(encodeJson(TranslationPayload.serializer()))
    }.stableTranslationHash()

private fun DbTranslation?.toDisplayState(): TranslationDisplayState =
    when (this?.status) {
        TranslationStatus.Pending,
        TranslationStatus.Translating,
        -> TranslationDisplayState.Translating

        TranslationStatus.Completed -> TranslationDisplayState.Translated
        TranslationStatus.Failed -> TranslationDisplayState.Failed
        TranslationStatus.Skipped,
        null,
        -> TranslationDisplayState.Hidden
    }

private fun List<ActionMenu>.withTranslationMenuAction(
    action: TranslationMenuAction?,
    accountType: dev.dimension.flare.model.AccountType,
    statusKey: dev.dimension.flare.model.MicroBlogKey,
) = if (action == null) {
    this.toPersistentList()
} else if (accountType is AccountType.Specific) {
    map { menu ->
        menu.prependTranslationAction(
            accountKey = accountType.accountKey,
            statusKey = statusKey,
            translationAction = action,
        )
    }.toPersistentList()
} else {
    this.toPersistentList()
}

private fun ActionMenu.prependTranslationAction(
    accountKey: dev.dimension.flare.model.MicroBlogKey,
    statusKey: dev.dimension.flare.model.MicroBlogKey,
    translationAction: TranslationMenuAction,
): ActionMenu =
    when (this) {
        is ActionMenu.Group ->
            if (displayItem.text.isMoreMenuText()) {
                val localAction =
                    ActionMenu.Item(
                        text =
                            ActionMenu.Item.Text.Localized(
                                when (translationAction) {
                                    TranslationMenuAction.Retry -> ActionMenu.Item.Text.Localized.Type.RetryTranslation
                                    TranslationMenuAction.Translate -> ActionMenu.Item.Text.Localized.Type.Translate
                                    TranslationMenuAction.ShowOriginal -> ActionMenu.Item.Text.Localized.Type.ShowOriginal
                                },
                            ),
                        clickEvent =
                            ClickEvent.Deeplink(
                                DeeplinkEvent(
                                    accountKey = accountKey,
                                    translationEvent =
                                        when (translationAction) {
                                            TranslationMenuAction.Retry -> DeeplinkEvent.TranslationEvent.RetryTranslation(statusKey)
                                            TranslationMenuAction.Translate -> DeeplinkEvent.TranslationEvent.Translate(statusKey)
                                            TranslationMenuAction.ShowOriginal -> DeeplinkEvent.TranslationEvent.ShowOriginal(statusKey)
                                        },
                                ),
                            ),
                        icon = UiIcon.Translate,
                    )
                copy(
                    actions =
                        (
                            listOf(localAction) +
                                actions.filterNot {
                                    (it as? ActionMenu.Item)?.text.let { text ->
                                        val localized = text as? ActionMenu.Item.Text.Localized
                                        localized?.type == ActionMenu.Item.Text.Localized.Type.RetryTranslation ||
                                            localized?.type == ActionMenu.Item.Text.Localized.Type.Translate ||
                                            localized?.type == ActionMenu.Item.Text.Localized.Type.ShowOriginal
                                    }
                                }
                        ).toPersistentList(),
                )
            } else {
                this
            }

        is ActionMenu.Item,
        ActionMenu.Divider,
        -> this
    }

private fun ActionMenu.Item.Text?.isMoreMenuText(): Boolean =
    (this as? ActionMenu.Item.Text.Localized)?.type == ActionMenu.Item.Text.Localized.Type.More

private enum class TranslationMenuAction {
    Retry,
    Translate,
    ShowOriginal,
}

private fun String.stableTranslationHash(): String {
    var hash = -0x340d631b8c4674c3L
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
