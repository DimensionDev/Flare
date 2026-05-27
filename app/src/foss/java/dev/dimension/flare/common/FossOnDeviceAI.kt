package dev.dimension.flare.common

import android.content.Context
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single(binds = [OnDeviceAI::class])
internal class FossOnDeviceAI(
    @Provided private val context: Context,
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
