package dev.dimension.flare.data.model.appearance

import kotlinx.serialization.KSerializer

public sealed interface AppearanceKey<T : Any> {
    public val id: String
    public val default: T
    public val serializer: KSerializer<T>
}

public sealed interface GlobalAppearanceKey<T : Any> : AppearanceKey<T>

public sealed interface PerTimelineAppearanceKey<T : Any> : AppearanceKey<T>
