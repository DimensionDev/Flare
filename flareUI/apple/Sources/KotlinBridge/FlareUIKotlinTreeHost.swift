@preconcurrency import FlareUIKotlinRuntime
import FlareUIRuntime

/// Connects the consumer's Kotlin umbrella framework to the Swift Package.
@MainActor
public final class FlareUIKotlinTreeHost: FlareUITreeHost {
    private let host: FlareUiTreeHost

    public init(host: FlareUiTreeHost) {
        self.host = host
    }

    public func snapshot() -> [FlareUINode] {
        host.snapshot().map(mapFlareUIKotlinNode)
    }

    public func setOnTreeChanged(
        _ listener: (([FlareUINode]) -> Void)?
    ) {
        guard let listener else {
            host.setOnTreeChanged(listener: nil)
            return
        }
        host.setOnTreeChanged { nodes in
            listener(nodes.map(mapFlareUIKotlinNode))
        }
    }

    public func dispose() {
        host.dispose()
    }
}

func mapFlareUIKotlinText(_ value: FlareText) -> FlareUIText {
    if let literal = value.literal {
        return .literal(literal)
    }
    guard let resource = value.resource else {
        preconditionFailure("FlareText has neither a literal nor a resource")
    }
    return .resource(
        FlareUIStringResource(
            key: mapFlareUIKotlinResourceKey(resource.key)
        )
    )
}

func mapFlareUIKotlinImage(
    _ value: FlareImageResource
) -> FlareUIImageResource {
    FlareUIImageResource(
        key: mapFlareUIKotlinResourceKey(value.key)
    )
}

private func mapFlareUIKotlinResourceKey(
    _ value: FlareResourceKey
) -> FlareUIResourceKey {
    FlareUIResourceKey(
        resourceNamespace: value.resourceNamespace,
        name: value.name,
        platformNamespace: value.platformNamespace,
        platformName: value.platformName
    )
}
