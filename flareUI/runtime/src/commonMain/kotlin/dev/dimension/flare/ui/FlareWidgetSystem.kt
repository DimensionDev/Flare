package dev.dimension.flare.ui

/**
 * Immutable, statically assembled set of native widget factories for one backend type.
 *
 * The backend instance is supplied only when a widget is created. A reusable widget system
 * therefore cannot accidentally retain a host-owned Android Context or SwiftUI tree.
 */
public class FlareWidgetSystem<B : FlareBackend>(
    vararg plugins: FlareRendererPlugin<B>,
) {
    private val factories: Map<FlareComponentType<*>, (B) -> FlareWidget> =
        run {
            val result =
                linkedMapOf<FlareComponentType<*>, (B) -> FlareWidget>()
            val registrar =
                object : FlareWidgetRegistrar<B> {
                    override fun <W : FlareWidget> register(
                        componentType: FlareComponentType<W>,
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

    @LowLevelFlareApi
    public fun <W : FlareWidget> create(
        backend: B,
        componentType: FlareComponentType<W>,
    ): W {
        val factory =
            factories[componentType]
                ?: error("Backend $backend has no renderer for $componentType.")
        @Suppress("UNCHECKED_CAST")
        return factory(backend) as W
    }
}
