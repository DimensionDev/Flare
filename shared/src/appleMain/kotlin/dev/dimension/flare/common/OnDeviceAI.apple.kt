package dev.dimension.flare.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual class OnDeviceAI(
    private val delegate: SwiftOnDeviceAI,
) {
    actual suspend fun isAvailable(): Boolean =
        runCatching {
            withContext(Dispatchers.Main) {
                delegate.isAvailable()
            }
        }.getOrDefault(false)

    actual suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        runCatching {
            withContext(Dispatchers.Main) {
                delegate.translate(
                    source = source,
                    targetLanguage = targetLanguage,
                    prompt = prompt,
                )
            }
        }.getOrNull()

    actual suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        runCatching {
            withContext(Dispatchers.Main) {
                delegate.tldr(
                    source = source,
                    targetLanguage = targetLanguage,
                    prompt = prompt,
                )
            }
        }.getOrNull()
}
