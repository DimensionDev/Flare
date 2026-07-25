import FlareUIRuntime
import SwiftUI

struct FlareSwiftUITextRenderer: View {
    let payload: FlareUITextPayload
    let children: [FlareUINode]
    let resources: FlareAppleResources

    var body: some View {
        Text(resources.string(payload.value))
            .multilineTextAlignment(.leading)
    }
}
