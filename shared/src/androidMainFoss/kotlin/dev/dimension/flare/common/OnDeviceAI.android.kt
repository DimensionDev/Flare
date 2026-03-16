package dev.dimension.flare.common

import android.content.Context

internal actual class OnDeviceAI(
    private val context: Context,
) {
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
