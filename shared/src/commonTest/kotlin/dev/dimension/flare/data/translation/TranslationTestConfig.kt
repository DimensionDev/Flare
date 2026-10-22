package dev.dimension.flare.data.translation

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.datastore.model.AppSettings

internal fun aiPreTranslateConfig(preTranslate: Boolean = true): AppSettings.TranslateConfig =
    AppSettings.TranslateConfig(
        preTranslate = preTranslate,
        provider = AppSettings.TranslateConfig.Provider.AI,
    )

internal fun TranslationPayload.legacyPlatformSourceHash(
    platformId: String,
    translated: TranslationPayload,
): String {
    val payloadHash =
        translated.encodeJson(TranslationPayload.serializer()).encodeToByteArray().fold(-0x340d631b8c4674c3L) { hash, byte ->
            (hash xor (byte.toLong() and 0xffL)) * 0x100000001b3L
        }
    return sourceHash("platform:$platformId:${payloadHash.toULong().toString(16)}")
}
