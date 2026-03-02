package dev.dimension.flare.common

internal actual class OnDeviceAI(
    private val delegate: SwiftOnDeviceAI,
) {
    actual suspend fun isAvailable(): Boolean =
        runCatching {
            delegate.isAvailable()
        }.getOrDefault(false)

    actual suspend fun translate(
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

    actual suspend fun tldr(
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
