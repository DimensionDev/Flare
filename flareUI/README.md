# Flare UI

This is a proof of concept for defining a small UI with Compose Runtime and
rendering it with platform UI toolkits. The current widget vocabulary is only
`Column`, `Row`, `Text`, `Button`, and `Icon`.

`flareUI` is its own Gradle build: it owns its settings, version catalog, build
logic, code generator, runtime modules, and demos. It does not reference any
Flare module, root version catalog, or root convention plugin. The Flare
repository consumes it as an included build; the dependency points only from
the application toward Flare UI.

## Layout

| Path | Responsibility |
| --- | --- |
| `settings.gradle.kts`, `gradle/libs.versions.toml` | Standalone build and dependency versions |
| `build-logic` | Flare UI conventions and the consumer resource plugin |
| `core` | One Kotlin file per component plus resource references, Compose Runtime, registry, and Applier |
| `codegen` | KSP discovery, generated registries/routers/payloads, and one-time renderer scaffolding |
| `android-compose` | Maps the widget tree to Compose Material 3 |
| `android-view` | Maps the widget tree to `LinearLayout`, `MaterialTextView`, and `MaterialButton` |
| `apple-runtime` | Runs a composition and exposes immutable snapshots to Swift |
| `apple/Package.swift` | Local Swift package manifest |
| `apple/Sources/Runtime` | Swift-only tree/resource boundary distributed as `FlareUIRuntime` |
| `apple/Sources/SwiftUI`, `UIKit`, `AppKit` | Native renderer targets distributed by SwiftPM |
| `apple/Sources/KotlinBridge` | Generated consumer bridge from one Kotlin umbrella framework into `FlareUIRuntime` |
| `demo/shared` | Stateful UI and consumer-owned strings/SVG shared by every demo |
| `demo/androidApp` | Standalone Android app containing Compose and View screens |
| `demo/apple-framework` | Standalone `FlareUIDemoKit` framework and demo factory |
| `demo/appleApp` | XcodeGen app project with only iOS and macOS demo entry points |

The platform coverage is:

| Platform | Renderers |
| --- | --- |
| Android | Compose UI and Android Views |
| iOS | SwiftUI and UIKit, each on its own navigation destination |
| macOS | SwiftUI and AppKit, each on its own navigation destination |

UIKit is iOS-only and AppKit is macOS-only. Both Apple demos use the same shared
definition and link only the renderer frameworks available on their platform.

## Dependency direction

```text
core ──KSP/codegen──┬── android-compose
                   ├── android-view
                   ├── apple-runtime
                   ├── Swift runtime models and Kotlin bridge
                   └── SwiftUI/UIKit/AppKit routers

consumer content ── core
consumer Apple umbrella framework ── core + apple-runtime
consumer Kotlin bridge ── module alias ── consumer Apple umbrella framework
SwiftUI/UIKit/AppKit ── FlareUIRuntime ←── consumer Kotlin bridge
```

There is no runtime backend plug-in system. Each app selects its renderer when
it is assembled, while the iOS demo keeps both of its already-linked renderers
only to make comparison convenient.

## Consume the standalone build

A source checkout can be attached to another Gradle build with:

```kotlin
pluginManagement {
    includeBuild("flareUI/build-logic") {
        name = "flare-ui-build-logic"
    }
}

includeBuild("flareUI")
```

Consumer modules then use normal coordinates rather than reaching into Flare
UI with cross-project paths:

```kotlin
implementation("dev.dimension.flareui:core:0.1.0-SNAPSHOT")
implementation("dev.dimension.flareui:android-compose:0.1.0-SNAPSHOT")
```

The current repository uses this form for `apple-shared`. Publishing those
same coordinates later does not require changing consumer source code.

## Consume the local Apple package

Add `flareUI/apple` as a local package dependency in Xcode, or declare the
same path in XcodeGen:

```yaml
packages:
  FlareUI:
    path: ../flareUI/apple
```

The package exposes four libraries:

- `FlareUIRuntime`: Swift-only node, host, and resource contracts.
- `FlareUISwiftUI`: iOS and macOS SwiftUI renderer.
- `FlareUIUIKit`: iOS UIKit renderer.
- `FlareUIAppKit`: macOS AppKit renderer.

