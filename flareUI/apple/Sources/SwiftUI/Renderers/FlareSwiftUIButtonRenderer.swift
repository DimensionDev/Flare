import FlareUIRuntime
import SwiftUI

struct FlareSwiftUIButtonRenderer: View {
    let payload: FlareUIButtonPayload
    let children: [FlareUINode]
    let resources: FlareAppleResources

    var body: some View {
        Button(resources.string(payload.label)) {
            payload.performClick()
        }
        .disabled(!payload.enabled)
    }
}
