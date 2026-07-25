import FlareUIRuntime
import SwiftUI

struct FlareSwiftUIChildren: View {
    let nodes: [FlareUINode]
    let resources: FlareAppleResources

    var body: some View {
        ForEach(nodes, id: \.id) { node in
            FlareSwiftUINode(
                node: node,
                resources: resources
            )
        }
    }
}