The package deliberately does not contain a prebuilt Kotlin framework. A KMP
application must keep exactly one Kotlin/Native umbrella framework, export
Flare UI's `core` and `apple-runtime` modules from it, and compile the generated
`apple/Sources/KotlinBridge` files in a small consumer target. That target
depends on `FlareUIRuntime` and maps the logical import to the application's
framework:

```text
-module-alias FlareUIKotlinRuntime=MyKotlinFramework
```

Keeping this adapter consumer-side prevents two Kotlin/Native runtimes from
being linked into the same application. A non-Kotlin client can instead
implement the public `FlareUITreeHost` protocol directly.

## Default layout semantics

Compose UI is the reference for the default behavior of the minimal widget
vocabulary:

| Widget | Default behavior |
| --- | --- |
| `Column` | Wrap content, place children from top to bottom, align children to start, no spacing |
| `Row` | Wrap content, place children from start to end, align children to top, no spacing |
| `Text` | Use intrinsic size and leading multiline alignment |
| `Button` | Use intrinsic content size, center its label, and add no sibling spacing |
| `Icon` | Use the source vector's intrinsic size and native template tint |

Android Views, SwiftUI, UIKit, and AppKit implement these same layout rules.
Visual styling remains native to each toolkit. UIKit excludes `UIButton`'s
style-dependent outer content insets from its Flare layout size so those insets
do not become gaps inside a zero-spacing `Row`.

## Run Android

Build the standalone APK:

```shell
./gradlew -p flareUI :demo:androidApp:assembleDebug
```

Install it on a connected device or emulator:

```shell
./gradlew -p flareUI :demo:androidApp:installDebug
```

The launcher presents two entries: Compose UI and Android Views.
Both use Material 3 components. An app embedding the Android View renderer
should use a theme derived from `Theme.Material3`; the demo uses
`Theme.Material3.DayNight.NoActionBar`.

## Run iOS and macOS

Generate backend glue, resources, and the ignored demo project:

```shell
./gradlew -p flareUI :codegen:generateFlareUiCode :demo:shared:generateFlareUiResources
xcodegen generate --spec flareUI/demo/appleApp/project.yml
```

Open `flareUI/demo/appleApp/FlareUIDemo.xcodeproj`, then choose:

- `FlareUIDemo-iOS` for the SwiftUI/UIKit navigation demo.
- `FlareUIDemo-macOS` for the SwiftUI/AppKit navigation demo.

The app project references `flareUI/apple` as a local Swift package and
selects these products:

- `FlareUISwiftUI` for iOS and macOS.
- `FlareUIUIKit` for iOS.
- `FlareUIAppKit` for macOS.

The generated demo project is ignored, while the Swift package manifest,
runtime models, routers, and bridge are checked in. The consumer bridge runs
`:demo:apple-framework:embedAndSignAppleFrameworkForXcode`
as its pre-build step; that task regenerates all backend glue before compiling
the Kotlin framework.

Only the consumer bridge imports the logical module
`FlareUIKotlinRuntime`. Xcode maps that name to the consumer's single Kotlin
umbrella framework: `FlareUIDemoKit` in the demo and `KotlinSharedUI` in the
Flare app. The SPM runtime and renderers contain no Kotlin import.

## Component generation

There is no handwritten schema or renderer manifest. A component is declared
next to its props and public Compose Runtime function in `core`:

```kotlin
@Immutable
public data class BadgeProps(
    public val value: String,
)

@FlareComponent
public data object BadgeType : WidgetType<BadgeProps>("Badge")

@Composable
@FlareUiComposable
public fun Badge(value: String) {
    EmitWidget(BadgeType, BadgeProps(value), content = {})
}
```

KSP derives the component name from the required `BadgeType` suffix and reads
the props type, constructor properties, and callback events directly from that
declaration. It passes this model straight to the generator; there is no
intermediate schema file.

`:codegen:generateFlareUiCode` then generates:

- Compose and Android View registry/dispatch glue.
- The Apple node enum, typed payload classes, event methods, snapshot mapping,
  and registry.
- Swift-only node/payload models and the Kotlin-to-Swift consumer bridge.
- SwiftUI, UIKit, and AppKit routers.
- A renderer file for each toolkit if that file does not already exist.

The last item is deliberately create-only. Running generation again never
overwrites a handwritten renderer. A new renderer starts with a
`FLARE_UI_RENDERER_TODO` marker, and
`:codegen:verifyFlareUiRenderers` fails until all five implementations
are completed. Native rendering semantics remain handwritten because they
cannot be inferred from a props class; all repetitive registration and routing
is generated.

