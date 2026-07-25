import Foundation

/// A renderer-facing tree host. Consumer adapters translate their Kotlin
/// umbrella framework into this Swift-only boundary.
@MainActor
public protocol FlareUITreeHost: AnyObject {
    func snapshot() -> [FlareUINode]

    func setOnTreeChanged(
        _ listener: (([FlareUINode]) -> Void)?
    )

    func dispose()
}

public struct FlareUIResourceKey: Hashable, Sendable {
    public let resourceNamespace: String
    public let name: String
    public let platformNamespace: String
    public let platformName: String

    public init(
        resourceNamespace: String,
        name: String,
        platformNamespace: String,
        platformName: String
    ) {
        self.resourceNamespace = resourceNamespace
        self.name = name
        self.platformNamespace = platformNamespace
        self.platformName = platformName
    }
}

public struct FlareUIStringResource: Hashable, Sendable {
    public let key: FlareUIResourceKey

    public init(key: FlareUIResourceKey) {
        self.key = key
    }
}

public struct FlareUIImageResource: Hashable, Sendable {
    public let key: FlareUIResourceKey

    public init(key: FlareUIResourceKey) {
        self.key = key
    }
}

public enum FlareUIText: Hashable, Sendable {
    case literal(String)
    case resource(FlareUIStringResource)
}
