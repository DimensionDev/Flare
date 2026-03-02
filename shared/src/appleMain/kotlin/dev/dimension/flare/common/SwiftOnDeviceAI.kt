package dev.dimension.flare.common

public interface SwiftOnDeviceAI {
    public fun isAvailable(): Boolean

    public fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?

    public fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?
}
