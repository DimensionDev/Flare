package dev.dimension.compose.nativekit

import kotlin.reflect.KClass

/**
 * Immutable, statically assembled set of native widget factories for one backend type.
 *
 * The backend instance is supplied only when a widget is created. A reusable widget system
 * therefore cannot accidentally retain a host-owned Android Context or Apple view hierarchy.
 */
public class NativeKitWidgetSystem<B : NativeKitBackend>(
    vararg plugins: NativeKitRendererPlugin<B>,
) {
    private val factories: Map<KClass<out NativeKitWidget>, (B) -> NativeKitWidget> =
        run {
            val result =
                linkedMapOf<KClass<out NativeKitWidget>, (B) -> NativeKitWidget>()
            val registrar =
                object : NativeKitWidgetRegistrar<B> {
                    override fun <W : NativeKitWidget> register(
                        componentType: KClass<W>,
                        factory: (B) -> W,
                    ) {
                        check(componentType !in result) {
                            "Widget system already has a renderer for $componentType."
                        }
                        result[componentType] = factory
                    }
                }
            plugins.forEach { plugin -> plugin.register(registrar) }
            result.toMap()
        }

    @LowLevelNativeKitApi
    public fun <W : NativeKitWidget> create(
        backend: B,
        componentType: KClass<W>,
    ): W {
        val factory =
            factories[componentType]
                ?: error("Backend $backend has no renderer for $componentType.")
        @Suppress("UNCHECKED_CAST")
        return factory(backend) as W
    }
}
