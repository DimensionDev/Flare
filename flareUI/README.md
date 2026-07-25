# Flare UI

This is a proof of concept for defining a small UI with Compose Runtime and
rendering it with platform UI toolkits. The current widget vocabulary is only
`Column`, `Row`, `Text`, `Button`, and `Icon`.

The runtime libraries and runnable demos are deliberately separate. None of the
demo code is linked into the existing Flare Android or Apple applications.

## Layout

| Path | Responsibility |
| --- | --- |
| `core` | One Kotlin file per component plus resource references, Compose Runtime, registry, and Applier |
| `codegen` | KSP discovery, generated registries/routers/payloads, and one-time renderer scaffolding |
| `android-compose` | Maps the widget tree to Compose Material 3 |
| `android-view` | Maps the widget tree to `LinearLayout`, `MaterialTextView`, and `MaterialButton` |
| `apple-runtime` | Runs a composition and exposes immutable snapshots to Swift |
| `apple` | Independent XcodeGen project with SwiftUI, UIKit, and AppKit framework targets |
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
core ──KSP/codegen──┬── android-compose glue
                   ├── android-view glue
                   ├── apple-runtime payloads
                   └── SwiftUI/UIKit/AppKit routers

core ── apple-runtime ── demo/apple-framework ──┬── apple
                              ▲                 │     │
                              └── demo/shared   └─────┴── demo/appleApp
```

There is no runtime backend plug-in system. Each app selects its renderer when
it is assembled, while the iOS demo keeps both of its already-linked renderers
only to make comparison convenient.

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
./gradlew :flareUI:demo:androidApp:assembleDebug
```

Install it on a connected device or emulator:

```shell
./gradlew :flareUI:demo:androidApp:installDebug
```

The launcher presents two entries: Compose UI and Android Views.
Both use Material 3 components. An app embedding the Android View renderer
should use a theme derived from `Theme.Material3`; the demo uses
`Theme.Material3.DayNight.NoActionBar`.

## Run iOS and macOS

Generate all backend glue and both ignored Xcode projects:

```shell
./gradlew :flareUI:codegen:generateFlareUiCode :flareUI:demo:shared:generateFlareUiResources
xcodegen generate --spec flareUI/apple/project.yml
xcodegen generate --spec flareUI/demo/appleApp/project.yml
```

Open `flareUI/demo/appleApp/FlareUIDemo.xcodeproj`, then choose:

- `FlareUIDemo-iOS` for the SwiftUI/UIKit navigation demo.
- `FlareUIDemo-macOS` for the SwiftUI/AppKit navigation demo.

The app project references `flareUI/apple/FlareUIApple.xcodeproj`. That project
provides the following independently buildable static frameworks:

- `FlareUISwiftUI` for iOS and macOS.
- `FlareUIUIKit` for iOS.
- `FlareUIAppKit` for macOS.

Both generated Xcode projects are ignored, while the generated Swift routers
are checked in. Each shared scheme runs
`:flareUI:demo:apple-framework:embedAndSignAppleFrameworkForXcode`
before building any Xcode target; that task regenerates all backend glue
before compiling the Kotlin framework.

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

`:flareUI:codegen:generateFlareUiCode` then generates:

- Compose and Android View registry/dispatch glue.
- The Apple node enum, typed payload classes, event methods, snapshot mapping,
  and registry.
- SwiftUI, UIKit, and AppKit routers.
- A renderer file for each toolkit if that file does not already exist.

The last item is deliberately create-only. Running generation again never
overwrites a handwritten renderer. A new renderer starts with a
`FLARE_UI_RENDERER_TODO` marker, and
`:flareUI:codegen:verifyFlareUiRenderers` fails until all five implementations
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
    id("dev.dimension.flare.ui-resources")
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
    host: contentFactory.createHost(),
    resources: .init(bundle: .main)
)
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

FlareSwiftUIHost(
    host: contentFactory.createHost(),
    resources: .init(bundle: .main)
)
```

The UIKit equivalent is `FlareUIKitHostView(host:resources:)` from
`FlareUIUIKit`. On macOS, use `FlareAppKitHostView(host:resources:)` from
`FlareUIAppKit`.

## Extending the widget vocabulary

Adding a widget no longer touches a central list, registry, `switch`, JSON
schema, or Apple snapshot model:

1. Add one annotated component file in `core`.
2. Run `:flareUI:codegen:generateFlareUiCode`.
3. Fill in the five create-only renderer files generated for that component.
4. Run `:flareUI:codegen:verifyFlareUiRenderers`.

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
