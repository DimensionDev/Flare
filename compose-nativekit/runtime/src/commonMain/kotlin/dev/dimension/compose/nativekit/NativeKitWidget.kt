package dev.dimension.compose.nativekit

/**
 * One backend primitive managed by NativeKit's Compose Runtime applier.
 *
 * Implementations wrap an Android View, UIView, or NSView, or hold observable renderer state for
 * Compose UI. Layout primitives normally expose their backend container through [children].
 */
public interface NativeKitWidget {
    public val modifier: NativeKitModifier

    public fun updateModifier(modifier: NativeKitModifier)

    /** The primitive's single child container, or null for a leaf primitive. */
    public val children: NativeKitChildren?
        get() = null

    /** Releases callbacks and platform resources. Called exactly once. */
    public fun dispose(): Unit = Unit
}

/**
 * Structural operations for one backend child container.
 *
 * Runtime lifecycle callbacks are dispatched by NativeKit itself so every backend observes the same
 * ordering.
 */
public interface NativeKitChildren {
    /** Called before one Compose Runtime apply transaction mutates this tree. */
    public fun onBeginChanges(): Unit = Unit

    /** Called after one Compose Runtime apply transaction has finished mutating this tree. */
    public fun onEndChanges(): Unit = Unit

    public fun insert(
        index: Int,
        widget: NativeKitWidget,
    )

    public fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    )

    public fun remove(
        index: Int,
        count: Int,
    )
}

/**
 * Convenience base which owns modifier state while leaving platform application to subclasses.
 */
public abstract class AbstractNativeKitWidget : NativeKitWidget {
    final override var modifier: NativeKitModifier = NativeKitModifier.None
        private set

    final override fun updateModifier(modifier: NativeKitModifier) {
        if (this.modifier == modifier) return
        val previous = this.modifier
        this.modifier = modifier
        onModifierChanged(previous, modifier)
    }

    protected open fun onModifierChanged(
        previous: NativeKitModifier,
        current: NativeKitModifier,
    ): Unit = Unit
}

/** Scoped registration surface supplied to one renderer plugin. */
public interface NativeKitWidgetRegistrar<B : NativeKitBackend> {
    public fun <W : NativeKitWidget> register(
        componentType: kotlin.reflect.KClass<W>,
        factory: (B) -> W,
    )
}

/** Installable group of native primitive renderers for one strongly typed backend. */
public interface NativeKitRendererPlugin<B : NativeKitBackend> {
    public fun register(registrar: NativeKitWidgetRegistrar<B>)
}
