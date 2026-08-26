# Flare UI

Flare UI is a small Kotlin Multiplatform UI runtime. Shared `@Composable` functions use Compose
Runtime for reconciliation and render through a selected platform backend.

- Android renders Android Views or Jetpack Compose UI.
- iOS renders UIKit views.
- macOS renders AppKit views.
- There is no SwiftUI backend, schema, reflection, or code generation.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for runtime invariants and the remaining production work.

## Modules

| Module | Responsibility |
| --- | --- |
| `flare-runtime` | Composition, applier, modifiers, renderer registry, Android hosts, and Apple frame clocks |
| `foundation` | `Column`, `Row`, `Text`, and `NativeButton` plus Android View/Compose/UIKit/AppKit renderers |
| `demo/shared` | One shared demo composition and native host factories |
| `demo/androidApp`, `demo/appleApp` | Thin Android, UIKit, and AppKit application shells |

The Apple applications link the Kotlin/Native framework directly. The current Compose Runtime
dependency publishes the required macOS artifact for arm64, so AppKit currently targets Apple
Silicon Macs.

## Host usage

Android View:

```kotlin
FlareAndroidViewHost(
    context = context,
    widgetSystem = createAndroidWidgetSystem(),
).apply {
    setContent {
        Text("Hello")
    }
}
```

Jetpack Compose:

```kotlin
FlareComposeHost(createAndroidComposeWidgetSystem()) {
    Column {
        Text("Hello")
        AndroidCompose {
            ExistingComposeOnlyComponent()
        }
    }
}
```

UIKit:

```kotlin
FlareUIKitHost(createUIKitWidgetSystem()).setContent {
    Text("Hello")
}
```

AppKit:

```kotlin
FlareAppKitHost(createAppKitWidgetSystem()).setContent {
    Text("Hello")
}
```

The Compose backend keeps lightweight Flare widget state and emits real Compose UI nodes. It uses
the surrounding Compose composition's Recomposer and frame clock.

## Define a primitive

Primitive APIs and renderer contracts are ordinary Kotlin:

```kotlin
interface StatusWidget : FlareWidget {
    fun setText(value: String)
}

@OptIn(LowLevelFlareApi::class)
@Composable
@FlareUiComposable
fun Status(text: String) {
    EmitFlareWidget(
        componentType = StatusWidget::class,
        update = {
            set(text, StatusWidget::setText)
        },
    )
}
```

Each platform implements `StatusWidget` and registers its factory in a typed plugin:

```kotlin
object AndroidStatusPlugin : FlareRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(StatusWidget::class) { backend ->
            AndroidStatusWidget(backend.context)
        }
    }
}
```

Applications pass optional plugins to `createAndroidWidgetSystem`,
`createAndroidComposeWidgetSystem`, `createUIKitWidgetSystem`, or `createAppKitWidgetSystem`.
Duplicate registrations fail when the widget system is created.

## Resources and localization

Flare currently has no cross-platform asset or localization pipeline. Applications resolve native
localized strings, images, colors, and SVG/vector assets and pass the resulting values into
primitives. A shared resource environment remains future work.

## Verify

Run all checks:

```shell
./gradlew -p flareUI check
```

Build the Android demo:

```shell
./gradlew -p flareUI :demo:androidApp:assembleDebug
```

Generate and build the UIKit/AppKit demo project:

```shell
xcodegen generate --spec flareUI/demo/appleApp/project.yml
xcodebuild -project flareUI/demo/appleApp/FlareUIDemo.xcodeproj \
  -scheme FlareUIDemo-iOS -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project flareUI/demo/appleApp/FlareUIDemo.xcodeproj \
  -scheme FlareUIDemo-macOS -destination 'platform=macOS,arch=arm64' \
  CODE_SIGNING_ALLOWED=NO build
```
