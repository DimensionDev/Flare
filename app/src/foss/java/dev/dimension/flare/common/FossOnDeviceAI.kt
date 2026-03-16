package dev.dimension.flare.common

import android.content.Context

internal class FossOnDeviceAI(
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
