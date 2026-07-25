@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUITextRenderer: View {
    let payload: FlareTextPayload
    let children: [FlareUiNodeSnapshot]
    let resources: FlareAppleResources

    var body: some View {
        Text(resources.string(payload.value))
            .multilineTextAlignment(.leading)
    }
}
