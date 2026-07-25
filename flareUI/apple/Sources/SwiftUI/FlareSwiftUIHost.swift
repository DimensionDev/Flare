@preconcurrency import FlareUIDemoKit
import SwiftUI

/// Renders a Flare UI tree with native SwiftUI primitives.
public struct FlareSwiftUIHost: View {
    @StateObject private var model: FlareTreeModel

    public init(host: FlareUiTreeHost) {
        _model = StateObject(wrappedValue: FlareTreeModel(host: host))
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: FlareSwiftUILayout.itemSpacing) {
            FlareSwiftUIChildren(nodes: model.nodes)
        }
    }
}
