# How To Contribute

First of all, I'd like to express my appreciation to you for contributing to this project.
Below is the guidance for how to report issues, propose new features, and submit contributions via Pull Requests (PRs).

## Before you start, file an issue
If you have a question, think you've discovered an issue, would like to propose a new feature, etc., then find/file an issue **BEFORE** starting work to fix/implement it.

### Search existing issues first

Before filing a new issue, search existing open and closed issues first: It is likely someone else has found the problem you're seeing, and someone may be working on or have already contributed a fix!

If no existing item describes your issue/feature, great - please file a new issue.

## Contributing fixes / features

For those able & willing to help fix issues and/or implement features ...

### Development environment

Make sure you have
 - JDK 21
 - Xcode 16 if you're building for iOS

### Code guidelines
Flare uses [ktlint](https://github.com/pinterest/ktlint) to check the code style for Kotlin, so make sure run `./gradlew ktlintFormat` and fix the errors before you submit any PR.

### Building
### Android
 - Make sure you have JDK 21 installed
 - Run `./gradlew installDebug` to build and install the debug version of the app
 - You can open the project in Android Studio or IntelliJ IDEA if you want

### iOS
 - Make sure you have a Mac with Xcode 16 installed
 - open `iosApp/iosApp.xcodeproj` in Xcode
 - Build and run the app

### Server
 - Flare Server uses Ktor with Kotlin Native, which only works on Linux X64 and MacOS X64/ARM64
 - Make sure you have JDK 21 installed
 - Run `./gradlew :server:runDebugExecutableMacosArm64 -PrunArgs="--config-path=path/to/server/src/commonMain/resources/application.yaml"` to build and run the server, remember to replace `path/to/server/src/commonMain/resources/application.yaml` with the path to your config file
 - The server will run on `http://localhost:8080` by default

### Project structure
The project is split into 3 parts:
 - `shared`: The common code shared between Android and iOS and Server
   - `shared/ui`: Compose Multiplatform UI that shared between Android/JVM and iOS
   - `shared/ui/Component`: Compose Multiplatform UI that shared between Android JVM
   - `shared/commonMain`: Bussiness logic without any UI.
   - `shared/api`: Shared API definition for the server and client
 - `app`: The Android app
 - `iosApp`: The iOS app
 - `server`: The server

Most of the business logic is in `shared`, and the platform specific code and UI is in `app` and `iosApp`.
Flare uses [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) to share code between platforms, [Jetpack Compose](https://developer.android.com/jetpack/compose) for the UI on Android, [SwiftUI](https://developer.apple.com/xcode/swiftui/) for the UI on iOS.

### Business logic
Flare leverages [Molecule](https://github.com/cashapp/molecule) to implement business logic, with most presenters extending from `PresenterBase`. Additionally, Flare employs the concept of a "single source of truth" to ensure consistency in its business logic implementation.

### UI
Since Flare uses Jetpack Compose and SwiftUI, both of which are declarative UI frameworks, ensure that the UI contains no business logic and is solely responsible for rendering the state provided by the presenter.
