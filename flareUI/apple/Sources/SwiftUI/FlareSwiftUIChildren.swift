@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIChildren: View {
    let nodes: [FlareUiNodeSnapshot]

    var body: some View {
        ForEach(nodes, id: \.id) { node in
            FlareSwiftUINode(node: node)
        }
    }
}
