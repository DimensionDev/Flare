import FlareUIRuntime
import SwiftUI

struct FlareSwiftUIRowRenderer: View {
    let payload: FlareUIRowPayload
    let children: [FlareUINode]
    let resources: FlareAppleResources

    var body: some View {
        HStack(alignment: .top, spacing: FlareSwiftUILayout.itemSpacing) {
            FlareSwiftUIChildren(
                nodes: children,
                resources: resources
            )
        }
    }
}
