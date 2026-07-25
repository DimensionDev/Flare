@preconcurrency import FlareUIDemoKit
import Foundation

/// Resolves resource references without making Flare UI own any resources.
///
/// Apps can keep everything in one bundle or route each resource namespace to
/// a feature-specific bundle.
public struct FlareAppleResources {
    private let bundleForNamespace: (String) -> Bundle

    public init(bundle: Bundle) {
        bundleForNamespace = { _ in bundle }
    }

    public init(
        bundleForNamespace: @escaping (String) -> Bundle
    ) {
        self.bundleForNamespace = bundleForNamespace
    }

    func string(_ value: FlareText) -> String {
        if let literal = value.literal {
            return literal
        }
        guard let resource = value.resource else {
            preconditionFailure("FlareText has neither a literal nor a resource")
        }
        return bundle(for: resource.key.namespace_)
            .localizedString(
                forKey: resource.key.name,
                value: resource.key.name,
                table: resource.key.platformNamespace
            )
    }

    func bundle(for namespace: String) -> Bundle {
        bundleForNamespace(namespace)
    }

    func imageName(_ resource: FlareImageResource) -> String {
        resource.key.platformName
    }
}
