# Flare UI runtime architecture

This document records the architecture implemented by the `runtime`, `foundation`, and plugin
modules.

## Product definition

Flare UI is a Kotlin Multiplatform UI runtime with a Compose Runtime authoring frontend and
strongly typed platform renderer backends.

- Shared screens and composite components are `@Composable` Kotlin functions.
- Every primitive is supplied by a renderer plugin for the selected backend.
- Composite components do not require platform code.
- Layout-only nodes may eventually be virtual and flattened.
- Android Views and UIKit are the reference backends.
- Jetpack Compose and SwiftUI are optional declarative renderer backends.
- SwiftUI renders its own `VStack`, `HStack`, `Text`, and `Button`; it does not wrap the UIKit
  hierarchy.

## Runtime path

```text
shared @Composable content
        |
        v
Compose Runtime
        |
        v
FlareApplier
        |
        v
statically assembled FlareWidgetSystem
        |
        +-- Android View widgets
        |
        +-- UIKit widgets
        |
        +-- Android Compose state nodes
        |           |
        |           v
        |     Compose UI
        |
        +-- SwiftUI live nodes
                    |
                    v
                SwiftUI
```

Android View and UIKit have no immutable production snapshot tree or second rendering pass.
Compose Runtime performs reconciliation and the Flare applier translates structural changes
directly into native child operations.

The Android Compose and SwiftUI backends necessarily have a second, lightweight rendering step.
Flare's applier mutates stable renderer nodes, then the declarative toolkit reads those nodes to
emit native UI. Component identity, factory selection, property updates, slots, and disposal
remain owned by Flare; Compose UI or SwiftUI owns layout and drawing.

SwiftUI nodes are live objects rather than immutable snapshots. One Compose apply transaction
deduplicates property changes by node and structural changes by child slot. A Swift Observation
model keeps weak-keyed revisions for those exact objects, so SwiftUI re-reads only the affected
subtree while preserving node identity. No serialized tree, schema, or explicit node ID is
introduced.

## Runtime invariants

### Bottom-up insertion and disposal

Primitive properties and descendants are created before the primitive is inserted into its
backend parent. This prevents a partially initialized widget from entering the visible hierarchy.

The ordering is:

```text
create
  -> apply initial properties and modifier
  -> create descendants
  -> insert into parent

remove
  -> remove from parent
  -> dispose descendants
  -> dispose
```

### Named slots

Children are never an implicit untyped list on a widget. A primitive explicitly resolves a
`FlareSlotId` to `FlareChildren`. The runtime uses synthetic slot nodes so components can later
support APIs such as `leading`, `content`, `trailing`, and `overlay`.

### Typed updates

Each common primitive definition produces a typed widget interface:

```kotlin
interface BadgeWidget : FlareWidget {
    companion object {
        val Type: FlareComponentType<BadgeWidget>
    }

    fun setText(value: String)
    fun setTone(value: BadgeTone)
    fun setOnClick(value: () -> Unit)
}
```

KSP also generates the callable composable object and updates these properties individually.
Application and component authors never call `EmitFlareWidget`; it remains a low-level codegen
surface. The widget system does not route an `Any` props payload through a central registry.

### Apply and frame batching

The applier brackets every mutation batch with `FlareChildren.onBeginChanges()` and
`onEndChanges()`. Backends can therefore defer notifications or layout work until the complete
Compose transaction has been applied. SwiftUI uses this boundary to deduplicate dirty nodes and
slots. UIKit records the desired arranged-subview order and applies it once at the transaction
boundary. Android View suppresses root layout while a supported platform hierarchy transaction is
being mutated.

On iOS, snapshot writes are conflated before apply notifications are sent, and recomposition is
driven by a paused-until-needed `CADisplayLink`. Multiple writes in one event-loop turn therefore
do not enqueue duplicate snapshot work, and visible updates align with the display refresh. Apple
hosts share one recomposer/frame clock while any host is active. Android and Apple snapshot
observers are reference counted and removed after the final host composition is disposed.

### Explicit disposal ownership

`FlareWidget.dispose` is the sole disposal hook. Renderers clear event handlers and other
cross-runtime references during disposal.

Android View and UIKit hosts create their composition only while attached to a window and dispose
it on detach. They retain only the declarative content needed for a later reattach. SwiftUI's
Observation owner disposes its Kotlin host from `deinit`; all disposal paths clear observers,
composition nodes, callbacks, recomposer jobs, frame clocks, and snapshot observers.

### Strong backend and component identity

Schema objects and string component IDs are intentionally absent. Every primitive owns one
generated `FlareComponentType<W>` object token. Its generic argument connects the composable
emitter, renderer contract, registry, and native factory without reflection or serialized names.

Backends are strongly typed values implementing `FlareBackend`. Stateless families such as
Android Compose use singleton objects. Host-bound families use instances, for example
`AndroidViewBackend(context)` and `SwiftUIBackend(tree)`. `FlareWidgetSystem<B>` stores
`(B) -> FlareWidget` factories rather than a backend instance; the host supplies its backend only
when a widget is created. A reusable widget system therefore cannot capture an Activity context or
SwiftUI tree. `FlareRendererPlugin<B>` carries the backend type through plugin assembly, while the
registrar exists only inside a typed plugin's registration call. Installing a UIKit plugin into an
Android View system is a compile-time error.

