@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIButtonRenderer: View {
    let payload: FlareButtonPayload
    let children: [FlareUiNodeSnapshot]

    var body: some View {
        Button(payload.label) {
            payload.performClick()
        }
        .disabled(!payload.enabled)
    }
}
