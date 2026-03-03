package dev.dimension.flare.common

internal expect class OnDeviceAI {
    suspend fun isAvailable(): Boolean

    suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?

    suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?
}