Normal backend compilation depends on generation and verification, so missing
coverage cannot silently ship. Generated Kotlin lives under each module's
`build/generated/flareui` directory. Generated Swift routers live in each
toolkit's `Sources/.../Generated` folder so XcodeGen includes them automatically.

## Consumer-owned resources

Flare UI defines only platform-neutral references (`FlareStringResource`,
`FlareImageResource`, and `FlareText`). It contains no product strings, SVGs,
resource bundles, or generated application accessors.

A consuming KMP module opts in to resource generation:

```kotlin
plugins {
    id("dev.dimension.flareui.resources")
}

flareUiResources {
    namespace.set("profile")
    accessorName.set("ProfileResources")
}
```

That module owns one input tree:

```text
src/commonMain/flareResources/
├── values/strings.xml
├── values-ja/strings.xml
├── values-b+zh+Hans/strings.xml
└── images/avatar_placeholder.svg
```

The task generates typed common accessors, Android `R` resources plus a
resolver, and an Apple string catalog plus asset catalog. Android therefore
uses its normal locale-qualified resources, while iOS and macOS use the
generated `.xcstrings` and `.xcassets`.

The shared definition references only the generated typed values:

```kotlin
Text(ProfileResources.Strings.title)
Icon(
    image = ProfileResources.Images.avatarPlaceholder,
    contentDescription = ProfileResources.Strings.avatarDescription,
)
```

The application injects the generated Android resolver:

```kotlin
FlareComposeContent(resources = ProfileAndroidResources) {
    ProfileContent()
}

FlareViewHost(
    context = this,
    resourceResolver = ProfileAndroidResources,
)
```

Apple applications add the generated catalog directories to their resource
build phase and pass the owning bundle:

```swift
FlareSwiftUIHost(
    resources: .init(bundle: .main)
) {
    contentFactory.createHost()
}
```

`FlareAppleResources(bundleForNamespace:)` can route namespaces to separate
feature bundles without making the renderer depend on those features. The
current SVG converter deliberately supports the portable demo subset:
`svg`/`g`/`path`, no transformed groups. Unsupported input fails generation
instead of silently rendering differently on Android and Apple.

## Reuse the runtime

Android Compose:

```kotlin
FlareComposeContent(resources = MyAndroidResources) {
    MySharedContent()
}
```

Android Views:

```kotlin
setContentView(
    FlareViewHost(
        context = this,
        resourceResolver = MyAndroidResources,
    ).apply {
        setContent {
            MySharedContent()
        }
    },
)
```

On Apple platforms, a small Kotlin framework module creates a
`FlareUiTreeHost` for concrete shared content. SwiftUI, UIKit, or AppKit then
maps its snapshots to native views. Keeping that factory outside
`apple-runtime` prevents the runtime library from depending on any particular
screen.

The renderer frameworks receive that host explicitly, so they do not depend on
the demo factory:

```swift
import FlareUISwiftUI
import MyFlareUIKotlinBridge

FlareSwiftUIHost(
    resources: .init(bundle: .main)
) {
    FlareUIKotlinTreeHost(host: contentFactory.createHost())
}
```

The UIKit equivalent is `FlareUIKitHostView(host:resources:)` from
`FlareUIUIKit`. On macOS, use `FlareAppKitHostView(host:resources:)` from
`FlareUIAppKit`.

## Extending the widget vocabulary

Adding a widget no longer touches a central list, registry, `switch`, JSON
schema, or Apple snapshot model:

1. Add one annotated component file in `core`.
2. Run `:codegen:generateFlareUiCode` from the standalone build.
3. Fill in the five create-only renderer files generated for that component.
4. Run `:codegen:verifyFlareUiRenderers`.

The component's public Compose Runtime function decides whether it accepts
children. Function-valued props are exported as typed Apple payload actions;
for example `onClick` becomes `performClick()`. The processor rejects nullable
callbacks and callbacks that do not return `Unit`.

Generated files grow linearly with the vocabulary, while the stable runtime,
hosts, and handwritten component renderers do not.

This demo intentionally does not define modifiers, design tokens, a general
accessibility model, focus, navigation, list virtualization, or state
restoration. Those semantics should be added from concrete product requirements
rather than by copying every API from the five host toolkits.
