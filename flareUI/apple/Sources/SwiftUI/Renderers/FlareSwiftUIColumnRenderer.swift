import FlareUIRuntime
import SwiftUI

struct FlareSwiftUIColumnRenderer: View {
    let payload: FlareUIColumnPayload
    let children: [FlareUINode]
    let resources: FlareAppleResources

    var body: some View {
        VStack(alignment: .leading, spacing: FlareSwiftUILayout.itemSpacing) {
            FlareSwiftUIChildren(
                nodes: children,
                resources: resources
            )
        }
    }
}
