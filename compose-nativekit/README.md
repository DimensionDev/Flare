# Compose NativeKit

Compose NativeKit is a small Kotlin Multiplatform UI runtime. Shared `@Composable` functions use Compose
Runtime for reconciliation and render through a selected platform backend.

- Android renders Android Views or Jetpack Compose UI.
- iOS renders UIKit views.
- macOS renders AppKit views.
- There is no SwiftUI backend, schema, or reflection. Core UI modules use no code generation.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for runtime invariants and the remaining production work.

## Modules

| Module | Responsibility |
| --- | --- |
| `nativekit-runtime` | Composition, applier, modifiers, renderer registry, Android hosts, and Apple frame clocks |
| `foundation` | `Column`, `Row`, `Text`, and `NativeButton` plus Android View/Compose/UIKit/AppKit renderers |
| `nativekit-lazy-layout` | Stable-key `LazyColumn`/`LazyRow`, state, diffing, and four native virtual-list renderers |
| `nativekit-resources-moko` | Optional Moko `stringResource`, `pluralStringResource`, `imageResource`, and image renderers |
| `demo/shared` | One shared demo composition and native host factories |
| `demo/androidApp`, `demo/appleApp` | Thin Android, UIKit, and AppKit application shells |

The Apple applications link the `ComposeNativeKit` Kotlin/Native framework directly and use
`import ComposeNativeKit` in Swift. The current Compose Runtime dependency publishes the required
macOS artifact for arm64, so AppKit currently targets Apple Silicon Macs.

## Host usage

Android View:

```kotlin
NativeKitAndroidViewHost(
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
NativeKitComposeHost(createAndroidComposeWidgetSystem()) {
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
NativeKitUIKitHost(createUIKitWidgetSystem()).setContent {
    Text("Hello")
}
```

AppKit:

```kotlin
NativeKitAppKitHost(createAppKitWidgetSystem()).setContent {
    Text("Hello")
}
```

The Compose backend keeps lightweight NativeKit widget state and emits real Compose UI nodes. It uses
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

`Column` supports `Start`, `Center`, `End`, and `Stretch`; `Row` supports `Top`, `Center`, `Bottom`,
and `Stretch`. `NativeKitModifier` supports wrap (the default), fill, and fixed width/height in dp on
Android or points on Apple. Text wraps on all four renderer paths. Padding and main-axis
arrangement remain outside the current Foundation API.

## Lazy collections

`nativekit-lazy-layout` keeps item declarations lightweight. Android delegates virtualization to
`RecyclerView` or Compose `LazyColumn`/`LazyRow`; Apple uses native `UIScrollView`/`NSScrollView`
adapters backed by a shared variable-extent index and viewport-bound item recycling:

```kotlin
val state = rememberLazyListState()

LazyColumn(
    modifier = NativeKitModifier.None.fillMaxWidth().height(320f),
    state = state,
    spacing = 8f,
) {
    item(key = "header", contentType = "header") {
        Text("Timeline")
    }
    items(
        items = posts,
        key = Post::id,
        contentType = Post::kind,
        layoutVersion = Post::layoutRevision,
    ) { post ->
        PostRow(post)
    }
}
```

Keys are required, unique, and stable across updates. They preserve item identity, keyed
`rememberSaveable` state, and the visible anchor during prepend/reorder. `contentType` selects a
compatible native reuse pool and improves the estimate for not-yet-measured items; it is not
identity. Item size is measured from content and may vary freely. A visible item's geometry is
invalidated after its child composition changes. Use `layoutVersion` when layout-affecting data can
change under the same key while the item is off-screen; it invalidates only that key's cached
measurement and does not impose a fixed size.

A lazy list needs a bounded main-axis viewport, normally supplied by its parent or a fixed/fill
modifier. `LazyListState` exposes visible-item layout information plus immediate and animated
index/offset scrolling.

Install the matching optional renderer plugin in the host widget system, for example
`AndroidViewLazyLayoutRendererPlugin`, `AndroidComposeLazyLayoutRendererPlugin`,
`UIKitLazyLayoutRendererPlugin`, or `AppKitLazyLayoutRendererPlugin`.

## Define a primitive

Primitive APIs and renderer contracts are ordinary Kotlin:

```kotlin
interface StatusWidget : NativeKitWidget {
    fun setText(value: String)
}

@OptIn(LowLevelNativeKitApi::class)
@Composable
@NativeKitComposable
fun Status(text: String) {
    EmitNativeKitWidget(
        componentType = StatusWidget::class,
        update = {
            set(text, StatusWidget::setText)
        },
    )
}
```

Each platform implements `StatusWidget` and registers its factory in a typed plugin:

```kotlin
object AndroidStatusPlugin : NativeKitRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AndroidViewBackend>) {
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
that use Moko Resources can add `nativekit-resources-moko`, apply Moko's generator plugin in the module
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
`resources-compose`) and does not depend on NativeKit's `foundation` module. Generated `AppRes`/`MR`
classes and localization files
belong to the consuming application. Static Apple frameworks must run Moko's
`copyFrameworkResourcesToApp` build phase; `demo/appleApp/project.yml` contains a working example.

## Verify

Run all checks:

```shell
./gradlew -p compose-nativekit check
```

Build the Android demo:

```shell
./gradlew -p compose-nativekit :demo:androidApp:assembleDebug
```

Generate and build the UIKit/AppKit demo project:

```shell
xcodegen generate --spec compose-nativekit/demo/appleApp/project.yml
xcodebuild -project compose-nativekit/demo/appleApp/ComposeNativeKitDemo.xcodeproj \
  -scheme ComposeNativeKitDemo-macOS-Tests -destination 'platform=macOS,arch=arm64' \
  CODE_SIGNING_ALLOWED=NO test
xcodebuild -project compose-nativekit/demo/appleApp/ComposeNativeKitDemo.xcodeproj \
  -scheme ComposeNativeKitDemo-iOS -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project compose-nativekit/demo/appleApp/ComposeNativeKitDemo.xcodeproj \
  -scheme ComposeNativeKitDemo-macOS -destination 'platform=macOS,arch=arm64' \
  CODE_SIGNING_ALLOWED=NO build
```
