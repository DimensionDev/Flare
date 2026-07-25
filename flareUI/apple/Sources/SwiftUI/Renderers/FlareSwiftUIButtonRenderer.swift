@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIButtonRenderer: View {
    let payload: FlareButtonPayload
    let children: [FlareUiNodeSnapshot]
    let resources: FlareAppleResources

    var body: some View {
        Button(resources.string(payload.label)) {
            payload.performClick()
        }
        .disabled(!payload.enabled)
    }
}
