@preconcurrency import FlareUIDemoKit
import SwiftUI

struct FlareSwiftUIIconRenderer: View {
    let payload: FlareIconPayload
    let children: [FlareUiNodeSnapshot]
    let resources: FlareAppleResources

    @ViewBuilder
    var body: some View {
        let image = Image(
            resources.imageName(payload.image),
            bundle: resources.bundle(for: payload.image.key.namespace_)
        )
        .renderingMode(.template)

        if let description = payload.contentDescription {
            image.accessibilityLabel(
                Text(resources.string(description))
            )
        } else {
            image.accessibilityHidden(true)
        }
    }
}
