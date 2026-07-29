# Flare UI

Flare UI is a Kotlin Multiplatform UI runtime that uses Compose Runtime for authoring and
reconciliation while rendering through strongly typed platform backends.

- Android primitives can be Android Views or Jetpack Compose UI.
- iOS primitives can be UIKit views or native SwiftUI views.
- Shared screens and composite components are Kotlin `@Composable` functions.
- Renderer plugins add typed primitives without reflection or runtime binary loading.
- Android View and UIKit apply changes directly to native hierarchies. The Compose and SwiftUI
  backends use stable live renderer nodes because both UI toolkits are declarative.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the runtime invariants, plugin model, module
boundaries, and planned milestones.

## Modules

| Module | Responsibility |
| --- | --- |
| `flare-runtime` (`runtime/`) | Strongly typed backend/component SPI, modifiers, disposal, composition, applier, and the Android View, Compose, UIKit, and SwiftUI backend runtimes/hosts |
| `codegen` | KSP generation for primitive APIs, component tokens, widget contracts, Kotlin renderer plugins, and SwiftUI node/plugin plumbing |
| `foundation` | Common definitions, Android View/Compose/UIKit renderers, generated SwiftUI nodes, and native SwiftUI views for `Column`, `Row`, `Text`, and `NativeButton` |
| `plugins/badge` | Independent primitive plugin with Android View/Compose/UIKit renderers, a generated SwiftUI node/plugin, and its native SwiftUI view |
| `benchmark/android` | Paired Android microbenchmarks for equivalent native View and Flare UI mount/update workloads |
| `benchmark/apple-shared`, `benchmark/apple` | Kotlin benchmark bridge and paired XCTest benchmarks for equivalent native UIKit and Flare UI mount/update workloads |
| `demo/shared` | Complete shared demo UI, native host factories, and iOS framework output |
| `demo/androidApp` | Thin Android application shell |
| `demo/appleApp` | Thin XcodeGen application shell that consumes the local Swift package |
| `Package.swift` | Local SPM products for the SwiftUI runtime, Foundation renderers, and Badge renderer |

The demo screen lives entirely in `demo/shared/commonMain`. Its `androidMain` source set exposes
Android View and Jetpack Compose host factories, and the Android launcher lets the user select
either renderer. `iosMain` exposes UIKit and SwiftUI hosts, and the iOS launcher offers the same
choice. The same KMP module produces `FlareUI`; no additional Apple framework Gradle module is
required. Xcode builds that framework in a scheme pre-action, then compiles the SwiftUI sources
through the local `FlareUIApple` package.

Backend package names such as `android`, `compose`, `uikit`, and `swiftui` describe renderer
families rather than Gradle modules. Applications normally depend on `foundation`; a custom
component library can depend on `flare-runtime` and provide its own renderers.

## Plugin usage

Applications statically assemble the renderers they ship:

```kotlin
val widgetSystem =
    createAndroidWidgetSystem(
        AndroidViewBadgeRendererPlugin,
    )

FlareAndroidViewHost(
    context = context,
    widgetSystem = widgetSystem,
).apply {
    setContent {
        Badge(text = "Native plugin")
    }
}
```

The same plugin exposes a UIKit renderer:

```kotlin
val widgetSystem =
    createUIKitWidgetSystem(
        UIKitBadgeRendererPlugin,
    )
```

The same common content can render as Jetpack Compose UI:

```kotlin
val widgetSystem =
    remember {
        createAndroidComposeWidgetSystem(
            AndroidComposeBadgeRendererPlugin,
        )
    }

FlareComposeHost(widgetSystem = widgetSystem) {
    Badge(text = "Compose plugin")
}
```

SwiftUI uses generated plugin objects for both the Kotlin runtime node factories and native
`View` registration:

```swift
import FlareUIBadgeSwiftUI
import FlareUIFoundationSwiftUI
import FlareUISwiftUI

let plugins: [any FlareSwiftUIPlugin] = [
    FoundationSwiftUIPlugin(),
    BadgeSwiftUIPlugin(),
]
let host = FlareDemoSwiftUIHost(
    plugins: plugins.map { $0 as FlareSwiftUINodePlugin }
)
let registry = FlareSwiftUIRendererRegistry(plugins: plugins)
```

`AndroidComposeBadgeRendererPlugin` is generated alongside the other Badge renderers in the same
plugin module. SwiftUI needs no Kotlin adapter module: KSP generates a Swift subclass of the
exported `FlareSwiftUINode`, its generated Kotlin widget-interface conformance,
property/slot/callback plumbing, and plugin registration. The module author writes only the actual
native `View`. The same generated plugin instance populates the Kotlin node factory and Swift view
registries.

Automatic Gradle dependency aggregation is a planned milestone. The current explicit assembly
keeps plugin composition deterministic. Backend compatibility is enforced by Kotlin's type system,
while duplicate component registration is checked when the widget system is created.