The widget system rejects duplicate component registrations. KSP rejects duplicate renderer
bindings and renderer classes which do not expose exactly one generated widget contract and one
concrete backend.

## Component categories

| Category | Backend object | Platform renderer |
| --- | --- | --- |
| Direct native primitive | Android View or UIKit object | Required |
| Direct native container | Usually a ViewGroup or UIView | Required |
| Compose primitive | Snapshot-backed renderer node | Required |
| SwiftUI primitive | Live Kotlin renderer node plus native Swift `View` | Required |
| Layout-only primitive | Optional after flattening | Required layout implementation |
| Composite component | No dedicated object | None |
| Modifier | Never | Interpreted by the widget |

The foundation module contains `Column`, `Row`, `Text`, and `NativeButton` together with their
Android View, Compose, and UIKit Kotlin renderers and module-owned SwiftUI source. It is
deliberately small: the purpose of this slice is to validate disposal, plugin composition,
events, and native identity before expanding the component catalogue.

## Plugin packaging

`plugins:badge` is the reference independent primitive plugin:

```text
plugins/badge
├── commonMain
│   └── BadgeSpec public UI definition
├── androidMain
│   ├── TextView-backed renderer
│   └── Compose-backed renderer
└── iosMain
    ├── Kotlin UIButton-backed renderer
    └── Swift
        ├── generated FlareSwiftUINode and plugin registration
        └── handwritten native View renderer
```

From `BadgeSpec`, KSP generates the callable `Badge`, `BadgeWidget`, its strongly typed component
token, and property update glue. From `@FlareRenderer` widget implementations it infers the
primitive and backend, then generates the Android View, Android Compose, and UIKit renderer plugins
and registrations. Adding a primitive therefore does not require a schema, renderer set, or
component ID. SwiftUI is the one source-language boundary: KSP additionally generates the
Swift-owned node, property/slot/callback plumbing, component-token registration, and native
renderer registration. The platform author supplies only the actual SwiftUI `View` body.

Badge keeps all platform renderers in one component-owned artifact. Applications still install
only the plugin matching their host, and backend compatibility remains enforced by its strong
backend type. A renderer should split into a separate adapter only when a real publication or
dependency-footprint boundary justifies it. The SwiftUI implementation is source-owned by Badge
and exposed through the local `FlareUIBadgeSwiftUI` package product. Applications select that
product explicitly.

An Android application assembles it with:

```kotlin
val system =
    createAndroidWidgetSystem(
        AndroidViewBadgeRendererPlugin,
    )
```

UIKit uses the corresponding statically referenced plugin:

```kotlin
val system =
    createUIKitWidgetSystem(
        UIKitBadgeRendererPlugin,
    )
```

Compose uses the generated plugin for its own backend type:

```kotlin
val system =
    createAndroidComposeWidgetSystem(
        AndroidComposeBadgeRendererPlugin,
    )

FlareComposeHost(widgetSystem = system) {
    Badge(text = "Compose plugin")
}
```

SwiftUI's node and plugin are generated. Badge only implements its native renderer:

```swift
extension SwiftUIBadgeNode: FlareSwiftUIRenderableNode {
    @MainActor
    func render(context: FlareSwiftUIRenderContext) -> some View {
        Button {
            self.performOnClick()
        } label: {
            Text(text)
        }
    }
}
```

The generated `SwiftUIBadgeNode` directly subclasses the Kotlin-exported `FlareSwiftUINode` and
conforms to the generated Kotlin `BadgeWidget` protocol. There is no Kotlin SwiftUI adapter module
or mirror node. The exported application host receives the generated plugins as
`[FlareSwiftUINodePlugin]`; the same Swift objects populate the typed native view registry.

## SwiftUI interop boundary

