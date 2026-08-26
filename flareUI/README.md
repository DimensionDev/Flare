# Flare UI

Flare UI is a small Kotlin Multiplatform UI runtime. Shared `@Composable` functions use Compose
Runtime for reconciliation and render through a selected platform backend.

- Android renders Android Views or Jetpack Compose UI.
- iOS renders UIKit views.
- macOS renders AppKit views.
- There is no SwiftUI backend, schema, or reflection. Core UI modules use no code generation.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for runtime invariants and the remaining production work.

## Modules

| Module | Responsibility |
| --- | --- |
| `flare-runtime` | Composition, applier, modifiers, renderer registry, Android hosts, and Apple frame clocks |
| `foundation` | `Column`, `Row`, `Text`, and `NativeButton` plus Android View/Compose/UIKit/AppKit renderers |
| `flare-resources-moko` | Optional Moko `stringResource`, `pluralStringResource`, `imageResource`, and image renderers |
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
the surrounding Compose composition's Recomposer and frame clock. Both Android renderer sets use
Material 3 controls: the View host must receive a Material 3-themed `Context`, and the Compose host
must run below `androidx.compose.material3.MaterialTheme`.

## Layout semantics

`Column` and `Row` use one shared stack-layout contract on every backend. Children wrap their
content by default, main-axis placement starts at the beginning, and `spacing` is expressed in
logical platform units (dp on Android and points on Apple platforms). Cross-axis alignment is
explicit:

```kotlin
Column(
    spacing = 12f,
    horizontalAlignment = HorizontalAlignment.Start,
) {
    Text("Title")
    Row(
        spacing = 8f,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        NativeButton(label = "Cancel", onClick = ::cancel)
        NativeButton(label = "Save", onClick = ::save)
    }
}
```

`Column` supports `Start`, `Center`, and `End`; `Row` supports `Top`, `Center`, and `Bottom`.
Text wraps on all four renderer paths. Sizing, padding, main-axis arrangement, and constraint-based
layout remain outside the current Foundation API.

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

Foundation stays resource-agnostic: its APIs continue to accept plain `String` values. Applications
that use Moko Resources can add `flare-resources-moko`, apply Moko's generator plugin in the module
which owns the resource catalog, and resolve values at the call site:

```kotlin
ProvideMokoResources(platformResolver) {
    Text(stringResource(AppRes.strings.title))
    Text(pluralStringResource(AppRes.plurals.items, itemCount, itemCount))
    ResourceImage(
        image = imageResource(AppRes.images.logo),
        contentDescription = stringResource(AppRes.strings.logo_description),
    )
}
```

The host installs the matching optional image renderer plugin, for example
`AndroidViewMokoResourcesRendererPlugin` or `UIKitMokoResourcesRendererPlugin`. Android uses
`AndroidMokoResourceResolver(context)`; UIKit and AppKit use `AppleMokoResourceResolver`.

For Moko itself, the adapter uses only the base `resources` artifact (not
`resources-compose`) and does not depend on Flare's `foundation` module. Generated `AppRes`/`MR`
classes and localization files
belong to the consuming application. Static Apple frameworks must run Moko's
`copyFrameworkResourcesToApp` build phase; `demo/appleApp/project.yml` contains a working example.

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
