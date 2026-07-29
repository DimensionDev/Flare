// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "FlareUIApple",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "FlareUISwiftUI",
            targets: ["FlareUISwiftUI"]
        ),
        .library(
            name: "FlareUIFoundationSwiftUI",
            targets: ["FlareUIFoundationSwiftUI"]
        ),
        .library(
            name: "FlareUIBadgeSwiftUI",
            targets: ["FlareUIBadgeSwiftUI"]
        ),
    ],
    targets: [
        .target(
            name: "FlareUISwiftUI",
            path: "runtime/src/iosMain/swift"
        ),
        .target(
            name: "FlareUIFoundationSwiftUI",
            dependencies: [
                "FlareUISwiftUI",
            ],
            path: "foundation/src/iosMain/swift"
        ),
        .target(
            name: "FlareUIBadgeSwiftUI",
            dependencies: [
                "FlareUISwiftUI",
            ],
            path: "plugins/badge/src/iosMain/swift"
        ),
    ],
    swiftLanguageModes: [.v6]
)
