package dev.dimension.flare.common

internal actual class OnDeviceAI {
    actual suspend fun isAvailable(): Boolean = false

    actual suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null

    actual suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}
