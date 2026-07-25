@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIColumnRenderer: View {
    let payload: FlareColumnPayload
    let children: [FlareUiNodeSnapshot]

    var body: some View {
        VStack(alignment: .leading, spacing: FlareSwiftUILayout.itemSpacing) {
            FlareSwiftUIChildren(nodes: children)
        }
    }
}
