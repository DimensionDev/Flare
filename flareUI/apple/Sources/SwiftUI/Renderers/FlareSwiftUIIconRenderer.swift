import FlareUIRuntime
import SwiftUI

struct FlareSwiftUIIconRenderer: View {
    let payload: FlareUIIconPayload
    let children: [FlareUINode]
    let resources: FlareAppleResources

    @ViewBuilder
    var body: some View {
        let image = Image(
            resources.imageName(payload.image),
            bundle: resources.bundle(for: payload.image.key.resourceNamespace)
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
