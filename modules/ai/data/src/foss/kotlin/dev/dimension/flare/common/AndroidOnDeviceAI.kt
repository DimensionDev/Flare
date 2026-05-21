package dev.dimension.flare.common

import android.content.Context
import dev.dimension.flare.data.ai.OnDeviceAI

internal class AndroidOnDeviceAI(
    @Suppress("UNUSED_PARAMETER")
    private val context: Context,
) : OnDeviceAI {
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
