// swift-tools-version: 5.8
import PackageDescription

let package = Package(
    name: "Generated",
    platforms: [
        .iOS(.v14),
        .macOS(.v11)
    ],
    products: [
        .library(
            name: "Generated",
            type: .dynamic,
            targets: ["Generated"]),
    ],
    targets: [
        .target(
            name: "Generated",
            dependencies: [],
            path: "Sources",
            publicHeadersPath: nil
        )
    ]
)
