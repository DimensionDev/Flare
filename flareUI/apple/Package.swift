// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "FlareUI",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "FlareUIRuntime",
            targets: ["FlareUIRuntime"]
        ),
        .library(
            name: "FlareUISwiftUI",
            targets: ["FlareUISwiftUI"]
        ),
        .library(
            name: "FlareUIUIKit",
            targets: ["FlareUIUIKit"]
        ),
        .library(
            name: "FlareUIAppKit",
            targets: ["FlareUIAppKit"]
        ),
    ],
    targets: [
        .target(
            name: "FlareUIRuntime",
            path: "Sources/Runtime"
        ),
        .target(
            name: "FlareUISwiftUI",
            dependencies: ["FlareUIRuntime"],
            path: "Sources/SwiftUI"
        ),
        .target(
            name: "FlareUIUIKit",
            dependencies: ["FlareUIRuntime"],
            path: "Sources/UIKit"
        ),
        .target(
            name: "FlareUIAppKit",
            dependencies: ["FlareUIRuntime"],
            path: "Sources/AppKit"
        ),
    ]
)
