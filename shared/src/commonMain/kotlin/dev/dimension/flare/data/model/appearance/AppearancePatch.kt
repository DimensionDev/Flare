package dev.dimension.flare.data.model.appearance

import androidx.compose.runtime.Immutable

@Immutable
public data class AppearancePatch internal constructor(
    private val values: Map<String, Any>,
) {
    public operator fun <T : Any> get(key: AppearanceKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return values[key.id] as? T ?: key.default
    }

    public fun <T : Any> contains(key: AppearanceKey<T>): Boolean = values.containsKey(key.id)

    public fun <T : Any> set(
        key: AppearanceKey<T>,
        value: T,
    ): AppearancePatch = copy(values = values + (key.id to value))

    public fun clear(key: AppearanceKey<*>): AppearancePatch = copy(values = values - key.id)

    internal val explicitEntries: Map<String, Any>
        get() = values

    public companion object {
        public val EMPTY: AppearancePatch = AppearancePatch(emptyMap())
    }
}