## Define a primitive

A primitive is authored once in `commonMain`. The interface is its complete public UI definition:

```kotlin
@FlarePrimitive
interface BadgeSpec {
    @Composable
    @FlareUiComposable
    operator fun invoke(
        text: String,
        modifier: FlareModifier = FlareModifier,
        tone: BadgeTone = BadgeTone.Neutral,
        onClick: () -> Unit = {},
    )
}
```

KSP generates:

- the callable `Badge(...)` API;
- the typed `BadgeWidget` renderer contract;
- a strongly typed `FlareComponentType<BadgeWidget>` token;
- property update calls to the low-level runtime;
- named child-slot tokens derived from `FlareContent` parameter names.
- when enabled for an Apple renderer module, the SwiftUI node, setters, slot routing, callback
  cleanup, and plugin registration.

An Apple renderer module enables that output with one convention setting:

```kotlin
kotlin {
    flareUi {
        platforms(FlareUiPlatform.ANDROID, FlareUiPlatform.IOS)
        swiftUI("Badge")
    }
}
```

`generateFlareSwiftUISources` updates the stable source under
`src/iosMain/swift/generated`. `check` runs `verifyFlareSwiftUISources`, so changing the common
primitive without refreshing its tracked Swift file fails CI before SwiftPM sees stale input.

There is no schema declaration, schema version, component string ID, or central component list.
The generated component token is shared directly by the common API and renderer plugins.

The platform implements only its backend widget:

```kotlin
@FlareRenderer
internal class AndroidBadgeWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<TextView>(
        view = TextView(backend.context),
    ),
    BadgeWidget {
    override fun setText(value: String) {
        view.text = value
    }

    // setTone and setOnClick...
}
```

KSP infers the primitive from `BadgeWidget`, the backend from `AbstractAndroidWidget`, and injects
the current host backend when the renderer constructor requests it. It generates
`AndroidViewBadgeRendererPlugin` and every `registrar.register(...)` call. UIKit uses the same
model; a one-line strongly typed `expect val` gives `iosMain` a shared plugin entry while KSP
generates device and simulator `actual` values.

Compose plugins use the same mechanism by extending `AbstractAndroidComposeWidget` and
implementing `Render()`. Their property setters update snapshot state, while container renderers
use `AndroidComposeChildren`.

For SwiftUI, KSP generates the live node and registration. The platform implementation is only a
native renderer extension:

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

The generated property setters mutate the live node, and one Compose apply transaction
deduplicates notifications by changed node and child slot. Swift Observation then invalidates only
the corresponding view subtree. Objective-C generic erasure is contained and validated inside
the Kotlin registrar; individual renderers remain strongly typed.

Backends are strongly typed values rather than strings. Stateless backends can be objects, while a
backend that carries host-scoped services is a class:

```kotlin
class AndroidViewBackend(
    val context: Context,
) : FlareBackend
```

Consequently `FlareWidgetSystem<AndroidViewBackend>` accepts only
`FlareRendererPlugin<AndroidViewBackend>`; attempting to install a UIKit plugin is a compile error.
The widget system stores `(Backend) -> Widget` factories and receives the backend from each host,
so a reusable plugin set cannot retain an Activity, UIKit host, or SwiftUI tree.

## Verify

Run all tests, lint checks, and target compilation:

```shell
./gradlew -p flareUI check
```

Build the Android demo:

```shell
./gradlew -p flareUI :demo:androidApp:assembleDebug
```

Compile the native-vs-Flare Android benchmark:

```shell
./gradlew -p flareUI :benchmark:android:assembleReleaseAndroidTest
```

Run it on a physical Android device:

```shell
./gradlew -p flareUI :benchmark:android:connectedReleaseAndroidTest
```

Generate and run the native-vs-Flare UIKit benchmark:

```shell
xcodegen generate --spec flareUI/benchmark/apple/project.yml
xcodebuild \
  -project flareUI/benchmark/apple/FlareUIAppleBenchmark.xcodeproj \
  -scheme FlareUIAppleBenchmark \
  -configuration Release \
  -destination 'platform=iOS,id=DEVICE_UDID' \
  test
```

See [`benchmark/README.md`](benchmark/README.md) for the exact workloads and interpretation.

Generate and build the iOS demo:

```shell
xcodegen generate --spec flareUI/demo/appleApp/project.yml
xcodebuild \
  -resolvePackageDependencies \
  -project flareUI/demo/appleApp/FlareUIDemo.xcodeproj \
  -scheme FlareUIDemo-iOS
xcodebuild \
  -project flareUI/demo/appleApp/FlareUIDemo.xcodeproj \
  -scheme FlareUIDemo-iOS \
  -sdk iphonesimulator \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

If Xcode already has the generated project open when it is regenerated, close and reopen the
project so Xcode reloads the local package graph.
