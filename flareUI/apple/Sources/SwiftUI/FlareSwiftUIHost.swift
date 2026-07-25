import FlareUIRuntime
import SwiftUI

/// Renders a Flare UI tree with native SwiftUI primitives.
public struct FlareSwiftUIHost: View {
    @StateObject private var model: FlareTreeModel
    private let resources: FlareAppleResources

    public init(
        resources: FlareAppleResources = .init(bundle: .main),
        makeHost: @escaping @MainActor () -> any FlareUITreeHost
    ) {
        _model = StateObject(wrappedValue: FlareTreeModel(makeHost: makeHost))
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
