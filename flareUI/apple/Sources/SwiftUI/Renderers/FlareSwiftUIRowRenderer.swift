@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIRowRenderer: View {
    let payload: FlareRowPayload
    let children: [FlareUiNodeSnapshot]
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
