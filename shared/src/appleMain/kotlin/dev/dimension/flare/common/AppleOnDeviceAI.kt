package dev.dimension.flare.common

import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single(binds = [OnDeviceAI::class])
internal class AppleOnDeviceAI(
    @Provided private val delegate: SwiftOnDeviceAI,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean =
        runCatching {
            delegate.isAvailable()
        }.getOrDefault(false)

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        runCatching {
            delegate.translate(
                source = source,
                targetLanguage = targetLanguage,
                prompt = prompt,
            )
        }.getOrNull()

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        runCatching {
            delegate.tldr(
                source = source,
                targetLanguage = targetLanguage,
                prompt = prompt,
            )
        }.getOrNull()
}
