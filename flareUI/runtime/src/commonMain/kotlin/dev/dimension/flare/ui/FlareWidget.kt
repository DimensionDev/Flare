package dev.dimension.flare.ui

/**
 * One platform-native primitive managed by Flare's Compose Runtime applier.
 *
 * Implementations normally wrap an Android View, UIView, or NSView. Declarative backends may
 * instead use a state node rendered by their host, while layout-only primitives may use a
 * renderer-owned layout object.
 */
public interface FlareWidget {
    public val modifier: FlareModifier

    public fun updateModifier(modifier: FlareModifier)

    public fun children(slot: FlareSlotId): FlareChildren = error("$this does not expose child slot '$slot'.")

    /** Releases callbacks and platform resources. Called exactly once. */
    public fun dispose(): Unit = Unit
}

/**
 * Structural operations for one native child slot.
 *
 * These methods change only the platform hierarchy. Runtime lifecycle callbacks are dispatched by
 * Flare itself so every backend observes the same ordering.
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
 * Associates a native widget family with its strongly typed backend.
 *
 * Platform base widgets implement this once; renderer code generation then infers the backend from
 * the renderer's supertypes.
 */
public interface FlareBackendWidget<B : FlareBackend> : FlareWidget

/**
 * Convenience base which owns modifier state while leaving platform application to subclasses.
 */
public abstract class AbstractFlareWidget : FlareWidget {
    final override var modifier: FlareModifier = FlareModifier
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
        componentType: FlareComponentType<W>,
        factory: (B) -> W,
    )
}

/** Installable group of native primitive renderers for one strongly typed backend. */
public interface FlareRendererPlugin<B : FlareBackend> {
    public fun register(registrar: FlareWidgetRegistrar<B>)
}
