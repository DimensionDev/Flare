package dev.dimension.flare.ui

/**
 * One backend primitive managed by Flare's Compose Runtime applier.
 *
 * Implementations wrap an Android View, UIView, or NSView, or hold observable renderer state for
 * Compose UI. Layout primitives normally expose their backend container through [children].
 */
public interface FlareWidget {
    public val modifier: FlareModifier

    public fun updateModifier(modifier: FlareModifier)

    /** The primitive's single child container, or null for a leaf primitive. */
    public val children: FlareChildren?
        get() = null

    /** Releases callbacks and platform resources. Called exactly once. */
    public fun dispose(): Unit = Unit
}

/**
 * Structural operations for one backend child container.
 *
 * Runtime lifecycle callbacks are dispatched by Flare itself so every backend observes the same
 * ordering.
 */
public interface FlareChildren {
    /** Called before one Compose Runtime apply transaction mutates this tree. */
    public fun onBeginChanges(): Unit = Unit

    /** Called after one Compose Runtime apply transaction has finished mutating this tree. */
    public fun onEndChanges(): Unit = Unit

    public fun insert(
        index: Int,
        widget: FlareWidget,
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
public abstract class AbstractFlareWidget : FlareWidget {
    final override var modifier: FlareModifier = FlareModifier.None
        private set

    final override fun updateModifier(modifier: FlareModifier) {
        if (this.modifier == modifier) return
        val previous = this.modifier
        this.modifier = modifier
        onModifierChanged(previous, modifier)
    }

    protected open fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ): Unit = Unit
}

/** Scoped registration surface supplied to one renderer plugin. */
public interface FlareWidgetRegistrar<B : FlareBackend> {
    public fun <W : FlareWidget> register(
        componentType: kotlin.reflect.KClass<W>,
        factory: (B) -> W,
    )
}

/** Installable group of native primitive renderers for one strongly typed backend. */
public interface FlareRendererPlugin<B : FlareBackend> {
    public fun register(registrar: FlareWidgetRegistrar<B>)
}
