package dev.dimension.flare.data.ai

public interface OnDeviceAI {
    public suspend fun isAvailable(): Boolean

    public suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?

    public suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?
}
