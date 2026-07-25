@preconcurrency import FlareUIDemoKit
import SwiftUI

/// Renders a Flare UI tree with native SwiftUI primitives.
public struct FlareSwiftUIHost: View {
    @StateObject private var model: FlareTreeModel
    private let resources: FlareAppleResources

    public init(
        host: FlareUiTreeHost,
        resources: FlareAppleResources = .init(bundle: .main)
    ) {
        _model = StateObject(wrappedValue: FlareTreeModel(host: host))
        self.resources = resources
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: FlareSwiftUILayout.itemSpacing) {
            FlareSwiftUIChildren(
                nodes: model.nodes,
                resources: resources
            )
        }
    }
}
