package dev.dimension.flare.common

import org.koin.core.annotation.Single

@Single(binds = [OnDeviceAI::class])
internal class WebOnDeviceAI : OnDeviceAI {
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
