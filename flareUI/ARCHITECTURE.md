# Flare UI runtime architecture

## Product definition

Flare UI is a Kotlin Multiplatform runtime with a Compose Runtime authoring frontend and four
backends: Android View, Jetpack Compose UI, UIKit, and AppKit.

```text
shared @Composable content
        |
        v
Compose Runtime + FlareApplier
        |
        v
typed FlareWidgetSystem
        |
        +-- Android View
        +-- Jetpack Compose UI
        +-- UIKit
        +-- AppKit
```

Compose Runtime owns reconciliation. Native backends apply structural operations directly to their
platform hierarchy. The Compose backend applies them to observable widget state whose `Render`
functions emit Compose UI nodes.

## Runtime invariants

### Bottom-up insertion and disposal

A primitive receives its initial properties and descendants before entering its backend parent.
Removal detaches the child, disposes descendants, and then disposes the widget.

```text
create -> update -> create descendants -> insert
remove -> detach -> dispose descendants -> dispose widget
```

### One child container

A widget is either a leaf or exposes one `FlareChildren` container through `FlareWidget.children`.
This matches every current primitive and keeps the applier tree identical to the backend widget
tree. Multiple named slots should be introduced only when a real primitive requires them.

### Typed updates and identity

Each primitive has a widget interface. Its Kotlin `KClass<W>` connects the composable emitter,
renderer registration, and native factory. Generated schemas, string IDs, and custom component
tokens are absent.

```kotlin
interface TextWidget : FlareWidget {
    fun setText(value: String)
}

@Composable
@FlareUiComposable
fun Text(text: String) {
    EmitFlareWidget(
        componentType = TextWidget::class,
        update = { set(text, TextWidget::setText) },
    )
}
```

### Renderer registration

`FlareWidgetSystem<B>` is an immutable map of `KClass` keys to `(B) -> FlareWidget` factories.
`FlareRendererPlugin<B>` groups registrations while keeping backend mismatches as compile errors.
The host supplies its backend when creating a widget, so a reusable widget system cannot retain an
Activity or native view hierarchy.

Registration is handwritten. This keeps the build free of KSP and makes primitive API changes
ordinary source edits while the component set is small.

### Backend hierarchy operations

Android View, UIKit, and AppKit apply insert, move, and remove operations directly with platform
APIs. Android suppresses root layout during a Compose apply transaction where supported. UIKit and
AppKit rely on their native stack-view operations and do not keep shadow child lists.

Jetpack Compose stores renderer widgets in a `mutableStateListOf`. Each widget exposes a
`@UiComposable Render` function and keeps changed properties in snapshot state. `AndroidCompose`
is the escape hatch for Android-only components which already provide a Compose API.

### Scheduling

Android View uses the Choreographer-backed `MonotonicFrameClock` supplied by
`AndroidUiDispatcher.Main`.

The Compose host inherits the surrounding composition through `rememberCompositionContext()`, so
it does not create another Recomposer, snapshot observer, or frame clock.

UIKit and AppKit share the Apple recomposer lifecycle but keep platform display clocks:

- iOS requests an on-demand `CADisplayLink` frame.
- macOS requests an on-demand `CVDisplayLink` frame.
- Darwin frame timestamps use `CLOCK_MONOTONIC_RAW`.

The display clocks are process-scoped and sleep without frame awaiters. Apple hosts share a pooled
recomposer and its one snapshot observer.

### Host lifecycle

Android View, UIKit, and AppKit hosts create a composition only while attached to a window. They
retain declarative content for reattachment. The Compose host owns its child Flare composition with
`DisposableEffect`. Every host disposes composition nodes and callbacks when it leaves its owner.

`FlareWidget.dispose` is the single widget cleanup hook.

## Modules

| Module | Responsibility |
| --- | --- |
| `flare-runtime` | Runtime contracts, applier, lifecycle, Android View/Compose/UIKit/AppKit hosts, frame clocks |
| `foundation` | Four common primitives and their four renderer sets |
| `demo/shared` | Shared composition and framework exports |
| `demo/androidApp`, `demo/appleApp` | Native application shells |

`flare-runtime` does not depend on Foundation. Foundation depends one-way on runtime. A new host
belongs in runtime; a renderer for a Foundation primitive belongs in Foundation. A separate plugin
module should be added only for a real publication or dependency-footprint boundary.

## Resources

The runtime does not own localization or assets. Applications resolve platform resources and pass
values into primitives. Density, locale, layout direction, theme, safe area, and a shared resource
environment are not implemented yet.

## Verification gates

- Common tests cover direct native-tree construction, updates, duplicate registration, identity,
  recomposition, and bottom-up disposal.
- Robolectric smoke coverage verifies Android View rendering and in-place recomposition.
- A Compose UI smoke test covers Foundation rendering, events, and `AndroidCompose` content.
- Native UIKit and AppKit tests cover modifiers and direct hierarchy operations.
- macOS tests request real display-link frames and verify monotonic timestamps.
- The shared framework and UIKit/AppKit demo applications compile through Xcode.

Performance benchmark matrices were removed until a real product screen and regression budget
exist.

## Deliberately deferred

Flare UI is not production complete. The next gates are:

1. Constraints/measure/place layout beyond native stack containers.
2. Density, locale, layout direction, safe area, theme, and resource environments.
3. Accessibility semantics and focus.
4. Text input with selection and IME composition synchronization.
5. Scrolling and native lazy collections.
6. Screen-specific macOS display-link selection and multi-display validation.
7. Intel macOS support if a compatible Compose Runtime artifact is available.
8. Publication and Kotlin API/binary compatibility validation.
9. Product-screen validation of the Compose backend's extra state invalidation hop.
10. Product-screen migration, stability hardening, and performance budgets.

Navigation, networking, and application state management remain outside the runtime.
