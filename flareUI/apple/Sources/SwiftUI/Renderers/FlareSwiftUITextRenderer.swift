@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUITextRenderer: View {
    let payload: FlareTextPayload
    let children: [FlareUiNodeSnapshot]

    var body: some View {
        Text(payload.value)
            .multilineTextAlignment(.leading)
    }
}
