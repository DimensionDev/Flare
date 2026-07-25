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

    public func string(_ value: FlareUIText) -> String {
        switch value {
        case let .literal(literal):
            return literal
        case let .resource(resource):
            return bundle(for: resource.key.resourceNamespace)
                .localizedString(
                    forKey: resource.key.name,
                    value: resource.key.name,
                    table: resource.key.platformNamespace
                )
        }
    }

    public func bundle(for namespace: String) -> Bundle {
        bundleForNamespace(namespace)
    }

    public func imageName(_ resource: FlareUIImageResource) -> String {
        resource.key.platformName
    }
}
