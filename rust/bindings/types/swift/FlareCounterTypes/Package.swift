// swift-tools-version: 5.8
import PackageDescription

let package = Package(
    name: "FlareCounterTypes",
    products: [
        .library(
            name: "FlareCounterTypes",
            targets: ["FlareCounterTypes"]
        )
    ],
    targets: [
        .target(
            name: "FlareCounterTypes",
            dependencies: ["Serde"]
        ),
        .target(
            name: "Serde",
            dependencies: []
        ),
    ]
)
