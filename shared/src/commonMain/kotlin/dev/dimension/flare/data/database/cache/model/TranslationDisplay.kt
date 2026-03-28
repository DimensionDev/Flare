package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUiPlainText

internal data class TranslationDisplayOptions(
    val enabled: Boolean,
    val targetLanguage: String,
)

internal fun UiTimelineV2.applyTranslation(
    options: TranslationDisplayOptions,
    translations: List<DbTranslation>,
): UiTimelineV2 {
    if (!options.enabled) {
        return this
    }
    val payload = translationPayload() ?: return this
    val translation =
        translations.firstOrNull {
            it.targetLanguage == options.targetLanguage &&
                it.status == TranslationStatus.Completed &&
                it.sourceHash == payload.sourceHash()
        } ?: return this

    return when (this) {
        is UiTimelineV2.Feed ->
            copy(
                title = translation.payload?.title?.raw ?: title,
                description = translation.payload?.description?.raw ?: description,
            )

        is UiTimelineV2.Post ->
            copy(
                content = translation.payload?.content ?: content,
                contentWarning = translation.payload?.contentWarning ?: contentWarning,
            )

        is UiTimelineV2.Message -> this
        is UiTimelineV2.User -> this
        is UiTimelineV2.UserList -> this
    }
}

internal fun UiProfile.applyTranslation(
    options: TranslationDisplayOptions,
    translation: DbTranslation?,
): UiProfile {
    if (!options.enabled) {
        return this
    }
    val payload = translationPayload()
    if (
        translation == null ||
        translation.targetLanguage != options.targetLanguage ||
        translation.status != TranslationStatus.Completed ||
        translation.sourceHash != payload.sourceHash()
    ) {
        return this
    }
    return copy(
        description = translation.payload?.description ?: description,
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

internal fun TranslationPayload.sourceHash(): String = encodeJson(TranslationPayload.serializer()).stableTranslationHash()

private fun String.stableTranslationHash(): String {
    var hash = -0x340d631b8c4674c3L
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