Kotlin/Native cannot directly depend on a pure Swift module unless its API is exported through
Objective-C, so SwiftUI view construction remains in Swift. Kotlin instead exports the stable live
node base, generated widget protocols and component tokens, a node registrar, and the application
host. Generated Swift subclasses the Kotlin base and implements the Kotlin protocols directly.
The local Swift package compiles the runtime registry and module-owned native views, while an
Xcode scheme pre-action builds the single `demo/shared` framework first. This avoids an extra
Apple-framework Gradle layer and avoids adding package sources directly to the application target.
See the
[Kotlin/Native Swift interop documentation](https://kotlinlang.org/docs/native-objc-interop.html).

The node registrar accepts the component token as `Any` at the Objective-C surface because
Objective-C erases the covariance of `FlareComponentType<W>`. Kotlin immediately validates and
contains that cast before inserting the factory into `FlareWidgetSystem<SwiftUIBackend>`. Swift
plugins therefore need no `as!` cast. Their Kotlin protocol methods and node overrides are marked
`nonisolated`, while native view registration remains `@MainActor`, making the boundary explicit
under Swift 6 default actor isolation.

The Swift registry keys renderers by concrete `Node` metatype. SwiftUI's `View` protocol has an
associated `Body` type, so heterogeneous plugin results are erased to `AnyView` only after the
registry verifies the node cast; individual renderers stay generic and strongly typed. See
[SwiftUI `View`](https://developer.apple.com/documentation/swiftui/view).

The demo uses the iOS 17 Observation system for node- and child-slot-level invalidation. A small
observer proxy points weakly to the model, while weak-key maps keep per-object revisions without
retaining removed Kotlin nodes. See
[Apple's model data documentation](https://developer.apple.com/documentation/swiftui/model-data).

There is no reflection or runtime binary loading. Local SPM products package the SwiftUI runtime
and renderer modules; the current demo still selects plugin products explicitly.

## Module boundaries

| Module | Responsibility |
| --- | --- |
| `flare-runtime` | Backend/component types, codegen annotations, modifiers, Widget SPI, applier, disposal, composition, and the four backend runtimes/hosts |
| `codegen` | KSP primitive, typed contract/token, Kotlin renderer plugin, and SwiftUI node/plugin generation |
| `foundation` | Common primitive definitions, Android View/Compose/UIKit renderers, generated SwiftUI nodes, and module-owned native SwiftUI views |
| `plugins:badge` | Independent primitive API with Android View/Compose/UIKit renderers, a generated SwiftUI node/plugin, and its native SwiftUI view |
| `benchmark:android` | Paired release instrumentation microbenchmarks for native Android View and Flare UI mount/update costs |
| `benchmark:apple-shared`, `benchmark/apple` | Synchronous Kotlin benchmark bridge and paired Release XCTest benchmarks for native UIKit and Flare UI mount/update costs |
| `demo/shared` | Complete common demo composition, platform host entry points, iOS framework |
| `Package.swift` | Local Swift package products for the runtime and module-owned SwiftUI renderers |

`flare-runtime` owns backend mechanics and never depends on Foundation. `foundation` depends
one-way on the runtime and is the first renderer bundle. A new backend runtime belongs in
`flare-runtime`; a renderer for a Foundation primitive belongs in `foundation`. These modules
should split further only when a real publication, binary-compatibility, or platform dependency
boundary requires it.

The demo follows the intended application structure: `commonMain` owns the complete screen and
state, `androidMain` assembles Android View and Compose renderers, and `iosMain` assembles UIKit
while exposing a SwiftUI host that accepts Swift plugins. The Xcode app selects local SPM products
instead of compiling package sources directly. `demo/shared` itself emits the `FlareUI`
Kotlin/Native framework consumed by those products, so there is no separate Apple-only Gradle
wrapper module.

## Verification gates already implemented

- Runtime tests verify named slots, bottom-up insertion and disposal ordering, and duplicate
  component protection, plus state-driven headless recomposition.
- Codegen tests verify callable APIs, typed component contracts, inferred slots, strongly typed
  platform plugin output, and SwiftUI node/plugin generation.
- Robolectric verifies that an independently installed Badge renders as a native `TextView`,
  handles an event, recomposes, preserves native view identity, and that the Android host creates
  and releases its composition across attach/detach/reattach.
- Compose UI tests verify Foundation rendering and an independently installed Compose Badge,
  including event-driven Flare recomposition.
- The UIKit foundation and Badge renderers compile for the iOS simulator.
- SwiftUI runtime tests verify plugin-to-widget-system registration, stable identity, transaction
  deduplication, targeted invalidation, and event-driven recomposition. The Xcode build compiles
  generated Kotlin subclasses, generated widget-protocol conformances and registration, the
  Observation model, and native Foundation/Badge SwiftUI renderers through Local SPM against the
  exported framework.
- The shared demo module compiles for Android and links both Apple hosts into its own Kotlin/Native
  framework.
- The Android benchmark module compares equivalent 100-`TextView` mount and single-item update
  workloads, including measure/layout on both paths and Flare snapshot/recomposition on its update
  path.

## Deliberately deferred

Flare UI is not production complete. The next gates are:

1. Gradle dependency aggregation and automatic Swift package-product discovery so applications
   install renderer plugins from dependencies without listing them manually.
2. A common constraints/measure/place layout contract and layout-only node flattening.
3. Density, locale, layout direction, safe area, theme, and resource environments.
4. Accessibility semantics and focus.
5. Native TextInput with selection and IME composition synchronization.
6. Scroll and native RecyclerView/UICollectionView-backed LazyList.
7. AppKit renderer and macOS host.
8. Publication, BOM, plugin template, and Kotlin API/binary compatibility validation.
9. Migration of product screens and stability hardening against real application workloads.

Navigation, networking, and application state management are intentionally outside the runtime.
If server-driven or serialized UI is ever required, its versioned protocol belongs in a separate
`flare-protocol` layer rather than reintroducing schemas into the local native UI runtime.
