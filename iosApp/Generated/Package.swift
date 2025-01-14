// swift-tools-version: 5.8
import PackageDescription

let package = Package(
    name: "Generated",
    platforms: [
        .iOS(.v14)
    ],
    products: [
        .library(
            name: "Generated",
            targets: ["Generated"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "Generated",
            dependencies: [],
            path: "Sources"
        )
    ]
)
