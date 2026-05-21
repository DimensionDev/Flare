package dev.dimension.flare.common

import dev.dimension.flare.data.ai.OnDeviceAI

internal data object WebOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = false

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}
